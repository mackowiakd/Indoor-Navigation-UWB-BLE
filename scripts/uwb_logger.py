import argparse
import asyncio
import time
import csv
from bleak import BleakScanner, BleakClient
from bleak.exc import BleakError

# Konfiguracja (domyślne wartości, można nadpisać argumentami wiersza poleceń)
DEVICE_MAC = "98:3D:AE:AC:4D:B2"
CHAR_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8"
LOG_FILE = "log_uwb.csv"

last_time = 0
log_data = []
ref_point = ""  # etykieta punktu referencyjnego, w którym stoi tag podczas pomiaru


def parse_payload(text):
    """Rozbija ramkę z ESP32 (np. 'U_0x0001=2.45;U_0x0002=5.10;B_aa:bb:..=1.50;')
    na listę (typ, id, dystans). Pomija rekordy, których nie da się sparsować."""
    records = []
    for record in text.split(";"):
        record = record.strip()
        if "=" not in record or record[:2] not in ("U_", "B_"):
            continue
        key, value = record.split("=", 1)
        try:
            records.append((key[0], key[2:], float(value)))
        except ValueError:
            continue
    return records


def notification_handler(sender, data):
    global last_time
    current_time = time.time()

    # Obliczamy czas od ostatniego pakietu (Delta)
    delta = current_time - last_time if last_time != 0 else 0
    last_time = current_time

    decoded_data = data.decode('utf-8')

    # Wypisz w konsoli
    print(f"[{current_time:.3f}] Delta: {delta:.3f}s | Dane: {decoded_data}")

    # Zapisz do logów: jeden wiersz na każdy pomiar (format "długi", wygodny do analizy)
    # Ramki, których nie da się sparsować (np. wynik kalibracji), trafiają z pustym typem i ID.
    records = parse_payload(decoded_data)
    if not records:
        log_data.append([current_time, delta, "", "", "", ref_point, decoded_data])
    for kind, device_id, distance in records:
        log_data.append([current_time, delta, kind, device_id, distance, ref_point, decoded_data])


async def main(args):
    MAX_RETRIES = 5

    for attempt in range(MAX_RETRIES):
        print(f"Szukam urządzenia: {args.mac}...")

        device = await BleakScanner.find_device_by_filter(
            lambda d, ad: d.address.upper() == args.mac.upper(),
            timeout=20.0
        )

        if not device:
            print("[-] Target not found in the vicinity. Upewnij się, że ESP leży blisko!")
            return

        print(f"[+] Target found: {device.name} [{device.address}]. Establishing connection...")

        try:
            async with BleakClient(device, timeout=10.0) as client:
                print(">>> POŁĄCZONO SUKCESEM! Subskrybowanie danych... <<<")
                await client.start_notify(CHAR_UUID, notification_handler)

                print(f"Test potrwa {args.duration} sekund (punkt referencyjny: '{ref_point}')...\n")
                await asyncio.sleep(args.duration)

                await client.stop_notify(CHAR_UUID)
                print("Koniec testu. Zapisuję do pliku CSV...")
                break  # Jeśli doszliśmy tutaj, przerywamy pętlę prób (sukces!)

        except Exception as e:
            print(f"[!] Błąd połączenia: {e}")
            if attempt < MAX_RETRIES - 1:
                print(f"--- Ponawiam próbę ({attempt+2}/{MAX_RETRIES}) za 2 sekundy... ---")
                await asyncio.sleep(2)
            else:
                print("--- Osiągnięto limit prób. Test przerwany. ---")
                return

    # Zapis logów do pliku (dopisywanie, żeby kilka punktów referencyjnych trafiło do jednego CSV)
    write_header = True
    try:
        with open(args.out, mode='r', newline='') as existing:
            write_header = existing.read(1) == ""
    except FileNotFoundError:
        pass

    with open(args.out, mode='a', newline='') as file:
        writer = csv.writer(file)
        if write_header:
            writer.writerow(["Timestamp", "Delta_sec", "Kind", "Id", "Distance_m", "RefPoint", "Raw"])
        writer.writerows(log_data)

    print(f"Zapisano {len(log_data)} wierszy do {args.out}. Przeanalizuj kolumnę Delta_sec!")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Logger pomiarów UWB/BLE z ESP32 przez BLE (GATT notify).")
    parser.add_argument("--mac", default=DEVICE_MAC, help="adres BLE tagu (domyślnie stały z pliku)")
    parser.add_argument("--ref", default="", help="etykieta punktu referencyjnego, np. P3 (do analizy błędu)")
    parser.add_argument("--duration", type=int, default=60, help="czas pomiaru w sekundach")
    parser.add_argument("--out", default=LOG_FILE, help="plik CSV (dopisywanie)")
    cli_args = parser.parse_args()
    ref_point = cli_args.ref
    asyncio.run(main(cli_args))
