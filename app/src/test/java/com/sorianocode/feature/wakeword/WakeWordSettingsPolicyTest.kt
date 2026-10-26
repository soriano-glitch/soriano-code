package com.sorianocode.feature.wakeword

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordSettingsPolicyTest {
    @Test
    fun `cannot enable wake word until SorianoCode is the active assistant`() {
        assertFalse(WakeWordSettingsPolicy.canEnable(microphonePermission = true, assistantActive = false))
    }

    @Test
    fun `can enable wake word when microphone and assistant prerequisites are ready`() {
        assertTrue(WakeWordSettingsPolicy.canEnable(microphonePermission = true, assistantActive = true))
    }
}
