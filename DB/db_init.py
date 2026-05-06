import psycopg2
import random
from datetime import datetime, timedelta

# Konfiguracja dla lokalnego Dockera
DB_CONFIG = {
    "dbname": "uwb_ble_db",
    "user": "ania",
    "password": "root",
    "host": "localhost",
    "port": "5432"
}

def setup_and_generate_data():
    conn = psycopg2.connect(**DB_CONFIG)
    cur = conn.cursor()

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

    print("KROK 2: Wrzucanie wymiarów (Topologia i Urządzenia)...")
    cur.execute("""
        INSERT INTO Dim_Topology (Location_ID, Building, Wing, Floor, Room_Name)
        VALUES (1, 'AEI', 'Lewe', 1, 'Korytarz Główny')
        ON CONFLICT (Location_ID) DO NOTHING;
    """)

    mac_coffee = 'aa:bb:cc:dd:ee:ff'
    cur.execute("""
        INSERT INTO Dim_IoT_Devices (MAC_Address, Device_Type, Location_ID, Semantic_Role, TX_Power_Config)
        VALUES (%s, 'BLE_BEACON', 1, 'Ekspres do kawy', -59)
        ON CONFLICT (MAC_Address) DO NOTHING;
    """, (mac_coffee,))

    print("KROK 3: Generowanie danych do Fact_Telemetry (Symulacja 90 dni)...")
    start_date = datetime.now() - timedelta(days=90)
    base_rssi = -65 

    for day in range(90):
        readings_per_day = random.randint(5, 15)
        for _ in range(readings_per_day):
            current_date = start_date + timedelta(days=day, hours=random.randint(8, 18), minutes=random.randint(0, 59))
            
            rssi_drop = day // 10
            current_rssi = base_rssi - rssi_drop
            final_rssi = current_rssi + random.randint(-2, 2)

            cur.execute("""
                INSERT INTO Fact_Telemetry (Timestamp, Scanner_ID, MAC_Address, BLE_RSSI_dBm, Is_Ignored_By_Filter)
                VALUES (%s, 'PHONE_USER_1', %s, %s, FALSE);
            """, (current_date, mac_coffee, final_rssi))

    conn.commit()
    cur.close()
    conn.close()
    print("Sukces! Baza zbudowana i wypełniona.")

if __name__ == "__main__":
    setup_and_generate_data()