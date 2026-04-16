#include <Arduino.h>
#include <NimBLEDevice.h>
#include <NimBLEServer.h>
#include <NimBLEUtils.h>

#include <NimBLEScan.h>

// --- KONFIGURACJA SERWERA ---
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
    
NimBLEServer* pServer = NULL;
NimBLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;

// --- KONFIGURACJA SKANERA ---
int scanTime = 2; // Czas pojedynczego skanu w sekundach
NimBLEScan* pBLEScan;

// --- UCHWYTY ZADAŃ (FreeRTOS) ---
TaskHandle_t TaskNotifyHandle;
TaskHandle_t TaskScanHandle;

// --- SYMULATOR UWB ---
String get_simulated_uwb_data() {
    float distA1 = 2.10 + (sin(millis() / 1000.0) * 0.1); 
    char dist_str[32];
    sprintf(dist_str, "A1:%3.2f", distA1);
    return String(dist_str);
}

// --- CALLBACKI SERWERA ---
class MyServerCallbacks: public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* pServer) { deviceConnected = true; Serial.println(">>> TELEFON POŁĄCZONY Z SERWEREM! <<<"); };
    void onDisconnect(NimBLEServer* pServer) { deviceConnected = false; Serial.println(">>> TELEFON ODŁĄCZONY! <<<"); }
};

// --- CALLBACKI SKANERA (Co się dzieje, gdy ESP coś usłyszy) ---

class MyAdvertisedDeviceCallbacks: public NimBLEAdvertisedDeviceCallbacks {
    //Zamiast kopiować obiekt, NimBLE przekazuje tylko wskaźnik (*) do tego urządzenia w pamięci.
    void onResult(NimBLEAdvertisedDevice* advertisedDevice) {
        // Tu filtrujemy! Zamiast śmiecić konsolę wszystkim, wypiszmy to, co ma nazwę lub silny sygnał
        if (advertisedDevice->haveName()) {
            Serial.printf("Znalaziono TAG: %s | MAC: %s | RSSI: %d dBm \n", 
                          advertisedDevice->getName().c_str(), 
                          advertisedDevice->getAddress().toString().c_str(), 
                          advertisedDevice->getRSSI());
        }
    }
};
// ==============================================================
// ZADANIE 1: Szybkie wysyłanie UWB (10 razy na sekundę)
// ==============================================================
void TaskNotify(void *pvParameters) {
    for (;;) { // Nieskończona pętla zadania
        if (deviceConnected) {
            String uwb_data = get_simulated_uwb_data();
            //musi byc jawne rzutowanie na uint8_t* bo NimBLE ninaczej wysyla nam 4-bajtowy adres z RAM
            pCharacteristic->setValue((uint8_t*)uwb_data.c_str(), uwb_data.length());
            pCharacteristic->notify();
        }
        // Uśpij ten wątek na 100ms (10Hz). 
        // W tym czasie system oddaje zasoby procesora Skanerowi!
        vTaskDelay(100 / portTICK_PERIOD_MS); 
    }
}
// ==============================================================
// ZADANIE 2: Powolne skanowanie tagów w tle
// ==============================================================
void TaskScan(void *pvParameters) {
    for (;;) { // Nieskończona pętla zadania
        if (deviceConnected) {
            Serial.println("--- Rozpoczynam skan (2s) w tle ---");
            // To zablokuje tylko TEN wątek, TaskNotify wciąż działa!
            BLEScanResults foundDevices = pBLEScan->start(scanTime, false);
            Serial.printf("Zakończono skan. Znaleziono %d tagów.\n", foundDevices.getCount());
            pBLEScan->clearResults();
            
            // Dajmy radiu ułamek sekundy oddechu po skanie
            vTaskDelay(100 / portTICK_PERIOD_MS);
        } else {
            // Jeśli nie ma telefonu, uśpij zadanie, nie marnuj prądu
            vTaskDelay(500 / portTICK_PERIOD_MS);
        }
    }
}
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
                        NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY
                      );
    
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
    
    // 3. URUCHOMIENIE ZADAŃ FreeRTOS
    // Przypisujemy nasze funkcje do dwóch niezależnych wątków w systemie
    xTaskCreate(TaskNotify, "Notify_Task", 4096, NULL, 1, &TaskNotifyHandle);
    xTaskCreate(TaskScan,   "Scan_Task",   4096, NULL, 1, &TaskScanHandle);

    Serial.println("System i watki gotowy czekam na polaczenie...");
}

void loop() {
   // W architekturze FreeRTOS główna pętla loop() służy już tylko 
    // do dbania o logikę reklamowania się, gdy telefon ucieknie.
    // ---  Rekonekcja ---
    if (!deviceConnected && oldDeviceConnected) {
        delay(500); 
        pServer->startAdvertising(); 
        oldDeviceConnected = deviceConnected;
    }
    if (deviceConnected && !oldDeviceConnected) {
        oldDeviceConnected = deviceConnected;
    }

    delay(500); // tu i tak nic się nie dzieje, więc możemy pozwolić sobie na dłuższe opóźnienie
}