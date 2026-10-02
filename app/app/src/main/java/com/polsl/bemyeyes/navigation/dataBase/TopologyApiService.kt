package com.polsl.bemyeyes.navigation.dataBase

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Body
import retrofit2.http.PATCH


interface TopologyApiService {

    // Zwraca listę wszystkich urządzeń (tagów i kotwic)
    @GET("dim_iot_devices")
    suspend fun getAllDevices(): List<IoTDevice>

    // Zwraca urządzenia dla konkretnego piętra (np. Location_ID = 2)
    // PostgREST wymaga formatu zapytania w stylu ?location_id=eq.2
    @GET("dim_iot_devices")
    suspend fun getDevicesByLocation(@Query("location_id") locationQuery: String): List<IoTDevice>
    // query Pobieranie celów nawigacyjnych
    @GET("dim_navigation_targets")
    suspend fun getNavigationTargets(): List<NavigationTarget>

    // 🔴 NOWE ZAPYTANIE: Aktualizacja współrzędnych konkretnej kotwicy
    @PATCH("dim_iot_devices")
    suspend fun updateDeviceCoordinates(
        @Query("mac_address") macQuery: String, // Format oczekiwany przez PostgREST: "eq.ADRES_MAC"
        @Body coordinates: Map<String, Double>  // Przesyłamy tylko wybrane pola do nadpisania
    ): Response<Unit> // <--- ZMIANA TUTAJ: Jawnie deklarujemy obiekt Response<Unit>
}