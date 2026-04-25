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

## 🌟 System Architecture

The project is built strictly on the **"Dumb Sensor, Smart Brain"** architectural paradigm, ensuring high scalability and low edge-device power consumption. It operates across three main tiers:

### 1. The Edge (ESP32 WROVER + DW3000 UWB)
Written in **C++** using **FreeRTOS** to leverage the dual-core architecture:
* **Core 0 (PRO_CPU):** Dedicated exclusively to the `NimBLE` Bluetooth stack to ensure uninterrupted radio communication.
* **Core 1 (APP_CPU):** Handles real-time UWB Time-of-Flight (ToF) distance calculations in the main loop, while background FreeRTOS tasks manage BLE beacon scanning and asynchronous data transmission.
* **Dynamic Hardware Filtering:** To save bandwidth and battery, the ESP32 receives an active list of MAC addresses (Filter) from the mobile app via BLE `WRITE`. It automatically ignores all irrelevant BLE tags at the hardware level.

### 2. The Brain (Android Mobile App)
Written in **Native Kotlin**:
* **Navigation Routing Engine:** Acts as the State Machine. It determines the user's current floor/zone based on UWB anchors and dynamically updates the ESP32's hardware filter.
* **BLE Connection Manager:** A robust GATT client implementing custom MTU negotiation (512 bytes) and custom `ACK` (Acknowledgement) mechanisms for reliable data transfer.
* **Offline-First Topology:** The building's topology and node map are cached locally, ensuring navigation continues seamlessly in areas without internet connectivity (e.g., elevators).

### 3. The Data Warehouse (Analytics - *In Progress*)
A relational database designed using a **Star Schema** to analyze IoT fleet health and track telemetry:
* **Data Mining:** Implements Predictive Maintenance (e.g., Battery Decay Analysis based on long-term BLE RSSI drops) to alert building administration before a tag dies.

## 📂 Repository Structure

This repository is organized as a Monorepo containing hardware firmware, testing scripts, and the mobile application:

- `/firmware` - C++ / PlatformIO code for the ESP32 nodes (UWB Initiators, UWB Responders, BLE Scanners).
- `/app` - The Android mobile application (Kotlin) responsible for data processing, topology management, and connection handling.
- `/scripts` - Python and Bash scripts used for telemetry generation and testing.
- `/docs` - Project documentation, architectural diagrams, and mathematical models.

## ⚙️ Hardware Stack

- **Mobile Tag (User Device):** ESP32 WROVER + DW3000 UWB Module
- **Anchors:** 2+ ESP32 UWB modules placed statically in the environment.
- **Micro-Navigation Nodes:** Standard low-cost BLE Tags/Beacons (e.g., Shelly BLU).
- **Client:** Smartphone running Android 8.0+ with BLE support.

## 🚀 Current Status
- [x] BLE Server & Scanner architecture implementation (FreeRTOS).
- [x] Dual-Core execution and Time Multiplexing testing.
- [x] Android App integration (Kotlin, MTU 512, BLE GATT Callbacks).
- [x] Integration of actual UWB TWR (Time-of-Flight) distance readings.
- [x] Dynamic BLE hardware filtering controlled by the mobile app.
- [ ] Database / Data Warehouse implementation (HDiSED Project).
- [ ] RSSI to Distance calibration (Log-Distance Path Loss Model).
- [ ] Text-To-Speech (TTS) voice feedback implementation.
