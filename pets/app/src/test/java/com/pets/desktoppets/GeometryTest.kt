package com.pets.desktoppets

import com.pets.desktoppets.overlay.Area
import com.pets.desktoppets.overlay.clampToArea
import com.pets.desktoppets.overlay.fractionToXY
import com.pets.desktoppets.overlay.xyToFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {
    private val screen = Area(0, 66, 1080, 2148)   // below status bar, above nav bar
    private val w = 584
    private val h = 440

    @Test fun cornersMapToUsableArea() {
        assertEquals(0 to 66, fractionToXY(screen, w, h, 0f, 0f))
        assertEquals((1080 - w) to (2148 - h), fractionToXY(screen, w, h, 1f, 1f))
    }

    @Test fun roundTripIsStable() {
        val (x, y) = fractionToXY(screen, w, h, 0.37f, 0.61f)
        val (fx, fy) = xyToFraction(screen, w, h, x, y)
        assertEquals(0.37f, fx, 0.002f)
        assertEquals(0.61f, fy, 0.002f)
    }

    @Test fun rotationKeepsPetOnScreen() {
        val landscape = Area(0, 66, 2280, 1000)
        val (x, y) = fractionToXY(landscape, w, h, 0.92f, 0.68f)
        assertTrue(x >= 0 && x + w <= 2280)
        assertTrue(y >= 66 && y + h <= 1000)
    }

    @Test fun petLargerThanScreenDoesNotCrashOrEscape() {
        val tiny = Area(0, 0, 300, 300)
        assertEquals(0 to 0, fractionToXY(tiny, 900, 900, 0.9f, 0.9f))
        assertEquals(0 to 0, clampToArea(tiny, 900, 900, 50, 50))
    }

    @Test fun dragIsClamped() {
        assertEquals(0 to 66, clampToArea(screen, w, h, -500, -500))
        assertEquals((1080 - w) to (2148 - h), clampToArea(screen, w, h, 5000, 5000))
        assertEquals(100 to 200, clampToArea(screen, w, h, 100, 200))
    }

    @Test fun outOfRangeFractionsAreClamped() {
        assertEquals(fractionToXY(screen, w, h, 1f, 0f), fractionToXY(screen, w, h, 7f, -3f))
    }
}
