

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
#define DID 1       // ID tej konkretnej kotwicy (np. 2)
#define TAG_ID 1    // ID Taga, z którym testujemy układ (w przyszłości Kotwica sama to odczyta z POLLa!)

// Indeksy:                       0     1   2    3     4    5    6    7       8       9
static uint8_t rx_poll_msg1[]  = {0x41, 0x88, 0, 0xCA, 0xDE, 'P', 'O', 'L',   DID,    0x21, 0, 0};
static uint8_t tx_resp_msg1[]  = {0x41, 0x88, 0, 0xCA, 0xDE, 'R', 'E',  DID,  TAG_ID, 0x10, 0x02, 0, 0, 0, 0};
static uint8_t rx_final_msg1[] = {0x41, 0x88, 0, 0xCA, 0xDE, 'F', 'I',  DID,  TAG_ID, 0x23, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

// Zauważ, że tu usunąłem '2', '2' i wstawiłem czyste zmienne!
static uint8_t tx_report_msg[] = {0x41, 0x88, 0, 0xCA, 0xDE, 'R', 'P',  DID,  TAG_ID, 0x40, 0, 0, 0, 0, 0, 0, 0};

#define REPORT_MSG_DIST_IDX 11 // Cale miejsce od indeksu 11 jest czyste na naszego Floata!
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
//master conifg
static uint8_t tx_poll_msg[]   = {0x41, 0x88, 0, 0xCA, 0xDE, 'X', 'X', 'X', 0, 0x21, 0, 0}; //POL or CAL 
static uint8_t rx_resp_msg[]   = {0x41, 0x88, 0, 0xCA, 0xDE, 'R', 'E', 0, TAG_ID, 0x10, 0x02, 0, 0, 0, 0};
static uint8_t tx_final_msg[]  = {0x41, 0x88, 0, 0xCA, 0xDE, 'F', 'I', 0, TAG_ID, 0x23, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
static uint8_t tx_report_msg[] = {0x41, 0x88, 0, 0xCA, 0xDE, 'R', 'P', 0, TAG_ID, 0x40, 0, 0, 0, 0, 0, 0, 0};
#define REPORT_MSG_DIST_IDX 11 // Cale miejsce od indeksu 11 jest czyste na naszego Floata!
// PARAMETRY CZASOWE KOTWICY -> tag init (z taga przeklejone)
#define POLL_TX_TO_RESP_RX_DLY_UUS 150   // Zmienione z 0 na 150: Dajmy chipowi ułamek mikrosekundy na przejście w nasłuch
#define RESP_RX_TIMEOUT_UUS 3000         // Timeout 3ms zostaje (zabezpieczenie)
#define RESP_RX_TO_FINAL_TX_DLY_UUS 4000 // dajmy Kotwicy BARDZO DUŻO czasu na matematykę przed FINALem
#define RESP_delay 8000 //czekamy 

static uint64_t poll_rx_ts, resp_tx_ts, final_rx_ts;
static double tof, distance;

extern dwt_txconfig_t txconfig_options;



// anchor slave (poll resp)
void handle_normal_poll(uint8_t sender_id, uint32_t frame_len) {
    // 1. Odsyła RESP do sender_id
    // 2. Czeka na FIN
    // 3. Odsyła REPORT do sender_id
    //stary kod slave z loop
     uint32_t resp_tx_time1;

    poll_rx_ts = get_rx_timestamp_u64();
    resp_tx_time1 = (poll_rx_ts + (POLL_RX_TO_RESP_TX_DLY_UUS * UUS_TO_DWT_TIME)) >> 8;
    dwt_setdelayedtrxtime(resp_tx_time1);
    //konfig czsu oczekiwania na FINAL od Taga 
    dwt_setrxaftertxdelay(RESP_TX_TO_FINAL_RX_DLY_UUS);
    dwt_setrxtimeout(FINAL_RX_TIMEOUT_UUS);

    dwt_setpreambledetecttimeout(PRE_TIMEOUT); // juz nie potrzeba??

    tx_resp_msg1[ALL_MSG_SN_IDX] = frame_seq_nb;
    dwt_writetxdata(sizeof(tx_resp_msg1), tx_resp_msg1, 0);
    dwt_writetxfctrl(sizeof(tx_resp_msg1), 0, 1);
    Serial.println("\n[KOTWICA] Usłyszałem POLL! Przygotowuję RESP...");

    // Wysyła RESP i czekamy na FINAL, RESP musi zostac wyslane delayed,
    // OR z maska DWT_RESPONSE_EXPECTED, zeby od razu po wyslaniu przejsc w tryb nasluchiwania na FINALa
    if (dwt_starttx(DWT_START_TX_DELAYED| DWT_RESPONSE_EXPECTED) == DWT_SUCCESS) {
        // KRYTYCZNE: Czekamy, aż REPORT wyleci w eter!
        while (!(dwt_read32bitreg(SYS_STATUS_ID) & SYS_STATUS_TXFRS_BIT_MASK)) {};
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_TXFRS_BIT_MASK);
        
        // ---- ETAP 2: CZEKAMY NA FINAL OD TAGA ----
        while (!((status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK | SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR))) {};

        if (status_reg & SYS_STATUS_RXFCG_BIT_MASK) {
            dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);
            frame_len = dwt_read32bitreg(RX_FINFO_ID) & FRAME_LEN_MAX_EX;
            if (frame_len <= RX_BUF_LEN) {
                dwt_readrxdata(rx_buffer, frame_len, 0);
            }
            rx_buffer[ALL_MSG_SN_IDX] = 0;

    
            // odebrano FINAL -> Liczymy i odsyłamy REPORT
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

                distance = (tof_dtu * DWT_TIME_UNITS) * SPEED_OF_LIGHT;

                Serial.printf("Policzyłem %.2fm! Wysyłam REPORT do Taga.\n", distance);
                float distance_float = (float)distance; // usng 32 float not 64 double
                uint8_t *dist_bytes = (uint8_t*)&distance_float; // rzutowanie zmiennej 32 bitwoej na tablice 4x 8-bitową, żeby wysłać przez UWB

                tx_report_msg[REPORT_MSG_DIST_IDX] = dist_bytes[0];
                tx_report_msg[REPORT_MSG_DIST_IDX + 1] = dist_bytes[1];
                tx_report_msg[REPORT_MSG_DIST_IDX + 2] = dist_bytes[2];
                tx_report_msg[REPORT_MSG_DIST_IDX + 3] = dist_bytes[3];

                // I Kotwica wysyła tx_report_msg!
                dwt_writetxdata(sizeof(tx_report_msg), tx_report_msg, 0);
                dwt_writetxfctrl(sizeof(tx_report_msg), 0, 0);
                if (dwt_starttx(DWT_START_TX_IMMEDIATE) == DWT_SUCCESS) {
                    // KRYTYCZNE: Czekamy, aż REPORT wyleci w eter!
                    while (!(dwt_read32bitreg(SYS_STATUS_ID) & SYS_STATUS_TXFRS_BIT_MASK)) {};
                    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_TXFRS_BIT_MASK);
                }
            };

        } 
        else {
            dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR);
            Serial.println("[KOTWICA][BŁĄD] TIMEOUT: Czekałem na FINAL, ale Tag zamilkł!");
        }
        
        
    }
}



