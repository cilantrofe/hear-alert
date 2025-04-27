package com.example.hearalert

import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AudioHelperUnitTest {

    private lateinit var audioHelper: AudioHelper

    @Before
    fun setUp() {
        audioHelper = AudioHelper()
    }

    @Test
    fun extractMFCC_invalidPath_returnsNull() {
        val features = audioHelper.extractMFCC("non_existing_file.wav")
        assertNull(features)
    }

    @Test
    fun extractMFCC_silentAudio_returnsNull() {
        val features = audioHelper.extractMFCC("src/test/resources/silent_audio.wav")
        assertNull(features)
    }
}
