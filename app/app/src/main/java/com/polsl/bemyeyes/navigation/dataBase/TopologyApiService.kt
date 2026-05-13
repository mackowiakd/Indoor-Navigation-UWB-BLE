package com.polsl.bemyeyes.navigation.dataBase

import retrofit2.http.GET
import retrofit2.http.Query

interface TopologyApiService {

    // Zwraca listę wszystkich urządzeń (tagów i kotwic)
    @GET("dim_iot_devices")
    suspend fun getAllDevices(): List<IoTDevice>

    // Zwraca urządzenia dla konkretnego piętra (np. Location_ID = 2)
    // PostgREST wymaga formatu zapytania w stylu ?location_id=eq.2
    @GET("dim_iot_devices")
    suspend fun getDevicesByLocation(@Query("location_id") locationQuery: String): List<IoTDevice>
    // 🔴 NOWE ZAPYTANIE: Pobieranie celów nawigacyjnych
    @GET("dim_navigation_targets")
    suspend fun getNavigationTargets(): List<NavigationTarget>
}