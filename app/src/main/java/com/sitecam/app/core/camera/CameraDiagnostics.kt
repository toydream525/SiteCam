package com.sitecam.app.core.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager as SystemCameraManager
import android.os.Build

object CameraDiagnostics {

    fun generateDiagnosticReport(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("=== SiteCam Camera Diagnostics ===")
        sb.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        sb.appendLine("Model: ${Build.MODEL}")
        sb.appendLine("Brand: ${Build.BRAND}")
        sb.appendLine("Device: ${Build.DEVICE}")
        sb.appendLine("Android OS: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("Fingerprint: ${Build.FINGERPRINT}")
        sb.appendLine("Timestamp: ${System.currentTimeMillis()}")
        sb.appendLine()

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? SystemCameraManager
            if (cameraManager != null) {
                val cameraIds = cameraManager.cameraIdList
                sb.appendLine("Total Available Camera IDs: ${cameraIds.size}")
                for (id in cameraIds) {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                        CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                        CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                        CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                        else -> "UNKNOWN"
                    }
                    val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                    val hardwareLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                    val hwLevelStr = when (hardwareLevel) {
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                        else -> "UNKNOWN ($hardwareLevel)"
                    }

                    sb.appendLine("--- Camera ID: $id ---")
                    sb.appendLine("  Facing: $facing")
                    sb.appendLine("  Hardware Level: $hwLevelStr")
                    sb.appendLine("  Flash Available: $flashAvailable")
                    sb.appendLine("  Sensor Orientation: ${sensorOrientation}°")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val zoomRange = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                        sb.appendLine("  Control Zoom Ratio Range: $zoomRange")
                    }

                    val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    if (focalLengths != null) {
                        sb.appendLine("  Focal Lengths: ${focalLengths.joinToString()}")
                    }
                }
            } else {
                sb.appendLine("System CameraManager service is unavailable.")
            }
        } catch (e: Exception) {
            sb.appendLine("Error reading camera characteristics: ${e.message}")
        }

        sb.appendLine("=================================")
        return sb.toString()
    }
}
