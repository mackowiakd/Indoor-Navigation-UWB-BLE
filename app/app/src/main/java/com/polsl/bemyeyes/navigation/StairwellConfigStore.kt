package com.polsl.bemyeyes.navigation

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

/**
 * Odczyt pliku konfiguracji klatki wskazanego przez użytkownika (Storage Access Framework - bez żadnych
 * uprawnień) oraz lokalna kopia w pamięci wewnętrznej aplikacji, wczytywana automatycznie przy starcie.
 */
class StairwellConfigStore(private val context: Context) {

    private val savedFile: File
        get() = File(context.filesDir, "stairwell_config.json")

    @Throws(IOException::class)
    fun readUri(uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Nie można otworzyć pliku")
        if (bytes.size > MAX_BYTES) throw IOException("Plik jest za duży (${bytes.size} B, limit $MAX_BYTES B)")
        return bytes.toString(Charsets.UTF_8).removePrefix("﻿") // JSON bywa zapisany z BOM
    }

    @Throws(IOException::class)
    fun save(json: String) {
        savedFile.writeText(json, Charsets.UTF_8)
    }

    fun loadSaved(): String? = try {
        if (savedFile.exists()) savedFile.readText(Charsets.UTF_8) else null
    } catch (e: IOException) {
        null
    }

    private companion object {
        const val MAX_BYTES = 2_000_000
    }
}
