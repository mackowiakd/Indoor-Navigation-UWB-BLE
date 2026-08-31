

/*
 * Anchor (Kotwica) as slave 
 * 
 *nasłuchiwania i reagowania tylko wtedy, gdy ktoś zawoła jej ID (POLL z odpowiednim bajtem).
 * Kotwica przejmie cały ten skomplikowany wzór matematyczny,
 *  z Taga
 * 
 */
#include "config.h"

// anchor slave (poll resp)
void handle_normal_poll(uint8_t sender_id, uint32_t frame_len) {
    double  distance;
    uint32_t resp_tx_time1;
    uint32_t local_status_reg = 0;
    tx_resp_msg[DEST_IDX] = sender_id;  // Wysyłam RESP do tego, kto zapytał
    tx_resp_msg[SENDER_IDX]= ANCHOR_NUM ;
    rx_final_msg[SENDER_IDX] = sender_id; // Będę oczekiwał FINALa od tego, kto zapytał
    
    poll_rx_ts = get_rx_timestamp_u64();
    resp_tx_time1 = (poll_rx_ts + (POLL_RX_TO_RESP_TX_DLY_UUS * UUS_TO_DWT_TIME)) >> 8;
    dwt_setdelayedtrxtime(resp_tx_time1);
    //konfig czsu oczekiwania na FINAL od Taga 
    dwt_setrxaftertxdelay(RESP_TX_TO_FINAL_RX_DLY_UUS);
    dwt_setrxtimeout(FINAL_RX_TIMEOUT_UUS);

    dwt_setpreambledetecttimeout(PRE_TIMEOUT); // potrzeba??

    tx_resp_msg[ALL_MSG_SN_IDX] = frame_seq_nb;
    dwt_writetxdata(sizeof(tx_resp_msg), tx_resp_msg, 0);
    dwt_writetxfctrl(sizeof(tx_resp_msg), 0, 1);
    Serial.println("\n[KOTWICA] Usłyszałem POLL! Przygotowuję RESP...");

    // Wysyła RESP i czekamy na FINAL, RESP musi zostac wyslane delayed,
    // OR z maska DWT_RESPONSE_EXPECTED, zeby od razu po wyslaniu przejsc w tryb nasluchiwania na FINALa
    if (dwt_starttx(DWT_START_TX_DELAYED| DWT_RESPONSE_EXPECTED) == DWT_SUCCESS) {
        // KRYTYCZNE: Czekamy, aż REPORT wyleci w eter!
        while (!(dwt_read32bitreg(SYS_STATUS_ID) & SYS_STATUS_TXFRS_BIT_MASK)) {};
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_TXFRS_BIT_MASK);
        
        // ---- ETAP 2: CZEKAMY NA FINAL OD TAGA ----
        while (!((local_status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK | SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR))) {};

        if (local_status_reg & SYS_STATUS_RXFCG_BIT_MASK) {
            dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);
            frame_len = dwt_read32bitreg(RX_FINFO_ID) & FRAME_LEN_MAX_EX;
            if (frame_len <= RX_BUF_LEN) {
                dwt_readrxdata(rx_buffer, frame_len, 0);
            }
            rx_buffer[ALL_MSG_SN_IDX] = 0;

    
            // odebrano FINAL -> Liczymy i odsyłamy REPORT
           if (rx_buffer[CMD_IDX] == 'F' && rx_buffer[CMD_IDX+1] == 'I' && rx_buffer[CMD_IDX+2] == 'N' && rx_buffer[DEST_IDX] == ANCHOR_NUM) {
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

                // Zabezpieczenie przed skażeniem pamięci po kalibracji - zmienia adres wysylki
                tx_report_msg[SENDER_IDX] = ANCHOR_NUM;
                tx_report_msg[DEST_IDX] = sender_id;

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
    uint32_t local_status_reg = 0;

    // POLL message header! already defined
   
    tx_poll_msg[DEST_IDX]   = target_anchor; // Kogo wołam (Kotwica)
    rx_resp_msg[SENDER_IDX]   = target_anchor; // Od kogo czekam na odp (Kotwica)
    tx_final_msg[DEST_IDX]  = target_anchor; // Do kogo wysyłam FINAL (Kotwica)
    tx_report_msg[SENDER_IDX] = target_anchor; // Od kogo czekam na raport (Kotwica)


    // ID kotwicy nadawajacej 
    rx_resp_msg[SENDER_IDX]   = ANCHOR_NUM;
    tx_final_msg[SENDER_IDX]  = ANCHOR_NUM;
    tx_report_msg[SENDER_IDX] = ANCHOR_NUM;
    tx_poll_msg[SENDER_IDX] = ANCHOR_NUM;

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
    while (!((local_status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK | SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR))) {
    taskYIELD();  //oddaje procesor zadaniom z tym samym lub wyższym priorytetem (
    }
    // 2. Coś przyleciało do anteny! -> zmiana na TAG initialized TWR czyli my pytamy 
    //po ID z naszej listy urządzeń docelowych (np. POLL do 0x0001)

    if (local_status_reg & SYS_STATUS_RXFCG_BIT_MASK) {
        uint32_t frame_len;
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);
        Serial.println("[TAG] Odebrano odpowiedź! Sprawdzam, czy to RESP...");
    
        //dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);
        frame_len = dwt_read32bitreg(RX_FINFO_ID) & FRAME_LEN_MAX_EX;
        if (frame_len <= RX_BUF_LEN) {
            dwt_readrxdata(rx_buffer, frame_len, 0);
        }
        rx_buffer[ALL_MSG_SN_IDX] = 0; // Usunięcie numeru sekwencyjnego do porównania- jakiego prownania??
        
        // RESP from tag deliverd, sending FINAL
        if (rx_buffer[CMD_IDX] == 'R' && rx_buffer[CMD_IDX+1] == 'E' && rx_buffer[CMD_IDX+2] == 'S' && rx_buffer[DEST_IDX] == ANCHOR_NUM) {
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
                while (!((local_status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK | SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR))) {
                    
                };

                if (local_status_reg & SYS_STATUS_RXFCG_BIT_MASK) {
                    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);
                    frame_len = dwt_read32bitreg(RX_FINFO_ID) & FRAME_LEN_MAX_EX;
                    dwt_readrxdata(rx_buffer, frame_len, 0);
                    rx_buffer[ALL_MSG_SN_IDX] = 0;

                    // Czy to jest REPORT?
                   if (rx_buffer[CMD_IDX] == 'R' && rx_buffer[CMD_IDX+1] == 'E' && rx_buffer[CMD_IDX+2] == 'P' && rx_buffer[DEST_IDX] == ANCHOR_NUM) {
                        Serial.println("[TAG] Odebrano REPORT od Kotwicy! Rozpakowuję...");
                        memcpy(&received_distance, &rx_buffer[REPORT_MSG_DIST_IDX], sizeof(float));
                      
                    } 
                } 
                else {
                    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR);
                }
            } 
        } 
    } else {
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR);   
    };

   // --- SPRZĄTANIE (Niezależnie czy sukces, czy błąd) ---
    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR | SYS_STATUS_RXFCG_BIT_MASK);
    return received_distance; // Zwracamy zmierzoną odległość (lub -1.0 jeśli był błąd)
}

