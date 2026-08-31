
#pragma once
#include "dw3000.h" 
#undef READ
#undef WRITE

// 3. Dopiero teraz importujesz NimBLE

#include <NimBLEDevice.h>
#include <NimBLEServer.h>
#include <NimBLEUtils.h>
#include <NimBLEScan.h>
#include <math.h> 
#include <string>
#include <vector>

// TODO zrobic wspolny jeden config (dla kotwicy i taga ) skoro i tak mamy static przy tablicach to kazda ma swoja ko
/*
Tryb Normalny (POL): Wymaga wymiany tylko dwóch adresów (Kto do kogo mówi).
[5, 6, 7] = "P", "O", "L"

[8] = Kto ma odpowiedzieć (Adresat, np. Kotwica 1)
[9] = 0x21 (Kod funkcji systemowej)
[11] = Kto pyta (Nadawca, Tag)

Tryb Kalibracji (CAL): Wymaga wymiany aż trzech adresów, ponieważ Tag zleca Kotwicy 1 wykonanie zadania na Kotwicy 2 i prosi o zwrot wyników.
[5, 6, 7] = "C", "A", "L"

[8] = Kto dostaje rozkaz (Wykonawca, Kotwica 1)
[9] = 0x21 (Kod funkcji systemowej)
[10] = Kogo Wykonawca ma spingować (Cel, Kotwica 2)
[11] = Do kogo Wykonawca ma odesłać wynik (Nadawca, Tag)
*/
// =========================================================================
// 1. KONFIGURACJA UWB (DW3000)
// =========================================================================


const uint8_t PIN_RST = 27;  // reset pin
const uint8_t PIN_IRQ = 34;  // irq pin
const uint8_t PIN_SS = 4;    // spi select pin

static dwt_config_t config = {
  5, DWT_PLEN_128, DWT_PAC8, 9, 9, 1, DWT_BR_6M8, DWT_PHRMODE_STD, DWT_PHRRATE_STD, 
  (129 + 8 - 8), DWT_STS_MODE_OFF, DWT_STS_LEN_64, DWT_PDOA_M0
};
#define TX_ANT_DLY 16385
#define RX_ANT_DLY 16385

#define TAG_ID 0

// --- PARAMETRY RAMKI MAC ---
#define ALL_MSG_COMMON_LEN 10
#define ALL_MSG_SN_IDX     2   // Sequence number index
#define RX_BUF_LEN         32

// --- UNIWERSALNY NAGŁÓWEK ---
#define CMD_IDX    5
#define DEST_IDX   8
#define SENDER_IDX 9


// --- INDEKSY PAYLOADU (Od 10 w górę) ---
#define CAL_TARGET_IDX            10 // Kogo Kotwica 1 ma spingować (dla CAL)
#define REPORT_MSG_DIST_IDX       10 // Wynik pomiaru (dla REP)
#define CRS_MSG_DIST_IDX          10 // Wynik kalibracji (dla CRS)
#define FINAL_MSG_POLL_TX_TS_IDX 10 // Znaczniki TWR
#define FINAL_MSG_RESP_RX_TS_IDX 14 // Znaczniki TWR
#define FINAL_MSG_FINAL_TX_TS_IDX 18 // Znaczniki TWR


// --- CZYSTE SZABLONY TABLIC ---
static uint8_t tx_poll_msg[]   = {0x41, 0x88, 0, 0xCA, 0xDE, 'P', 'O', 'L', 0, 0, 0, 0};
static uint8_t rx_resp_msg[]   = {0x41, 0x88, 0, 0xCA, 0xDE, 'R', 'E', 'S', 0, 0, 0, 0, 0, 0, 0};
static uint8_t tx_final_msg[]  = {0x41, 0x88, 0, 0xCA, 0xDE, 'F', 'I', 'N', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
static uint8_t tx_report_msg[] = {0x41, 0x88, 0, 0xCA, 0xDE, 'R', 'E', 'P', 0, 0, 0, 0, 0, 0, 0, 0, 0};
static uint8_t tx_cal_msg[]    = {0x41, 0x88, 0, 0xCA, 0xDE, 'C', 'A', 'L', 0, 0, 0, 0};
static uint8_t tx_crs_msg[]    = {0x41, 0x88, 0, 0xCA, 0xDE, 'C', 'R', 'S', 0, 0, 0, 0, 0, 0, 0, 0};

static uint8_t rx_buffer[RX_BUF_LEN];
static uint8_t frame_seq_nb = 0;

//PARAMETRY CZASOWE do comm z KOTWICA -> tag init
#define POLL_TX_TO_RESP_RX_DLY_UUS 150   // Zmienione z 0 na 150: Dajmy chipowi ułamek mikrosekundy na przejście w nasłuch
#define RESP_RX_TIMEOUT_UUS 3000         // Timeout 3ms zostaje (zabezpieczenie)
#define RESP_RX_TO_FINAL_TX_DLY_UUS 4000 // dajmy Kotwicy BARDZO DUŻO czasu na matematykę przed FINALem
#define RESP_delay 8000 //czekamy na resp od kotwicy do 8ms


#define CALIB_RX_TIMEOUT_UUS 100000 // 100ms czasu na wykonanie całego zadania przez Kotwice

extern dwt_txconfig_t txconfig_options;

// ANCHOR CONFIG

#define ANCHOR_NUM 2   // ID tej konkretnej kotwicy - DO ZMIANY JESLI WGRYWAMY NA WIECEJ NIZ JEDNA

#define RX_BUF_LEN 24
#define POLL_RX_TO_RESP_TX_DLY_UUS 2500
#define RESP_TX_TO_FINAL_RX_DLY_UUS 150// no blind window after sending resp
#define FINAL_RX_TIMEOUT_UUS 8000 // dajmy Kotwicy BARDZO DUŻO czasu na matematykę przed FINALem
#define PRE_TIMEOUT 0 // usefull??

static uint8_t tx_resp_msg[]   = {0x41, 0x88, 0, 0xCA, 0xDE, 'R', 'E', 'S', 0, 0, 0, 0, 0, 0, 0};
static uint8_t rx_poll_msg[]   = {0x41, 0x88, 0, 0xCA, 0xDE, 'P', 'O', 'L', 0, 0, 0, 0};
static uint8_t rx_final_msg[]  = {0x41, 0x88, 0, 0xCA, 0xDE, 'F', 'I', 'N', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};


static uint64_t poll_rx_ts, resp_tx_ts, final_rx_ts;



