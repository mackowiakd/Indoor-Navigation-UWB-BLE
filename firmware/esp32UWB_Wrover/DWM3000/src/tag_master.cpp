
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

const std::string MOCK_TAG_1_MAC = "ff:ff:12:b1:64:d1"; // desk black tag 1 (trigger)
const std::string MOCK_TAG_2_MAC = "a8:03:2a:b8:ee:fa"; // coffe 
const std::string MOCK_TAG_3_MAC = "ff:ff:12:8d:7c:df"; // blue tag 
const std::string MOCK_TAG_4_MAC = "ff:ff:12:a2:43:90"; // black tag 2



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
TaskHandle_t TaskUwbHandle = NULL; 

// Tworzymy filtr: Max prędkość obiektu 3.0 m/s, odchudzamy strumień danych do aktualizacji co 300 ms (ok. 3Hz)
std::vector<SmartUWBFilter> filters(8, SmartUWBFilter(3.0, 300)); // Jeden filtr na każdego aktywnego tag BLE


// =========================================================================
// NOWE: MASZYNA STANÓW (State Machine)
// =========================================================================
enum SystemMode {
    MODE_IDLE,
    MODE_NAVIGATION,
    MODE_CALIBRATION
};
SystemMode currentMode = MODE_IDLE;

// --- BLE CALLBACKS ---
class MyServerCallbacks: public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* pServer) { deviceConnected = true; Serial.println(">>> TELEFON POŁĄCZONY! <<<"); };
    void onDisconnect(NimBLEServer* pServer) { deviceConnected = false; Serial.println(">>> TELEFON ODŁĄCZONY! <<<"); }
};

