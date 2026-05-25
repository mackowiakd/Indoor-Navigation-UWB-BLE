
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
            return;
        }
    }
};

/*used by TaskNotify() to prepare payload for smartphone*/
String AppDataManager::getAggregatedData() {
    String payload = "";
    std::lock_guard<std::mutex> lock(dataMutex);
    // 1. Sklejamy odległości Kotwic UWB (np. U_1=2.45;U_2=5.10;)
    for (const auto& anchor : active_uwb_anchors) {
        if (anchor.distance > 0) {
            // %04X oznacza: "Wydrukuj jako HEX (X), użyj 4 znaków (4), brakujące uzupełnij zerami (0)"
            Serial.printf("0x%04X ", anchor.id);
        }
    }
    
    for (const auto& device : target_ble_devices) {
        if (device.distance > 0) {
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
    std::lock_guard<std::mutex> lock(dataMutex);
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
        if (!isTargetBleDevice(mac)) {
            target_ble_devices.push_back({mac, -1.0f});
        }
}