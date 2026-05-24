
#include "config.h"
#include <Arduino.h>
#include "kinematicFilter.h"
#include "app_data.h"
// =========================================================================
// KONFIGURACJA BLE (NimBLE)
// =========================================================================
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define FILTER_CHARACTERISTIC_UUID "c0de0001-feed-4688-b7f5-ea07361b26a8"

const std::string MOCK_TAG_1_MAC = "ff:ff:12:b1:64:d1"; // desk 
const std::string MOCK_TAG_2_MAC = "a8:03:2a:b8:ee:fa"; // coffe 



NimBLEServer* pServer = NULL;
NimBLECharacteristic* pCharacteristic = NULL;
NimBLECharacteristic* pFilterCharacteristic = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;
volatile bool newFilterReceived = false;

int scanTime = 2;
NimBLEScan* pBLEScan;
TaskHandle_t TaskNotifyHandle= NULL;
TaskHandle_t TaskScanHandle=NULL;
// Tworzymy filtr: Max prędkość obiektu 3.0 m/s, odchudzamy strumień danych do aktualizacji co 300 ms (ok. 3Hz)
std::vector<SmartUWBFilter> filters(8, SmartUWBFilter(3.0, 300)); // Jeden filtr na każdego aktywnego tag BLE




// --- BLE CALLBACKS ---
class MyServerCallbacks: public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* pServer) { deviceConnected = true; Serial.println(">>> TELEFON POŁĄCZONY! <<<"); };
    void onDisconnect(NimBLEServer* pServer) { deviceConnected = false; Serial.println(">>> TELEFON ODŁĄCZONY! <<<"); }
};

//odbior listy urzadzen z  app 
class MyWriteCallbacks: public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* pCharacteristic) {
        std::string rxValue = pCharacteristic->getValue();
        if(rxValue.length() > 0) {
            //mock ble list hardcoded for tests
          // Automatycznie doklejamy "U:" na początek i ";B:MAC1,MAC2" na koniec
          //UWAGA TO WTTW GDY TESTUJEMY Z nRF CONNECT 
           // String payload = "U:" + String(rxValue.c_str()) + ";B:" + String(MOCK_TAG_1_MAC.c_str()) + "," + String(MOCK_TAG_2_MAC.c_str());
           //APKA sama sklada odpowiedni format, więc tu wystarczy przekazać to co przyszło
            String payload = String(rxValue.c_str());
            appData.parseBlePayload(payload);
           
            newFilterReceived = true;
        }

        
    }
};

class MyAdvertisedDeviceCallbacks: public NimBLEAdvertisedDeviceCallbacks {
    void onResult(NimBLEAdvertisedDevice* advertisedDevice) {
        std::string deviceMac = advertisedDevice->getAddress().toString();
        int currentRssi = advertisedDevice->getRSSI();

       // ---------------------------------------------------------
        // TRYB 1: NAWIGACJA (Mamy już listę od aplikacji z telefonu)
        // ---------------------------------------------------------
        if (appData.hasBleTargets()) { 
            // hasBleTargets() to funkcja którą musisz dodać, zwraca true jeśli vector nie jest pusty
            
            if (appData.isTargetBleDevice(deviceMac)) {
                // To jest nasz cel! Aktualizujemy dystans.
                appData.updateBleDistance(deviceMac, calculateDistance(currentRssi), EMA_ALPHA);
            }
            return; // Kończymy, nie interesują nas inne urządzenia!
        }

        // ---------------------------------------------------------
        // TRYB 2: COLD START (Lista jest pusta, szukamy gdzie jesteśmy)
        // ---------------------------------------------------------
        if (currentRssi > -60) {
            
            bool isOurTag = false; // Flaga, która określi, czy wpuszczamy to urządzenie

        // =================================================================
        // KROK 1: WERYFIKACJA PO NAZWIE (Whitelisting słów kluczowych)
        // =================================================================
        if (advertisedDevice->haveName()) {
            std::string deviceName = advertisedDevice->getName();
            
            // Funkcja 'find' szuka podciągu. Dzięki temu złapie "iTAG", "iTAG_1", "my_iTAG" itp.
            if (deviceName.find("iTAG") != std::string::npos || 
                deviceName.find("BLE") != std::string::npos ||
                deviceName.find("Beacon") != std::string::npos) {
                
                Serial.printf("🎯 [WHITELIST] Znaleziono po nazwie: '%s' | MAC: %s\n", deviceName.c_str(), deviceMac.c_str());
                isOurTag = true;
            }
        }

        // =================================================================
        // KROK 2: WERYFIKACJA PO PAYLOADZIE (Jeśli nazwa nie pasowała/brak nazwy)
        // =================================================================
        if (!isOurTag && advertisedDevice->haveManufacturerData()) {
            std::string data = advertisedDevice->getManufacturerData();
            
            // Format Apple iBeacon
            if (data.length() >= 25 && data[0] == 0x4C && data[1] == 0x00 && data[2] == 0x02 && data[3] == 0x15) {
                Serial.printf("🎯 [WHITELIST] Znaleziono po iBeacon Payload! MAC: %s\n", deviceMac.c_str());
                isOurTag = true;
            }
        }

        // =================================================================
        // KROK 3: DECYZJA (Zatwierdzenie celu)
        // =================================================================
        if (isOurTag) {
            appData.addBleTarget(deviceMac);
            // Gdy tylko złapiemy pierwszy tag, telefon odpyta bazę i wyśle pełną listę
        }
        }
            

            
    }
};