//odbior komend z apki mobilnej (lista urządzeń, tryb pracy, itp) 
class MyWriteCallbacks: public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* pCharacteristic) {
        std::string rxValue = pCharacteristic->getValue();
        if(rxValue.length() > 0) {
          
          //UWAGA TO WTTW GDY TESTUJEMY Z nRF CONNECT -> wpisac z palca np U:0x0001,0x0002;B:a8:03:2a:b8:ee:fa || CALIB:0x0001,0x0002

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

      // =================================================================
        // ETAP 1: AKTUALIZACJA ZNANYCH CELÓW (Z listą od apki lub zwiadowcą)
        // =================================================================
        if (appData.isTargetBleDevice(deviceMac)) {
            // hasBleTargets() to funkcja ktr zwraca true jeśli vector nie jest pusty
            
            if (appData.isTargetBleDevice(deviceMac)) {
              
                appData.updateBleDistance(deviceMac, calculateDistance(currentRssi), EMA_ALPHA);
            }
            return; // wywolywane wttw mamy znane urzadzenie wiec nie musimy go sprawdzac
        }

        // ---------------------------------------------------------
        // TRYB 2: COLD START (Lista jest pusta, szukamy gdzie jesteśmy)
        // ---------------------------------------------------------
        if (currentRssi > -75) {
            
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
                // 1. PRIORYTET: Sprawdzamy, czy mamy wyniki kalibracji do wysłania
                String calibPayload = appData.getCalibrationResponse();
                if (calibPayload.length() > 0) {
                    pCharacteristic->setValue((uint8_t*)calibPayload.c_str(), calibPayload.length());
                    pCharacteristic->notify();
                    Serial.println("[NOTIFY] Wysyłam wynik KALIBRACJI: " + calibPayload);
                } 
                // 2. Jeśli nie ma kalibracji, wysyłamy zwykłą nawigację
                else {
                    String payload = appData.getAggregatedData();
                    if (payload.length() > 0) {
                        pCharacteristic->setValue((uint8_t*)payload.c_str(), payload.length());
                        pCharacteristic->notify();
                        // Serial.println("[NOTIFY] Wysyłam dane nawigacji...");
                    }
                }
             
            }
        }
        // --- PRZENIESIONA REKONEKCJA BLE ---
        static unsigned long lastReconnectCheck = 0;
        if (millis() - lastReconnectCheck > 500) {
            if (!deviceConnected && oldDeviceConnected) {
                pServer->startAdvertising(); 
                oldDeviceConnected = deviceConnected;
                Serial.println("[BLE] Wznowiono rozgłaszanie (Advertising)...");
            }
            if (deviceConnected && !oldDeviceConnected) {
                oldDeviceConnected = deviceConnected;
            }
            lastReconnectCheck = millis();
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
// Logika kalibracji kotwic UWB (wysyłanie pingów między kotwicami)
// =========================================================================
void runCalibrationMode() {

    // 1. Pobieramy BEZPIECZNIE listę kotwic przeznaczonych do kalibracji
    std::vector<uint8_t> calibList = appData.getCalibrationAnchors();
    int n = calibList.size();

     Serial.println("\n[UWB-CORE1] 🛠 Start trybu autokalibracji...");
    if (n < 2) {
        Serial.println("[UWB-CORE1] ⚠️ Za mało kotwic do kalibracji! Prerywam.");
        isCalibrationCommand = false;
        currentMode = MODE_IDLE;
        return;
    }

    // Zmienna do sklejania gotowej paczki dla telefonu (np. CALIB_RES:1_2=5.00;1_3=8.45;)
    String mockResult = "CALIB_RES:";

    // 2. Tworzymy UNIKALNE pary (j zaczyna się od i + 1)
    for(int i = 0; i < n; i++) {
        for(int j = i + 1; j < n; j++) { 
            uint8_t anchor1 = calibList[i];
            uint8_t anchor2 = calibList[j];

            // TODO: cal -> anchorI (anchorJ) rn just mock to check logic
            vTaskDelay(pdMS_TO_TICKS(400)); // Symulujemy, że pomiar zajmuje 400ms

            // Generujemy wymyślony dystans (np. 5.0m + jakaś mała wariacja, żeby pary miały inne wyniki)
            float mockDistance = 5.0f + (float)i + ((float)j * 0.5f); 
          
            // float dist = executeTWR(anchor1, anchor2, true); chyba powinna miec 2 arg, kogo pingujemy i destynacje!
            // vTaskDelay(pdMS_TO_TICKS(100));
            
            mockResult += String(anchor1, HEX) + "_" + String(anchor2, HEX) + "=" + String(mockDistance, 2) + ";";
            
        }
        appData.setCalibrationResponse(mockResult); // Przekazujemy na rdzeń 0
        currentMode = MODE_IDLE;
        
    }

    Serial.println("[UWB-CORE1] pakiet wynikowy: " + mockResult);
    // 3. TODO: Przekazanie mockResult na rdzeń 0 (do TaskNotify), aby poleciał przez BLE

    vTaskDelay(pdMS_TO_TICKS(1000)); // Chwila oddechu przed resetem

    // Po zakończeniu sprzątamy i wracamy do trybu IDLE (nasłuchu)
    appData.clearCalibrationAnchors(); 
    isCalibrationCommand = false;
    currentMode = MODE_IDLE;
    Serial.println("[UWB-CORE1] ✅ Kalibracja zakończona. Wracam do IDLE.\n");
}
// --- UNIWERSALNA FUNKCJA do kom UWB ---

float executeTWR(uint8_t target_anchor, uint8_t source_anchor, bool isCalibrationMode) {
    float received_distance = -1.0; // Domyślnie ustawiamy na -1.0, co oznacza błąd (timeout lub brak odpowiedzi)
    
    // 1. PODMIANA NAGŁÓWKA W LOCIE (Trwa nanosekundy!)
    if (isCalibrationMode) {
        tx_poll_msg[5] = 'C'; tx_poll_msg[6] = 'A'; tx_poll_msg[7] = 'L';
        tx_poll_msg[10] = target_anchor;
    } else {
        tx_poll_msg[5] = 'P'; tx_poll_msg[6] = 'O'; tx_poll_msg[7] = 'L';
    }

    tx_poll_msg[8]   = target_anchor; // Kogo wołam (Kotwica)
    rx_resp_msg[7]   = target_anchor; // Od kogo czekam na odp (Kotwica)
    tx_final_msg[7]  = target_anchor; // Do kogo wysyłam FINAL (Kotwica)
    tx_report_msg[7] = target_anchor; // Od kogo czekam na raport (Kotwica)

    // Numerujemy ramkę i inkrementujemy od razu na przyszłość
    tx_poll_msg[ALL_MSG_SN_IDX] = frame_seq_nb;
    frame_seq_nb++;

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
                    
                };

                if (status_reg & SYS_STATUS_RXFCG_BIT_MASK) {
                
                    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);
                    frame_len = dwt_read32bitreg(RX_FINFO_ID) & FRAME_LEN_MAX_EX;
                    dwt_readrxdata(rx_buffer, frame_len, 0);
                    rx_buffer[ALL_MSG_SN_IDX] = 0;

                    // Czy to jest REPORT?
                    if (memcmp(rx_buffer, tx_report_msg, ALL_MSG_COMMON_LEN) == 0) {

                        Serial.println("[TAG] Odebrano REPORT od Kotwicy! Rozpakowuję...");
                        memcpy(&received_distance, &rx_buffer[REPORT_MSG_DIST_IDX], 4);
                      
                    } else {
                        Serial.println("[TAG] To nie jest paczka REPORT. Zły nagłówek.");
                    }
                } else {
                    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR);
                    appData.incrementUwbError(target_anchor);
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

