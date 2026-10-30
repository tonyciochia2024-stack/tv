package com.pets.desktoppets.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "pets_settings")

const val DEFAULT_SIZE_DP = 160
const val MIN_SIZE_DP = 80
const val MAX_SIZE_DP = 320
const val DEFAULT_FX = 0.92f
const val DEFAULT_FY = 0.68f

data class PetSettings(
    val selectedId: String? = null,
    val sizeDp: Int = DEFAULT_SIZE_DP,
    /** Position as a fraction (0..1) of the free travel range, so it survives rotation / resolution changes. */
    val fx: Float = DEFAULT_FX,
    val fy: Float = DEFAULT_FY,
    val restoreOnBoot: Boolean = true,
    /** What the user last asked for. NOT proof the service runs - see PetState for the real state. */
    val wantActive: Boolean = false,
)

class PetPrefs(context: Context) {
    private val store = context.applicationContext.store

    private object K {
        val selected = stringPreferencesKey("selected_id")
        val size = intPreferencesKey("size_dp")
        val fx = floatPreferencesKey("pos_fx")
        val fy = floatPreferencesKey("pos_fy")
        val restore = booleanPreferencesKey("restore_on_boot")
        val want = booleanPreferencesKey("want_active")
    }

    val flow: Flow<PetSettings> = store.data.map { p ->
        PetSettings(
            selectedId = p[K.selected],
            sizeDp = (p[K.size] ?: DEFAULT_SIZE_DP).coerceIn(MIN_SIZE_DP, MAX_SIZE_DP),
            fx = (p[K.fx] ?: DEFAULT_FX).coerceIn(0f, 1f),
            fy = (p[K.fy] ?: DEFAULT_FY).coerceIn(0f, 1f),
            restoreOnBoot = p[K.restore] ?: true,
            wantActive = p[K.want] ?: false,
        )
    }

    suspend fun current(): PetSettings = flow.first()
    suspend fun setSelected(id: String) = store.edit { it[K.selected] = id }
    suspend fun setSize(dp: Int) = store.edit { it[K.size] = dp.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP) }
    suspend fun setPosition(fx: Float, fy: Float) = store.edit { it[K.fx] = fx.coerceIn(0f, 1f); it[K.fy] = fy.coerceIn(0f, 1f) }
    suspend fun resetPosition() = setPosition(DEFAULT_FX, DEFAULT_FY)
    suspend fun setRestoreOnBoot(v: Boolean) = store.edit { it[K.restore] = v }
    suspend fun setWantActive(v: Boolean) = store.edit { it[K.want] = v }
}
