#include <Arduino.h>
#include <NimBLEDevice.h>
#include <Adafruit_NeoPixel.h>

// --- HARDWARE CONFIGURATION ---
#define LED_PIN     3      // GPIO pin for NeoPixel (D1 on XIAO)
#define LED_COUNT   1      // Number of LEDs
#define BRIGHTNESS  100     // LED Brightness (0-255)

// --- BLE CONFIGURATION (UUIDs) ---
// Target service and characteristic UUIDs for the PoC
#define SERVICE_UUID        "12345678-1234-1234-1234-123456789012"
#define CHARACTERISTIC_UUID "87654321-4321-4321-4321-210987654321"

// LED Strip initialization
Adafruit_NeoPixel pixels(LED_COUNT, LED_PIN, NEO_GRB + NEO_KHZ800);
bool deviceConnected = false;

// 1. Server Callbacks (Connection management)
class MyServerCallbacks: public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* pServer) {
        deviceConnected = true;
        Serial.println("[*] Client connected. Device is vulnerable.");
    };

    void onDisconnect(NimBLEServer* pServer) {
        deviceConnected = false;
        Serial.println("[*] Client disconnected.");
        // Crucial: Restart advertising so the device can be exploited again
        NimBLEDevice::startAdvertising(); 
        Serial.println("[*] Restarting advertising...");
    }
};

// 2. Characteristic Callbacks (Payload execution)
class MyCallbacks: public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic *pCharacteristic) {
        std::string value = pCharacteristic->getValue();

        // Expecting a 3-byte payload (R, G, B)
        if (value.length() >= 3) {
            uint8_t r = value[0];
            uint8_t g = value[1];
            uint8_t b = value[2];
            
            Serial.printf("[+] COMMAND RECEIVED: R=%d, G=%d, B=%d\n", r, g, b);
            
            // Execute the unauthorized payload
            pixels.setPixelColor(0, pixels.Color(r, g, b));
            pixels.show();
        } else {
            Serial.println("[-] Error: Invalid payload length.");
        }
    }
};

void setup() {
  Serial.begin(115200);
  delay(2000);
  
  // --- LED INITIALIZATION ---
  pixels.begin();
  pixels.show(); 
  pixels.setBrightness(BRIGHTNESS);
  Serial.println("[*] Hardware initialized.");
  
  // --- BLE VULNERABLE SERVER SETUP ---
  NimBLEDevice::init("XIAO_Vulnerable_LED"); 
  
  NimBLEServer *pServer = NimBLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  NimBLEService *pService = pServer->createService(SERVICE_UUID);

  // VULNERABILITY:
  // Read/Write properties are enabled, but NO encryption or authentication is required.
  // Missing NIMBLE_PROPERTY::WRITE_ENC / READ_ENC flags.
  NimBLECharacteristic *pCharacteristic = pService->createCharacteristic(
                                         CHARACTERISTIC_UUID,
                                         NIMBLE_PROPERTY::READ |
                                         NIMBLE_PROPERTY::WRITE |
                                         NIMBLE_PROPERTY::WRITE_NR // Write No Response (faster exploit)
                                       );

  pCharacteristic->setCallbacks(new MyCallbacks());
  pService->start();
  
  NimBLEAdvertising *pAdvertising = NimBLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->start();
  
  Serial.println("[*] BLE Server running. Awaiting connections...");
  
  // Set to idle color (Blue) indicating it's ready
  pixels.setPixelColor(0, pixels.Color(0, 0, 255));
  pixels.show();
}

void loop() {
  // Everything is handled asynchronously by NimBLE callbacks
  delay(2000);
}