void send_calib_result_to_tag(uint8_t tag_id, uint8_t target_anchor_id, float distance) {
 
    // Zamiast POL, RES, FIN, nazywamy ją 'C', 'R', 'S' (Calibration Result)
    
    // 2. Wrzucamy nasz wynik kalibracji (float) do paczki
    uint8_t *dist_bytes = (uint8_t*)&distance;
    tx_crs_msg[CRS_MSG_DIST_IDX] = dist_bytes[0];
    tx_crs_msg[CRS_MSG_DIST_IDX+1] = dist_bytes[1];
    tx_crs_msg[CRS_MSG_DIST_IDX+2] = dist_bytes[2];
    tx_crs_msg[CRS_MSG_DIST_IDX+3] = dist_bytes[3];

    // 3. Numerujemy paczkę
    tx_crs_msg[ALL_MSG_SN_IDX] = frame_seq_nb++;

    // 4. Załadowanie do anteny (0 na końcu oznacza zwykłe dane, bez stempli czasowych TWR!)
    dwt_writetxdata(sizeof(tx_crs_msg), tx_crs_msg, 0);
    dwt_writetxfctrl(sizeof(tx_crs_msg), 0, 0);

    Serial.printf("[KOTWICA-MASTER] Wysyłam wynik kalibracji (%.2fm) do Taga...\n", distance);

    // 5. Wysylka (IMMEDIATE - bez czekania na odpowiedź)
    if (dwt_starttx(DWT_START_TX_IMMEDIATE) == DWT_SUCCESS) {
        // fire and forget
        while (!(dwt_read32bitreg(SYS_STATUS_ID) & SYS_STATUS_TXFRS_BIT_MASK)) {};
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_TXFRS_BIT_MASK);
    }
}
// anchor master (tag impostor)
void handle_calibration_delegation(uint8_t target_anchor_id, uint8_t tag_id) {
    
    
    // 1. ZMIENIAM SIĘ W TAGA (To jest dokładna kopia funkcji executeTWR tag.cpp)
    // Wysyłam 'POL' do target_anchor_id (bo Kotwica 2 zareaguje na to automatycznie!)
    float distance_to_peer = executeTWR(target_anchor_id);
    // 2. Odsyłam  raport do ESP32 w
    send_calib_result_to_tag(tag_id, target_anchor_id, distance_to_peer);

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
// 5. MAIN LOOP dipatcher (nasłuchuje i reaguje na POLL i CAL)
// =========================================================================
void loop() {

    dwt_setpreambledetecttimeout(0);
    dwt_setrxtimeout(0);
    dwt_rxenable(DWT_START_RX_IMMEDIATE);
    uint32_t status_reg = 0;
    uint8_t sender_id;

    // 1. Zabezpieczona pętla nasłuchu - czekamy, aż coś przyleci do anteny (POLL od Taga)
    while (!((status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK | SYS_STATUS_ALL_RX_ERR))) {
       
    };

    // 2. Coś przyleciało do anteny! -> zmiana na TAG initialized TWR czyli my pytamy 

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
        sender_id=rx_buffer[SENDER_IDX];
        // Sprawdzamy,  czy jest DO NAS
        if ( rx_buffer[DEST_IDX] == ANCHOR_NUM) {
            
            // KTOŚ DO NAS KRZYCZY - SPRAWDZAMY CO CHCE:
            if (rx_buffer[CMD_IDX] == 'P' && rx_buffer[CMD_IDX+1] == 'O' && rx_buffer[CMD_IDX+2] == 'L') {
                
                Serial.println("\n[KOTWICA] Odebrano POLL od Taga! Wysyłam RESP...");
               
                handle_normal_poll(sender_id, frame_len); 
            
                
            } 
            else if (rx_buffer[CMD_IDX] == 'C' && rx_buffer[CMD_IDX+1] == 'A' && rx_buffer[CMD_IDX+2] == 'L') {
                
                Serial.println("\n[KOTWICA] 🛠 Dostałem rozkaz KALIBRACJI! Zmieniam się w Inicjatora!");
                uint8_t target_anchor_id = rx_buffer[10]; // To przemyciliśmy w ESP32!
                
                handle_calibration_delegation(target_anchor_id, sender_id);
                
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
                       
        