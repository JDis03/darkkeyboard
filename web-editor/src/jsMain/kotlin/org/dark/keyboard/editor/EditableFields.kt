package org.dark.keyboard.editor

import androidx.compose.runtime.*
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.dom.*

/**
 * Editable text field
 */
@Composable
fun EditableTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = ""
) {
    Div(
        attrs = {
            style {
                property("display", "flex")
                property("flex-direction", "column")
                property("gap", "4px")
                property("margin-bottom", "8px")
            }
        }
    ) {
        Label(
            attrs = {
                style {
                    property("font-size", "12px")
                    property("font-weight", "600")
                    property("color", "#666")
                }
            }
        ) {
            Text(label)
        }
        
        Input(
            type = InputType.Text
        ) {
            value(value)
            onInput { event ->
                onValueChange(event.value)
            }
            attr("placeholder", placeholder)
            style {
                property("padding", "6px 8px")
                property("border", "1px solid #ccc")
                property("border-radius", "4px")
                property("font-size", "14px")
                property("font-family", "inherit")
            }
        }
    }
}

/**
 * Editable number field
 */
@Composable
fun EditableNumberField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int? = null,
    max: Int? = null
) {
    Div(
        attrs = {
            style {
                property("display", "flex")
                property("flex-direction", "column")
                property("gap", "4px")
                property("margin-bottom", "8px")
            }
        }
    ) {
        Label(
            attrs = {
                style {
                    property("font-size", "12px")
                    property("font-weight", "600")
                    property("color", "#666")
                }
            }
        ) {
            Text(label)
        }
        
        Input(
            type = InputType.Number
        ) {
            value(value.toString())
            onInput { event ->
                val target = event.target.asDynamic()
                val newValue = target.valueAsNumber as? Int ?: return@onInput
                
                // Apply constraints
                val bounded = when {
                    min != null && newValue.compareTo(min) < 0 -> min
                    max != null && newValue.compareTo(max) > 0 -> max
                    else -> newValue
                }
                
                onValueChange(bounded)
            }
            if (min != null) attr("min", min.toString())
            if (max != null) attr("max", max.toString())
            style {
                property("padding", "6px 8px")
                property("border", "1px solid #ccc")
                property("border-radius", "4px")
                property("font-size", "14px")
                property("font-family", "inherit")
                property("width", "100%")
            }
        }
    }
}

/**
 * Editable checkbox field
 */
@Composable
fun EditableCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Div(
        attrs = {
            style {
                property("display", "flex")
                property("align-items", "center")
                property("gap", "8px")
                property("margin-bottom", "8px")
            }
        }
    ) {
        Input(InputType.Checkbox) {
            checked(checked)
            onChange { event ->
                val target = event.target.asDynamic()
                onCheckedChange(target.checked as Boolean)
            }
            style {
                property("cursor", "pointer")
            }
        }
        
        Label(
            attrs = {
                style {
                    property("font-size", "14px")
                    property("color", "#333")
                    property("cursor", "pointer")
                }
            }
        ) {
            Text(label)
        }
    }
}
