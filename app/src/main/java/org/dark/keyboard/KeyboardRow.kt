package org.dark.keyboard

data class KeyboardRow(
    val keys: MutableList<Key> = mutableListOf(),
    var y: Int = 0,
    var defaultKeyWidth: Int = 0,
    var defaultKeyHeight: Int = 0,
    val isExtension: Boolean = false,
    val keyboardMode: Int = -1
)