package com.polsl.bemyeyes.navigation;

import java.util.HashMap;
import java.util.Map;

/**
 * Klasa abstrakcyjna służąca do modelowania przestrzeni budynku AEI.
 * Zarządza regułami umiejscowienia sal, klatek schodowych oraz łączników.
 */
public class BuildingTopologyDatabase {

    public enum WingIdentifier {
        WING_LEFT, WING_RIGHT
    }

    public enum NodeType {
        ROOM, STAIRCASE, ELEVATOR, WING_CONNECTOR
    }

    public static class AnchorNode {
        public final String macAddress;
        public final WingIdentifier wing;
        public final int floorLevel;
        public final NodeType type;
        public final String humanReadableName;

        public AnchorNode(String macAddress, WingIdentifier wing, int floorLevel, NodeType type, String name) {
            this.macAddress = macAddress;
            this.wing = wing;
            this.floorLevel = floorLevel;
            this.type = type;
            this.humanReadableName = name;
        }
    }

    private final Map<String, AnchorNode> anchorRegistry = new HashMap<>();

    public BuildingTopologyDatabase() {
        // Wypełnienie rejestru danymi z rozmieszczenia węzłów MQTT/UWB
        // Mapowanie kotwic z Twojego systemu MQTT
        anchorRegistry.put("A1", new AnchorNode("A1", WingIdentifier.WING_RIGHT, 4, NodeType.ROOM, "Sala 420"));
        
        // Przykładowe dane pomocnicze
        anchorRegistry.put("UWB_001", new AnchorNode("UWB_001", WingIdentifier.WING_LEFT, 2, NodeType.ROOM, "Sala 214"));
        anchorRegistry.put("UWB_002", new AnchorNode("UWB_002", WingIdentifier.WING_LEFT, 2, NodeType.ROOM, "Sala 215"));
        anchorRegistry.put("UWB_003", new AnchorNode("UWB_003", WingIdentifier.WING_LEFT, 2, NodeType.STAIRCASE, "Klatka schodowa centralna"));
    }

    public AnchorNode getNodeById(String id) {
        return anchorRegistry.get(id);
    }

    public boolean isConnectorAvailableOnFloor(int floorLevel) {
        return floorLevel == 0 || floorLevel == 1 || floorLevel == 3 || floorLevel == 5 || floorLevel == 7;
    }
}
