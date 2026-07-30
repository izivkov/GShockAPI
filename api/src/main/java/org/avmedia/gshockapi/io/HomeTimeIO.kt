package org.avmedia.gshockapi.io

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.utils.Utils

// ============================================================================
// Pure Functional Core: Home Time Data Processing
// ============================================================================

/**
 * Pure functional core for home time processing.
 * 
 * All methods are pure: no mutable state, no side effects.
 * Handles home city data transformations.
 */
@RequiresApi(Build.VERSION_CODES.O)
object HomeTimeIOFunctional {
    /**
     * Pure parser: Extracts home city name from world cities data.
     * 
     * Converts raw city data (starting at index 2 or 4) to ASCII string.
     * No side effects - pure string transformation.
     */
    fun parseHomeCity(data: String): String {
        if (data.isBlank()) return "N/A"
        val offset = if (!WatchInfo.hasWorldCities) 4 else 2
        val name = Utils.toAsciiString(data, offset)
        return if (name.isBlank() || name.all { it == 'ÿ' }) "N/A" else name
    }
}

// ============================================================================
// Imperative Shell: Side Effects & State Management
// ============================================================================

/**
 * Home Time IO handler with state management.
 * 
 * Provides access to the primary home city timezone.
 * Uses pure functional core for data parsing.
 */
@RequiresApi(Build.VERSION_CODES.O)
object HomeTimeIO {
    private data class State(
        val deferredResult: CompletableDeferred<String>? = null,
        val homeCity: String = ""
    )

    private var state = State()

    suspend fun request(slot: Int = 0): String {
        val raw = requestRaw(slot)
        return HomeTimeIOFunctional.parseHomeCity(raw)
    }

    suspend fun requestRaw(slot: Int = 0): String {
        return if (WatchInfo.hasHomeTime && !WatchInfo.hasWorldCities) {
            CachedIO.request("240$slot") { key ->
                val deferred = CompletableDeferred<String>()
                synchronized(this) {
                    state = state.copy(deferredResult = deferred)
                }
                IO.request(key)
                deferred.await()
            }
        } else {
            WorldCitiesIO.request(slot)
        }
    }

    fun onReceived(data: String) {
        synchronized(this) {
            state.deferredResult?.complete(data)
            state = state.copy(homeCity = data)
        }
    }
}
