package com.sitecam.app.feature

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.sitecam.app.MainActivity
import com.sitecam.app.feature.camera.CaptureMode
import com.sitecam.app.feature.camera.components.CameraBottomBar
import org.junit.Assert.*
import org.junit.Test

/** Uses public UiAutomation APIs; does not depend on Espresso's hidden InputManager API. */
class ScreenControlsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun find(root: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.contentDescription?.toString() == label || root.text?.toString() == label) return root
        for (i in 0 until root.childCount) find(root.getChild(i), label)?.let { return it }
        return null
    }
    private fun bounds(label: String): Rect {
        val deadline = SystemClock.uptimeMillis()+10000
        while(SystemClock.uptimeMillis()<deadline) {
            instrumentation.waitForIdleSync()
            find(instrumentation.uiAutomation.rootInActiveWindow,label)?.let { return Rect().also(it::getBoundsInScreen) }
            SystemClock.sleep(100)
        }
        error("Missing $label")
    }
    private fun click(rect: Rect) {
        val t=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val e=MotionEvent.obtain(t,SystemClock.uptimeMillis(),action,rect.exactCenterX(),rect.exactCenterY(),0)
            e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(instrumentation.uiAutomation.injectInputEvent(e,true)); e.recycle()
        }
        instrumentation.waitForIdleSync()
    }
    @Test fun smallCoverProductionControlsRemainCenteredAndClickable() {
        val size=mutableStateOf(96 to 96)
        var clicks=0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                Box(Modifier.requiredSize(size.value.first.dp,size.value.second.dp).semantics { contentDescription="测试操作区" }) {
                    CameraBottomBar(null,isCapturing=false,captureMode=CaptureMode.PHOTO,isRecordingVideo=false,
                        recordingDurationSeconds=0,zoomPresets=listOf(1f),currentZoomRatio=1f,onZoomSelected={},
                        smallCover=true,onModeChange={},onShutterClick={clicks++},onGalleryClick={},onFlipCameraClick={},
                        shutterSoundEnabled=false,modifier=Modifier.align(Alignment.CenterEnd))
                }
            } }
            listOf(96 to 96,120 to 180,180 to 120,327 to 327,320 to 240).forEachIndexed { i,dimensions ->
                scenario.onActivity { size.value=dimensions }; SystemClock.sleep(400)
                val dock=bounds("测试操作区"); val shutter=bounds("拍照快门")
                assertEquals(dock.exactCenterY(),shutter.exactCenterY(),1f)
                assertTrue(shutter.right<=dock.right && shutter.left>=dock.left)
                click(shutter); scenario.onActivity { assertEquals(i+1,clicks) }
            }
        }
    }
    @Test fun landscapeShutterReservesGripSpace() = checkGrip(true)
    @Test fun portraitShutterReservesGripSpace() = checkGrip(false)
    @Test fun compactPortraitKeepsZoomVisible() = checkGrip(false, 196)
    private fun checkGrip(landscape: Boolean, portraitHeight: Int = 230) {
        val width=if(landscape)240 else 400
        val height=if(landscape)400 else portraitHeight
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                Box(Modifier.requiredSize(width.dp,height.dp).semantics { contentDescription="测试操作区" }) {
                    CameraBottomBar(null,isCapturing=false,captureMode=CaptureMode.PHOTO,isRecordingVideo=false,
                        recordingDurationSeconds=0,zoomPresets=listOf(1f,2f,3f,5f),currentZoomRatio=1f,onZoomSelected={},
                        isLandscape=landscape,landscapeBarWidth=width.dp,compactGroup=true,onModeChange={},onShutterClick={},
                        onGalleryClick={},onFlipCameraClick={},shutterSoundEnabled=false)
                }
            } }
            val dock=bounds("测试操作区"); val shutter=bounds("拍照快门"); val density=dock.width().toFloat()/width
            if(landscape) {
                assertTrue("Right hand space",dock.right-shutter.right>=58*density-1)
                assertEquals(dock.exactCenterY(),shutter.exactCenterY(),1f)
                assertTrue("No mode overlap",bounds("拍照").right<=shutter.left)
            } else {
                assertTrue("Bottom hand space",dock.bottom-shutter.bottom>=30*density-1)
                assertEquals(dock.exactCenterX(),shutter.exactCenterX(),1f)
                assertTrue("Modes above shutter",bounds("拍照").bottom<=shutter.top)
                val zoom=bounds("1×")
                assertTrue("Zoom must stay inside the control area",zoom.top>=dock.top)
                assertTrue("Complete zoom label",zoom.height()>=(if (portraitHeight<205)14 else 17)*density-1)
            }
        }
    }
}
