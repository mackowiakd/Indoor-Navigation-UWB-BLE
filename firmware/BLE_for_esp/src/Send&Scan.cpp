#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <BLEScan.h>

// --- KONFIGURACJA SERWERA ---
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

BLEServer* pServer = NULL;
BLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;

// --- KONFIGURACJA SKANERA ---
int scanTime = 2; // Czas pojedynczego skanu w sekundach
BLEScan* pBLEScan;

// --- SYMULATOR UWB ---
String get_simulated_uwb_data() {
    float distA1 = 2.10 + (sin(millis() / 1000.0) * 0.1); 
    char dist_str[32];
    sprintf(dist_str, "A1:%3.2f", distA1);
    return String(dist_str);
}

// --- CALLBACKI SERWERA ---
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) { deviceConnected = true; Serial.println(">>> TELEFON POŁĄCZONY Z SERWEREM! <<<"); };
    void onDisconnect(BLEServer* pServer) { deviceConnected = false; Serial.println(">>> TELEFON ODŁĄCZONY! <<<"); }
};

// --- CALLBACKI SKANERA (Co się dzieje, gdy ESP coś usłyszy) ---
class MyAdvertisedDeviceCallbacks: public BLEAdvertisedDeviceCallbacks {
    void onResult(BLEAdvertisedDevice advertisedDevice) {
        // Tu filtrujemy! Zamiast śmiecić konsolę wszystkim, wypiszmy to, co ma nazwę lub silny sygnał
        if (advertisedDevice.haveName()) {
            Serial.printf("Znalaziono TAG: %s | MAC: %s | RSSI: %d dBm \n", 
                          advertisedDevice.getName().c_str(), 
                          advertisedDevice.getAddress().toString().c_str(), 
                          advertisedDevice.getRSSI());
        }
    }
};

void setup() {
    Serial.begin(115200);
    delay(2000);
    Serial.println("Startowanie systemu Multiplexing BLE...");

    BLEDevice::init("XIAO_UWB_Combo");

    // 1. INICJALIZACJA SERWERA (Nadawanie do telefonu)
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());
    BLEService *pService = pServer->createService(SERVICE_UUID);
    pCharacteristic = pService->createCharacteristic(
                        CHARACTERISTIC_UUID,
                        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
                      );
    pCharacteristic->addDescriptor(new BLE2902());
    pService->start();
    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    BLEDevice::startAdvertising();

    // 2. INICJALIZACJA SKANERA (Nasłuchiwanie tagów)
    pBLEScan = BLEDevice::getScan(); 
    pBLEScan->setAdvertisedDeviceCallbacks(new MyAdvertisedDeviceCallbacks(), true);
    pBLEScan->setActiveScan(true); // Aktywne skanowanie wyciąga więcej danych (np. nazwy) z tagów
    pBLEScan->setInterval(100);    // Czas całego cyklu (w ms)
    pBLEScan->setWindow(70);       // ZMIANA: Skan tylko przez 50 ms (50% czasu)
    
    Serial.println("System gotowy. Rozpoczynam pętlę...");
}

void loop() {
    // --- STAN 1: TELEFON JEST PODŁĄCZONY ---
    if (deviceConnected) {
        // 1. Wyślij aktualne odległości (UWB) do telefonu
        String uwb_data = get_simulated_uwb_data();
        pCharacteristic->setValue(uwb_data.c_str());
        pCharacteristic->notify();
        
        // 2. Wykonaj skan otoczenia w poszukiwaniu tagów (Mikronawigacja)
        Serial.println("--- Skanowanie otoczenia (2s) ---");
        BLEScanResults foundDevices = pBLEScan->start(scanTime, false);
        Serial.printf("Zakończono skan. Znaleziono %d urządzeń w okolicy.\n\n", foundDevices.getCount());
        pBLEScan->clearResults(); // Bardzo ważne zwalnianie pamięci!
    } 
    // --- STAN 2: TELEFON NIE JEST PODŁĄCZONY ---
    else {
        // Zamiast skanować, dajemy radiu 100% czasu na "krzyczenie" (Advertising).
        // Dzięki temu urządzenie łączy się błyskawicznie i nie znika z eteru!
        delay(200); 
    }
    // --- SEKCJA 3: Rekonekcja ---
    if (!deviceConnected && oldDeviceConnected) {
        delay(500); 
        pServer->startAdvertising(); 
        oldDeviceConnected = deviceConnected;
    }
    if (deviceConnected && !oldDeviceConnected) {
        oldDeviceConnected = deviceConnected;
    }

    delay(400); // Krótki oddech dla procesora
}