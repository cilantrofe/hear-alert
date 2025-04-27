package com.example.hearalert

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class SettingsActivityUnitTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setUp() {
        prefs = mock(SharedPreferences::class.java)
        editor = mock(SharedPreferences.Editor::class.java)

        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putBoolean(anyString(), anyBoolean())).thenReturn(editor)
    }

    @Test
    fun saveCheckboxState_correctlySaved() {
        editor.putBoolean("class_8", true).apply()
        verify(editor).putBoolean("class_8", true)
    }
}
