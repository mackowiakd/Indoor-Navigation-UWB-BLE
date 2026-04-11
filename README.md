# 🧭 Indoor Navigation System (UWB + BLE)

![Status](https://img.shields.io/badge/Status-In_Development-orange)
![Platform](https://img.shields.io/badge/Hardware-ESP32%20%7C%20UWB-blue)
![App](https://img.shields.io/badge/App-Android%20(Kotlin)-green)

An indoor wayfinding and navigation system designed to assist visually impaired individuals. The system relies on a hardware module attached to the user (e.g., a white cane or backpack) that scans the environment and communicates with a smartphone app to provide real-time audio guidance.

## 🌟 Core Concept

The system solves the problem of indoor positioning using a two-tier approach:
1. **Macro-Navigation (UWB):** The ESP32 utilizes an Ultra-Wideband (DW3000) module to perform Two-Way Ranging (TWR) with pre-installed anchors. This provides precise (cm-level) distance measurements for standard trilateration.
2. **Micro-Navigation (BLE):** To identify specific Points of Interest (POIs) like doors, classrooms, or fire extinguishers without expensive UWB anchors, the ESP32 acts as a BLE Scanner. It detects nearby low-cost BLE Beacons and estimates proximity based on RSSI (Received Signal Strength Indicator).
3. **Data Relay:** The ESP32 acts merely as a sensor hub. It bundles raw UWB distances and BLE RSSI readings and pushes them via a BLE GATT Server directly to the user's smartphone. The smartphone handles all heavy mathematical calculations and Text-To-Speech (TTS) feedback.

## 📂 Repository Structure

This repository is organized as a Monorepo containing hardware firmware, testing scripts, and the mobile application:

- `/firmware` - C++ / Arduino code for the ESP32 modules (BLE Server, Scanner, and UWB integration).
- `/scripts` - Python and Bash scripts used for latency testing, time multiplexing evaluation, and simulating BLE tags on Linux.
- `/app` - The Android mobile application (Kotlin) responsible for data processing, trilateration, and voice feedback. *(Work in progress)*
- `/docs` - Project documentation, architectural diagrams, and mathematical models.

## ⚙️ Hardware Stack

- **Primary Module:** [Makerfabs ESP32 UWB (DW3000)](https://www.makerfabs.com/esp32-uwb-ultra-wideband.html)
- **Prototyping & Testing:** Seeed Studio XIAO ESP32C3
- **Environment:** Standard BLE Tags/Beacons & UWB Anchors.

## 🚀 Current Status
- [x] BLE Server architecture implementation.
- [x] BLE Scanner and Observer implementation.
- [x] Time Multiplexing testing (Scanning + Notifying simultaneously).
- [ ] Integration of actual UWB TWR readings.
- [ ] RSSI to Distance calibration (Log-Distance Path Loss Model).
- [ ] Android App integration.