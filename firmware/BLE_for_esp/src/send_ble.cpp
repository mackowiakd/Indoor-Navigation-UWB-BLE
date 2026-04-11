// #include <Arduino.h>
// #include <BLEDevice.h>
// #include <BLEServer.h>
// #include <BLEUtils.h>
// #include <BLE2902.h> // Wymagane do działania powiadomień (Notify)

// // --- BLE config for GATT architecture ---
// // Wygenerowane losowe UUID dla naszego serwisu i charakterystyki
// #define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
// #define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

// BLEServer* pServer = NULL;
// BLECharacteristic* pCharacteristic = NULL;
// bool deviceConnected = false;
// bool oldDeviceConnected = false;

// // --- CALLBACKI BLE ---
// // Ta klasa sprawdza, czy telefon się podłączył, czy odłączył
// class MyServerCallbacks: public BLEServerCallbacks {
//     void onConnect(BLEServer* pServer) {
//       deviceConnected = true;
//       Serial.println("Telefon podłączony!");
//     };

//     void onDisconnect(BLEServer* pServer) {
//       deviceConnected = false;
//       Serial.println("Telefon odłączony!");
//     }
// };

// // --- CZARNA SKRZYNKA (SYMULATOR UWB) ---
// // Ta funkcja symuluje działanie prawdziwego czujnika UWB.
// // Generuje lekko zmieniające się wartości, żebyśmy widzieli ruch w nRF Connect.
// String get_simulated_uwb_data() {
//     // Używamy millis() jako "szumu" żeby dystanse się zmieniały
//     float distA1 = 2.10 + (sin(millis() / 1000.0) * 0.1); 
//     float distA2 = 4.50 + (cos(millis() / 800.0) * 0.2);
//     float distA3 = 1.85; // Stała wartość

//     char dist_str[64];
//     sprintf(dist_str, "A1:%3.2f;A2:%3.2f;A3:%3.2f", distA1, distA2, distA3);
    
//     return String(dist_str);
// }

// // --- MODUŁ INICJALIZACJI BLE ---
// // Tę funkcję po prostu wywołasz raz w głównym setup() projektu ESP32 UWB
// void setupBLE() {
//     BLEDevice::init("XIAO_UWB_Nawigacja"); // Nazwa, którą zobaczysz w telefonie

//     // Tworzenie Serwera
//     pServer = BLEDevice::createServer();
//     pServer->setCallbacks(new MyServerCallbacks());

//     // Tworzenie Serwisu
//     BLEService *pService = pServer->createService(SERVICE_UUID);

//     // Tworzenie Charakterystyki (Tylko Odczyt + Powiadomienia)
//     pCharacteristic = pService->createCharacteristic(
//                         CHARACTERISTIC_UUID,
//                         BLECharacteristic::PROPERTY_READ   |
//                         BLECharacteristic::PROPERTY_NOTIFY
//                       );

//     // Dodanie deskryptora (CCC), który pozwala telefonowi subskrybować Notify
//     pCharacteristic->addDescriptor(new BLE2902());

//     // Uruchomienie serwisu i rozgłaszania (Advertising)
//     pService->start();
//     BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
//     pAdvertising->addServiceUUID(SERVICE_UUID);
//     pAdvertising->setScanResponse(false);
//     pAdvertising->setMinPreferred(0x0);  
//     BLEDevice::startAdvertising();
//     Serial.println("BLE gotowe! Oczekuję na połączenie...");
// }

// // --- GŁÓWNY PROGRAM ---

// void setup() {
//     Serial.begin(115200);
//     delay(2000); // Krótka przerwa na otwarcie portu szeregowego
    
//     Serial.println("Startowanie modułu BLE...");
//     setupBLE(); // Inicjalizacja naszej modułowej sekcji
// }

// void loop() {
//     // Wysyłaj dane TYLKO wtedy, gdy telefon jest podłączony (oszczędzanie energii)
//     if (deviceConnected) {
//         String uwb_data = get_simulated_uwb_data(); // Pobierz dane z "UWB"
        
//         // Zapakuj string do charakterystyki i wyślij powiadomienie
//         pCharacteristic->setValue(uwb_data.c_str());
//         pCharacteristic->notify();
        
//         Serial.print("Wysłano po BLE: ");
//         Serial.println(uwb_data);
        
//         delay(100); // Wysyłamy co 100ms (10Hz) - wystarczająco szybko dla nawigacji
//     }

//     // Obsługa rozłączenia (aby móc podłączyć się ponownie bez resetu płytki)
//     if (!deviceConnected && oldDeviceConnected) {
//         delay(500); 
//         pServer->startAdvertising(); // Wznów reklamowanie
//         Serial.println("Wznowiono rozgłaszanie (Advertising)");
//         oldDeviceConnected = deviceConnected;
//     }
//     if (deviceConnected && !oldDeviceConnected) {
//         oldDeviceConnected = deviceConnected;
//     }
// }