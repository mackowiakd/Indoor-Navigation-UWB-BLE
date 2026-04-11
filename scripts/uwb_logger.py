import asyncio
import time
import csv
from bleak import BleakScanner, BleakClient
from bleak.exc import BleakError

# Konfiguracja
DEVICE_MAC = "98:3D:AE:AC:4D:B2"
CHAR_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8"
LOG_FILE = "log_uwb.csv"

last_time = 0
log_data = []

def notification_handler(sender, data):
    global last_time
    current_time = time.time()
    
    # Obliczamy czas od ostatniego pakietu (Delta)
    delta = current_time - last_time if last_time != 0 else 0
    last_time = current_time
    
    decoded_data = data.decode('utf-8')
    
    # Wypisz w konsoli
    print(f"[{current_time:.3f}] Delta: {delta:.3f}s | Dane: {decoded_data}")
    
    # Zapisz do logów
    log_data.append([current_time, delta, decoded_data])

async def main():
    print(f"Szukam urządzenia: {DEVICE_MAC}...")
    
    device = await BleakScanner.find_device_by_filter(
            lambda d, ad: d.address.upper() == DEVICE_MAC.upper(),
            timeout=20.0
        )

    if not device:
        print("[-] Target not found in the vicinity. Upewnij się, że ESP leży blisko!")
        return

    # POPRAWKA: Zmiana logger.info na print
    print(f"[+] Target found: {device.name} [{device.address}]. Establishing connection...")

   MAX_RETRIES = 5
    for attempt in range(MAX_RETRIES):
        try:
            async with BleakClient(device, timeout=10.0) as client:
                print(">>> POŁĄCZONO SUKCESEM! Subskrybowanie danych... <<<")
                await client.start_notify(CHAR_UUID, notification_handler)
                
                test_duration = 60
                print(f"Test potrwa {test_duration} sekund...\n")
                await asyncio.sleep(test_duration)
                
                await client.stop_notify(CHAR_UUID)
                print("Koniec testu. Zapisuję do pliku CSV...")
                break # Jeśli doszliśmy tutaj, przerywamy pętlę prób (sukces!)

        except Exception as e:
            print(f"[!] Błąd połączenia: {e}")
            if attempt < MAX_RETRIES - 1:
                print(f"--- Ponawiam próbę ({attempt+2}/{MAX_RETRIES}) za 2 sekundy... ---")
                await asyncio.sleep(2)
            else:
                print("--- Osiągnięto limit prób. Test przerwany. ---")
                return 

    # Zapis logów do pliku
    with open(LOG_FILE, mode='w', newline='') as file:
        writer = csv.writer(file)
        writer.writerow(["Timestamp", "Delta_sec", "Data"])
        writer.writerows(log_data)
        
    print(f"Zapisano dane do {LOG_FILE}. Przeanalizuj kolumnę Delta_sec!")

if __name__ == "__main__":
    asyncio.run(main())
