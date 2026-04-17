package com.polsl.bemyeyes.navigation;

import android.util.Log;

/**
 * Silnik analizujący stany przejść grafu budynku oraz dystrybuujący komendy głosowe.
 */
public class NavigationRoutingEngine {

    private final BuildingTopologyDatabase topologyDatabase;
    private final AccessibilitySpeechService speechService;
    
    private BuildingTopologyDatabase.AnchorNode currentEstimatedNode;
    private BuildingTopologyDatabase.AnchorNode userDestinationNode;

    private static final double PASSING_THRESHOLD_METERS = 1.5;
    private int lastAnnouncedDistanceInt = -1;
    private boolean reachAnnounced15 = false;
    private boolean reachAnnounced10 = false;

    public NavigationRoutingEngine(BuildingTopologyDatabase topology, AccessibilitySpeechService speechService) {
        this.topologyDatabase = topology;
        this.speechService = speechService;
    }

    public void setNavigationTarget(String targetAnchorId) {
        this.currentEstimatedNode = null;
        this.userDestinationNode = topologyDatabase.getNodeById(targetAnchorId);
        this.lastAnnouncedDistanceInt = -1;
        this.reachAnnounced15 = false;
        this.reachAnnounced10 = false;
        
        if (userDestinationNode != null) {
            speechService.announceImportant("Rozpoczynam nawigację do " + userDestinationNode.humanReadableName);
        }
    }

    public void processNewTelemetryData(String anchorId, double distanceInMeters) {
        BuildingTopologyDatabase.AnchorNode detectedNode = topologyDatabase.getNodeById(anchorId);
        if (detectedNode == null) return;

        // Obsługa dystansu do CELU (niezależnie od innych kotwic)
        if (userDestinationNode != null && anchorId.equals(userDestinationNode.macAddress)) {
            announceDistanceProgress(distanceInMeters);
        }

        // Logika "mijania" innych punktów (tylko jeśli są blisko)
        if (distanceInMeters <= PASSING_THRESHOLD_METERS) {
            if (currentEstimatedNode == null || !currentEstimatedNode.macAddress.equals(detectedNode.macAddress)) {
                currentEstimatedNode = detectedNode;
                evaluatePathAndAnnounce();
            }
        }
    }

    private void announceDistanceProgress(double distance) {
        int distanceInt = (int) Math.round(distance);

        // Scenariusz: Finisz (1.0m)
        if (distance <= 1.0 && !reachAnnounced10) {
            speechService.announceImportant("Jesteś przed " + userDestinationNode.humanReadableName);
            reachAnnounced10 = true;
            return;
        }

        // Scenariusz: Finisz (1.5m) - to jest moment "dotarcia"
        if (distance <= 1.5 && !reachAnnounced15) {
            speechService.announceImportant("Dotarłeś do celu. Jesteś przed " + userDestinationNode.humanReadableName);
            reachAnnounced15 = true;
            return;
        }

        // Scenariusz: Zbliżanie się (powyżej 1.5m)
        if (distance > 1.5) {
            if (distanceInt <= 10) {
                // Poniżej 10m: co 1 metr
                if (distanceInt != lastAnnouncedDistanceInt) {
                    speechService.announceBackground(distanceInt + " " + getMeterSpelling(distanceInt));
                    lastAnnouncedDistanceInt = distanceInt;
                }
            } else {
                // Powyżej 10m: co 5 metrów
                int roundedTo5 = (distanceInt / 5) * 5;
                if (roundedTo5 != lastAnnouncedDistanceInt && distanceInt % 5 == 0) {
                    speechService.announceBackground(distanceInt + " " + getMeterSpelling(distanceInt));
                    lastAnnouncedDistanceInt = roundedTo5;
                }
            }
        }
    }

    /**
     * Zwraca poprawną formę słowa "metr" w języku polskim.
     */
    private String getMeterSpelling(int count) {
        if (count == 1) return "metr";
        int lastDigit = count % 10;
        int lastTwoDigits = count % 100;

        if (lastDigit >= 2 && lastDigit <= 4 && (lastTwoDigits < 12 || lastTwoDigits > 14)) {
            return "metry";
        }
        return "metrów";
    }

    private void evaluatePathAndAnnounce() {
        if (userDestinationNode == null || currentEstimatedNode == null) return;

        // Jeśli właśnie minęliśmy cel, czyścimy go
        if (currentEstimatedNode.macAddress.equals(userDestinationNode.macAddress)) {
            userDestinationNode = null; 
            currentEstimatedNode = null;
            return;
        }

        speechService.announceBackground("Mijasz " + currentEstimatedNode.humanReadableName);
        
        // ... reszta logiki kierunkowej (skrzydła/piętra) ...
    }
}
