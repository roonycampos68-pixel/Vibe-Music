package com.vibemusic.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore("vibe-preferences")
const val DEFAULT_CATALOG_URL = "https://raw.githubusercontent.com/roonycampos68-pixel/Vibe-Music/main/catalog/vibe-originals.json"

data class Settings(val wifiOnly: Boolean = true, val offlineOnly: Boolean = false, val catalogUrl: String = DEFAULT_CATALOG_URL, val quality: String = "Original", val recentSearches: List<String> = emptyList())
class SettingsStore(private val context: Context) {
    private val wifi = booleanPreferencesKey("wifi_only")
    private val offline = booleanPreferencesKey("offline_only")
    private val catalog = stringPreferencesKey("catalog_url")
    private val quality = stringPreferencesKey("quality")
    private val searches = stringPreferencesKey("searches")
    val settings = context.preferences.data.map { Settings(it[wifi] ?: true, it[offline] ?: false, it[catalog] ?: DEFAULT_CATALOG_URL, it[quality] ?: "Original", it[searches]?.split('\n')?.filter(String::isNotBlank) ?: emptyList()) }
    suspend fun wifiOnly(value: Boolean) { context.preferences.edit { it[wifi] = value } }
    suspend fun offlineOnly(value: Boolean) { context.preferences.edit { it[offline] = value } }
    suspend fun catalogUrl(value: String) { context.preferences.edit { it[catalog] = value.trim() } }
    suspend fun quality(value: String) { context.preferences.edit { it[quality] = value } }
    suspend fun rememberSearch(value: String) {
        if (value.isBlank()) return
        context.preferences.edit { p -> p[searches] = (listOf(value.trim().replace('\n', ' ')) + p[searches].orEmpty().split('\n')).filter(String::isNotBlank).distinct().take(8).joinToString("\n") }
    }
}
