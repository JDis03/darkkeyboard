package org.dark.keyboard

import android.graphics.Color

data class KeyboardTheme(
    val name: String,
    val background: Int,
    val keyNormal: Int,
    val keyModifier: Int,
    val keyPressed: Int,
    val keyActive: Int,
    val keyBorder: Int,
    val textNormal: Int,
    val textModifier: Int,
    val textActive: Int
) {
    companion object {
        val DARK = KeyboardTheme(
            name = "Dark (Default)",
            background = Color.parseColor("#1B1B1B"),   // Gboard: fondo casi negro
            keyNormal = Color.parseColor("#2C2C2E"),    // Gboard: letras gris oscuro
            keyModifier = Color.parseColor("#3D3D40"),  // Gboard: modifiers más claros
            keyPressed = Color.parseColor("#1565C0"),
            keyActive = Color.parseColor("#1565C0"),
            keyBorder = Color.TRANSPARENT,              // Gboard: sin borde visible
            textNormal = Color.WHITE,
            textModifier = Color.WHITE,
            textActive = Color.WHITE
        )

        val LIGHT = KeyboardTheme(
            name = "Light",
            background = Color.parseColor("#ECEFF1"),
            keyNormal = Color.parseColor("#FFFFFF"),
            keyModifier = Color.parseColor("#CFD8DC"),
            keyPressed = Color.parseColor("#42A5F5"),
            keyActive = Color.parseColor("#42A5F5"),
            keyBorder = Color.parseColor("#B0BEC5"),
            textNormal = Color.parseColor("#37474F"),
            textModifier = Color.parseColor("#607D8B"),
            textActive = Color.WHITE
        )

        val AMOLED = KeyboardTheme(
            name = "AMOLED",
            background = Color.parseColor("#000000"),
            keyNormal = Color.parseColor("#1A1A1A"),
            keyModifier = Color.parseColor("#0D0D0D"),
            keyPressed = Color.parseColor("#1565C0"),
            keyActive = Color.parseColor("#1565C0"),
            keyBorder = Color.parseColor("#2A2A2A"),
            textNormal = Color.parseColor("#E0E0E0"),
            textModifier = Color.parseColor("#757575"),
            textActive = Color.WHITE
        )

        val NAVY = KeyboardTheme(
            name = "Navy",
            background = Color.parseColor("#1A237E"),
            keyNormal = Color.parseColor("#283593"),
            keyModifier = Color.parseColor("#1A237E"),
            keyPressed = Color.parseColor("#3949AB"),
            keyActive = Color.parseColor("#3949AB"),
            keyBorder = Color.parseColor("#3949AB"),
            textNormal = Color.parseColor("#E8EAF6"),
            textModifier = Color.parseColor("#9FA8DA"),
            textActive = Color.WHITE
        )

        val all = listOf(DARK, LIGHT, AMOLED, NAVY)

        fun fromName(name: String): KeyboardTheme =
            all.firstOrNull { it.name == name } ?: DARK

        fun preferenceKey(theme: KeyboardTheme): String = theme.name
    }
}
