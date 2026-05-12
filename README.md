# 🧭 Indoor Navigation System (UWB + BLE)

![Status](https://img.shields.io/badge/Status-In_Development-orange)
![Platform](https://img.shields.io/badge/Hardware-ESP32%20%7C%20UWB-blue)
![App](https://img.shields.io/badge/App-Android%20(Kotlin)-green)
![Backend](https://img.shields.io/badge/Backend-PostgreSQL%20%7C%20PostgREST%20%7C%20Docker-blue)

An indoor wayfinding and navigation system designed to assist visually impaired individuals. The system relies on a hardware module attached to the user (e.g., a white cane or backpack) that scans the environment and communicates with a smartphone app to provide real-time audio guidance.

## 🌟 Core Concept

The system solves the problem of indoor positioning using a two-tier approach:
1. **Macro-Navigation (UWB):** The ESP32 utilizes an Ultra-Wideband (DW3000) module to perform Two-Way Ranging (TWR) with pre-installed anchors. This provides precise (cm-level) distance measurements for standard trilateration.
2. **Micro-Navigation (BLE):** To identify specific Points of Interest (POIs) like doors, classrooms, or fire extinguishers without expensive UWB anchors, the ESP32 acts as a BLE Scanner. It detects nearby low-cost BLE Beacons and estimates proximity based on RSSI (Received Signal Strength Indicator).
3. **Boundary Triggers (Context Switching):** BLE Beacons are also used as "Transition Zone Triggers" (e.g., in elevators or staircases). They solve the **Cold Start Problem** by waking up the system and telling the mobile app to load a new architectural context (e.g., switching from Floor 1 to Floor 2 anchors).
4. **Data Relay:** The ESP32 acts strictly as a "Dumb Sensor Hub". It bundles raw UWB distances and BLE RSSI readings and pushes them via a BLE GATT Server directly to the user's smartphone. The smartphone handles all heavy mathematical calculations and logic.

## 🌟 System Architecture

The project is built strictly on the **"Dumb Sensor, Smart Brain"** architectural paradigm, ensuring high scalability and low edge-device power consumption. It operates across three main tiers:

## 1. The Edge (ESP32 WROVER + DW3000 UWB)
Written in **C++** using **FreeRTOS** to leverage the dual-core architecture:
* **Core 0 (PRO_CPU):** Dedicated exclusively to the `NimBLE` Bluetooth stack to ensure uninterrupted radio communication with the smartphone.
* **Core 1 (APP_CPU):** Handles real-time UWB Time-of-Flight (ToF) distance calculations.
* **Dynamic Hardware Filtering:** To save bandwidth (MTU limits) and battery, the ESP32 receives an active "Whitelist" of MAC addresses from the mobile app. It automatically ignores all irrelevant BLE pollution (e.g., neighbor's headphones) at the hardware level.

### 2. The Brain (Android Mobile App)
Written in **Native Kotlin** (Jetpack Compose, Coroutines):
* **Navigation Routing Engine:** Acts as the main State Machine. It determines the user's current zone and dynamically updates the ESP32's hardware filter via custom BLE GATT characteristics.
* **Hierarchical Navigation UI:** The UI dynamically renders **Macro-Targets** (distant rooms/floors leading to transition points) and **Micro-Targets** (precise objects in the current room) based on context.
* **Offline-First Cache:** The building's topology and POI map are pre-fetched via Retrofit into a local RAM Cache (Single Source of Truth), ensuring navigation continues seamlessly in areas without Wi-Fi (e.g., basements).

### 3. Infrastructure & Backend (Database-as-Code)
A robust data layer running locally via **Docker Compose**:
* **PostgreSQL (Star Schema):** Stores `Dim_Topology`, `Dim_IoT_Devices`, and hierarchical `Dim_Navigation_Targets`.
* **Smart SQL Triggers:** The database features automated normalization. Inserting a new navigation target via MAC address automatically triggers a PL/pgSQL function to inherit the `Location_ID` from the physical device table, preventing data anomalies.
* **PostgREST (Zero-Code API):** Automatically exposes the PostgreSQL schema as a rapid, fully functional REST API (`http://localhost:3000`) consumed by the Android App.
* **Automated Provisioning:** A `db_init.py` script acts as Infrastructure-as-Code, capable of fully rebuilding the database structure and populating it with test data/simulated telemetry in seconds.
  
## 📂 Repository Structure

This repository is organized as a Monorepo containing hardware firmware, testing scripts, and the mobile application:

- `/firmware` - C++ / PlatformIO code for the ESP32 nodes (UWB Initiators, UWB Responders, BLE Scanners).
- `/app` - The Android mobile application (Kotlin) responsible for data processing, topology management, and connection handling.
- `/DB` - Database-as-Code configurations (`docker-compose.yml`, Python initialization scripts, SQL dumps).
- `/scripts` - Python and Bash scripts used for telemetry generation and testing.
- `/docs` - Project documentation, architectural diagrams, and mathematical models.

## ⚙️ Hardware Stack

- **Mobile Tag (User Device):** ESP32 WROVER + DW3000 UWB Module
- **Anchors:** 2+ ESP32 UWB modules placed statically in the environment.
- **Micro-Navigation Nodes:** Standard low-cost BLE Tags/Beacons (e.g., Shelly BLU).
- **Client:** Smartphone running Android 8.0+ with BLE support.

## 🚀 Current Status
- [x] BLE Server & Scanner architecture implementation (FreeRTOS).
- [x] Android App integration (Kotlin, MTU Negotiation, BLE GATT Callbacks).
- [x] Integration of actual UWB TWR (Time-of-Flight) distance readings.
- [x] Relational Database (PostgreSQL) & REST API (PostgREST) setup via Docker.
- [x] Smart SQL Triggers for automated relational data syncing.
- [x] Dynamic BLE hardware filtering controlled by the mobile app (Addressing the Cold Start problem).
- [x] Hierarchical Macro/Micro Navigation Data Model.
- [ ] RSSI to Distance calibration (Log-Distance Path Loss Model).
- [ ] Predictive Maintenance Data Mining (HDiSED Project - Battery decay analysis).
- [ ] Text-To-Speech (TTS) voice feedback implementation.
