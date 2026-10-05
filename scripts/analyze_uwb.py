#!/usr/bin/env python3
"""Analiza offline pomiarów UWB z klatki schodowej.

Wejście:
  - CSV z scripts/uwb_logger.py (kolumny: Timestamp, Delta_sec, Kind, Id, Distance_m, RefPoint, Raw),
  - konfiguracja klatki (JSON z tools/stairwell-editor): kotwice, bryła klatki, punkty referencyjne.

Dla każdego punktu referencyjnego (RefPoint w logu = nazwa punktu w konfiguracji) liczy:
  1. błąd dystansu do każdej kotwicy: surowy vs średnia krocząca, mediana, Kalman,
  2. błąd pozycji 3D po trilateracji (solver zgodny z Positioning3D.kt) dla każdego filtra.

Tylko biblioteka standardowa. Jeśli zainstalowany jest matplotlib, flaga --plots zapisuje wykresy Z(t).

Przykład:
  python scripts/analyze_uwb.py log_uwb.csv --config stairwell_config.json --summary wyniki.csv
"""
import argparse
import csv
import json
import math
import os
import statistics
import sys
from collections import defaultdict


# ---------------------------------------------------------------- pomocnicze
def norm_id(raw):
    s = raw.strip()
    if s.lower().startswith("0x"):
        s = s[2:]
    try:
        return "0x%04x" % int(s, 16)
    except ValueError:
        return raw.strip()


def percentile(values, p):
    if not values:
        return float("nan")
    s = sorted(values)
    k = (len(s) - 1) * p / 100.0
    lo, hi = int(math.floor(k)), int(math.ceil(k))
    return s[lo] + (s[hi] - s[lo]) * (k - lo)


def rms(values):
    return math.sqrt(sum(v * v for v in values) / len(values)) if values else float("nan")


# ---------------------------------------------------------------- filtry (odpowiadają DistanceFilters.kt)
class RawFilter:
    def update(self, m, dt):
        return m


class MovingAverage:
    def __init__(self, window=5):
        self.window, self.buf = window, []

    def update(self, m, dt):
        self.buf = (self.buf + [m])[-self.window:]
        return sum(self.buf) / len(self.buf)


class Median:
    def __init__(self, window=5):
        self.window, self.buf = window, []

    def update(self, m, dt):
        self.buf = (self.buf + [m])[-self.window:]
        return statistics.median(self.buf)


class Kalman:
    def __init__(self, q=0.5, r=0.01, gate=0.0, max_outliers=5):
        self.q, self.r, self.gate, self.max_outliers = q, r, gate, max_outliers
        self.init = False
        self.outliers = 0

    def _initialize(self, m):
        self.d, self.v = m, 0.0
        self.p00, self.p01, self.p10, self.p11 = self.r, 0.0, 0.0, 1.0
        self.outliers, self.init = 0, True
        return self.d

    def update(self, m, dt):
        if not self.init:
            return self._initialize(m)
        dt = max(dt, 1e-3)
        d_pred = self.d + self.v * dt
        a00 = self.p00 + dt * (self.p10 + self.p01) + dt * dt * self.p11
        a01 = self.p01 + dt * self.p11
        a10 = self.p10 + dt * self.p11
        a11 = self.p11
        q, dt2 = self.q, dt * dt
        pp00 = a00 + q * dt2 * dt2 / 4
        pp01 = a01 + q * dt2 * dt / 2
        pp10 = a10 + q * dt2 * dt / 2
        pp11 = a11 + q * dt2
        s = pp00 + self.r
        innov = m - d_pred
        if self.gate > 0 and abs(innov) > self.gate * math.sqrt(s):
            self.outliers += 1
            if self.outliers >= self.max_outliers:
                return self._initialize(m)
            self.d = d_pred
            self.p00, self.p01, self.p10, self.p11 = pp00, pp01, pp10, pp11
            return self.d
        self.outliers = 0
        k0, k1 = pp00 / s, pp10 / s
        self.d = d_pred + k0 * innov
        self.v += k1 * innov
        self.p00 = (1 - k0) * pp00
        self.p01 = (1 - k0) * pp01
        self.p10 = pp10 - k1 * pp00
        self.p11 = pp11 - k1 * pp01
        return self.d


