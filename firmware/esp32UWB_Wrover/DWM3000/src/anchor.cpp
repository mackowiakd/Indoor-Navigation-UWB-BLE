/*
 * Anchor (Kotwica) - Inicjator pomiaru odległości UWB
 * 
 * Ten kod implementuje funkcję kotwicy (Anchor) w systemie pomiaru odległości UWB. 
 * Kotwica inicjuje pomiar, wysyłając wiadomość "POLL" do tagu, a następnie czeka na odpowiedź "RESP". 
 * Po otrzymaniu odpowiedzi, kotwica oblicza czas transmisji i odbioru, a następnie wysyła ostateczną wiadomość "FINAL" z tymi informacjami, umożliwiając tagowi obliczenie odległości.
 * 
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

// =========================================================================
// 2. KONFIGURACJA RADARU (Dokładnie taka sama jak w Tagu!)
// =========================================================================
static dwt_config_t config = {
  5, DWT_PLEN_128, DWT_PAC8, 9, 9, 1, DWT_BR_6M8, DWT_PHRMODE_STD, DWT_PHRRATE_STD, 
  (129 + 8 - 8), DWT_STS_MODE_OFF, DWT_STS_LEN_64, DWT_PDOA_M0
};

#define TX_ANT_DLY 16385
#define RX_ANT_DLY 16385

// =========================================================================
// 3. RAMKI DANYCH DLA KOTWICY A1 (Ping-Pong)
// =========================================================================
static uint8_t tx_poll_msg[] = {0x41, 0x88, 0, 0xCA, 0xDE, 'P', 'O', 'L', '1', 0x21, 0, 0};
static uint8_t rx_resp_msg[] = {0x41, 0x88, 0, 0xCA, 0xDE, 'R', 'E', '1', '1', 0x10, 0x02, 0, 0, 0, 0};
static uint8_t tx_final_msg[]  = {0x41, 0x88, 0, 0xCA, 0xDE, 'F', 'I', '1', '1', 0x23, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

#define ALL_MSG_COMMON_LEN 10
#define ALL_MSG_SN_IDX 2
#define FINAL_MSG_POLL_TX_TS_IDX 10
#define FINAL_MSG_RESP_RX_TS_IDX 14
#define FINAL_MSG_FINAL_TX_TS_IDX 18
#define RX_BUF_LEN 24

static uint8_t frame_seq_nb = 0;
static uint8_t rx_buffer[RX_BUF_LEN];
static uint32_t status_reg = 0;


// PARAMETRY CZASOWE KOTWICY
#define POLL_TX_TO_RESP_RX_DLY_UUS 150   // Zmienione z 0 na 150: Dajmy chipowi ułamek mikrosekundy na przejście w nasłuch
#define RESP_RX_TIMEOUT_UUS 3000         // Timeout 3ms zostaje (zabezpieczenie)
#define RESP_RX_TO_FINAL_TX_DLY_UUS 4000 // dajmy Kotwicy BARDZO DUŻO czasu na matematykę przed FINALem

extern dwt_txconfig_t txconfig_options;

// Funkcja pomocnicza do wklejania znaczników czasu (Timestamps) w ramkę
static void final_msg_set_ts(uint8_t *ts_field, uint32_t ts) {
    for (int i = 0; i < 4; i++) {
        ts_field[i] = (uint8_t) ts;
        ts >>= 8;
    }
};

// Pomocnik do wyciągania pełnych 40-bitów z układu radaru


// =========================================================================
// 4. SETUP
// =========================================================================
void setup() {
    Serial.begin(115200);
    delay(2000);
    
    Serial.println("==================================");
    Serial.println(" UWB KOTWICA A1 (Inicjator) START ");
    Serial.println("==================================");
    Serial.begin(115200);
    delay(2000);
    
    
 
    // KRYTYCZNE LINIJKI
    // Włączają one komunikację po kablach (SPI) między ESP32 a radarem DW3000
    spiBegin(PIN_IRQ, PIN_RST);
    spiSelect(PIN_SS);
    delay(2);
 

    while (!dwt_checkidlerc()) {
        Serial.println("Błąd: Moduł UWB nie odpowiada...");
        delay(1000);
    }
    if (dwt_initialise(DWT_DW_INIT) == DWT_ERROR) {
        Serial.println("Błąd Inicjalizacji DW3000!");
        while (1);
    }
    
    dwt_setleds(DWT_LEDS_ENABLE | DWT_LEDS_INIT_BLINK);
    if (dwt_configure(&config)) {
        Serial.println("Błąd Konfiguracji!");
        while (1);
    }

    dwt_configuretxrf(&txconfig_options);
    dwt_setrxantennadelay(RX_ANT_DLY);
    dwt_settxantennadelay(TX_ANT_DLY);
    dwt_setlnapamode(DWT_LNA_ENABLE | DWT_PA_ENABLE);
    Serial.println("Kotwica Gotowa. Rozpoczynam nadawanie Ping...");
}

// =========================================================================
// 5. GŁÓWNA PĘTLA (Nadawanie do Tagu)
// =========================================================================
void loop() {
 
    // KROK 1: Wysyłamy wiadomość POLL
    tx_poll_msg[ALL_MSG_SN_IDX] = frame_seq_nb;
    dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_TXFRS_BIT_MASK);
    dwt_writetxdata(sizeof(tx_poll_msg), tx_poll_msg, 0);
    dwt_writetxfctrl(sizeof(tx_poll_msg), 0, 1);

    // KRYTYCZNA ZMIANA: Zabezpieczenie przed zawieszeniem!
    // Ustawiamy, jak długo Kotwica ma czekać na Ponga (np. 3 milisekundy)
    dwt_setrxaftertxdelay(POLL_TX_TO_RESP_RX_DLY_UUS);
    dwt_setrxtimeout(RESP_RX_TIMEOUT_UUS);
    dwt_starttx(DWT_START_TX_IMMEDIATE | DWT_RESPONSE_EXPECTED);

    // Czekamy na odpowiedź RESP od Tagu
    while (!((status_reg = dwt_read32bitreg(SYS_STATUS_ID)) & (SYS_STATUS_RXFCG_BIT_MASK | SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR))) {
      
    }

    if (status_reg & SYS_STATUS_RXFCG_BIT_MASK) {
        uint32_t frame_len;
        // ZŁOTY STRZAŁ: Czyścimy OBA zdarzenia naraz!
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK | SYS_STATUS_TXFRS_BIT_MASK);

        //dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_RXFCG_BIT_MASK);
        frame_len = dwt_read32bitreg(RX_FINFO_ID) & FRAME_LEN_MAX_EX;
        if (frame_len <= RX_BUF_LEN) {
            dwt_readrxdata(rx_buffer, frame_len, 0);
        }
        rx_buffer[ALL_MSG_SN_IDX] = 0;

        // Jeśli to jest prawidłowa odpowiedź RE11 od Tagu:
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

            // KROK 3: Wysyłamy ostateczną wiadomość FINAL OD RAZU po obliczeniach
            if (dwt_starttx(DWT_START_TX_DELAYED) == DWT_SUCCESS) {
                while (!(dwt_read32bitreg(SYS_STATUS_ID) & SYS_STATUS_TXFRS_BIT_MASK)) {};
                dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_TXFRS_BIT_MASK);
                
                // Cichy log - żeby nie zapychać procesora, wypisujemy kropkę
                Serial.print("."); 
            }
             else {
                // NOWY LOG: Złapany na gorącym uczynku!
                Serial.println("\n[A1 ERR] Procesor zbyt wolny! Nie zdążyłem wysłać FINAL na czas.");
            }
        }
    } else {
        dwt_write32bitreg(SYS_STATUS_ID, SYS_STATUS_ALL_RX_TO | SYS_STATUS_ALL_RX_ERR);
    }

    // Częstotliwość pingu: 100ms daje ~10 pomiarów na sekundę
    delay(100); 
    frame_seq_nb++;
   
}