// =========================================================================
// 3. FREERTOS TASKS (BLE)
// =========================================================================
void TaskNotify(void *pvParameters) {
    for (;;) {
        if (deviceConnected) {
            if (newFilterReceived) {
                String confirmMsg = "DELIVERED_SUCCEED:1.1";
                pCharacteristic->setValue((uint8_t*)confirmMsg.c_str(), confirmMsg.length());
                pCharacteristic->notify();
                newFilterReceived = false;
            } else {
               
                // TUTAJ ZBIERAMY DANE: UWB_dist jest na bieżąco aktualizowane przez DW3000 w pętli loop()!

                String uwb_data = appData.getAggregatedData();
                pCharacteristic->setValue((uint8_t*)uwb_data.c_str(), uwb_data.length());
                pCharacteristic->notify();
            }
        }
        vTaskDelay(100 / portTICK_PERIOD_MS); 
    }
}

void TaskScan(void *pvParameters) {
    for (;;) {
        if (deviceConnected) {
            pBLEScan->start(scanTime, false);
            pBLEScan->clearResults();
            vTaskDelay(100 / portTICK_PERIOD_MS);
        } else {
            vTaskDelay(500 / portTICK_PERIOD_MS);
        }
    }
}

// =========================================================================
// 4. SETUP (Inicjalizacja SPI, UWB i BLE)
// =========================================================================
void setup() {
    Serial.begin(115200);
    delay(2000);
    Serial.println("Startowanie systemu UWB + BLE...");

    // Inicjalizacja magistrali SPI dla DW3000
    spiBegin(PIN_IRQ, PIN_RST);
    spiSelect(PIN_SS);
    delay(2);

    while (!dwt_checkidlerc()) {
        Serial.println("IDLE FAILED");
        while (1);
    }
    if (dwt_initialise(DWT_DW_INIT) == DWT_ERROR) {
        Serial.println("INIT FAILED");
        while (1);
    }
    
    dwt_setleds(DWT_LEDS_ENABLE | DWT_LEDS_INIT_BLINK);
    if (dwt_configure(&config)) {
        Serial.println("CONFIG FAILED");
        while (1);
    }

    dwt_configuretxrf(&txconfig_options);
    dwt_setrxantennadelay(RX_ANT_DLY);
    dwt_settxantennadelay(TX_ANT_DLY);
    dwt_setlnapamode(DWT_LNA_ENABLE | DWT_PA_ENABLE);

    // Inicjalizacja BLE
    BLEDevice::init("ESP32_UWB_DW3000");
    BLEDevice::setMTU(512); // Pamiętaj o MTU!

    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());
    BLEService *pService = pServer->createService(SERVICE_UUID);
    
    pCharacteristic = pService->createCharacteristic(CHARACTERISTIC_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
    pFilterCharacteristic = pService->createCharacteristic(FILTER_CHARACTERISTIC_UUID, NIMBLE_PROPERTY::WRITE);
    pFilterCharacteristic->setCallbacks(new MyWriteCallbacks());

    pService->start();
    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    BLEDevice::startAdvertising();

    pBLEScan = BLEDevice::getScan(); 
    pBLEScan->setAdvertisedDeviceCallbacks(new MyAdvertisedDeviceCallbacks(), true);
    pBLEScan->setActiveScan(true);
    pBLEScan->setInterval(100);
    pBLEScan->setWindow(70);
    
    xTaskCreate(TaskNotify, "Notify_Task", 4096, NULL, 1, &TaskNotifyHandle);
    xTaskCreate(TaskScan,   "Scan_Task",   4096, NULL, 1, &TaskScanHandle);

    Serial.println("Gotowe! Czekam na telefon i Kotwice UWB...");
}

