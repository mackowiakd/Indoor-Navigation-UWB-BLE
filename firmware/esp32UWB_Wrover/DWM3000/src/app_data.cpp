
/*
Zaproponujmy taki format ładunku (Payload):
U:123;B:ff:ff:12:b1:64:d1,a8:03:2a:b8:ee:fa

*/
#include "app_data.h"

// Inicjalizacja globalnej instancji
AppDataManager appData;

AppDataManager::AppDataManager() {
    active_uwb_anchors.clear();
    target_ble_devices.clear();
}

bool AppDataManager::parseBlePayload(String payload) {
    Serial.println("\n[AppData] Otrzymano nową konfigurację: " + payload);

    int uwbIndex = payload.indexOf("U:");
    int bleIndex = payload.indexOf(";B:");

    if (uwbIndex == -1 || bleIndex == -1) {
        Serial.println("[AppData][ERR] Zły format! Oczekiwano np. U:123;B:mac1,mac2");
        return false;
    }

    // --- 1. PARSOWANIE KOTWIC UWB (Z przecinkami!) ---
    String uwbStr = payload.substring(uwbIndex + 2, bleIndex);
    active_uwb_anchors.clear(); // Czyścimy starą listę

    int commaIndex;
    while ((commaIndex = uwbStr.indexOf(',')) != -1) {
        String idStr = uwbStr.substring(0, commaIndex);
        idStr.trim();
        if (idStr.length() > 0) {
            active_uwb_anchors.push_back((uint8_t)idStr.toInt()); // Magia! Tekst "10" staje się liczbą 10
        }
        uwbStr = uwbStr.substring(commaIndex + 1);
    }
    // Dodajemy ostatnie ID z listy
    uwbStr.trim();
    if (uwbStr.length() > 0) {
        active_uwb_anchors.push_back((uint8_t)uwbStr.toInt());
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

uint8_t AppDataManager::getUwbAnchorCount() {
    return active_uwb_anchors.size();
}

uint8_t AppDataManager::getUwbAnchorId(uint8_t index) {
    if (index < active_uwb_anchors.size()) return active_uwb_anchors[index];
    return '\0';
}

bool AppDataManager::isTargetBleDevice(const std::string& mac) {
    for (const auto& device : target_ble_devices) {
        if (device.mac == mac) return true;
    }
    return false;
}

void AppDataManager::updateBleDistance(const std::string& mac, float newDist, float emaAlpha) {
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

String AppDataManager::getAggregatedData(float current_uwb_distance) {
    // Format docelowy: UWB:2.45;BLE_b164d1:1.50;BLE_b8eefa:3.20
    String payload = "UWB:" + String(current_uwb_distance, 2);
    
    for (const auto& device : target_ble_devices) {
        if (device.distance > 0) {
            // Dla oszczędności bajtów ucinamy początek MACa i wysyłamy np. "BLE_b8eefa:3.20"
            String shortMac = String(device.mac.c_str()).substring(9); 
            shortMac.replace(":", "");
            payload += ";BLE_" + shortMac + ":" + String(device.distance, 2);
        }
    }
    return payload;
}

void AppDataManager::printCurrentState() {
    Serial.print("[AppData] Zaktualizowane Kotwice UWB (");
    Serial.print(active_uwb_anchors.size());
    Serial.print("): ");
    for (int i = 0; i < active_uwb_anchors.size(); i++) {
        Serial.print(active_uwb_anchors[i]);
        Serial.print(" ");
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
        if (!isTargetBleDevice(mac)) {
            target_ble_devices.push_back({mac, -1.0f});
        }
}