FILTERS = {
    "raw": RawFilter,
    "mov_avg5": lambda: MovingAverage(5),
    "median5": lambda: Median(5),
    "kalman": lambda: Kalman(0.5, 0.01),
    "kalman_gate": lambda: Kalman(0.5, 0.01, gate=4.0),
}


# ---------------------------------------------------------------- bryła klatki i solver 3D (odpowiada Positioning3D.kt)
class Volume:
    def __init__(self, polygon, z_min, z_max):
        self.poly = [(p["x"], p["y"]) for p in polygon]
        self.z_min, self.z_max = z_min, z_max

    def _inside(self, x, y):
        inside, j = False, len(self.poly) - 1
        for i, (xi, yi) in enumerate(self.poly):
            xj, yj = self.poly[j]
            if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
                inside = not inside
            j = i
        return inside

    def _boundary_dist(self, px, py):
        best = float("inf")
        for i, (ax, ay) in enumerate(self.poly):
            bx, by = self.poly[(i + 1) % len(self.poly)]
            dx, dy = bx - ax, by - ay
            l2 = dx * dx + dy * dy
            t = 0.0 if l2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / l2))
            best = min(best, math.hypot(px - (ax + t * dx), py - (ay + t * dy)))
        return best

    def contains(self, x, y, z, margin=0.0):
        if z < self.z_min - margin or z > self.z_max + margin:
            return False
        return self._inside(x, y) or self._boundary_dist(x, y) <= margin


def _dist(p, a):
    return math.sqrt((p[0] - a[0]) ** 2 + (p[1] - a[1]) ** 2 + (p[2] - a[2]) ** 2)


def _solve3(a, b):
    m = [a[i][:] + [b[i]] for i in range(3)]
    for col in range(3):
        piv = max(range(col, 3), key=lambda r: abs(m[r][col]))
        if abs(m[piv][col]) < 1e-12:
            return None
        m[col], m[piv] = m[piv], m[col]
        for r in range(col + 1, 3):
            f = m[r][col] / m[col][col]
            for c in range(col, 4):
                m[r][c] -= f * m[col][c]
    x = [0.0] * 3
    for i in (2, 1, 0):
        x[i] = (m[i][3] - sum(m[i][c] * x[c] for c in range(i + 1, 3))) / m[i][i]
    return x


def _chi2(p, pts, sig):
    return sum(((_dist(p, a) - a[3]) / s) ** 2 for a, s in zip(pts, sig))


def _refine(start, pts, sig, iters=60):
    p, cost, lam = list(start), _chi2(start, pts, sig), 1e-2
    for _ in range(iters):
        jtj, g = [[0.0] * 3 for _ in range(3)], [0.0] * 3
        for a, s in zip(pts, sig):
            d = max(_dist(p, a), 1e-9)
            r = (d - a[3]) / s
            j = [(p[k] - a[k]) / d / s for k in range(3)]
            for i in range(3):
                g[i] += j[i] * r
                for k in range(3):
                    jtj[i][k] += j[i] * j[k]
        improved = False
        for _try in range(10):
            m = [row[:] for row in jtj]
            for k in range(3):
                m[k][k] += lam * (jtj[k][k] + 1e-3)
            delta = _solve3(m, [-g[0], -g[1], -g[2]])
            if delta is None:
                lam *= 4
                continue
            np_ = [p[k] + delta[k] for k in range(3)]
            nc = _chi2(np_, pts, sig)
            if nc < cost:
                p, cost, lam, improved = np_, nc, max(lam / 3, 1e-9), True
                break
            lam *= 4
        if not improved or math.sqrt(sum(d * d for d in delta)) < 1e-7:
            break
    return (p, cost) if math.isfinite(cost) else None