// =========================================================================
// 5. MAIN LOOP (Zajmuje się tylko i wyłącznie UWB Ping-Pong!)
// =========================================================================
void loop() {
    // Odpytujemy każdą Kotwicę po kolei

    for (int i = 0; i < appData.getActiveUwbAnchorCount(); i++) {
        uint8_t target_id = appData.getActiveUwbAnchors()[i];
       
        tx_poll_msg[8]   = target_id; // Kogo wołam (Kotwica)
        rx_resp_msg[7]   = target_id; // Od kogo czekam na odp (Kotwica)
        tx_final_msg[7]  = target_id; // Do kogo wysyłam FINAL (Kotwica)
        tx_report_msg[7] = target_id; // Od kogo czekam na raport (Kotwica)

        // TWOJE ID TAGA NA INDEKSIE 8 (Zawsze równe 1)
        rx_resp_msg[8]   = 1;
        tx_final_msg[8]  = 1;
        tx_report_msg[8] = 1;
    
        // KROK 1: Wysyłamy wiadomość POLL
        tx_poll_msg[ALL_MSG_SN_IDX] = frame_seq_nb;
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_TXFRS_BIT_MASK);
        dwt_writetxdata(sizeof(tx_poll_msg), tx_poll_msg, 0);
        dwt_writetxfctrl(sizeof(tx_poll_msg), 0, 1); 

        // KRYTYCZNA ZMIANA: Zabezpieczenie przed zawieszeniem!
        // Ustawiamy, jak długo Kotwica ma czekać na Ponga (np. 3 milisekundy)
        dwt_setrxaftertxdelay(POLL_TX_TO_RESP_RX_DLY_UUS);
        dwt_setrxtimeout(RESP_RX_TIMEOUT_UUS);
        //sendig POLL and waiting for RESP
        dwt_starttx(DWT_START_TX_IMMEDIATE | DWT_RESPONSE_EXPECTED);

        // Czekamy na odpowiedź RESP od Tagu
        while (!((status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK | SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR))) {
        taskYIELD();  //for tag
        }

        // 2. Coś przyleciało do anteny! -> zmiana na TAG initialized TWR czyli my pytamy 
        //po ID z naszej listy urządzeń docelowych (np. POLL do 0x0001)

        if (status_reg & SYS_STATUS_RXFCG_BIT_MASK) {
            uint32_t frame_len;
            dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);
            Serial.println("[TAG] Odebrano odpowiedź! Sprawdzam, czy to RESP...");
        
            //dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);
            frame_len = dwt_read32bitreg(RX_FINFO_ID) & FRAME_LEN_MAX_EX;
            if (frame_len <= RX_BUF_LEN) {
                dwt_readrxdata(rx_buffer, frame_len, 0);
            }
            rx_buffer[ALL_MSG_SN_IDX] = 0; // Usunięcie numeru sekwencyjnego do porównania- jakiego prownania??
            
            // RESP from anchor deliverd, sending FINAL
            if (memcmp(rx_buffer, rx_resp_msg, ALL_MSG_COMMON_LEN) == 0) {
            
                uint32_t final_tx_time, poll_tx_ts, resp_rx_ts, final_tx_ts;

                poll_tx_ts = dwt_readtxtimestamplo32();
                resp_rx_ts = dwt_readrxtimestamplo32();

                uint64_t resp_rx_ts_64 = get_rx_timestamp_u64();
                uint64_t final_tx_time_64 = (resp_rx_ts_64 + (RESP_RX_TO_FINAL_TX_DLY_UUS * UUS_TO_DWT_TIME)) ;
                final_tx_time= (uint32_t)(final_tx_time_64 >> 8);
                dwt_setdelayedtrxtime(final_tx_time);

                final_tx_ts = (((uint64_t)(final_tx_time & 0xFFFFFFFEUL)) << 8) + TX_ANT_DLY; // rekonstrukcja pełnego 40-bit timestampu dla FINALa, z uwzgeldniem maski dla rejestru opznienia

                // Wklejamy znaczniki czasu do ramki, żeby Tag mógł wyliczyć odległość
                final_msg_set_ts(&tx_final_msg[FINAL_MSG_POLL_TX_TS_IDX], poll_tx_ts);
                final_msg_set_ts(&tx_final_msg[FINAL_MSG_RESP_RX_TS_IDX], resp_rx_ts);
                final_msg_set_ts(&tx_final_msg[FINAL_MSG_FINAL_TX_TS_IDX], final_tx_ts);

                tx_final_msg[ALL_MSG_SN_IDX] = frame_seq_nb;
                dwt_writetxdata(sizeof(tx_final_msg), tx_final_msg, 0);
                dwt_writetxfctrl(sizeof(tx_final_msg), 0, 1);

                // KRYTYCZNA ZMIANA: Wysyłamy FINAL, ale każemy radarowi znowu czekać! (DWT_RESPONSE_EXPECTED)
                dwt_setrxaftertxdelay(150); // Krótki czas na oddech
                dwt_setrxtimeout(RESP_delay);     // Czekamy na REPORT do 8ms
            

                // Sending FINAL and waiting for REPORT
                if (dwt_starttx(DWT_START_TX_DELAYED | DWT_RESPONSE_EXPECTED) == DWT_SUCCESS) {

                    // >>> now we can print as task for uwb are already launched  <<<
                    Serial.println("[TAG] Poprawne RESP -> Wysłano FINAL.");
                    Serial.println("[TAG] Czekam na REPORT od Kotwicy...");
                    
                    // 3. CZEKAMY NA PACZKĘ "REPORT" OD KOTWICY!
                    while (!((status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK | SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR))) {
                        taskYIELD();
                    };

                    if (status_reg & SYS_STATUS_RXFCG_BIT_MASK) {
                    
                        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);
                        frame_len = dwt_read32bitreg(RX_FINFO_ID) & FRAME_LEN_MAX_EX;
                        dwt_readrxdata(rx_buffer, frame_len, 0);
                        rx_buffer[ALL_MSG_SN_IDX] = 0;

                        // Czy to jest REPORT?
                        if (memcmp(rx_buffer, tx_report_msg, ALL_MSG_COMMON_LEN) == 0) {
                            Serial.println("[TAG] Odebrano REPORT od Kotwicy! Rozpakowuję...");

                            float received_distance;
                            memcpy(&received_distance, &rx_buffer[REPORT_MSG_DIST_IDX], 4);

                            // I gotowe! Możemy to wrzucić do naszego filtra SmartUWBFilter!
                            
                            if (i >= filters.size()) {
                               filters.emplace_back(3.0, 300); // Jeśli z jakiegoś powodu mamy więcej kotwic niż filtrów, tworzymy nowy filtr "w locie"   
                            }
                            filters[i].addRawMeasurement(received_distance); // dodajemy do odpowiedniego filtra dla tej kotwicy 

                        
                            // ---TO ZOSTAJE NA TAGU  ---
                            if (received_distance > 0.0 && received_distance < 100.0) {
                                
                                
                                // 2. Sprawdzamy, czy zebrało się wystarczająco poprawnych danych i minął zadany czas
                                float clean_distance;
                                if (filters[i].isReadyToReport(clean_distance)) {

                                    Serial.print("[TAG] UWB Dystans do Kotwicy z id");
                                    Serial.print(target_id);
                                    Serial.print(": ");
                                    Serial.println(clean_distance);
                                    Serial.println("\n");

                                    // 3. TUTAJ AKTUALIZUJESZ ZMIENNĄ DLA BLUETOOTHA!
                                    appData.updateUwbDistance(target_id, clean_distance);
                                    // dA1 = true; // flaga, że mamy już pomiar od A1 (żeby nie mieszać danych z różnych cykli)
                                }
                                else {
                                    Serial.println("[TAG] UWB filter not ready (not enough data).");
                                }
                            }else {
                                Serial.println("[TAG] Błąd fizyki! Surowy dystans poza zakresem 0-100m.");
                            }
                        } else {
                            Serial.println("[TAG] To nie jest paczka REPORT. Zły nagłówek.");
                        }
                    } else {
                        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR);
                        Serial.println("[TAG][BŁĄD] TIMEOUT 2: Kotwica nie przysłała paczki REPORT na czas!");
                    }
                } else {
                    Serial.println("[TAG][BŁĄD] Za wolny procesor! Nie zdążyłem wysłać FINAL w oknie czasowym.");
                }
            } else {
                Serial.println("[TAG] Odebrano paczkę, ale to nie było RESP od Kotwicy 1.");
            }
        } else {
            dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR);
            Serial.println("[TAG][BŁĄD] TIMEOUT 1: Kotwica nie odpowiedziała na pierwszego POLLa!");
        };

        frame_seq_nb++; // inkrementacja numeru sekwencyjnego dla kolejnych cykli
      
        //  Twarde wyczyszczenie wszystkich flag błędów z rejestru systemowego, 
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR | SYS_STATUS_RXFCG_BIT_MASK);
        delay(60);     
    }  

    // --- Rekonekcja BLE ---
    static unsigned long lastReconnectCheck = 0;
    if (millis() - lastReconnectCheck > 500) {
        if (!deviceConnected && oldDeviceConnected) {
            pServer->startAdvertising(); 
            oldDeviceConnected = deviceConnected;
        }
        if (deviceConnected && !oldDeviceConnected) {
            oldDeviceConnected = deviceConnected;
        }
        lastReconnectCheck = millis();
    }
   
    delay(500);
}