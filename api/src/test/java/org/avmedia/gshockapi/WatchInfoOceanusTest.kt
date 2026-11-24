package org.avmedia.gshockapi

import org.avmedia.gshockapi.protocols.OceanusProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

/** GENERIC writes the 0x22 scratchpad, which makes the CASIO WATCHES app forget an Oceanus watch (upstream GShockAPI 38708a0). */
class WatchInfoOceanusTest {
    @Test fun ocwS400ResolvesToOcwS400WithoutAScratchpad() {
        val info = WatchInfo.infoFor("CASIO OCW-S400")
        assertEquals(WatchInfo.WatchModel.OCW_S400, info.model)
        assertFalse(info.hasScratchpad)
        assertSame(OceanusProtocol, info.protocol)
    }
}