   // --- SPRZĄTANIE (Niezależnie czy sukces, czy błąd) ---
    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR | SYS_STATUS_RXFCG_BIT_MASK);
    return received_distance; // Zwracamy zmierzoną odległość (lub -1.0 jeśli był błąd)
}
// =========================================================================
//  OLD MAIN LOOP fun
// =========================================================================
void runNavigationMode() {
    int anchorCount = appData.getActiveUwbAnchorCount();
    for (int i = 0; i < anchorCount; i++) {
        uint8_t target_id = appData.getUwbAnchorId(i);
        
        // Strzelamy (false, bo to nie kalibracja)
        float dist = executeTWR(target_id, target_id, false); 
        
        if (dist > 0) {

               // I gotowe! Możemy to wrzucić do naszego filtra SmartUWBFilter!
            
            if (i >= filters.size()) {
                filters.emplace_back(3.0, 300); // Jeśli z jakiegoś powodu mamy więcej kotwic niż filtrów, tworzymy nowy filtr "w locie"   
            }
            filters[i].addRawMeasurement(dist); // dodajemy do odpowiedniego filtra dla tej kotwicy 

        
            // ---TO ZOSTAJE NA TAGU  ---
            if (dist > 0.0 && dist < 100.0) {
                
                
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
                    
                }
                else {
                    
                    Serial.println("[TAG] UWB filter not ready (not enough data).");

                }
            }else {
                Serial.println("[TAG] Błąd fizyki! Surowy dystans poza zakresem 0-100m.");
            }
            // Wrzucamy do filtra i aktualizujemy appData!
        }
     
    }
    vTaskDelay(pdMS_TO_TICKS(60)); 
   // NIE ustawiamy tu MODE_IDLE, ponieważ nawigacja to proces ciągły! 
}

// =========================================================================
// 6. GŁÓWNY TASK UWB (Rdzeń 1) i PUSTY LOOP
// =========================================================================
void TaskUWB(void *pvParameters) {
    for (;;) {
        // 1. Zmiana trybu na żądanie (Zmienna z app_data.cpp)
        if (isCalibrationCommand) {
            currentMode = MODE_CALIBRATION;
        } else if (appData.getActiveUwbAnchorCount() > 0) {
            currentMode = MODE_NAVIGATION;
        } else {
            currentMode = MODE_IDLE;
        }

        // 2. Wykonywanie odpowiedniej logiki (CZYSTY KOD!)
        switch (currentMode) {
            case MODE_NAVIGATION:
                runNavigationMode(); // Twój stary, dobry Ping-Pong
                vTaskDelay(pdMS_TO_TICKS(100));
                break;
            case MODE_CALIBRATION:
                runCalibrationMode(); // Nowa logika tworzenia par
                break;
            case MODE_IDLE:
                vTaskDelay(200 / portTICK_PERIOD_MS); // Odpoczynek procesora
                break;
        }
    }
}

// =========================================================================
//  SETUP (Inicjalizacja SPI, UWB i BLE)
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
    
  // --- ZMIANA: Przypisujemy taski BLE sztywno do rdzenia 0 (PRO_CPU) ---
    xTaskCreatePinnedToCore(TaskNotify, "Notify_Task", 4096, NULL, 1, &TaskNotifyHandle, 0);
    xTaskCreatePinnedToCore(TaskScan,   "Scan_Task",   4096, NULL, 1, &TaskScanHandle, 0);
    
    // --- NOWE: Tworzymy task UWB i przypisujemy sztywno do rdzenia 1 (APP_CPU) ---
    xTaskCreatePinnedToCore(TaskUWB, "UWB_Task", 8192, NULL, 2, &TaskUwbHandle, 1);


    Serial.println("Gotowe! Czekam na telefon i Kotwice UWB...");
}


// Zabijamy domyślną pętlę Arduino. Systemem rządzi teraz w 100% FreeRTOS!
void loop() {
    vTaskDelete(NULL); 
}



/*

    
    for (int i = 0; i < anchorCount; i++) {
        // Bezpieczne pobranie ID bez fragmentowania pamięci RAM!
        uint8_t target_id = appData.getUwbAnchorId(i); //getCalibAnchorId(i) if calibration mode
        if (target_id == 0) continue;
       
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
                        appData.incrementUwbError(target_id);
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
        vTaskDelay(pdMS_TO_TICKS(60));     
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
   
    vTaskDelay(pdMS_TO_TICKS(500)); // Oddech dla procesora, żeby nie zjadał całego czasu CPU
*/