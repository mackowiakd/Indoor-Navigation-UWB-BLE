#pragma once
#include <Arduino.h>
#include <vector>
#include <string>
#include <mutex>

#define MAX_UWB_ANCHORS 10

// Struktura trzymająca adres MAC i jego aktualny dystans
//Prawdop wymaga MUTEXA dla asyn task BLE
struct BleDeviceData {
    std::string mac;
    float distance;
    unsigned long last_seen_ms; // Timestamp ostatniego widzenia, do usuwania duchow wylaczonych tagow
};
struct UwbDeviceData {
    uint8_t id;
    float distance;
    unsigned long last_seen_ms; // Timestamp ostatniego widzenia, do usuwania duchow wylaczonych kotwic
    uint8_t failed_attempts;
};

class AppDataManager {
private:
    std::mutex dataMutex; // <--- STRAŻNIK PAMIĘCI RAM
    std::vector<UwbDeviceData> active_uwb_anchors;
    std::vector<BleDeviceData> target_ble_devices;

public:
    AppDataManager();

    int getActiveUwbAnchorCount() {
        return active_uwb_anchors.size();
    }   
   uint8_t getUwbAnchorId(int index);

     std::vector<BleDeviceData> getTargetBleDevices() {
        return target_ble_devices;
    }
    // 1. GŁÓWNY PARSER (Rozcina "U:123;B:mac1,mac2")
    bool parseBlePayload(String payload);

    
    // 3. FUNKCJE dla espa
    bool isTargetBleDevice(const std::string& mac);
    void updateBleDistance(const std::string& mac, float newDist, float emaAlpha);
    void updateUwbDistance(uint8_t anchorId, float newDist);

    // 4. GENERATOR PACZKI DO WYSYŁKI NA TELEFON
    String getAggregatedData();

    // Narzędzia
    void printCurrentState();
    void addBleTarget(const std::string& mac);
    bool hasBleTargets() {
        return !target_ble_devices.empty();
    }
    void markUwbAnchorDead(uint8_t anchorId);
    void incrementUwbError(uint8_t anchorId);
};

// Globalna instancja dostępna wszędzie po zaincludowaniu nagłówka
extern AppDataManager appData;