package com.example.saddlemate.store

import android.content.Context
import com.example.saddlemate.domain.AudioMode
import com.example.saddlemate.domain.RiderProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ride settings. The active horse, audio mode, and rider profile are remembered across app starts.
 */
@Singleton
class RideSettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("ride_settings", Context.MODE_PRIVATE)

    private val _activeHorseId = MutableStateFlow(prefs.getString(KEY_HORSE, null))
    val activeHorseId: StateFlow<String?> = _activeHorseId.asStateFlow()

    private val _audioMode = MutableStateFlow(
        runCatching { AudioMode.valueOf(prefs.getString(KEY_AUDIO, "") ?: "") }
            .getOrDefault(AudioMode.FULL_COPILOT),
    )
    val audioMode: StateFlow<AudioMode> = _audioMode.asStateFlow()

    private val _riderProfile = MutableStateFlow(loadRiderProfile())
    val riderProfile: StateFlow<RiderProfile> = _riderProfile.asStateFlow()

    private val _selectedRouteId = MutableStateFlow(prefs.getString(KEY_ROUTE, null))
    val selectedRouteId: StateFlow<String?> = _selectedRouteId.asStateFlow()

    private val _emergencyContact = MutableStateFlow(
        Pair(
            prefs.getString(KEY_EMERGENCY_NAME, "") ?: "",
            prefs.getString(KEY_EMERGENCY_PHONE, "") ?: "",
        ),
    )
    val emergencyContact: StateFlow<Pair<String, String>> = _emergencyContact.asStateFlow()

    fun selectHorse(id: String?) {
        prefs.edit().putString(KEY_HORSE, id).apply()
        _activeHorseId.value = id
    }

    fun selectRoute(id: String) {
        prefs.edit().putString(KEY_ROUTE, id).apply()
        _selectedRouteId.value = id
    }

    fun setAudioMode(mode: AudioMode) {
        prefs.edit().putString(KEY_AUDIO, mode.name).apply()
        _audioMode.value = mode
    }

    fun saveEmergencyContact(name: String, phone: String) {
        val n = name.trim()
        val p = phone.trim()
        prefs.edit()
            .putString(KEY_EMERGENCY_NAME, n)
            .putString(KEY_EMERGENCY_PHONE, p)
            .apply()
        _emergencyContact.value = Pair(n, p)
    }

    fun saveRiderProfile(profile: RiderProfile) {
        prefs.edit()
            .putString(KEY_RIDER_NAME, profile.name)
            .putFloat(KEY_RIDER_WEIGHT, profile.weightKg.toFloat())
            .putInt(KEY_RIDER_HEIGHT, profile.heightCm)
            .putInt(KEY_RIDER_AGE, profile.ageYears)
            .apply()
        _riderProfile.value = profile
    }

    private val _supabaseConfig = MutableStateFlow(
        Pair(
            prefs.getString(KEY_SUPABASE_URL, "") ?: "",
            prefs.getString(KEY_SUPABASE_KEY, "") ?: "",
        ),
    )
    val supabaseConfig: StateFlow<Pair<String, String>> = _supabaseConfig.asStateFlow()

    fun saveSupabaseConfig(url: String, anonKey: String) {
        val u = url.trim()
        val k = anonKey.trim()
        prefs.edit()
            .putString(KEY_SUPABASE_URL, u)
            .putString(KEY_SUPABASE_KEY, k)
            .apply()
        _supabaseConfig.value = Pair(u, k)
    }

    private fun loadRiderProfile(): RiderProfile = RiderProfile(
        name = prefs.getString(KEY_RIDER_NAME, "") ?: "",
        weightKg = prefs.getFloat(KEY_RIDER_WEIGHT, 70f).toDouble(),
        heightCm = prefs.getInt(KEY_RIDER_HEIGHT, 175),
        ageYears = prefs.getInt(KEY_RIDER_AGE, 35),
    )

    private companion object {
        const val KEY_HORSE = "active_horse"
        const val KEY_ROUTE = "selected_route"
        const val KEY_AUDIO = "audio_mode"
        const val KEY_RIDER_NAME = "rider_name"
        const val KEY_RIDER_WEIGHT = "rider_weight_kg"
        const val KEY_RIDER_HEIGHT = "rider_height_cm"
        const val KEY_RIDER_AGE = "rider_age_years"
        const val KEY_EMERGENCY_NAME = "emergency_name"
        const val KEY_EMERGENCY_PHONE = "emergency_phone"
        const val KEY_SUPABASE_URL = "supabase_url"
        const val KEY_SUPABASE_KEY = "supabase_anon_key"
    }
}
