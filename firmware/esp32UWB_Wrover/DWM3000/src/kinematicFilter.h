#pragma once
#include <Arduino.h>

class SmartUWBFilter {
  private:
    float max_speed_mps;     // Maksymalna prędkość w metrach na sekundę (m/s)
    uint32_t report_interval_ms; // Co ile milisekund wysyłać paczkę po BLE?
    
    float aggregated_sum = 0;
    int valid_count = 0;
    
    float last_accepted_dist = -1.0;
    uint32_t last_accepted_time = 0;
    uint32_t last_report_time = 0;
    int consecutive_rejects = 0;

  public:
    // Konstruktor: ustalamy np. 3.0 m/s (szybki chód) i raportowanie co 250ms (4Hz)
    SmartUWBFilter(float max_speed = 3.0, uint32_t interval_ms = 250) {
        max_speed_mps = max_speed;
        report_interval_ms = interval_ms;
    }

    // Dodawanie nowego surowego pomiaru
    void addRawMeasurement(float new_dist) {
        uint32_t current_time = millis();

        // 1. Pierwszy pomiar po włączeniu prądu -> akceptujemy w ciemno
        if (last_accepted_dist < 0) {
            acceptMeasurement(new_dist, current_time);
            return;
        }

        // 2. Ograniczenie Kinematyczne (Obliczamy prędkość między pomiarami)
        float delta_time_s = (current_time - last_accepted_time) / 1000.0;
        if (delta_time_s <= 0.001) delta_time_s = 0.001; // Zabezpieczenie na wypadek super-szybkich pętli
        
        float speed = abs(new_dist - last_accepted_dist) / delta_time_s;

        // 3. Filtracja błędów
        if (speed > max_speed_mps) {
            consecutive_rejects++;
            Serial.printf("[FILTR] Odrzucono anomalię: %.2f m. Predkość z rzędu: %.1f m/s\n", new_dist, speed);
            
            // Anti-Lockup: Jeśli 4 razy z rzędu pomiar przekracza prędkość, 
            // to znaczy że to nie błąd, tylko ktoś przestawił tag fizycznie. Resetujemy.
            if (consecutive_rejects >= 4) {
                Serial.println("[FILTR] Reset! Nowa prawdziwa pozycja.");
                acceptMeasurement(new_dist, current_time);
            }
            return; // Odrzucamy outliera, wychodzimy z funkcji
        }

        // 4. Jeśli tu doszliśmy, pomiar jest zgodny z fizyką!
        acceptMeasurement(new_dist, current_time);
    }

    // Funkcja do "Odchudzania" - mówi nam, czy nadszedł czas wysłać zbitą paczkę
    bool isReadyToReport(float &out_averaged_distance) {
        if (millis() - last_report_time >= report_interval_ms && valid_count > 0) {
            out_averaged_distance = aggregated_sum / valid_count; // Zwracamy średnią z poprawnych
            
            // Czyszczenie agregatora na kolejną rundę
            aggregated_sum = 0;
            valid_count = 0;
            last_report_time = millis();
            return true;
        }
        return false; // Jeszcze nie czas
    }

  private:
    void acceptMeasurement(float dist, uint32_t time) {
        last_accepted_dist = dist;
        last_accepted_time = time;
        aggregated_sum += dist;
        valid_count++;
        consecutive_rejects = 0;
    }
};