float executeTWR(uint8_t target_anchor) {
    float received_distance = -1.0; // Domyślnie ustawiamy na -1.0, co oznacza błąd (timeout lub brak odpowiedzi)
    
    // tryb poll musi byc juz ustawiony w dispacherze (loop)
  

    tx_poll_msg[8]   = target_anchor; // Kogo wołam (Kotwica)
    rx_resp_msg[7]   = target_anchor; // Od kogo czekam na odp (Kotwica)
    tx_final_msg[7]  = target_anchor; // Do kogo wysyłam FINAL (Kotwica)
    tx_report_msg[7] = target_anchor; // Od kogo czekam na raport (Kotwica)



    // ID TAGA NA INDEKSIE 8 (Zawsze równe 1)
    rx_resp_msg[8]   = DID;
    tx_final_msg[8]  = DID;
    tx_report_msg[8] = DID;

    // KROK 1: Wysyłamy wiadomość POLL
    tx_poll_msg[ALL_MSG_SN_IDX] = frame_seq_nb;
    frame_seq_nb++; //inkrementacja numeru paczki 
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
    taskYIELD();  //oddaje procesor zadaniom z tym samym lub wyższym priorytetem (
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
                       
                    }
                } else {
                    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR);
                     }
            } else {
                }
        } else {
              }
    } else {
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR);
        
    };

   // --- SPRZĄTANIE (Niezależnie czy sukces, czy błąd) ---
    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR | SYS_STATUS_RXFCG_BIT_MASK);
    return received_distance; // Zwracamy zmierzoną odległość (lub -1.0 jeśli był błąd)
}
// anchor master (tag impostor)
void handle_calibration_delegation(uint8_t target_anchor_id, uint8_t tag_id) {
    
    //@TODO: W tym miejscu implementujemy logikę delegowania kalibracji do innej kotwicy.
    // 1. ZMIENIAM SIĘ W TAGA! 
    // (To jest dokładna kopia funkcji executeTWR z Twojego ESP32!)
    // Wysyłam 'POL' do target_anchor_id (bo Kotwica 2 zareaguje na to automatycznie!)
    float distance_to_peer = executeTWR(target_anchor_id);
    
    // 2. Odsyłam specjalny raport do ESP32, żeby wiedział, że skończyłam misję
   // send_calib_result_to_tag(tag_id, target_anchor_id, distance_to_peer);
    
    // 3. Funkcja się kończy, wracam do nasłuchu jako Pasywny Sługa.
}

