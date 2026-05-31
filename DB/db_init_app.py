import psycopg2

# ==========================================
# KONFIGURACJA BAZY DANYCH
# ==========================================
DB_CONFIG = {
    "dbname": "uwb_ble_db",
    "user": "domi",
    "password": "1234",
    "host": "localhost",
    "port": "5432"
}

# ==========================================
# ZMIENNE SPRZĘTOWE (MAC ADRESY)
# Łatwe do podmiany w razie zmiany sprzętu!
# ==========================================
MAC_BLE_DOOR   = 'ff:ff:12:b1:64:d1'
MAC_UWB_LEFT   = '0x0001'
MAC_UWB_RIGHT  = '0x0002'
MAC_BLE_WINDOW = 'a8:03:2a:b8:ee:fa'
MAC_BLE_COFFEE = 'ff:ff:12:8d:7c:df' # Tag niebieski
MAC_BLE_DESK   = 'ff:ff:12:a2:43:90'

def reset_app_database():
    print("🔌 Łączenie z bazą danych...")
    conn = psycopg2.connect(**DB_CONFIG)
    cur = conn.cursor()

    # Ustawiamy schemat na publiczny (domyślny dla aplikacji)
    cur.execute("SET search_path TO public;")

    try:
        print("🧹 KROK 1: Czyszczenie starych danych...")
        # RESTART IDENTITY resetuje liczniki SERIAL, CASCADE czyści powiązane fakty (jeśli jakieś zostały)
        cur.execute("""
            TRUNCATE TABLE 
                Dim_Navigation_Targets, 
                Dim_IoT_Devices, 
                Dim_Topology,
                Fact_Telemetry
            RESTART IDENTITY CASCADE;
        """)

        print("🏗️ KROK 2: Tworzenie Stref (Topologia)...")
        # Używamy RETURNING Location_ID, aby dynamicznie złapać ID, co zabezpiecza nas
        # na wypadek, gdyby auto-inkrementacja bazy z jakiegoś powodu nie zaczęła się od 1
        cur.execute("INSERT INTO Dim_Topology (building, wing, floor, room_name) VALUES ('Dom', 'Przedpokój', 1, 'Strefa Startowa') RETURNING Location_ID;")
        loc_1 = cur.fetchone()[0]

        cur.execute("INSERT INTO Dim_Topology (building, wing, floor, room_name) VALUES ('Dom', 'Pokój', 1, 'Laboratorium UWB') RETURNING Location_ID;")
        loc_2 = cur.fetchone()[0]

        print("📡 KROK 3: Rejestracja Urządzeń (IoT Devices)...")
        # Wstrzykujemy zmienne MAC adresów oraz ID pokoi za pomocą krotek parametrów (%s)
        cur.execute("""
            INSERT INTO Dim_IoT_Devices (mac_address, device_type, location_id, semantic_role, tx_power_config) 
            VALUES 
            (%s, 'BLE_BEACON', %s, 'Drzwi Wejściowe (Od zewnątrz)', -59),
            (%s, 'UWB_ANCHOR', %s, 'Kotwica UWB - Narożnik Lewy', NULL),
            (%s, 'UWB_ANCHOR', %s, 'Kotwica UWB - Narożnik Prawy', NULL),
            (%s, 'BLE_BEACON', %s, 'Okno', -59),
            (%s, 'BLE_BEACON', %s, 'Ekspres / Kubek', -59),
            (%s, 'BLE_BEACON', %s, 'Biurko z laptopem', -59);
        """, (
            MAC_BLE_DOOR, loc_1,
            MAC_UWB_LEFT, loc_2,
            MAC_UWB_RIGHT, loc_2,
            MAC_BLE_WINDOW, loc_2,
            MAC_BLE_COFFEE, loc_2,
            MAC_BLE_DESK, loc_2
        ))

        print("🎯 KROK 4: Generowanie Celów Nawigacyjnych...")
        cur.execute("""
            INSERT INTO Dim_Navigation_Targets (location_id, name, category, associated_mac, is_macro_target) 
            VALUES 
            (%s, 'Idź do: Przedpokój', 'ZONE', NULL, TRUE),
            (%s, 'Idź do: Laboratorium UWB', 'ZONE', NULL, TRUE),
            (%s, 'Precyzyjnie: Okno', 'EQUIPMENT', %s, FALSE),
            (%s, 'Precyzyjnie: Ekspres', 'EQUIPMENT', %s, FALSE),
            (%s, 'Precyzyjnie: Biurko', 'EQUIPMENT', %s, FALSE);
        """, (
            loc_1,
            loc_2,
            loc_2, MAC_BLE_WINDOW,
            loc_2, MAC_BLE_COFFEE,
            loc_2, MAC_BLE_DESK
        ))

        # Zatwierdzamy zmiany w bazie
        conn.commit()
        print("✅ Sukces! Baza pod aplikację mobilną została świeżo zresetowana i skonfigurowana.")

    except Exception as e:
        conn.rollback()
        print(f"❌ Błąd podczas inicjalizacji bazy: {e}")
    finally:
        cur.close()
        conn.close()

if __name__ == "__main__":
    reset_app_database()