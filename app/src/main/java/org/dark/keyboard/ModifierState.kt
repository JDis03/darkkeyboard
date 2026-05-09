package org.dark.keyboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ModifierState {
    private val _shift = MutableStateFlow(false)
    private val _ctrl = MutableStateFlow(false)
    private val _alt = MutableStateFlow(false)
    private val _fn = MutableStateFlow(false)

    val shift: StateFlow<Boolean> = _shift.asStateFlow()
    val ctrl: StateFlow<Boolean> = _ctrl.asStateFlow()
    val alt: StateFlow<Boolean> = _alt.asStateFlow()
    val fn: StateFlow<Boolean> = _fn.asStateFlow()

    fun setShift(v: Boolean) { _shift.value = v }
    fun setCtrl(v: Boolean) { _ctrl.value = v }
    fun setAlt(v: Boolean) { _alt.value = v }
    fun setFn(v: Boolean) { _fn.value = v }

    fun toggleShift() { _shift.value = !_shift.value }
    fun toggleCtrl() { _ctrl.value = !_ctrl.value }
    fun toggleAlt() { _alt.value = !_alt.value }
    fun toggleFn() { _fn.value = !_fn.value }

    fun clearAll() {
        _shift.value = false
        _ctrl.value = false
        _alt.value = false
        _fn.value = false
    }

    fun isShiftActive() = _shift.value
    fun isCtrlActive() = _ctrl.value
    fun isAltActive() = _alt.value
    fun isFnActive() = _fn.value
}
