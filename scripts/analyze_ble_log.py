"""Analiza logów z ble_timing_test.py: statystyki, utrata pakietów, jitter, histogram i dystrybuanta.

Uruchom:  python analyze_ble_log.py [ble_notify_log.csv] [ble_rtt_log.csv]
Wymaga: pandas, numpy, matplotlib.
"""
import sys

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import os

# 1. Pobierz folder, w którym znajduje się Twój skrypt .py
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

# 2. Połącz ścieżki plików CSV z katalogiem skryptu
notify_file = sys.argv[1] if len(sys.argv) > 1 else "ble_notify_log.csv"
rtt_file = sys.argv[2] if len(sys.argv) > 2 else "ble_rtt_log.csv"

notify_path = os.path.join(SCRIPT_DIR, notify_file)
rtt_path = os.path.join(SCRIPT_DIR, rtt_file)



def describe_ms(series_ms, name):
    s = series_ms.dropna()
    if s.empty:
        print(f"{name}: brak danych")
        return
    q = s.quantile([0.5, 0.95, 0.99])
    print(
        f"{name}: n={len(s)}  średnia={s.mean():.1f} ms  std={s.std():.1f} ms  "
        f"p50={q[0.5]:.1f}  p95={q[0.95]:.1f}  p99={q[0.99]:.1f}  min={s.min():.1f}  max={s.max():.1f}"
    )


# --- Test A: powiadomienia --------------------------------------------------
df = pd.read_csv(notify_path)
delta_ms = df["delta_s"] * 1000.0
duration = df["t_perf_s"].iloc[-1] - df["t_perf_s"].iloc[0] if len(df) > 1 else float("nan")
print(f"Pakietów: {len(df)}  czas: {duration:.1f} s  średnia częstość: {len(df) / duration:.2f} Hz")
describe_ms(delta_ms, "Odstęp między pakietami")

if df["seq"].notna().any():
    seq = df["seq"].dropna().astype(int)
    expected = seq.iloc[-1] - seq.iloc[0] + 1
    lost = expected - len(seq)
    print(f"Utrata pakietów (wg numerów sekwencyjnych): {lost} z {expected} ({100 * lost / expected:.2f}%)")
    # Jitter transportu bez synchronizacji zegarów: różnica odstępów po stronie PC i po stronie ESP.
    d = df.dropna(subset=["seq", "esp_us"]).copy()
    d["esp_delta_ms"] = d["esp_us"].diff() / 1000.0
    d["jitter_ms"] = (d["delta_s"] * 1000.0 - d["esp_delta_ms"]).abs()
    describe_ms(d["jitter_ms"], "Jitter transportu |Δ_PC − Δ_ESP|")

# --- Test B: RTT -------------------------------------------------------------
try:
    rtt = pd.read_csv(rtt_path)
    describe_ms(rtt["rtt_write_ms"], "RTT: Write With Response")
except FileNotFoundError:
    rtt = None

# --- Wykresy -----------------------------------------------------------------
fig, ax = plt.subplots(1, 3, figsize=(15, 4))
dm = delta_ms.dropna()
ax[0].hist(dm, bins=60)
ax[0].set(title="Odstęp między powiadomieniami", xlabel="ms", ylabel="liczba pakietów")
xs = np.sort(dm.values)
ax[1].plot(xs, np.arange(1, len(xs) + 1) / len(xs))
ax[1].set(title="Dystrybuanta odstępów", xlabel="ms", ylabel="P(X ≤ x)")
ax[1].grid(True)
if rtt is not None and rtt["rtt_write_ms"].notna().any():
    ax[2].hist(rtt["rtt_write_ms"].dropna(), bins=40)
    ax[2].set(title="RTT: Write With Response", xlabel="ms", ylabel="liczba zapisów")
plt.tight_layout()
# 2. Połącz ścieżkę folderu z nazwą wykresu
PLOT_FILE = os.path.join(SCRIPT_DIR, "ble_timing_plots.png")
plt.savefig(PLOT_FILE, dpi=150)
print(f"Zapisano {PLOT_FILE}")