package com.polsl.bemyeyes.navigation

import com.polsl.bemyeyes.navigation.dataBase.IoTDevice
import com.polsl.bemyeyes.navigation.dataBase.NavigationTarget

// 1. Enumy w Kotlinie
enum class WingIdentifier {
    WING_LEFT, WING_RIGHT
}

/**
 * Klasa zarządzająca topologią budynku AEI.
 */
class BuildingTopologyDatabase {

    // 3. Niezmienna mapa inicjalizowana od razu (bez użycia 'put' i 'new')
    //@TODO powinna pobrac z aktualnej listy devices jakie to pietro i na tej podstawie wystawic opcje nawigacji
    var cachedDevices: List<IoTDevice> = emptyList()
    var cachedTargets: List<NavigationTarget> = emptyList()

    // Funkcje pomocnicze dla UI
    fun getMacroTargets() = cachedTargets.filter { it.isMacroTarget }

    fun getMicroTargets(currentLocationId: Int?) =
        cachedTargets.filter { !it.isMacroTarget && it.locationId == currentLocationId }


    // --- FUNKCJE DLA SILNIKA (RoutingEngine) ---
    //  zastępuje Twoje stare 'getNodeById'
    fun getDeviceByMac(mac: String) = cachedDevices.find { it.macAddress == mac }

    // Funkcja do pobierania urządzeń na konkretne piętro
    fun getDevicesForLocation(locationId: Int): List<IoTDevice> {
        return cachedDevices.filter { it.locationId == locationId }
    }

}