#pragma once
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
#define DID 1      // ID tej konkretnej kotwicy 
#define TAG_ID 1    // ID Taga, z którym testujemy układ (w przyszłości Kotwica sama to odczyta z POLLa!)

// slave config                   0     1   2    3     4    5    6    7       8       9
static uint8_t tx_resp_msg1[]  = {0x41, 0x88, 0, 0xCA, 0xDE, 'R', 'E',  DID,  TAG_ID, 0x10, 0x02, 0, 0, 0, 0};
static uint8_t rx_final_msg1[] = {0x41, 0x88, 0, 0xCA, 0xDE, 'F', 'I',  DID,  TAG_ID, 0x23, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

static uint8_t tx_report_msg[] = {0x41, 0x88, 0, 0xCA, 0xDE, 'R', 'P',  DID,  TAG_ID, 0x40, 0, 0, 0, 0, 0, 0, 0};
//master conifg
static uint8_t tx_poll_msg[]   = {0x41, 0x88, 0, 0xCA, 0xDE, 'X', 'X', 'X', 0, 0x21, 0, 0}; //POL or CAL 
static uint8_t rx_resp_msg[]   = {0x41, 0x88, 0, 0xCA, 0xDE, 'R', 'E', 0, TAG_ID, 0x10, 0x02, 0, 0, 0, 0};
static uint8_t tx_final_msg[]  = {0x41, 0x88, 0, 0xCA, 0xDE, 'F', 'I', 0, TAG_ID, 0x23, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

#define REPORT_MSG_DIST_IDX 11 // Cale miejsce od indeksu 11 jest czyste na naszego Floata!
#define ALL_MSG_COMMON_LEN 10
#define ALL_MSG_SN_IDX 2
#define FINAL_MSG_POLL_TX_TS_IDX 10
#define FINAL_MSG_RESP_RX_TS_IDX 14
#define FINAL_MSG_FINAL_TX_TS_IDX 18
static uint8_t frame_seq_nb = 0;
#define RX_BUF_LEN 24
static uint8_t rx_buffer[RX_BUF_LEN];
#define POLL_RX_TO_RESP_TX_DLY_UUS 2500
#define RESP_TX_TO_FINAL_RX_DLY_UUS 150// no blind window after sending resp
#define FINAL_RX_TIMEOUT_UUS 8000 // dajmy Kotwicy BARDZO DUŻO czasu na matematykę przed FINALem
#define PRE_TIMEOUT 0

#define REPORT_MSG_DIST_IDX 11 // Cale miejsce od indeksu 11 jest czyste na naszego Floata!
// PARAMETRY CZASOWE KOTWICY -> tag init (z taga przeklejone)
#define POLL_TX_TO_RESP_RX_DLY_UUS 150   // Zmienione z 0 na 150: Dajmy chipowi ułamek mikrosekundy na przejście w nasłuch
#define RESP_RX_TIMEOUT_UUS 3000         // Timeout 3ms zostaje (zabezpieczenie)
#define RESP_RX_TO_FINAL_TX_DLY_UUS 4000 // dajmy Kotwicy BARDZO DUŻO czasu na matematykę przed FINALem
#define RESP_delay 8000 //czekamy 

static uint64_t poll_rx_ts, resp_tx_ts, final_rx_ts;
static double tof, distance;

extern dwt_txconfig_t txconfig_options;


float to_executeTWR(uint8_t target_anchor_id);
void handle_normal_poll(uint8_t sender_id, uint32_t frame_len);
void handle_calibration_delegation(uint8_t target_anchor_id, uint8_t tag_id);

