
/*
Zaproponujmy taki format ładunku (Payload):
U_1=4.20;U_2=1.85;B_ff:ff:12:b1:64:d1=3.10,B_a8:03:2a:b8:ee:fa=5.60;

Znak : -> "Aha, nadchodzi lista, rozcinam po przecinkach".

Znak = -> "Aha, to jest pomiar, wrzucam do bazy danych".
*/
#include "app_data.h"

// Inicjalizacja globalnej instancji
AppDataManager appData;

AppDataManager::AppDataManager() {
    active_uwb_anchors.clear();
    target_ble_devices.clear();
}

/*used by onWrite BLE callback 
- updates the list of target devices and active anchors
- expects payload in format: U:123,124;B:mac1,mac2
*/
bool AppDataManager::parseBlePayload(String payload) {
    Serial.println("\n[AppData] Otrzymano nową konfigurację: " + payload);
    

    int uwbIndex = payload.indexOf("U:");
    int bleIndex = payload.indexOf(";B:");

    if (uwbIndex == -1 || bleIndex == -1) {
        Serial.println("[AppData][ERR] Zły format! Oczekiwano np. U_1=4.20;B_ff:ff:12:b1:64:d1=3.10");
        return false;
    }

    // --- 1. PARSOWANIE KOTWIC UWB (Z przecinkami!) ---
    String uwbStr = payload.substring(uwbIndex + 2, bleIndex);
    std:: lock_guard<std::mutex> lock(dataMutex); // shared varaible -> locking whole function scope
    active_uwb_anchors.clear(); // Czyścimy starą listę

    int commaIndex;
    while ((commaIndex = uwbStr.indexOf(',')) != -1) {
        String idStr = uwbStr.substring(0, commaIndex);
        idStr.trim();
        if (idStr.length() > 0) {
          // strtol automatycznie rozpoznaje "0x" i konwertuje szesnastkowy tekst na czystą liczbę uint8_t
            uint8_t parsedId = (uint8_t)strtol(idStr.c_str(), NULL, 16);
            active_uwb_anchors.push_back({parsedId, -1.0f});
        }
        uwbStr = uwbStr.substring(commaIndex + 1);
    }
    // Dodajemy ostatnie ID z listy
    uwbStr.trim();
    if (uwbStr.length() > 0) {
        // TUTAJ TEŻ MUSI BYĆ strtol, inaczej wyjdzie 0!
        uint8_t parsedId = (uint8_t)strtol(uwbStr.c_str(), NULL, 16);
        active_uwb_anchors.push_back({parsedId, -1.0f});
    }

    // --- PARSOWANIE TAGÓW BLE ---
    String bleStr = payload.substring(bleIndex + 3); // wtedy juz tylko +2 (srednik zostal na uwb praserze)
    target_ble_devices.clear(); // Wywalamy starą listę

  
    while ((commaIndex = bleStr.indexOf(',')) != -1) {
        String mac = bleStr.substring(0, commaIndex);
        mac.trim();
        if (mac.length() > 0) {
            target_ble_devices.push_back({mac.c_str(), -1.0f}); // -1.0 oznacza "jeszcze nie zmierzono"
        }
        bleStr = bleStr.substring(commaIndex + 1);
    }
    // Dodanie ostatniego MACa w łańcuchu
    bleStr.trim();
    if (bleStr.length() > 0) {
        target_ble_devices.push_back({bleStr.c_str(), -1.0f});
    }

    printCurrentState();
    return true;
}



bool AppDataManager::isTargetBleDevice(const std::string& mac) {
    std::lock_guard<std::mutex> lock(dataMutex);
    for (const auto& device : target_ble_devices) {
        if (device.mac == mac) return true;
    }
    return false;
}

