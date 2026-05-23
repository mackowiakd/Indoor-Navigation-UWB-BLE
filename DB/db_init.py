import psycopg2
import random
from datetime import datetime, timedelta

# Konfiguracja dla lokalnego Dockera
DB_CONFIG = {
    "dbname": "uwb_ble_db",
    "user": "domi",
    "password": "1234",
    "host": "localhost",
    "port": "5432"
}

def setup_and_generate_data():
    conn = psycopg2.connect(**DB_CONFIG)
    cur = conn.cursor()

    cur.execute("CREATE SCHEMA IF NOT EXISTS dwh_test;")
    cur.execute("SET search_path TO dwh_test, public;")

    print("KROK 1: Tworzenie struktury bazy (jeśli nie istnieje)...")
    cur.execute("""
        CREATE TABLE IF NOT EXISTS Dim_Topology (
            Location_ID SERIAL PRIMARY KEY,
            Building VARCHAR(50) NOT NULL,
            Wing VARCHAR(50),
            Floor INT NOT NULL,
            Room_Name VARCHAR(100)
        );

        CREATE TABLE IF NOT EXISTS Dim_IoT_Devices (
            MAC_Address VARCHAR(17) PRIMARY KEY,
            Device_Type VARCHAR(20) NOT NULL,
            Location_ID INT REFERENCES Dim_Topology(Location_ID),
            Semantic_Role VARCHAR(100),
            TX_Power_Config INT
        );

        CREATE TABLE IF NOT EXISTS Fact_Telemetry (
            Telemetry_ID SERIAL PRIMARY KEY,
            Timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            Scanner_ID VARCHAR(50),
            MAC_Address VARCHAR(17) REFERENCES Dim_IoT_Devices(MAC_Address),
            UWB_Distance_m DECIMAL(5,2),
            BLE_RSSI_dBm INT,
            Is_Ignored_By_Filter BOOLEAN
        );

        CREATE INDEX IF NOT EXISTS idx_telemetry_time_mac ON Fact_Telemetry(Timestamp, MAC_Address);
    """)

    ble_devices = [] # Lista do przechowywania wygenerowanych tagów BLE do telemetrii
    mac_counter = 100

    # Generujemy 3 piętra dla budynku AEI
    for floor in range(1, 4):
        rooms = [f"Sala Wykładowa {floor}01", f"Laboratorium {floor}02", f"Toaleta Południowa", f"Korytarz Skrzydło A"]
        
        for room in rooms:
            # 3A. Wstawiamy pokój
            cur.execute("""
                INSERT INTO Dim_Topology (Building, Wing, Floor, Room_Name)
                VALUES ('AEI', 'Skrzydło A', %s, %s) RETURNING Location_ID;
            """, (floor, room))
            loc_id = cur.fetchone()[0]

            # 3B. Generujemy po jednym tagu BLE i dwóch kotwicach UWB dla każdego pokoju
            ble_mac = f"ff:ff:12:00:00:{mac_counter}"
            uwb_mac_1 = f"UWB_{mac_counter}_A"
            uwb_mac_2 = f"UWB_{mac_counter}_B"
            mac_counter += 1

            # Dodajemy urządzenia do bazy
            cur.execute("""
                INSERT INTO Dim_IoT_Devices (MAC_Address, Device_Type, Location_ID, Semantic_Role, TX_Power_Config)
                VALUES 
                (%s, 'BLE_BEACON', %s, 'Drzwi Główne', -59),
                (%s, 'UWB_ANCHOR', %s, 'Narożnik L', NULL),
                (%s, 'UWB_ANCHOR', %s, 'Narożnik P', NULL);
            """, (ble_mac, loc_id, uwb_mac_1, loc_id, uwb_mac_2, loc_id))

            # Rejestrujemy Tag BLE do naszej listy, żeby potem zrobić mu historię baterii
            # Przypisujemy mu status "Zdrowy" lub "Umierający" (np. co trzeci psujemy)
            status = "DECAYING" if mac_counter % 3 == 0 else "STABLE"
            ble_devices.append({"mac": ble_mac, "status": status, "base_rssi": random.randint(-65, -55)})

            # 3C. Od razu tworzymy cel nawigacyjny dla aplikacji!
            cur.execute("""
                INSERT INTO Dim_Navigation_Targets (Location_ID, Name, Category, Associated_MAC, Is_Macro_Target)
                VALUES (%s, %s, 'SALA', %s, FALSE);
            """, (loc_id, f"Wejście do {room}", ble_mac))

    print(f"Wygenerowano infrastrukturę: {len(ble_devices)} tagów BLE gotowych do symulacji.")

    # KROK 4: Generowanie Telemetrii Baterii (Dla skryptu Predictive Maintenance)
    print("KROK 4: Wypełnianie tabeli faktów (Symulacja 90 dni ruchu)...")
    start_date = datetime.now() - timedelta(days=90)
    
    telemetry_records = []

    for day in range(90):
        current_day_date = start_date + timedelta(days=day)
        
        for device in ble_devices:
            # Symulujemy, że aplikacja skanuje tagi kilka razy dziennie
            readings_per_day = random.randint(3, 8)
            
            for _ in range(readings_per_day):
                read_time = current_day_date + timedelta(hours=random.randint(8, 18), minutes=random.randint(0, 59))
                
                # SZUM: Naturalne wahania sygnału
                noise = random.randint(-3, 3) 
                
                if device["status"] == "DECAYING":
                    # Bateria pada - co 10 dni sygnał słabnie o 1 dBm
                    rssi_drop = day // 10
                    final_rssi = device["base_rssi"] - rssi_drop + noise
                else:
                    # Bateria sprawna - sygnał trzyma poziom (tylko szum)
                    final_rssi = device["base_rssi"] + noise

                telemetry_records.append((read_time, 'SIMULATED_PHONE_1', device["mac"], final_rssi))

    # Wrzucamy dane paczkowo (executemany jest setki razy szybsze niż pojedynczy execute!)
    cur.executemany("""
        INSERT INTO Fact_Telemetry (Timestamp, Scanner_ID, MAC_Address, BLE_RSSI_dBm, Is_Ignored_By_Filter)
        VALUES (%s, %s, %s, %s, FALSE);
    """, telemetry_records)

    conn.commit()
    cur.close()
    conn.close()
    print("Sukces! Baza zbudowana i wypełniona.")

if __name__ == "__main__":
    setup_and_generate_data()