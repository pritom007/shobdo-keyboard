package com.shohojakkhor.keyboard.ime

import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class BackspaceHandlerTest {
    @Test
    fun `backspace replaces selected text with an empty string`() {
        val calls = mutableListOf<String>()
        val inputConnection = fakeInputConnection(
            selectedText = "selected sentence",
            calls = calls,
        )

        assertTrue(BackspaceHandler.deleteSelection(inputConnection) == true)
        assertEquals(listOf("getSelectedText", "commitText:"), calls)
    }

    @Test
    fun `backspace deletes one character before cursor when selection is empty`() {
        val calls = mutableListOf<String>()
        val inputConnection = fakeInputConnection(selectedText = null, calls = calls)

        assertEquals(null, BackspaceHandler.deleteSelection(inputConnection))
        assertTrue(BackspaceHandler.deleteBeforeCursor(inputConnection))
        assertEquals(listOf("getSelectedText", "deleteSurroundingText:1:0"), calls)
    }

    private fun fakeInputConnection(
        selectedText: CharSequence?,
        calls: MutableList<String>,
    ): InputConnection = Proxy.newProxyInstance(
        InputConnection::class.java.classLoader,
        arrayOf(InputConnection::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getSelectedText" -> {
                calls += "getSelectedText"
                selectedText
            }
            "commitText" -> {
                calls += "commitText:${args?.get(0)}"
                true
            }
            "deleteSurroundingText" -> {
                calls += "deleteSurroundingText:${args?.get(0)}:${args?.get(1)}"
                true
            }
            "toString" -> "FakeInputConnection"
            "hashCode" -> 0
            "equals" -> false
            else -> when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                else -> null
            }
        }
    } as InputConnection
}
