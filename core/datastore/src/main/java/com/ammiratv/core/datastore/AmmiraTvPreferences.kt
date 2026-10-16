package com.ammiratv.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ammiratv_prefs")

/**
 * Centralized preference store using Jetpack DataStore.
 * Stores user settings: theme, active profile, PIN, buffer config, etc.
 */
@Singleton
class AmmiraTvPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // ── Keys ─────────────────────────────────────────────────────────────
    private object Keys {
        val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")
        val ACTIVE_PLAYLIST_ID = longPreferencesKey("active_playlist_id")
        val PARENTAL_PIN = stringPreferencesKey("parental_pin")
        val IS_PARENTAL_ENABLED = booleanPreferencesKey("is_parental_enabled")
        val BUFFER_SIZE_MS = intPreferencesKey("buffer_size_ms")
        val PREFERRED_AUDIO_LANGUAGE = stringPreferencesKey("preferred_audio_lang")
        val PREFERRED_SUBTITLE_LANGUAGE = stringPreferencesKey("preferred_subtitle_lang")
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val CINEMA_MODE = booleanPreferencesKey("cinema_mode")
        val LAST_CHANNEL_ID = longPreferencesKey("last_channel_id")
        val ADAPTIVE_BUFFER = booleanPreferencesKey("adaptive_buffer")
        val KEYWORDS = stringSetPreferencesKey("keywords")
        val MERGE_DUPLICATES = booleanPreferencesKey("merge_duplicates")
        val OFFICIAL_FALLBACK = booleanPreferencesKey("official_fallback")
        val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        val MULTIVIEW_LAYOUTS = stringSetPreferencesKey("multiview_layouts")
        val VOLUME_BOOST = booleanPreferencesKey("volume_boost")
        val NUMBERING_ENABLED = booleanPreferencesKey("numbering_enabled")
        val UNOFFICIAL_NUMBERS = booleanPreferencesKey("unofficial_numbers")
        val SORT_BY_NUMBER = booleanPreferencesKey("sort_by_number")
        val NUMBER_ENTRY_TIMEOUT_MS = intPreferencesKey("number_entry_timeout_ms")
        val NUMBERING_REMOTE_URL = stringPreferencesKey("numbering_remote_url")
        val NUMBERING_STAMP = stringPreferencesKey("numbering_stamp")
        val FONT_SCALE =androidx.datastore.preferences.core.floatPreferencesKey("font_scale")
    }

    // Data usage per calendar day, keys "data_<yyyyMMdd>"
    suspend fun addDataUsage(day: String, bytes: Long) {
        val key = longPreferencesKey("data_$day")
        context.dataStore.edit { it[key] = (it[key] ?: 0L) + bytes }
    }

    /** Bytes used on [day] (yyyyMMdd). */
    fun dataUsageOfDay(day: String): Flow<Long> {
        val key = longPreferencesKey("data_$day")
        return context.dataStore.data.map { it[key] ?: 0L }
    }

    /** Bytes used in the month [yearMonth] (yyyyMM): sum of every daily counter in that month. */
    fun dataUsageOfMonth(yearMonth: String): Flow<Long> = context.dataStore.data.map { prefs ->
        prefs.asMap().entries
            .filter { it.key.name.startsWith("data_$yearMonth") }
            .sumOf { (it.value as? Long) ?: 0L }
    }

    // ── TV-style channel numbering ────────────────────────────────────────
    val numberingEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.NUMBERING_ENABLED] ?: true }
    suspend fun setNumberingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NUMBERING_ENABLED] = enabled }
    }

    /** Numbers from 1000 upwards for Italian channels missing from the catalogue (off by default). */
    val unofficialNumbers: Flow<Boolean> = context.dataStore.data.map { it[Keys.UNOFFICIAL_NUMBERS] ?: false }
    suspend fun setUnofficialNumbers(enabled: Boolean) {
        context.dataStore.edit { it[Keys.UNOFFICIAL_NUMBERS] = enabled }
    }

    val sortByNumber: Flow<Boolean> = context.dataStore.data.map { it[Keys.SORT_BY_NUMBER] ?: false }
    suspend fun setSortByNumber(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SORT_BY_NUMBER] = enabled }
    }

    /** How long the app waits after the last digit before tuning (OK confirms at once). */
    val numberEntryTimeoutMs: Flow<Int> = context.dataStore.data.map { it[Keys.NUMBER_ENTRY_TIMEOUT_MS] ?: 1800 }
    suspend fun setNumberEntryTimeoutMs(ms: Int) {
        context.dataStore.edit { it[Keys.NUMBER_ENTRY_TIMEOUT_MS] = ms.coerceIn(500, 5000) }
    }

    /** Where the canonical channel database can be refreshed from; empty = remote update off. */
    val numberingRemoteUrl: Flow<String> = context.dataStore.data.map { it[Keys.NUMBERING_REMOTE_URL] ?: "" }
    suspend fun setNumberingRemoteUrl(url: String) {
        context.dataStore.edit { it[Keys.NUMBERING_REMOTE_URL] = url.trim() }
    }

    /** What the stored numbers were computed from, so they are only recomputed when it changes. */
    val numberingStamp: Flow<String> = context.dataStore.data.map { it[Keys.NUMBERING_STAMP] ?: "" }
    suspend fun setNumberingStamp(stamp: String) {
        context.dataStore.edit { it[Keys.NUMBERING_STAMP] = stamp }
    }

    // Night / dialogue mode: raises overall loudness by 6 dB (off by default)
    val volumeBoost: Flow<Boolean> = context.dataStore.data.map { it[Keys.VOLUME_BOOST] ?: false }
    suspend fun setVolumeBoost(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VOLUME_BOOST] = enabled }
    }

    // Text size multiplier for accessibility (1.0 = system size, 3 m viewing distance may want 1.25+)
    val fontScale: Flow<Float> = context.dataStore.data.map { it[Keys.FONT_SCALE] ?: 1f }
    suspend fun setFontScale(scale: Float) {
        context.dataStore.edit { it[Keys.FONT_SCALE] = scale.coerceIn(0.85f, 1.6f) }
    }

    // ── Active Profile ───────────────────────────────────────────────────
    val activeProfileId: Flow<Long> = context.dataStore.data.map { it[Keys.ACTIVE_PROFILE_ID] ?: 1L }
    suspend fun setActiveProfileId(id: Long) {
        context.dataStore.edit { it[Keys.ACTIVE_PROFILE_ID] = id }
    }

    // ── Daily watch time (per profile, per calendar day) ───────────────
    // Keys are built dynamically: "watched_<profileId>_<yyyyMMdd>".
    fun watchedMsToday(profileId: Long, day: String): Flow<Long> {
        val key = longPreferencesKey("watched_${profileId}_$day")
        return context.dataStore.data.map { it[key] ?: 0L }
    }

    suspend fun addWatchedMs(profileId: Long, day: String, deltaMs: Long) {
        val key = longPreferencesKey("watched_${profileId}_$day")
        context.dataStore.edit { it[key] = (it[key] ?: 0L) + deltaMs }
    }

    // ── Active Playlist ──────────────────────────────────────────────────
    val activePlaylistId: Flow<Long> = context.dataStore.data.map { it[Keys.ACTIVE_PLAYLIST_ID] ?: 0L }
    suspend fun setActivePlaylistId(id: Long) {
        context.dataStore.edit { it[Keys.ACTIVE_PLAYLIST_ID] = id }
    }

    // ── Parental Controls ────────────────────────────────────────────────
    val isParentalEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.IS_PARENTAL_ENABLED] ?: false }
    val parentalPin: Flow<String> = context.dataStore.data.map { it[Keys.PARENTAL_PIN] ?: "" }
    suspend fun setParentalPin(pin: String) {
        context.dataStore.edit {
            it[Keys.PARENTAL_PIN] = pin
            it[Keys.IS_PARENTAL_ENABLED] = pin.isNotEmpty()
        }
    }

    // ── Buffer ───────────────────────────────────────────────────────────
    val bufferSizeMs: Flow<Int> = context.dataStore.data.map { it[Keys.BUFFER_SIZE_MS] ?: 2000 }
    suspend fun setBufferSizeMs(ms: Int) {
        context.dataStore.edit { it[Keys.BUFFER_SIZE_MS] = ms }
    }

    // ── Language Preferences ─────────────────────────────────────────────
    val preferredAudioLanguage: Flow<String> = context.dataStore.data.map { it[Keys.PREFERRED_AUDIO_LANGUAGE] ?: "" }
    val preferredSubtitleLanguage: Flow<String> = context.dataStore.data.map { it[Keys.PREFERRED_SUBTITLE_LANGUAGE] ?: "" }

    suspend fun setPreferredAudioLanguage(code: String) {
        context.dataStore.edit { it[Keys.PREFERRED_AUDIO_LANGUAGE] = code }
    }

    suspend fun setPreferredSubtitleLanguage(code: String) {
        context.dataStore.edit { it[Keys.PREFERRED_SUBTITLE_LANGUAGE] = code }
    }

    // ── Adaptive ("learning") buffer: on by default, can be switched off ──
    val adaptiveBuffer: Flow<Boolean> = context.dataStore.data.map { it[Keys.ADAPTIVE_BUFFER] ?: true }
    suspend fun setAdaptiveBuffer(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ADAPTIVE_BUFFER] = enabled }
    }

    // ── Merge duplicate channels in lists (on by default) ──
    /** Keep playing (audio + notification) when the app is left or the screen turns off. On by default. */
    val backgroundPlayback: Flow<Boolean> = context.dataStore.data.map { it[Keys.BACKGROUND_PLAYBACK] ?: true }
    suspend fun setBackgroundPlayback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKGROUND_PLAYBACK] = enabled }
    }

    /** RaiPlay as a fallback when a playlist stream fails (on by default). */
    val officialFallback: Flow<Boolean> = context.dataStore.data.map { it[Keys.OFFICIAL_FALLBACK] ?: true }
    suspend fun setOfficialFallback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OFFICIAL_FALLBACK] = enabled }
    }

    val mergeDuplicates: Flow<Boolean> = context.dataStore.data.map { it[Keys.MERGE_DUPLICATES] ?: true }
    suspend fun setMergeDuplicates(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MERGE_DUPLICATES] = enabled }
    }

    // ── Keyword rules ("avvisami quando nell'EPG compare…") ──────────────
    val keywords: Flow<Set<String>> = context.dataStore.data.map { it[Keys.KEYWORDS] ?: emptySet() }

    suspend fun addKeyword(keyword: String) {
        val clean = keyword.trim()
        if (clean.isEmpty()) return
        context.dataStore.edit { it[Keys.KEYWORDS] = (it[Keys.KEYWORDS] ?: emptySet()) + clean }
    }

    suspend fun removeKeyword(keyword: String) {
        context.dataStore.edit { it[Keys.KEYWORDS] = (it[Keys.KEYWORDS] ?: emptySet()) - keyword }
    }

    // ── Multi-view saved layouts (encoded strings, see MultiViewLayout) ──
    val multiViewLayouts: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.MULTIVIEW_LAYOUTS] ?: emptySet() }

    suspend fun saveMultiViewLayout(encoded: String, replacePrefix: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.MULTIVIEW_LAYOUTS] ?: emptySet()
            // Saving under an existing name replaces it
            prefs[Keys.MULTIVIEW_LAYOUTS] = current.filterNot { it.startsWith(replacePrefix) }.toSet() + encoded
        }
    }

    suspend fun deleteMultiViewLayout(encoded: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MULTIVIEW_LAYOUTS] = (prefs[Keys.MULTIVIEW_LAYOUTS] ?: emptySet()) - encoded
        }
    }

    // ── First Launch ─────────────────────────────────────────────────────
    // Backup / restore of portable settings. Deliberately excludes the parental PIN, the active
    // profile/playlist ids and watch-time counters: those are device specific and must not
    // travel in a file people may share.

    /** Serialises the user-facing settings to a small JSON document. */
    suspend fun exportSettings(): String {
        val p = context.dataStore.data.first()
        val json = org.json.JSONObject()
        json.put("version", 1)
        p[Keys.BUFFER_SIZE_MS]?.let { json.put("bufferMs", it) }
        p[Keys.PREFERRED_AUDIO_LANGUAGE]?.let { json.put("audioLang", it) }
        p[Keys.PREFERRED_SUBTITLE_LANGUAGE]?.let { json.put("subtitleLang", it) }
        p[Keys.CINEMA_MODE]?.let { json.put("cinemaMode", it) }
        p[Keys.ADAPTIVE_BUFFER]?.let { json.put("adaptiveBuffer", it) }
        p[Keys.MERGE_DUPLICATES]?.let { json.put("mergeDuplicates", it) }
        json.put("keywords", org.json.JSONArray((p[Keys.KEYWORDS] ?: emptySet()).toList()))
        json.put("multiviewLayouts", org.json.JSONArray((p[Keys.MULTIVIEW_LAYOUTS] ?: emptySet()).toList()))
        return json.toString(2)
    }

    /** @return true when [text] was a valid backup and has been applied. */
    suspend fun importSettings(text: String): Boolean {
        val json = try {
            org.json.JSONObject(text)
        } catch (_: org.json.JSONException) {
            return false
        }
        if (!json.has("version")) return false

        fun strings(name: String): Set<String> {
            val array = json.optJSONArray(name) ?: return emptySet()
            return (0 until array.length()).map { array.getString(it) }.toSet()
        }

        context.dataStore.edit { p ->
            if (json.has("bufferMs")) p[Keys.BUFFER_SIZE_MS] = json.getInt("bufferMs")
            if (json.has("audioLang")) p[Keys.PREFERRED_AUDIO_LANGUAGE] = json.getString("audioLang")
            if (json.has("subtitleLang")) p[Keys.PREFERRED_SUBTITLE_LANGUAGE] = json.getString("subtitleLang")
            if (json.has("cinemaMode")) p[Keys.CINEMA_MODE] = json.getBoolean("cinemaMode")
            if (json.has("adaptiveBuffer")) p[Keys.ADAPTIVE_BUFFER] = json.getBoolean("adaptiveBuffer")
            if (json.has("mergeDuplicates")) p[Keys.MERGE_DUPLICATES] = json.getBoolean("mergeDuplicates")
            p[Keys.KEYWORDS] = strings("keywords")
            p[Keys.MULTIVIEW_LAYOUTS] = strings("multiviewLayouts")
        }
        return true
    }

    val isFirstLaunch: Flow<Boolean> =context.dataStore.data.map { it[Keys.IS_FIRST_LAUNCH] ?: true }
    suspend fun setFirstLaunchDone() {
        context.dataStore.edit { it[Keys.IS_FIRST_LAUNCH] = false }
    }

    // ── Cinema Mode ──────────────────────────────────────────────────────
    val cinemaMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.CINEMA_MODE] ?: false }
    suspend fun setCinemaMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CINEMA_MODE] = enabled }
    }

    // ── Last Channel ─────────────────────────────────────────────────────
    val lastChannelId: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_CHANNEL_ID] ?: 0L }
    suspend fun setLastChannelId(id: Long) {
        context.dataStore.edit { it[Keys.LAST_CHANNEL_ID] = id }
    }
}
