package com.sitecam.app.core.camera

import org.junit.Assert.assertTrue
import org.junit.Test

class CameraDiagnosticsTest {
    @Test fun lastPhysicalLensRemainsReadableAfterLeavingCamera() {
        CameraDiagnostics.updateSession("logical", "tele", 3.5f, 3.5f, 12f)
        assertTrue(CameraDiagnostics.sessionDiagnostic().contains("Latest active capture result:"))
        CameraDiagnostics.clearSession()
        val report = CameraDiagnostics.sessionDiagnostic()
        assertTrue(report.contains("session inactive"))
        assertTrue(report.contains("Active Physical Camera ID: tele"))
        assertTrue(report.contains("Capture Result Zoom: 3.5"))
    }
}
