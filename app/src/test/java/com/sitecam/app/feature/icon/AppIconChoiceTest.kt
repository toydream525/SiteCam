package com.sitecam.app.feature.icon

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconChoiceTest {

    @Test
    fun resolverUsesStableOrderForAllChoicesAndFallsBackToD() {
        assertEquals(AppIconChoice.D, AppIconChoiceResolver.resolve(emptySet()))
        for (choice in AppIconChoice.entries) {
            assertEquals(choice, AppIconChoiceResolver.resolve(setOf(choice)))
        }
        assertEquals(
            AppIconChoice.A,
            AppIconChoiceResolver.resolve(AppIconChoice.entries.toSet())
        )
    }
}
