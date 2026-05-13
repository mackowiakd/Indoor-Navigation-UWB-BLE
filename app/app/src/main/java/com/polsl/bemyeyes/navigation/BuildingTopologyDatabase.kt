package com.polsl.bemyeyes.navigation

import com.polsl.bemyeyes.navigation.dataBase.IoTDevice

// 1. Enumy w Kotlinie
enum class WingIdentifier {
    WING_LEFT, WING_RIGHT
}

enum class NodeType {
    ROOM, STAIRCASE, ELEVATOR, WING_CONNECTOR
}

// 2. Klasa Danych (Data Class) - Kotlin sam generuje dla niej konstruktor, gettery i funkcję toString()!
data class AnchorNode(
    val macAddress: String,
    val wing: WingIdentifier,
    val floorLevel: Int,
    val type: NodeType,
    val humanReadableName: String
)

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

    // Ta funkcja zastępuje Twoje stare 'getNodeById'
    fun getDeviceByMac(mac: String) = cachedDevices.find { it.macAddress == mac }

    // Kotlin zwraca typ nullable (AnchorNode?), jeśli danego ID nie ma w bazie


    // 4. Składnia Kotlina dla sprawdzania warunków (operator 'in')
    fun isConnectorAvailableOnFloor(floorLevel: Int): Boolean {
        // Zamiast długiego łańcucha (== 0 || == 1 || ...), sprawdzamy czy wartość jest w zbiorze
        return floorLevel in setOf(0, 1, 3, 5, 7)
    }

    // To jest nasz "Singleton w RAM". Zapiszemy tu całą bazę po starcie apki.


    // Szybka funkcja do wyciągania triggerów (żeby łatwo sprawdzać, czy jesteśmy przy schodach)
    fun getBoundaryTriggers(): List<IoTDevice> {
        return cachedDevices.filter { it.semanticRole.contains("TRIGGER") }
    }

    // Funkcja do pobierania urządzeń na konkretne piętro
    fun getDevicesForLocation(locationId: Int): List<IoTDevice> {
        return cachedDevices.filter { it.locationId == locationId }
    }

}