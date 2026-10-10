package com.sitecam.app.core.camera

import com.sitecam.app.core.layout.cameraGeometry
import com.sitecam.app.core.layout.screenProfile
import com.sitecam.app.core.layout.LayoutRect
import com.sitecam.app.core.layout.adaptivePaneArrangement
import com.sitecam.app.core.layout.unobstructedRegions
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
        assertFalse(screenProfile("razr", 400f, 400f, false).smallCover)
        assertEquals(screenProfile("tablet", 360f, 800f, false), screenProfile("phone", 360f, 800f, false))
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
            assertTrue(g.hover)
            assertEquals(0f, g.controlsX, .01f)
            assertEquals(w, g.controlsWidth, .01f)
            assertEquals(4f / 3f, g.previewWidth / g.previewHeight, .001f)
        }
    }

    @Test fun managementPanesUseLocalWidthAndFontCapacity() {
        assertFalse(adaptivePaneArrangement(700f, 900f, 1f).split)
        assertTrue(adaptivePaneArrangement(840f, 900f, 1f).split)
        assertFalse(adaptivePaneArrangement(840f, 900f, 2f).split)
        val enlarged = adaptivePaneArrangement(1100f, 900f, 2f)
        assertTrue(enlarged.split)
        assertTrue(enlarged.primary.width >= 464f)
        assertTrue(enlarged.secondary!!.width >= 536f)
        assertFalse(adaptivePaneArrangement(520f, 900f, 1f).split)
    }

    @Test fun allFoldRectanglesAreExcludedFromEveryPane() {
        val folds = listOf(LayoutRect(480f, 0f, 500f, 900f), LayoutRect(0f, 430f, 1000f, 450f))
        val regions = unobstructedRegions(1000f, 900f, folds)
        assertEquals(4, regions.size)
        val arrangement = adaptivePaneArrangement(1000f, 900f, 1f, folds)
        assertTrue(arrangement.split)
        for (pane in regions + listOfNotNull(arrangement.primary, arrangement.secondary)) {
            assertTrue(pane.left >= 0f && pane.top >= 0f && pane.right <= 1000f && pane.bottom <= 900f)
            folds.forEach { fold -> assertNoIntersection(pane, fold) }
        }
    }

    @Test fun zeroWidthFoldAndOffWindowFoldsAreHandled() {
        val folds = listOf(LayoutRect(440f, 0f, 440f, 800f), LayoutRect(1200f, 0f, 1210f, 800f))
        val panes = adaptivePaneArrangement(920f, 800f, 1f, folds)
        assertTrue(panes.split)
        assertTrue(panes.primary.right < 440f)
        assertTrue(panes.secondary!!.left > 440f)
        // FoldingFeature coordinates already belong to the window: only the local content origin is removed.
        val localFold = LayoutRect(510f, 40f, 530f, 840f).translated(-30f, -40f)
        assertEquals(LayoutRect(480f, 0f, 500f, 800f), localFold)
    }

    @Test fun nearSquareWindowKeepsSideControlsAndFourByThreePreview() {
        val geometry = cameraGeometry(700f, 800f, 3f)
        assertTrue(geometry.side)
        assertEquals(4f / 3f, geometry.previewWidth / geometry.previewHeight, .001f)
        assertTrue(geometry.previewX + geometry.previewWidth <= geometry.controlsX)
    }

    @Test fun cameraUsesAllFoldsWithoutCrossingAnyObstruction() {
        val folds = listOf(LayoutRect(490f, 0f, 510f, 900f), LayoutRect(0f, 420f, 1000f, 440f))
        val geometry = cameraGeometry(1000f, 900f, 3f, folds = folds)
        assertTrue(geometry.hover)
        assertEquals(4f / 3f, geometry.previewWidth / geometry.previewHeight, .001f)
        val preview = LayoutRect(geometry.previewX, geometry.previewY,
            geometry.previewX + geometry.previewWidth, geometry.previewY + geometry.previewHeight)
        val controls = LayoutRect(geometry.controlsX, geometry.controlsY,
            geometry.controlsX + geometry.controlsWidth, geometry.controlsY + geometry.controlsHeight)
        folds.forEach { fold -> assertNoIntersection(preview, fold); assertNoIntersection(controls, fold) }
        assertTrue(preview.bottom < 420f && controls.top > 440f)
    }

    @Test fun ordinaryPhoneLandscapeKeepsGripAndSeparateFourByThreePreview() {
        val geometry = cameraGeometry(800f, 360f, 1f, phoneGripSpace = true)
        assertEquals(4f / 3f, geometry.previewWidth / geometry.previewHeight, .001f)
        assertTrue(geometry.immersivePhoneLandscape)
        assertTrue(geometry.toolbarX + geometry.toolbarWidth <= geometry.previewX)
        assertEquals(geometry.previewX + geometry.previewWidth, geometry.controlsX, .001f)
        assertEquals(360f, geometry.controlsHeight, .001f)
        assertTrue(800f - geometry.controlsX - geometry.controlsWidth >= 48f)
        assertTrue(geometry.controlsWidth in 160f..216f)

        val geometryWithRef = cameraGeometry(800f, 360f, 1f, phoneGripSpace = true, referenceLandscape = true)
        assertTrue(800f - geometryWithRef.controlsX - geometryWithRef.controlsWidth >= 48f)
    }

    @Test fun phoneSizedLandscapeKeepsGripWithoutTallPhoneEligibility() {
        // The device-aspect gate behind phoneGripSpace excludes some ordinary
        // phones; a phone-sized landscape window must still reserve the ~48dp
        // grip instead of falling back to the shared landscape dock.
        for ((w, h) in listOf(800f to 360f, 960f to 480f, 720f to 480f)) {
            val geometry = cameraGeometry(w, h, 1f, referenceLandscape = true)
            assertTrue("$w x $h immersive", geometry.immersivePhoneLandscape)
            assertTrue("$w x $h grip", w - geometry.controlsX - geometry.controlsWidth >= 48f - .001f)
            assertEquals(geometry.previewX + geometry.previewWidth, geometry.controlsX, .01f)
            assertEquals(4f / 3f, geometry.previewWidth / geometry.previewHeight, .001f)
        }
    }

    @Test fun foldableLandscapeKeepsItsOwnStructureWithoutPhoneGrip() {
        // A small-short-side foldable inner display in landscape keeps its
        // confirmed structure: no immersive phone branch and no phone grip.
        for ((w, h) in listOf(1015f to 415f, 900f to 420f)) {
            val geometry = cameraGeometry(w, h, 2.6f, referenceLandscape = false)
            assertTrue("$w x $h side", geometry.side)
            assertFalse("$w x $h no immersive phone layout", geometry.immersivePhoneLandscape)
            assertEquals("$w x $h dock flush right", w, geometry.controlsX + geometry.controlsWidth, .01f)
        }
    }

    @Test fun phoneLandscapeLargeFontKeepsToolsSeparateAndDockAdjacentToPhoto() {
        val geometry = cameraGeometry(680f, 360f, 1f, fontScale = 2f, phoneGripSpace = true)
        assertEquals(360f, geometry.toolbarHeight, .001f)
        assertEquals(56f, geometry.toolbarWidth, .001f)
        assertEquals(geometry.previewX + geometry.previewWidth, geometry.controlsX, .001f)
        assertEquals(0f, geometry.controlsY, .001f)
        assertTrue(680f - geometry.controlsX - geometry.controlsWidth >= 48f - .001f)
    }

    @Test fun tabletLandscapeUsesAdoptedControlsWithoutPhoneGripOrTopShelf() {
        for (fontScale in listOf(1f, 1.5f, 2f)) {
            val geometry = cameraGeometry(1280f, 800f, 2f,
                fontScale = fontScale, referenceLandscape = true)
            assertTrue(geometry.immersivePhoneLandscape)
            assertEquals(56f, geometry.toolbarWidth, .001f)
            assertEquals(800f, geometry.toolbarHeight, .001f)
            assertEquals(0f, geometry.controlsY, .001f)
            assertEquals(geometry.previewX + geometry.previewWidth, geometry.controlsX, .001f)
            assertEquals(1280f, geometry.controlsX + geometry.controlsWidth, .001f)
            assertEquals(4f / 3f, geometry.previewWidth / geometry.previewHeight, .001f)
            assertTrue(geometry.previewY < 32f)
        }
    }

    @Test fun portraitPhoneShrinksPreviewToKeepMeasuredLargeFontDockWhole() {
        for ((width, height, controlsHeight) in listOf(Triple(412f, 844f, 288f), Triple(360f, 780f, 352f))) {
            val geometry = cameraGeometry(width, height, 1f, fontScale = 2f,
                phoneGripSpace = true, minimumPortraitControlsHeight = controlsHeight)
            assertFalse(geometry.side)
            assertTrue(geometry.controlsHeight >= controlsHeight - .01f)
            assertTrue(geometry.previewWidth < width)
            assertEquals(3f / 4f, geometry.previewWidth / geometry.previewHeight, .001f)
            assertTrue(geometry.previewY + geometry.previewHeight <= geometry.controlsY + .01f)
            assertTrue(geometry.controlsY + geometry.controlsHeight <= height + .01f)
        }
    }

    @Test fun portraitPhoneInitialDockWithoutMeasurementReservesStableFloor() {
        val geometry = cameraGeometry(393f, 850f, 3f, fontScale = 1f, phoneGripSpace = true, minimumPortraitControlsHeight = 0f)
        assertFalse(geometry.side)
        assertTrue("controlsHeight was ${geometry.controlsHeight}", geometry.controlsHeight >= 280f)
        assertEquals(3f / 4f, geometry.previewWidth / geometry.previewHeight, .001f)
    }

    private fun assertNoIntersection(pane: LayoutRect, fold: LayoutRect) {
        assertTrue("$pane intersects $fold", pane.right <= fold.left || pane.left >= fold.right ||
            pane.bottom <= fold.top || pane.top >= fold.bottom)
    }
}