def solve_position(pts, volume=None, hint=None, ranging_sigma=0.10, margin=0.30, gap=4.0):
    """pts: lista (x, y, z, dystans, sigma_kotwicy). Zwraca dict lub None."""
    pts = [a for a in pts if a[3] > 0 and all(math.isfinite(v) for v in a)]
    if len(pts) < 3:
        return None
    sig = [math.hypot(max(a[4], 0.0), ranging_sigma) for a in pts]
    cx, cy, cz = (sum(a[k] for a in pts) / len(pts) for k in range(3))
    bases = [(cx, cy, cz)]
    if volume:
        bases.append((sum(p[0] for p in volume.poly) / len(volume.poly),
                      sum(p[1] for p in volume.poly) / len(volume.poly),
                      (volume.z_min + volume.z_max) / 2))
    r = max(0.5, sum(a[3] for a in pts) / len(pts) * 0.5)
    offsets = [(0, 0, r), (0, 0, -r), (r, 0, r), (r, 0, -r), (-r, 0, r), (-r, 0, -r),
               (0, r, r), (0, r, -r), (0, -r, r), (0, -r, -r)]
    starts = [(b[0] + o[0], b[1] + o[1], b[2] + o[2]) for b in bases for o in offsets]
    if hint:
        starts.append(tuple(hint))
    cands = [c for c in (_refine(s, pts, sig) for s in starts) if c]
    distinct = []
    for p, cost in sorted(cands, key=lambda c: c[1]):
        if all(_dist(p, q) >= 0.05 for q, _ in distinct):
            distinct.append((p, cost))
    if not distinct:
        return None
    inside = [c for c in distinct if volume is None or volume.contains(*c[0], margin)]
    pool = inside or distinct
    best, ambiguous = pool[0], len(pool) > 1 and pool[1][1] - pool[0][1] < gap
    if ambiguous and hint:
        close = [c for c in pool if c[1] - pool[0][1] < gap]
        best, ambiguous = min(close, key=lambda c: _dist(c[0], hint)), False
    return {"pos": best[0], "chi2": best[1], "inside": bool(inside), "ambiguous": ambiguous}


# ---------------------------------------------------------------- wczytywanie
def load_log(path):
    """Zwraca {ref_point: {timestamp: {anchor_id: dystans}}} dla rekordów UWB (Kind == 'U')."""
    data = defaultdict(lambda: defaultdict(dict))
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if row.get("Kind") != "U":
                continue
            try:
                ts, dist = float(row["Timestamp"]), float(row["Distance_m"])
            except (KeyError, ValueError):
                continue
            data[row.get("RefPoint", "")][ts][norm_id(row["Id"])] = dist
    return data


def load_config(path):
    with open(path, encoding="utf-8") as f:
        cfg = json.load(f)
    anchors = {norm_id(a["id"]): (a["x"], a["y"], a["z"], a.get("sigma", 0.05) or 0.05) for a in cfg["anchors"]}
    refs = {r["name"]: (r["x"], r["y"], r["z"]) for r in cfg.get("referencePoints", [])}
    v = cfg["volume"]
    return anchors, refs, Volume(v["polygon"], v["zMin"], v["zMax"])


