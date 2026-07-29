package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils

// ============================================================================
// Pure Functional Core: DST Watch State Operations
// ============================================================================

/**
 * Pure functional core for DST watch state processing.
 * 
 * All methods are pure: no mutable state, no side effects.
 * Handles DST flag manipulation for the main watch clock.
 */
@RequiresApi(Build.VERSION_CODES.O)
object DstWatchStateIOFunctional {
    /**
     * Pure transformer: Updates DST flag for main clock.
     * 
     * Takes original DST string and new DST value, updates first clock flag.
     * No side effects - pure data transformation.
     */
    fun setDST(dst: String, value: Int): String =
        Utils.toIntArray(dst)
            .takeIf { it.size >= 4 }
            ?.apply { this[3] = value }
            ?.let { intArray ->
                Utils.byteArrayOfIntArray(intArray.toIntArray())
                    .let(Utils::fromByteArrayToHexStrWithSpaces)
            }
            ?: dst
}

@RequiresApi(Build.VERSION_CODES.O)
object DstWatchStateIO {

    private data class State(
        val deferredResult: CompletableDeferred<String>? = null
    )

    private var state = State()

    suspend fun request(dstState: IO.DstState): String =
        CachedIO.request("1d0" + dstState.state) { key -> getDSTWatchState(key) }

    private suspend fun getDSTWatchState(key: String): String {
        val deferred = CompletableDeferred<String>()
        synchronized(this) {
            state = state.copy(deferredResult = deferred)
        }
        IO.request(key)
        return deferred.await()
    }

    fun setDST(dst: String, value: Int): String =
        DstWatchStateIOFunctional.setDST(dst, value)

    fun onReceived(data: String) {
        synchronized(this) {
            state.deferredResult?.complete(data)
        }
    }
}
