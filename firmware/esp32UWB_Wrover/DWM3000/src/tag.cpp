
#include "config.h"
#include <Arduino.h>
#include "kinematicFilter.h"
// =========================================================================
// 2. KONFIGURACJA BLE (NimBLE)
// =========================================================================
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define FILTER_CHARACTERISTIC_UUID "c0de0001-feed-4688-b7f5-ea07361b26a8"

const std::string MOCK_TAG_1_MAC = "ff:ff:12:b1:64:d1"; // desk 
const std::string MOCK_TAG_2_MAC = "a8:03:2a:b8:ee:fa"; // coffe 

float distTag1 = -1.0;
float distTag2 = -1.0;
float UWB_dist = 2.0; // Globalna zmienna do wysyłki (aktualizowana przez pętlę UWB)

const int TX_POWER = -60;
const float N_FACTOR = 2.5;
const float EMA_ALPHA = 0.15;

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
SmartUWBFilter filterA1(3.0, 300);

float calculateDistance(int rssi) {
    return pow(10.0, ((float)TX_POWER - rssi) / (10.0 * N_FACTOR));
}

String get_aggregated_data(float current_uwb_distance) {
    String payload = "A1:" + String(current_uwb_distance, 2);
    if (distTag1 > 0) payload += ";TAG_DESK:" + String(distTag1, 2); 
    if (distTag2 > 0) payload += ";TAG_COFFEE:" + String(distTag2, 2); 
    return payload;
}

// --- BLE CALLBACKS ---
class MyServerCallbacks: public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* pServer) { deviceConnected = true; Serial.println(">>> TELEFON POŁĄCZONY! <<<"); };
    void onDisconnect(NimBLEServer* pServer) { deviceConnected = false; Serial.println(">>> TELEFON ODŁĄCZONY! <<<"); }
};

class MyWriteCallbacks: public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* pCharacteristic) {
        std::string rxValue = pCharacteristic->getValue();
        if (rxValue.length() > 0) {
            Serial.printf("Otrzymano nową listę filtrów: %s\n", rxValue.c_str());
            newFilterReceived = true;
        }
    }
};