// =========================================================================
// SETUP (Inicjalizacja SPI, UWB i BLE)
// =========================================================================
void setup() {
    Serial.begin(115200);
    delay(2000);
   

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

    

    // 1. Zabezpieczona pętla nasłuchu - czekamy, aż coś przyleci do anteny (POLL od Taga)
    while (!((status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK | SYS_STATUS_ALL_RX_ERR))) {
       
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
        // =======================================================
        // ROZDZIELACZ LOGIKI (DISPATCHER)
        // =======================================================
    
        // Sprawdzamy,  czy jest DO NAS
        if ( rx_buffer[8] == DID) {
            
            // KTOŚ DO NAS KRZYCZY - SPRAWDZAMY CO CHCE:
            if (rx_buffer[5] == 'P' && rx_buffer[6] == 'O' && rx_buffer[7] == 'L') {
                
                Serial.println("\n[KOTWICA] Odebrano POLL od Taga! Wysyłam RESP...");
                // W trybie nawigacji Tag jest oznaczony jako ID 1
                handle_normal_poll(1, frame_len); 
                
            } 
            else if (rx_buffer[5] == 'C' && rx_buffer[6] == 'A' && rx_buffer[7] == 'L') {
                
                Serial.println("\n[KOTWICA] 🛠 Dostałem rozkaz KALIBRACJI! Zmieniam się w Inicjatora!");
                uint8_t target_anchor_id = rx_buffer[10]; // To przemyciliśmy w ESP32!
                
                handle_calibration_delegation(target_anchor_id, 1);
                
            } 
            else {
                Serial.println("[KOTWICA] Nieznana komenda (ani POL, ani CAL).");
            }
        } 
        else {
            Serial.println("[KOTWICA][SZPIEG] Przyleciała paczka, ale nie do mnie (albo szum).");
        }
    } 
    else {
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_ERR);
        Serial.println("[KOTWICA][BŁĄD] Uszkodzona fizycznie fala radiowa.");
    }
   

};
                       
        