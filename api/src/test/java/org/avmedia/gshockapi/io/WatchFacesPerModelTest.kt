package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.WatchInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faces per model, from Casio's user manuals: module 3516 (DW-H5600) has faces 1 to 3, module 3586 (GBX-H5600)
 * faces 1 to 4 (face 4 is the tide graph). Face 2 offers the 13 items both manuals list.
 */
class WatchFacesPerModelTest {
    private fun frame(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }
    private val before = frame(0x51, 2, 0, 10, 12, 19, 0x77)

    @Test fun theDwH5600HasThreeFacesAndTheGbxH5600Four() {
        assertEquals(3, WatchInfo.faceCountFor("CASIO DW-H5600"))
        assertEquals(4, WatchInfo.faceCountFor("CASIO GBX-H5600"))
        assertEquals(4, WatchInfo.faceCountFor("GBX-H5600"))
        assertEquals(0, WatchInfo.faceCountFor("CASIO OCW-S7000"))
        assertEquals(0, WatchInfo.faceCountFor("CASIO GW-B5600"))
    }

    @Test fun aFaceTheModelDoesNotHaveIsNeverWritten() {
        assertEquals(1..3, WatchFaceCodec.faces(3))
        assertEquals(1..4, WatchFaceCodec.faces(4))
        assertTrue(WatchFaceCodec.faces(0).isEmpty())
        assertNull(WatchFaceCodec.withFace(before, 4, faceCount = 3))
        assertNotNull(WatchFaceCodec.withFace(before, 3, faceCount = 3))
        assertNotNull(WatchFaceCodec.withFace(before, 4, faceCount = 4))
        assertNull(WatchFaceCodec.withFace(before, 1, faceCount = 0))
    }

    @Test fun faceFourIsTheTideGraph() {
        assertEquals("Tide", WatchFaceCodec.faceName(4))
    }

    @Test fun theCustomFaceOffersTheManualsThirteenItems() {
        // steps, cardio load status, monthly run and walk distance, heart rate, highest/lowest heart rate,
        // sleep score and level, sleep time, sleep evaluation, calories, active time, sunrise/sunset, moon age
        assertEquals(listOf(0, 2, 3, 4, 10, 11, 12, 13, 14, 16, 17, 18, 19), WatchFaceCodec.OFFERED_ITEMS)
        assertNull(WatchFaceCodec.withTopRight(before, 1)) // step goal graph: decoded, never offered
        assertEquals(20, WatchFaceCodec.ITEM_NAMES.size)
    }

    @Test fun theFactoryOwnPointCountsAsNotSet() {
        assertTrue(TideUserPointCodec.isFactory(TideUserPointCodec.Point(9, 0, 35.60, 139.80, 9f, false)))
        assertFalse(TideUserPointCodec.isFactory(TideUserPointCodec.Point(4, 20, 51.23, 2.93, 1f, false)))
        assertFalse(TideUserPointCodec.isFactory(TideUserPointCodec.Point(9, 0, 35.60, 139.80, 9f, true).copy(latitude = 35.61)))
    }
}
