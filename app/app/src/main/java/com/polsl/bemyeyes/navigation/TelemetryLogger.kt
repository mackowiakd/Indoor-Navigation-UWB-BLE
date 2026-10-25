package com.polsl.bemyeyes.navigation
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 1. Struktura pojedynczego wiersza logu
data class TelemetryLogEntry(
    val timestampMs: Long,
    val refX: Double?, // Zmienione z pojedynczego dystansu na współrzędne
    val refY: Double?,
    val calX: Double?,
    val calY: Double?,
    val measuredDistanceMeters: Double,
    val anchorId: String
)
//TODO: ui/action for inserting ref Points of path

class TelemetryLogger(private val context: Context) {
    // Status logowania (obserwowalny z UI)
    var isLogging = false
        private set

    // Scope do asynchronicznego zapisu plików (IO)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logJob: Job? = null

    // Kolejka (bufor) chroniąca przed gubieniem pakietów i blokowaniem wątków
    private val logChannel = Channel<TelemetryLogEntry>(Channel.UNLIMITED)

    var currentSessionFile: File? = null

    fun startLogging() {
        if (isLogging) return
        isLogging = true

        // 2. Tworzenie unikalnego pliku dla danej sesji
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "uwb_telemetry_$timestamp.csv"

        // Zapis do bezpiecznego folderu aplikacji: Android/data/com.polsl.bemyeyes/files/
        val file = File(context.getExternalFilesDir(null), fileName)
        currentSessionFile = file

        // 3. Wątek konsumujący dane w tle i zapisujący je na dysk
        logJob = scope.launch {
            BufferedWriter(FileWriter(file, true)).use { writer ->
                // Zrzut nagłówka CSV
                writer.write("Timestamp_ms,Ref_X,Ref_Y,Ref_X,Measured_X,Measured_Y,Measured_Distance_m,Anchor_ID,RSSI_FP_PWR\n")
                writer.flush()

                for (entry in logChannel) {
                    val xStr = entry.refX?.toString() ?: ""
                    val yStr = entry.refY?.toString() ?: ""
                    val xCal = entry.calX?.toString() ?: ""
                    val yCal = entry.calY?.toString() ?: ""
                    writer.write("${entry.timestampMs},$xStr,$yStr,$xCal,$yCal,${entry.measuredDistanceMeters},${entry.anchorId}\n")
                }
            }
        }
    }

    // 4. Główne API dla NavigationRou
    // Continuous mvm test(refX, refY = null) LUB ręcznie dla próbki statycznej
    fun logDataRow(refX: Double?, refY: Double?, measuredDist: Double, anchorId: String, calulatedPos: Pair<Double,Double>,) {
        if (!isLogging) return


        val entry = TelemetryLogEntry(
            timestampMs = System.currentTimeMillis(),
            refX = refX,
            refY = refY,
            calX=calulatedPos.first,
            calY=calulatedPos.second,
            //calX and calY must come from fun called in NavigationEninge
            measuredDistanceMeters = measuredDist,
            anchorId = anchorId
        )
        logChannel.trySend(entry)
    }



    fun stopLogging() {
        isLogging = false
        logJob?.cancel() // Zamyka bezpiecznie strumień zapisu
    }
}