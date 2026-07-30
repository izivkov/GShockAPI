package org.avmedia.gshockapi.casio

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.utils.Utils

@RequiresApi(Build.VERSION_CODES.O)
object StandardProtocol : WatchProtocol {
    override fun extractKey(data: String): Int? {
        return runCatching { Utils.toIntArray(data)[0] }.getOrNull()
    }

    override fun unwrapPayload(data: String, key: Int): String {
        return data
    }
}
