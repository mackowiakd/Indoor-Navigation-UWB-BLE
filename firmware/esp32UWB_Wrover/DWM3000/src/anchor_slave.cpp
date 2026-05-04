

/*
 * Anchor (Kotwica) as slave 
 * 
 *nasłuchiwania i reagowania tylko wtedy, gdy ktoś zawoła jej ID (POLL z odpowiednim bajtem).
 * Kotwica przejmie cały ten skomplikowany wzór matematyczny,
 *  z Taga
 * 
 */
#include <Arduino.h>
#include <DW3000.h>


// =========================================================================
// 1. PINY WROVER
// =========================================================================
const uint8_t PIN_RST = 27;
const uint8_t PIN_IRQ = 34;
const uint8_t PIN_SS = 4;

static dwt_config_t config = {
  5, DWT_PLEN_128, DWT_PAC8, 9, 9, 1, DWT_BR_6M8, DWT_PHRMODE_STD, DWT_PHRRATE_STD, 
  (129 + 8 - 8), DWT_STS_MODE_OFF, DWT_STS_LEN_64, DWT_PDOA_M0
};
#define TX_ANT_DLY 16385
#define RX_ANT_DLY 16385
#define DID '1'

static uint8_t rx_poll_msg1[] = { 0x41, 0x88, 0, 0xCA, 0xDE, 'P', 'O', 'L', '1', 0x21, 0, 0 };
static uint8_t tx_resp_msg1[] = { 0x41, 0x88, 0, 0xCA, 0xDE, 'R', 'E', DID, '1', 0x10, 0x02, 0, 0, 0, 0 };
static uint8_t rx_final_msg1[] = { 0x41, 0x88, 0, 0xCA, 0xDE, 'F', 'I', DID, '1', 0x23, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

// Nowa ramka: REPORT (Od Kotwicy do Taga)
// Zawiera znak 'D' (Distance) i 4 bajty na przeliczonego Floata (odległość)
static uint8_t tx_report_msg[] = {0x41, 0x88, 0, 0xCA, 0xDE, 'R', 'E', 'P', 'O', 'R', 'T', 0, 0, 0, 0, 0, 0};
#define REPORT_MSG_DIST_IDX 11 // Od tego miejsca zaczynamy wpisywać 4 bajty odległości

#define ALL_MSG_COMMON_LEN 10
#define ALL_MSG_SN_IDX 2
#define FINAL_MSG_POLL_TX_TS_IDX 10
#define FINAL_MSG_RESP_RX_TS_IDX 14
#define FINAL_MSG_FINAL_TX_TS_IDX 18
static uint8_t frame_seq_nb = 0;
#define RX_BUF_LEN 24
static uint8_t rx_buffer[RX_BUF_LEN];
static uint32_t status_reg = 0;
#define POLL_RX_TO_RESP_TX_DLY_UUS 2500
#define RESP_TX_TO_FINAL_RX_DLY_UUS 150// no blind window after sending resp
#define FINAL_RX_TIMEOUT_UUS 8000 // dajmy Kotwicy BARDZO DUŻO czasu na matematykę przed FINALem
#define PRE_TIMEOUT 0

static uint64_t poll_rx_ts, resp_tx_ts, final_rx_ts;
static double tof, distance;
int dA1 = 0; // flaga, czy mamy już pomiar od A1 (żeby nie mieszać danych z różnych cykli)
extern dwt_txconfig_t txconfig_options;



// =========================================================================
// SETUP (Inicjalizacja SPI, UWB i BLE)
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

                         // DLA  Kotwicy (zostaje) przy zmianie na incjatora
                        uint8_t *dist_bytes = (uint8_t*)&distance; // Magia rzutowania!

                        tx_report_msg[REPORT_MSG_DIST_IDX] = dist_bytes[0];
                        tx_report_msg[REPORT_MSG_DIST_IDX + 1] = dist_bytes[1];
                        tx_report_msg[REPORT_MSG_DIST_IDX + 2] = dist_bytes[2];
                        tx_report_msg[REPORT_MSG_DIST_IDX + 3] = dist_bytes[3];

                       // I Kotwica wysyła tx_report_msg!
                        

                  
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
}