package com.vibemusic.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore("vibe-preferences")
const val LEGACY_CATALOG_URL = "https://raw.githubusercontent.com/roonycampos68-pixel/Vibe-Music/main/catalog/vibe-originals.json"
const val DEFAULT_CATALOG_URL = ""

data class Settings(val wifiOnly: Boolean = true, val offlineOnly: Boolean = false, val catalogUrl: String = DEFAULT_CATALOG_URL, val quality: String = "Original", val recentSearches: List<String> = emptyList(), val recentYouTube: List<YouTubeTrack> = emptyList())
class SettingsStore(private val context: Context) {
    private val wifi = booleanPreferencesKey("wifi_only")
    private val offline = booleanPreferencesKey("offline_only")
    private val catalog = stringPreferencesKey("catalog_url")
    private val quality = stringPreferencesKey("quality")
    private val searches = stringPreferencesKey("searches")
    private val youtubeRecent = stringPreferencesKey("youtube_recent")
    private fun readRecent(json: String?): List<YouTubeTrack> = runCatching {
        val array = org.json.JSONArray(json ?: "[]")
        (0 until minOf(array.length(), 12)).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id");if (!id.matches(Regex("[a-zA-Z0-9_-]{11}"))) return@mapNotNull null
            YouTubeTrack(id, item.optString("title").take(500), item.optString("artist").take(500), item.optLong("duration").coerceAtLeast(0))
        }
    }.getOrDefault(emptyList())
    val settings = context.preferences.data.map { Settings(it[wifi] ?: true, it[offline] ?: false, it[catalog]?.takeUnless { url -> url == LEGACY_CATALOG_URL } ?: DEFAULT_CATALOG_URL, it[quality] ?: "Original", it[searches]?.split('\n')?.filter(String::isNotBlank) ?: emptyList(), readRecent(it[youtubeRecent])) }
    suspend fun rememberYouTube(track: YouTubeTrack) {
        context.preferences.edit { prefs ->
            val array = org.json.JSONArray()
            (listOf(track) + readRecent(prefs[youtubeRecent])).distinctBy { it.id }.take(12).forEach {
                array.put(org.json.JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist).put("duration", it.durationMs))
            }
            prefs[youtubeRecent] = array.toString()
        }
    }
    suspend fun wifiOnly(value: Boolean) { context.preferences.edit { it[wifi] = value } }
    suspend fun offlineOnly(value: Boolean) { context.preferences.edit { it[offline] = value } }
    suspend fun catalogUrl(value: String) { context.preferences.edit { it[catalog] = value.trim() } }
    suspend fun quality(value: String) { context.preferences.edit { it[quality] = value } }
    suspend fun rememberSearch(value: String) {
        if (value.isBlank()) return
        context.preferences.edit { p -> p[searches] = (listOf(value.trim().replace('\n', ' ')) + p[searches].orEmpty().split('\n')).filter(String::isNotBlank).distinct().take(8).joinToString("\n") }
    }
}