# ---------------------------------------------------------------- analiza
def analyze(log, anchors, refs, volume, solver_on=True):
    rows = []        # wiersze podsumowania
    series = {}      # (ref, metoda) -> lista (ts, z)
    for ref, frames in sorted(log.items()):
        truth = refs.get(ref)
        if truth is None:
            print(f"[!] Punkt '{ref}' z logu nie występuje w konfiguracji - pomijam błąd względem prawdy.", file=sys.stderr)
        timestamps = sorted(frames)
        for name, factory in FILTERS.items():
            filters, last_ts = {}, {}
            dist_err = defaultdict(list)
            errs3, errs_xy, errs_z = [], [], []
            n_amb = n_out = n_solved = 0
            hint = None
            for ts in timestamps:
                pts = []
                for aid, d in frames[ts].items():
                    if aid not in anchors:
                        continue
                    flt = filters.setdefault(aid, factory())
                    dt = min(max(ts - last_ts.get(aid, ts - 0.1), 0.01), 1.0)
                    last_ts[aid] = ts
                    dfilt = flt.update(d, dt)
                    ax, ay, az, asig = anchors[aid]
                    pts.append((ax, ay, az, dfilt, asig))
                    if truth:
                        dist_err[aid].append(dfilt - math.dist(truth, (ax, ay, az)))
                if solver_on and len(pts) >= 3:
                    res = solve_position(pts, volume, hint)
                    if res:
                        n_solved += 1
                        n_amb += res["ambiguous"]
                        n_out += not res["inside"]
                        hint = res["pos"]
                        series.setdefault((ref, name), []).append((ts, res["pos"][2]))
                        if truth:
                            e = [res["pos"][k] - truth[k] for k in range(3)]
                            errs3.append(math.sqrt(sum(v * v for v in e)))
                            errs_xy.append(math.hypot(e[0], e[1]))
                            errs_z.append(abs(e[2]))
            all_d = [v for lst in dist_err.values() for v in lst]
            rows.append({
                "ref": ref, "filter": name, "frames": len(timestamps),
                "dist_bias_m": statistics.mean(all_d) if all_d else float("nan"),
                "dist_rms_m": rms(all_d),
                "solved": n_solved, "ambiguous": n_amb, "outside_volume": n_out,
                "pos3d_rms_m": rms(errs3), "pos3d_p95_m": percentile(errs3, 95),
                "pos_xy_rms_m": rms(errs_xy), "pos_z_rms_m": rms(errs_z),
            })
    return rows, series


def print_table(rows):
    cols = ["ref", "filter", "frames", "dist_bias_m", "dist_rms_m", "solved", "ambiguous", "outside_volume",
            "pos3d_rms_m", "pos3d_p95_m", "pos_xy_rms_m", "pos_z_rms_m"]
    fmt = lambda v: f"{v:.3f}" if isinstance(v, float) else str(v)
    table = [cols] + [[fmt(r[c]) for c in cols] for r in rows]
    widths = [max(len(row[i]) for row in table) for i in range(len(cols))]
    for row in table:
        print("  ".join(c.ljust(w) for c, w in zip(row, widths)))


def save_plots(series, refs, out_dir):
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print("[!] Brak matplotlib - pomijam wykresy (to opcjonalne).", file=sys.stderr)
        return
    os.makedirs(out_dir, exist_ok=True)
    for ref in sorted({r for r, _ in series}):
        plt.figure(figsize=(9, 4))
        for (r, name), pts in series.items():
            if r == ref:
                t0 = pts[0][0]
                plt.plot([t - t0 for t, _ in pts], [z for _, z in pts], label=name, linewidth=1)
        if ref in refs:
            plt.axhline(refs[ref][2], color="k", linestyle="--", label="Z referencyjne")
        plt.xlabel("czas [s]")
        plt.ylabel("Z [m]")
        plt.title(f"Estymata Z - punkt {ref or '(bez etykiety)'}")
        plt.legend(fontsize=7)
        plt.tight_layout()
        plt.savefig(os.path.join(out_dir, f"z_{ref or 'brak'}.png"), dpi=150)
        plt.close()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("log", help="CSV z uwb_logger.py")
    ap.add_argument("--config", required=True, help="JSON klatki z edytora (kotwice, bryła, punkty referencyjne)")
    ap.add_argument("--summary", help="zapisz tabelę wyników do CSV")
    ap.add_argument("--plots", help="katalog na wykresy Z(t) (wymaga matplotlib)")
    ap.add_argument("--no-solver", action="store_true", help="tylko statystyki dystansów, bez trilateracji")
    args = ap.parse_args()

    anchors, refs, volume = load_config(args.config)
    log = load_log(args.log)
    if not log:
        sys.exit("Brak rekordów UWB w logu (kolumna Kind == 'U'). Czy log pochodzi z uwb_logger.py?")

    rows, series = analyze(log, anchors, refs, volume, solver_on=not args.no_solver)
    print_table(rows)
    if args.summary:
        with open(args.summary, "w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
            w.writeheader()
            w.writerows(rows)
        print(f"\nZapisano podsumowanie: {args.summary}")
    if args.plots:
        save_plots(series, refs, args.plots)


if __name__ == "__main__":
    main()
