package com.polsl.bemyeyes.navigation

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
    private val anchorRegistry: Map<String, AnchorNode> = mapOf(

        // Makronawigacja (Oryginalne węzły UWB/MQTT z Twojego kodu)
        "A1" to AnchorNode("A1", WingIdentifier.WING_RIGHT, 4, NodeType.ROOM, "Sala 420"),
        "UWB_001" to AnchorNode("UWB_001", WingIdentifier.WING_LEFT, 2, NodeType.ROOM, "Sala 214"),
        "UWB_002" to AnchorNode("UWB_002", WingIdentifier.WING_LEFT, 2, NodeType.ROOM, "Sala 215"),
        "UWB_003" to AnchorNode("UWB_003", WingIdentifier.WING_LEFT, 2, NodeType.STAIRCASE, "Klatka schodowa centralna"),

        // Mikronawigacja (Tagi BLE - dodane pod Twoją prezentację w jednym pokoju)
        "TAG_DESK" to AnchorNode("TAG_DESK", WingIdentifier.WING_LEFT, 1, NodeType.ROOM, "Biurko prowadzącego"),
        "TAG_COFFEE" to AnchorNode("TAG_COFFEE", WingIdentifier.WING_LEFT, 1, NodeType.ROOM, "Ekspres do kawy")
    )

    // Kotlin zwraca typ nullable (AnchorNode?), jeśli danego ID nie ma w bazie
    fun getNodeById(id: String): AnchorNode? {
        return anchorRegistry[id]
    }

    // 4. Składnia Kotlina dla sprawdzania warunków (operator 'in')
    fun isConnectorAvailableOnFloor(floorLevel: Int): Boolean {
        // Zamiast długiego łańcucha (== 0 || == 1 || ...), sprawdzamy czy wartość jest w zbiorze
        return floorLevel in setOf(0, 1, 3, 5, 7)
    }
}