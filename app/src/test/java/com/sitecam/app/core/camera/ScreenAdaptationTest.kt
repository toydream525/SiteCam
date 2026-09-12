package com.sitecam.app.core.camera

import com.sitecam.app.core.layout.cameraGeometry
import com.sitecam.app.core.layout.screenProfile
import org.junit.Assert.*
import org.junit.Test

class ScreenAdaptationTest {
    @Test fun deviceSchemesDoNotDependOnWindowRotation() {
        assertFalse(screenProfile("Pixel 8", 412f, 915f, false).large)
        assertFalse(screenProfile("Pixel 8", 915f, 412f, false).large)
        assertFalse(screenProfile("Fold", 360f, 840f, false).large)
        assertTrue(screenProfile("Fold", 700f, 800f, true).large)
        assertTrue(screenProfile("Pura X", 450f, 800f, false).large)
        assertTrue(screenProfile("wide phone", 480f, 800f, false).large)
        assertTrue(screenProfile("tablet", 800f, 1280f, false).large)
        assertFalse(screenProfile("SM-F741", 412f, 915f, true).large)
        assertTrue(screenProfile("SM-F741", 350f, 320f, false).smallCover)
        assertTrue(screenProfile("razr", 400f, 400f, false).smallCover)
    }

    @Test fun geometryKeepsPreviewAndControlsInsideEveryWindow() {
        for ((w,h) in listOf(360f to 800f, 800f to 360f, 600f to 600f, 840f to 700f, 1280f to 800f)) {
            val g = cameraGeometry(w,h,3f)
            assertEquals(w>=h,g.side)
            assertTrue(g.previewX>=0 && g.previewY>=0)
            assertTrue(g.previewX+g.previewWidth<=w+.01f)
            assertTrue(g.previewY+g.previewHeight<=h+.01f)
            assertTrue(g.controlsX+g.controlsWidth<=w+.01f)
            assertTrue(g.controlsY+g.controlsHeight<=h+.01f)
            if(g.side) assertTrue(g.previewX+g.previewWidth<=g.controlsX+.01f)
            else assertTrue(g.previewY+g.previewHeight<=g.controlsY+.01f)
        }
    }

    @Test fun coverAndHoverAvoidObstruction() {
        for ((w,h) in listOf(96f to 96f,120f to 180f,180f to 120f,327f to 327f)) {
            val g=cameraGeometry(w,h,3f,true)
            assertTrue(g.side); assertEquals(h,g.controlsHeight)
            assertEquals(w-56,g.controlsX)
            assertTrue(g.previewX+g.previewWidth<=g.controlsX+.01f)
        }
        for ((w,h) in listOf(400f to 900f,900f to 700f)) {
            val g=cameraGeometry(w,h,3f,creaseTop=330f,creaseBottom=350f)
            assertTrue(g.previewY+g.previewHeight<330f)
            assertTrue(g.controlsY>350f)
        }
    }
}
