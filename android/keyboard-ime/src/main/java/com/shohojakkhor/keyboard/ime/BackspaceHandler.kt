package com.shohojakkhor.keyboard.ime

import android.view.inputmethod.InputConnection

/** Selection-aware deletion shared by tap and press-and-hold backspace. */
internal object BackspaceHandler {
    /** Returns null when there is no selection, otherwise the delete result. */
    fun deleteSelection(inputConnection: InputConnection): Boolean? {
        val selectedText = inputConnection.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            // commitText replaces the active selection. An empty replacement is
            // the InputConnection equivalent of deleting the selected range.
            return inputConnection.commitText("", 1)
        }
        return null
    }

    fun deleteBeforeCursor(inputConnection: InputConnection): Boolean =
        inputConnection.deleteSurroundingText(1, 0)
}
