#include <Arduino.h>
#include <NimBLEDevice.h>
#include <NimBLEServer.h>
#include <NimBLEUtils.h>

#include <NimBLEScan.h>
#include <math.h> // Wymagane do funkcji pow()
#include <string>
#include <vector>

// --- KONFIGURACJA SERWERA ---
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define FILTER_CHARACTERISTIC_UUID "c0de0001-feed-4688-b7f5-ea07361b26a8"

// adresy MAC swoich dwóch urządzeń testowych (pisane małymi literami!)
const std::string MOCK_TAG_1_MAC = "ff:ff:12:b1:64:d1"; // desk 
const std::string MOCK_TAG_2_MAC = "a8:03:2a:b8:ee:fa"; // coffe 

// Zmienne przechowujące ostatni przefiltrowany dystans (-1.0 oznacza, że tagu nie ma w pobliżu)
float distTag1 = -1.0;
float distTag2 = -1.0;
float UWB_dist = 2.0;

// Stałe do fizyki propagacji fal (Log-Distance Path Loss)
const int TX_POWER = -60;      // RSSI z odległości 1 metra
const float N_FACTOR = 2.5;    // Tłumienie (biuro)
const float EMA_ALPHA = 0.15;  // Waga naszego filtra z symulatora (15% nowe dane, 85% stare)


NimBLEServer* pServer = NULL;
NimBLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;

// --- KONFIGURACJA SKANERA ---
int scanTime = 2; // Czas pojedynczego skanu w sekundach
NimBLEScan* pBLEScan;

// --- UCHWYTY ZADAŃ (FreeRTOS) ---
TaskHandle_t TaskNotifyHandle= NULL;
TaskHandle_t TaskScanHandle=NULL;


// Funkcja przeliczająca dBm na metry
float calculateDistance(int rssi) {
    return pow(10.0, ((float)TX_POWER - rssi) / (10.0 * N_FACTOR));
}
    
// --- SYMULATOR UWB + data fro  BLE tags mocks ---
String get_aggregated_data(float current_uwb_distance) {
  
    String payload = "A1:" + String(current_uwb_distance, 2);

    // Dodajemy nasze Tagi (Mikronawigacja), jeśli je wykryto
   if (distTag1 > 0) {
        payload += ";TAG_DESK:" + String(distTag1, 2); 
    }
    // ZMIANA TUTAJ: Wysyłamy TAG_COFFEE zamiast TAG2
    if (distTag2 > 0) {
        payload += ";TAG_COFFEE:" + String(distTag2, 2); 
    }

    return payload;
}

// --- CALLBACKI SERWERA ---
class MyServerCallbacks: public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* pServer) { deviceConnected = true; Serial.println(">>> TELEFON POŁĄCZONY Z SERWEREM! <<<"); };
    void onDisconnect(NimBLEServer* pServer) { deviceConnected = false; Serial.println(">>> TELEFON ODŁĄCZONY! <<<"); }
};

// --- CALLBACKI SKANERA (Co się dzieje, gdy ESP coś usłyszy) ---
std::vector<std::string> activeMacFilters;

// Klasa nasłuchująca wiadomości od telefonu
class MyWriteCallbacks: public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* pCharacteristic) {
        std::string rxValue = pCharacteristic->getValue();
        if (rxValue.length() > 0) {
            Serial.printf("Otrzymano nową listę z telefonu: %s\n", rxValue.c_str());
            
            // Tutaj dopiszemy funkcję, która tnie ten tekst po średnikach (;) 
            // i wrzuca adresy MAC do wektora 'activeMacFilters'.
        }
    }
};

class MyAdvertisedDeviceCallbacks: public NimBLEAdvertisedDeviceCallbacks {
    //Zamiast kopiować obiekt, NimBLE przekazuje tylko wskaźnik (*) do tego urządzenia w pamięci.
    void onResult(NimBLEAdvertisedDevice* advertisedDevice) {

        std::string deviceMac = advertisedDevice->getAddress().toString();
        int currentRssi = advertisedDevice->getRSSI();

        //  // Tu filtrujemy! Zamiast śmiecić konsolę wszystkim, wypiszmy to, co ma nazwę lub silny sygnał
        // if (advertisedDevice->haveName()) {
        //     Serial.printf("Znalaziono TAG: %s | MAC: %s | RSSI: %d dBm \n", 
        //                   advertisedDevice->getName().c_str(), 
        //                   advertisedDevice->getAddress().toString().c_str(), 
        //                   advertisedDevice->getRSSI());
        if (currentRssi > -80) { // Jeśli nie ma nazwy, ale jest blisko (RSSI > -80 dBm)
            Serial.printf("Znaleziono TAG BEZ NAZWY | MAC: %s | RSSI: %d dBm \n", 
                          advertisedDevice->getAddress().toString().c_str(), 
                          advertisedDevice->getRSSI());
        }

        // 1. Sprawdzamy, czy to urządzenie nas interesuje (Fuzja Danych)
        if (deviceMac == MOCK_TAG_1_MAC || deviceMac == MOCK_TAG_2_MAC) {
            
            // 2. Przeliczamy surowe RSSI na metry
            float newDist = calculateDistance(currentRssi);

            if (deviceMac == MOCK_TAG_1_MAC) {
                if (distTag1 < 0) { 
                    distTag1 = newDist; // Pierwszy pomiar
                } else {
                    distTag1 = (EMA_ALPHA * newDist) + ((1.0 - EMA_ALPHA) * distTag1); // Wygładzanie
                }
            } 
            else if (deviceMac == MOCK_TAG_2_MAC) {
                if (distTag2 < 0) { 
                    distTag2 = newDist;
                } else {
                    distTag2 = (EMA_ALPHA * newDist) + ((1.0 - EMA_ALPHA) * distTag2);
                }
            }
        }
    }
};
// ==============================================================
// ZADANIE 1: Szybkie wysyłanie UWB + tag (10 razy na sekundę)
// ==============================================================
void TaskNotify(void *pvParameters) {
    for (;;) { // Nieskończona pętla zadania
        if (deviceConnected) {
        // Symulacja chodzenia użytkownika (wartość od 1.5 do 3.5 metra)
            UWB_dist = 2.5 + sin(millis() / 2000.0); 

            String uwb_data = get_aggregated_data(UWB_dist);
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