#pragma once
#include <Arduino.h>
#include <vector>
#include <string>

#define MAX_UWB_ANCHORS 10

// Struktura trzymająca adres MAC i jego aktualny dystans
struct BleDeviceData {
    std::string mac;
    float distance;
};

class AppDataManager {
private:
    std::vector<uint8_t> active_uwb_anchors; // Teraz to wektor liczb (ID od 1 do 255)!
    std::vector<BleDeviceData> target_ble_devices;

public:
    AppDataManager();

    // 1. GŁÓWNY PARSER (Rozcina "U:123;B:mac1,mac2")
    bool parseBlePayload(String payload);

    // 2. FUNKCJE DLA UWB
    uint8_t getUwbAnchorCount();
    uint8_t getUwbAnchorId(uint8_t index);

    // 3. FUNKCJE DLA BLE
    bool isTargetBleDevice(const std::string& mac);
    void updateBleDistance(const std::string& mac, float newDist, float emaAlpha);

    // 4. GENERATOR PACZKI DO WYSYŁKI NA TELEFON
    String getAggregatedData(float current_uwb_distance);

    // Narzędzia
    void printCurrentState();
    void addBleTarget(const std::string& mac);
    bool hasBleTargets() {
        return !target_ble_devices.empty();
    }
};

// Globalna instancja dostępna wszędzie po zaincludowaniu nagłówka
extern AppDataManager appData;