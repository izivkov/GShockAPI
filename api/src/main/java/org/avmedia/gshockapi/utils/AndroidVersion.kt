package org.avmedia.gshockapi.utils

import android.os.Build

object AndroidVersion {
    val isOreoOrAbove
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    val isSOrAbove
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isTiramisuOrAbove
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    // API 36 (Baklava) is sometimes represented as a preview or a specific constant depending on
    // SDK version
    // Using literal 36 or constant if available.
    val isBaklavaOrAbove
        get() = Build.VERSION.SDK_INT >= 36
}
