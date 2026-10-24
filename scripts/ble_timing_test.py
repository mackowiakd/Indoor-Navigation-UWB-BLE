"""Test czasowy łącza BLE: tag (ESP32) <-> komputer (bleak).

Test A: odstępy między kolejnymi powiadomieniami (Notify) przez DURATION_S sekund.
Test B: czas odpowiedzi na zapis (Write With Response) na istniejącą charakterystykę filtra.
        Czas = od wysłania zapisu do odebrania potwierdzenia ATT; obejmuje onWrite() po stronie ESP.

Wyniki: ble_notify_log.csv oraz ble_rtt_log.csv (analiza: analyze_ble_log.py).
"""
import asyncio
import csv
import re
import time
import os

from bleak import BleakClient, BleakScanner

# --- konfiguracja -----------------------------------------------------------
DEVICE_MAC = "A4:E5:7C:DE:BC:F2"
NOTIFY_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8"  # telemetria (ESP -> klient)
WRITE_UUID = "c0de0001-feed-4688-b7f5-ea07361b26a8"  # filtr (klient -> ESP); None = pomiń test B
# Poprawny, ten sam filtr za każdym razem: stan ESP się nie zmienia. Wpisz adresy, które ESP już zna.
WRITE_PAYLOAD = b"U:0x0003,0x0002;B:"
DURATION_S = 60  # czas testu A
RTT_COUNT = 200  # liczba zapisów w teście B
RTT_PERIOD_S = 0.1  # odstęp między zapisami
MAX_RETRIES = 5


NOTIFY_LOG = "ble_notify_log.csv"
RTT_LOG = "ble_rtt_log.csv"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

# Opcjonalny format testowy z firmware: "T:<seq>:<micros_esp>" (liczy utratę pakietów i jitter)
SEQ_RE = re.compile(r"T:(\d+):(\d+)")

# --- dane -------------------------------------------------------------------
notify_rows = []  # (t_perf, delta_s, seq, esp_us, n_bytes, text)
rtt_rows = []  # (n, rtt_write_ms)
_last_t = None


def on_notify(_sender, data: bytearray):
    """Odbiór telemetrii. Czas z zegara monotonicznego (nie podlega korekcie czasu systemowego)."""
    global _last_t
    t = time.perf_counter()
    delta = None if _last_t is None else t - _last_t
    _last_t = t
    text = bytes(data).decode("utf-8", errors="replace")
    m = SEQ_RE.match(text)
    seq, esp_us = (int(m.group(1)), int(m.group(2))) if m else (None, None)
    notify_rows.append((t, delta, seq, esp_us, len(data), text))


async def test_notify(client: BleakClient):
    await client.start_notify(NOTIFY_UUID, on_notify)  # bleak sam zapisuje deskryptor CCCD (0x2902)
    print(f"Test A: {DURATION_S} s zbierania powiadomień...")
    await asyncio.sleep(DURATION_S)
    await client.stop_notify(NOTIFY_UUID)
    print(f"  odebrano {len(notify_rows)} pakietów")


async def test_rtt(client: BleakClient):
    if WRITE_UUID is None:
        print("Test B pominięty (WRITE_UUID = None).")
        return
    print(f"Test B: {RTT_COUNT} zapisów co {RTT_PERIOD_S} s...")
    for n in range(RTT_COUNT):
        t0 = time.perf_counter()
        await client.write_gatt_char(WRITE_UUID, WRITE_PAYLOAD, response=True)
        rtt_rows.append((n, (time.perf_counter() - t0) * 1000.0))
        await asyncio.sleep(RTT_PERIOD_S)


async def connect_and_run():
    for attempt in range(1, MAX_RETRIES + 1):
        print(f"Szukam urządzenia {DEVICE_MAC} (próba {attempt}/{MAX_RETRIES})...")
        device = await BleakScanner.find_device_by_address(DEVICE_MAC, timeout=20.0)
        if device is None:
            print("[-] Nie znaleziono urządzenia. Połóż ESP blisko i sprawdź, czy rozgłasza.")
            continue
        try:
            async with BleakClient(device, timeout=10.0) as client:
                print(f">>> POŁĄCZONO (MTU = {client.mtu_size}) <<<")
                await test_notify(client)
                await test_rtt(client)
                return True
        except Exception as e:  # noqa: BLE001
            print(f"[!] Błąd połączenia: {e}")
            await asyncio.sleep(2)
    return False


def save():

    LOG_FILE = os.path.join(SCRIPT_DIR, NOTIFY_LOG)
    with open(LOG_FILE, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["t_perf_s", "delta_s", "seq", "esp_us", "bytes", "data"])
        w.writerows(notify_rows)
    LOG_FILE = os.path.join(SCRIPT_DIR, RTT_LOG)
    with open(LOG_FILE, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["n", "rtt_write_ms"])
        w.writerows(rtt_rows)
    print(f"Zapisano {NOTIFY_LOG} ({len(notify_rows)} wierszy) i {RTT_LOG} ({len(rtt_rows)} wierszy).")


if __name__ == "__main__":
    try:
        asyncio.run(connect_and_run())
    except KeyboardInterrupt:
        print("Przerwano ręcznie, zapisuję to, co zebrano.")
    finally:
        save()