class MyAdvertisedDeviceCallbacks: public NimBLEAdvertisedDeviceCallbacks {
    void onResult(NimBLEAdvertisedDevice* advertisedDevice) {
        std::string deviceMac = advertisedDevice->getAddress().toString();
        int currentRssi = advertisedDevice->getRSSI();
        //tu iteracja po docelowej liscie tagow BLE
        if (deviceMac == MOCK_TAG_1_MAC || deviceMac == MOCK_TAG_2_MAC) {
            float newDist = calculateDistance(currentRssi);
            if (deviceMac == MOCK_TAG_1_MAC) {
                distTag1 = (distTag1 < 0) ? newDist : (EMA_ALPHA * newDist) + ((1.0 - EMA_ALPHA) * distTag1);
            } else if (deviceMac == MOCK_TAG_2_MAC) {
                distTag2 = (distTag2 < 0) ? newDist : (EMA_ALPHA * newDist) + ((1.0 - EMA_ALPHA) * distTag2);
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
                //   UWB_dist = 2.5 + sin(millis() / 2000.0); // NA RAZIE DO TETSOW (BEZ UWB)
                // TUTAJ ZBIERAMY DANE: UWB_dist jest na bieżąco aktualizowane przez DW3000 w pętli loop()!

                String uwb_data = get_aggregated_data(UWB_dist);
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
    dwt_setpreambledetecttimeout(0);
    dwt_setrxtimeout(0);
    dwt_rxenable(DWT_START_RX_IMMEDIATE);

    unsigned long wait_start = millis();

    // 1. Zabezpieczona pętla nasłuchu z "Biciem Serca" (Heartbeat)
    while (!((status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK | SYS_STATUS_ALL_RX_ERR))) {
        
        // Co 2 sekundy wypisz, że Tag wciąż żyje, żebyśmy wiedzieli, że się nie zawiesił
        if (millis() - wait_start > 2000) {
            Serial.println("[DEBUG] Eter UWB pusty. Antena Taga wciąż nasłuchuje...");
            wait_start = millis();
        }
        
        // KRYTYCZNE: Oddajemy ułamek czasu procesora, żeby zadania BLE mogły oddychać!
        taskYIELD(); 
    };

    // 2. Coś przyleciało do anteny! -> zmiana na TAG initialized TWR czyli my pytamy 
    //po ID z naszej listy urządzeń docelowych (np. POLL do 0x0001)

    if (status_reg & SYS_STATUS_RXFCG_BIT_MASK) {
        uint32_t frame_len;
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);
        frame_len = dwt_read32bitreg(RX_FINFO_ID) & FRAME_LEN_MAX_EX;
        
        if (frame_len <= RX_BUF_LEN) {
            dwt_readrxdata(rx_buffer, frame_len, 0);
        }

        rx_buffer[ALL_MSG_SN_IDX] = 0; // Usunięcie numeru sekwencyjnego do porównania
        
        // ---- SPRAWDZAMY CO FAKTYCZNIE PRZYSZŁO ----
        if (memcmp(rx_buffer, rx_poll_msg1, ALL_MSG_COMMON_LEN) == 0 && dA1 == 0) {
            //Serial.println("[UWB] Otrzymano POLL od Kotwicy A1! Wysyłam odpowiedź...");
            
            uint32_t resp_tx_time1;
            int ret1;

            poll_rx_ts = get_rx_timestamp_u64();
            resp_tx_time1 = (poll_rx_ts + (POLL_RX_TO_RESP_TX_DLY_UUS * UUS_TO_DWT_TIME)) >> 8;
            dwt_setdelayedtrxtime(resp_tx_time1);
            dwt_setrxaftertxdelay(RESP_TX_TO_FINAL_RX_DLY_UUS);
            dwt_setrxtimeout(FINAL_RX_TIMEOUT_UUS);
            dwt_setpreambledetecttimeout(PRE_TIMEOUT);

            tx_resp_msg1[ALL_MSG_SN_IDX] = frame_seq_nb;
            dwt_writetxdata(sizeof(tx_resp_msg1), tx_resp_msg1, 0);
            dwt_writetxfctrl(sizeof(tx_resp_msg1), 0, 1);
            //ret1 = dwt_starttx(DWT_START_TX_DELAYED | DWT_RESPONSE_EXPECTED);
            ret1 = dwt_starttx(DWT_START_TX_IMMEDIATE | DWT_RESPONSE_EXPECTED);

            if (ret1 != DWT_ERROR) {
                while (!((status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK | SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR))) {};
                frame_seq_nb++;

                if (status_reg & SYS_STATUS_RXFCG_BIT_MASK) {
                    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK | SYS_STATUS_TXFRS_BIT_MASK);
                    frame_len = dwt_read32bitreg(RX_FINFO_ID) & FRAME_LEN_MAX_EX;
                    if (frame_len <= RX_BUF_LEN) {
                        dwt_readrxdata(rx_buffer, frame_len, 0);
                    }

                    rx_buffer[ALL_MSG_SN_IDX] = 0;
                    if (memcmp(rx_buffer, rx_final_msg1, ALL_MSG_COMMON_LEN) == 0) {
                        uint32_t poll_tx_ts, resp_rx_ts, final_tx_ts;
                        double Ra, Rb, Da, Db;
                        int64_t tof_dtu;

                        resp_tx_ts = get_tx_timestamp_u64();
                        final_rx_ts = get_rx_timestamp_u64();

                        final_msg_get_ts(&rx_buffer[FINAL_MSG_POLL_TX_TS_IDX], &poll_tx_ts);
                        final_msg_get_ts(&rx_buffer[FINAL_MSG_RESP_RX_TS_IDX], &resp_rx_ts);
                        final_msg_get_ts(&rx_buffer[FINAL_MSG_FINAL_TX_TS_IDX], &final_tx_ts);

                        Ra = (double)(resp_rx_ts - poll_tx_ts);
                        Rb = (double)((uint32_t)final_rx_ts - (uint32_t)resp_tx_ts);
                        Da = (double)(final_tx_ts - resp_rx_ts);
                        Db = (double)((uint32_t)resp_tx_ts - (uint32_t)poll_rx_ts);
                        tof_dtu = (int64_t)((Ra * Rb - Da * Db) / (Ra + Rb + Da + Db));

                        tof = tof_dtu * DWT_TIME_UNITS;
                        distance = tof * SPEED_OF_LIGHT;

                        // Wypiszmy ten sukces wielkimi literami!
                        Serial.print(">>>>> SUKCES UWB! DYSTANS DO A1: ");
                        Serial.print(distance);
                        Serial.println(" metrów <<<<<");
                    // --- Gdzieś w void loop() podczas odbierania danych UWB ---
                    if (distance > 0.0 && distance < 100.0) {
                        
                        // 1. Wrzucamy do filtra (on sam zdecyduje, czy pomiar ma sens fizyczny w czasie)
                        filterA1.addRawMeasurement(distance);

                        // 2. Sprawdzamy, czy zebrało się wystarczająco poprawnych danych i minął zadany czas
                        float clean_distance;
                        if (filterA1.isReadyToReport(clean_distance)) {
                            
                            Serial.print("[GOTOWE DO BLE] Wyliczona odległość do A1: ");
                            Serial.println(clean_distance);

                            // 3. TUTAJ AKTUALIZUJESZ ZMIENNĄ DLA BLUETOOTHA!
                            UWB_dist = clean_distance;
                            dA1 = 1;
                        }
                    }
                    } else {
                        Serial.println("[ERR] Odebrano pakiet na końcu, ale to nie był FINAL od A1.");
                    }
                } else {
                    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR);
                    Serial.println("[ERR] Timeout radaru! Tag czekał na FINAL, ale Kotwica zamilkła.");
                }
            } else {
                Serial.println("[ERR] Błąd układu! Nie udało się wysłać Ponga (starttx DWT_ERROR).");
            }
        } 
        else {
            // SZPIEG: Jeśli przyleci jakakolwiek inna ramka, wydrukuj jej bajty!
            Serial.print("[SZPIEG] Złapano nieznaną ramkę UWB! Długość: ");
            Serial.print(frame_len);
            Serial.print(" bajtów. Zawartość: ");
            for(int i=0; i<frame_len; i++) {
                Serial.printf("%02X ", rx_buffer[i]);
            }
            Serial.println();
        }
        
        if (dA1 == 1) {
            dA1 = 0;
        }

    } else {
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_ERR);
        Serial.println("[ERR] Odebrano sygnał, ale ramka była fizycznie uszkodzona (szum/kolizja fal).");
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
}