package com.pets.desktoppets.ui

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pets.desktoppets.data.CharacterInfo
import com.pets.desktoppets.data.CharacterRepository
import com.pets.desktoppets.data.PetPrefs
import com.pets.desktoppets.data.PetSettings
import com.pets.desktoppets.overlay.PetOverlayService
import com.pets.desktoppets.overlay.PetState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class PetStatus { Active, Starting, Hidden, StoppedUnexpectedly }

data class UiState(
    val characters: List<CharacterInfo> = emptyList(),
    val settings: PetSettings = PetSettings(),
    val selected: CharacterInfo? = null,
    val status: PetStatus = PetStatus.Hidden,
    val canDrawOverlays: Boolean = false,
    val ignoringBatteryOpt: Boolean = false,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
class PetsViewModel(app: Application) : AndroidViewModel(app) {
    val repo = CharacterRepository(app)
    private val prefs = PetPrefs(app)
    private val permission = MutableStateFlow(PetOverlayService.canDrawOverlays(app))
    private val battery = MutableStateFlow(isIgnoringBattery())

    val state: StateFlow<UiState> = combine(
        listOf<Flow<*>>(prefs.flow, PetState.serviceRunning, PetState.overlayAttached, PetState.error, permission, battery),
    ) { v ->
        val settings = v[0] as PetSettings
        val running = v[1] as Boolean
        val attached = v[2] as Boolean
        val status = when {
            attached -> PetStatus.Active
            running -> PetStatus.Starting
            settings.wantActive -> PetStatus.StoppedUnexpectedly   // user wants it, but nothing is running
            else -> PetStatus.Hidden
        }
        UiState(repo.characters, settings, repo.resolve(settings.selectedId), status, v[4] as Boolean, v[5] as Boolean, v[3] as String?)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState(characters = repo.characters))

    /** Call from ON_RESUME: permissions can change while the user is in system settings. */
    fun refreshSystemState() {
        permission.value = PetOverlayService.canDrawOverlays(getApplication())
        battery.value = isIgnoringBattery()
    }

    private fun isIgnoringBattery(): Boolean {
        val ctx = getApplication<Application>()
        return (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    }

    // Sliders fire dozens of events per second: persist only the settled value (the overlay follows the store live).
    private val sizeInput = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    private val opacityInput = MutableSharedFlow<Int>(extraBufferCapacity = 64)

    init {
        viewModelScope.launch { sizeInput.debounce(150).collect { prefs.setSize(it) } }
        viewModelScope.launch { opacityInput.debounce(150).collect { prefs.setOpacity(it) } }
    }

    fun showPet() = viewModelScope.launch {
        val ctx = getApplication<Application>()
        if (!PetOverlayService.canDrawOverlays(ctx)) return@launch
        val cur = prefs.current()
        if (cur.shuffle && repo.characters.size > 1) {
            repo.characters.filter { it.id != cur.selectedId }.random().let { prefs.setSelected(it.id) }   // "Surprise me"
        } else if (cur.selectedId == null) repo.characters.firstOrNull()?.let { prefs.setSelected(it.id) }
        prefs.setWantActive(true)
        runCatching { PetOverlayService.start(ctx) }
    }

    fun hidePet() = viewModelScope.launch {
        prefs.setWantActive(false)           // intentional stop: boot/restart logic must not revive it
        PetOverlayService.stop(getApplication())
    }

    fun select(c: CharacterInfo) = viewModelScope.launch { prefs.setSelected(c.id) }   // overlay follows live
    fun setSize(dp: Int) { sizeInput.tryEmit(dp) }
    fun setOpacity(percent: Int) { opacityInput.tryEmit(percent) }
    fun setShuffle(v: Boolean) = viewModelScope.launch { prefs.setShuffle(v) }
    fun toggleFavorite(c: CharacterInfo) = viewModelScope.launch { prefs.toggleFavorite(c.id) }
    fun finishOnboarding() = viewModelScope.launch { prefs.setOnboardingDone() }
    fun resetPosition() = viewModelScope.launch { prefs.resetPosition() }
    fun setRestoreOnBoot(v: Boolean) = viewModelScope.launch { prefs.setRestoreOnBoot(v) }
}
