package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils

// ============================================================================
// Pure Functional Core: Watch Name Processing
// ============================================================================

/**
 * Pure functional core for watch name processing.
 */
@RequiresApi(Build.VERSION_CODES.O)
object WatchNameIOFunctional {
    /**
     * Pure decoder: Extracts watch name from hex string.
     * 
     * Command code (0x23) followed by ASCII name string.
     */
    fun decode(data: String): String =
        Utils.toAsciiString(data, 1)
}

@RequiresApi(Build.VERSION_CODES.O)
object WatchNameIO {
    private data class State(
        val deferredResult: CompletableDeferred<String>? = null
    )

    private var state = State()

    suspend fun request(): String =
        CachedIO.request("23") { key -> getWatchName(key) }

    private suspend fun getWatchName(key: String): String {
        val deferred = CompletableDeferred<String>()
        synchronized(this) {
            state = state.copy(deferredResult = deferred)
        }
        IO.request(key)
        return deferred.await()
    }

    fun onReceived(data: String) {
        val name = WatchNameIOFunctional.decode(data)
        synchronized(this) {
            state.deferredResult?.complete(name)
        }
    }
}
