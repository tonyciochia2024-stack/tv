package com.pets.desktoppets.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Real (not persisted) state of the overlay, written only by [PetOverlayService]. Because the service and
 * the UI share a process, and a killed process resets these to false, the values cannot go stale.
 */
object PetState {
    private val _serviceRunning = MutableStateFlow(false)
    private val _overlayAttached = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning
    val overlayAttached: StateFlow<Boolean> = _overlayAttached
    val error: StateFlow<String?> = _error

    private val _appInForeground = MutableStateFlow(false)
    /** True while the Pets UI is on screen: the pet then lets touches through so it never covers app controls. */
    val appInForeground: StateFlow<Boolean> = _appInForeground
    fun setAppInForeground(v: Boolean) { _appInForeground.value = v }

    internal fun setServiceRunning(v: Boolean) { _serviceRunning.value = v }
    internal fun setOverlayAttached(v: Boolean) { _overlayAttached.value = v }
    internal fun setError(v: String?) { _error.value = v }
}