/*used by onWrite callback */
void AppDataManager::updateBleDistance(const std::string& mac, float newDist, float emaAlpha) {
    std::lock_guard<std::mutex> lock(dataMutex);
    for (auto& device : target_ble_devices) {
        if (device.mac == mac) {
            //device.last_seen_ms = millis();
            if (device.distance < 0) {
                device.distance = newDist; // Pierwszy strzał pomiaru
            } else {
                device.distance = (emaAlpha * newDist) + ((1.0f - emaAlpha) * device.distance); // Filtr EMA
            }
            return;
        }
       
    }
}
/*used by uwb loop()*/
void AppDataManager::updateUwbDistance(uint8_t anchorId, float newDist) {
    std::lock_guard<std::mutex> lock(dataMutex);
    for (auto& anchor : active_uwb_anchors) {
        if (anchor.id == anchorId) {
            anchor.distance = newDist;
            //anchor.last_seen_ms = millis(); // <--- ZNACZNIK ŻYCIA KOTWICY
            return;
        }
    }
};

/*used by TaskNotify() to prepare payload for smartphone*/
String AppDataManager::getAggregatedData() {
    String payload = "";
    std::lock_guard<std::mutex> lock(dataMutex);
    uint32_t current_time = millis(); // Pobieramy czas w tym ułamku sekundy
    const uint32_t TIMEOUT_MS = 2500; // 2.5 sekundy bez sygnału to śmierć taga

    // 1. Sklejamy odległości Kotwic UWB (np. U_1=2.45;U_2=5.10;)
    for (const auto& anchor : active_uwb_anchors) {
        if (anchor.distance > 0)  //&& (current_time - anchor.last_seen_ms > TIMEOUT_MS))
        {
            char hexBuf[10];
            // snprintf JEST BEZPIECZNE - sizeof(hexBuf) fizycznie blokuje wyciek pamięci!
            snprintf(hexBuf, sizeof(hexBuf), "0x%04X", anchor.id);
            
            payload += "U_" + String(hexBuf) + "=" + String(anchor.distance, 2) + ";";
            // %04X oznacza: "Wydrukuj jako HEX (X), użyj 4 znaków (4), brakujące uzupełnij zerami (0)"
            Serial.printf("0x%04X ", anchor.id);
        }
    }
    
    for (const auto& device : target_ble_devices) {
        if (device.distance > 0) //&& (current_time - device.last_seen_ms > TIMEOUT_MS))
        {
            // Wysyłamy PEŁNY MAC. Używamy znaku '=' żeby oddzielić MAC od dystansu!
            // Format docelowy: UWB:2.45;BLE_ff:ff:12:b1:64:d1=1.50
            payload += "B_" + String(device.mac.c_str()) + "=" + String(device.distance, 2)+ ";";
            Serial.printf("%s ", device.mac.c_str()); 
    }
    Serial.println("\n");
       
     
    }
    return payload;
}

void AppDataManager::printCurrentState() {
   // same lock is foribben here, because this function is called inside parseBlePayload() which already has a lock_guard. We can end in deadlock if we try to lock again.
    Serial.print("[AppData] Zaktualizowane Kotwice UWB (");
    Serial.print(active_uwb_anchors.size());
    Serial.print("): ");
    
     for (const auto& device : active_uwb_anchors) {
       // %04X oznacza: "Wydrukuj jako HEX (X), użyj 4 znaków (4), brakujące uzupełnij zerami (0)"
        Serial.printf("0x%04X ", device.id);
    }
    Serial.println();

    Serial.print("[AppData] Zaktualizowane Cele BLE (");
    Serial.print(target_ble_devices.size());
    Serial.print("): ");
    for (const auto& device : target_ble_devices) {
        Serial.printf("%s ", device.mac.c_str());
    }
    Serial.println("\n");

   
}

 void AppDataManager::addBleTarget(const std::string& mac) {
        std::lock_guard<std::mutex> lock(dataMutex);
       // Zamiast wołać zablokowane isTargetBleDevice(), robimy szybkie sprawdzenie ręcznie
    bool alreadyExists = false;
    for (const auto& device : target_ble_devices) {
        if (device.mac == mac) {
            alreadyExists = true;
            break;
        }
    }

    // Jeśli go nie ma, dodajemy do listy startowej
    if (!alreadyExists) {
        target_ble_devices.push_back({mac, -1.0f});
    }
}

uint8_t AppDataManager::getUwbAnchorId(int index) {
    std::lock_guard<std::mutex> lock(dataMutex);
    if (index < active_uwb_anchors.size()) {
        return active_uwb_anchors[index].id;
    }
    return 0;
}