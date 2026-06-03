# 🧭 Indoor Navigation System (UWB + BLE)

![Status](https://img.shields.io/badge/Status-In_Development-orange)
![Platform](https://img.shields.io/badge/Hardware-ESP32%20%7C%20UWB-blue)
![App](https://img.shields.io/badge/App-Android%20(Kotlin)-green)
![Backend](https://img.shields.io/badge/Backend-PostgreSQL%20%7C%20PostgREST%20%7C%20Docker-blue)

An indoor wayfinding and navigation system designed to assist visually impaired individuals. The system relies on a hardware module attached to the user (e.g., a white cane or backpack) that scans the environment and communicates with a smartphone app to provide real-time audio guidance.

## 🧭 Core Concept & Navigation Paradigm

Unlike traditional GPS-like positioning systems that continuously compute absolute 2D/3D coordinates (XYZ), this project implements a **Topological (Nodal) Navigation System** driven by a **Proximity-Based ("Hot/Cold") Guidance Paradigm**. 

The system maps the environment as a graph of interconnected semantic points (zones and assets), focusing on linear distance vectors and landmark exploration rather than geometric grid-tracking.

The system processes spatial data through a two-tier approach:
1. **Macro-Navigation (UWB Homing):** The ESP32 utilizes an Ultra-Wideband (DW3000) module to perform Two-Way Ranging (TWR) with static anchors. Instead of running heavy geometric equations on the edge, the app analyzes the 1D distance vector to the selected anchor, acting as a high-precision spatial radar ("Hot/Cold") that guides the user along corridors or open spaces.
2. **Micro-Navigation (BLE Proximity):** To identify specific Points of Interest (POIs) like desks, coffee machines, or windows without expensive infrastructure, the ESP32 acts as a BLE Scanner. It detects nearby low-cost BLE Beacons and estimates immediate proximity based on heavily filtered RSSI (Received Signal Strength Indicator) thresholds.
3. **Boundary Triggers (Context Switching):** BLE Beacons also serve as "Topological Transition Triggers" (e.g., placed at doors or zone boundaries). They solve the **Cold Start Problem** by waking up the application and forcing the mobile app to load a new architectural context (e.g., switching the active asset checklist from Floor 1 to the UWB Laboratory zone).
4. **Data Relay:** The ESP32 acts strictly as a "Dumb Sensor Hub". It bundles raw UWB distances and BLE RSSI readings and pushes them via a BLE GATT Server directly to the user's smartphone. The smartphone acts as the "Smart Brain", handling all state transitions, background asset alerts, and text-to-speech output.

## 📐 Engineering Scope & Architectural Limits

To maintain a strict and deliverable engineering focus for this thesis, a clear line was drawn between **Topological Awareness** and **Absolute Coordinate Positioning**:
* **In Scope (Implemented):** 1D distance tracking, multi-zone context switching, asynchronous passing/landmark announcements ("You are passing: Window"), proximity-based finish alerts, and dynamic BLE hardware white-list filtering.
* **Out of Scope (Future Work):** Multilateration/Trilateration algorithms, absolute XY coordinate mapping, and compass-driven angular vector calculations. The system is intentionally designed as a robust **hardware-and-software communication framework** that can easily ingest coordinate-calculation layers in future iterations (e.g., a Master's thesis expansion).
* 
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
