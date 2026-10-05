package com.polsl.bemyeyes.navigation

// =========================================================
// SESJA POZYCJONOWANIA NA KLATCE SCHODOWEJ
// Łączy: konfigurację klatki -> filtry dystansu -> solver 3D -> detekcję piętra.
// Czysty Kotlin (bez Androida). Wołana z wątku BLE, więc metody są synchronizowane.
// =========================================================

data class StairwellSnapshot(
    val position: Position3D,
    val floorState: FloorState,
    val usedAnchors: Int
)

class StairwellSession(
    val config: StairwellConfig,
    filterFactory: () -> DistanceFilter = { MedianFilter(5) },
    private val strategy: PositioningStrategy3D = Trilateration3DStrategy(),
    private val staleAfterMs: Long = 2500L, // tyle samo co TIMEOUT_MS w firmware (app_data.cpp)
    private val tagHeight: Double = 0.0,    // wysokość tagu nad powierzchnią [m]; odejmowana od Z przy detekcji piętra
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val volume = config.toVolume()
    val floorTracker: FloorTracker = config.createFloorTracker()

    private val filters = DistanceFilterBank(filterFactory)
    private val filtered = mutableMapOf<String, Double>()  // ostatni przefiltrowany dystans per kotwica
    private val rawLast = mutableMapOf<String, Double>()   // ostatnia surowa wartość (do wykrywania duplikatów)
    private val seenAt = mutableMapOf<String, Long>()      // kiedy ostatnio widzieliśmy kotwicę w ramce
    private val newDataAt = mutableMapOf<String, Long>()   // kiedy ostatnio przyszła NOWA wartość
    private var lastPosition: Position3D? = null

    /**
     * Przetwarza jeden rekord z ramki ESP32 (np. "U_0x0003=2.45").
     *
     * Firmware wysyła migawkę wszystkich kotwic co ok. 100 ms, a nowy pomiar UWB pojawia się rzadziej (~3 Hz),
     * więc ta sama wartość wraca w kilku kolejnych ramkach. Duplikaty nie trafiają do filtra (inaczej mediana
     * i średnia "uśredniałyby" w kółko jeden pomiar) i nie uruchamiają ponownie solvera.
     *
     * @return nowa pozycja albo null (nieznana kotwica, duplikat, za mało świeżych kotwic lub brak rozwiązania)
     */
    @Synchronized
    fun onMeasurement(rawId: String, distance: Double): StairwellSnapshot? {
        val anchor = config.anchorById(rawId) ?: return null
        if (!distance.isFinite() || distance <= 0.0) return null

        val id = normalizeAnchorId(anchor.id)
        val now = clock()

        val previousSeen = seenAt[id]
        if (previousSeen != null && now - previousSeen > staleAfterMs) {
            // po przerwie w sygnale historia filtra jest nieaktualna
            filters.reset(id)
            filtered.remove(id)
            rawLast.remove(id)
        }
        seenAt[id] = now

        if (rawLast[id] == distance) return null // duplikat migawki
        rawLast[id] = distance

        val dt = newDataAt[id]?.let { ((now - it) / 1000.0).coerceIn(0.01, 1.0) } ?: 0.1
        newDataAt[id] = now
        filtered[id] = filters.update(id, distance, dt)

        // kotwice niewidziane od dłuższego czasu nie biorą udziału w obliczeniach
        val fresh = filtered.filterKeys { (seenAt[it] ?: 0L) >= now - staleAfterMs }
        val points = fresh.mapNotNull { (key, d) ->
            config.anchorById(key)?.let { RangedPoint3D(it.x, it.y, it.z, d, it.sigma) }
        }
        if (points.size < 3) return null

        val position = strategy.calculatePosition(points, volume, lastPosition) ?: return null
        lastPosition = position

        // niejednoznaczna lub poza bryłą pozycja nie powinna wpływać na detekcję piętra
        if (position.insideVolume && !position.ambiguous) floorTracker.update(position.z - tagHeight)

        return StairwellSnapshot(position, floorTracker.state, points.size)
    }

    /** Ostatni przefiltrowany dystans do kotwicy (do podglądu / diagnostyki) albo null. */
    @Synchronized
    fun filteredDistance(rawId: String): Double? = filtered[normalizeAnchorId(rawId)]

    @Synchronized
    fun reset() {
        filters.reset()
        filtered.clear()
        rawLast.clear()
        seenAt.clear()
        newDataAt.clear()
        lastPosition = null
        floorTracker.reset()
    }
}
