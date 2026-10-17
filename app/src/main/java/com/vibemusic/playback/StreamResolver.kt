package com.vibemusic.playback

import com.vibemusic.data.YouTubeAudioStream
import com.vibemusic.data.YouTubeTrack
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Main-thread coordinator: bounded metadata prefetch, shared work and expiring in-memory URLs. */
class StreamResolver(
    private val scope: CoroutineScope,
    private val fetch: suspend (YouTubeTrack) -> YouTubeAudioStream,
    private val now: () -> Long = System::currentTimeMillis
) {
    private data class Cached(val audio: YouTubeAudioStream, val until: Long)
    private val cache = LinkedHashMap<String, Cached>()
    private val pending = mutableMapOf<String, Deferred<YouTubeAudioStream>>()
    suspend fun resolve(track: YouTubeTrack): YouTubeAudioStream {
        cache[track.id]?.takeIf { it.until > now() }?.let { return it.audio }
        val task = pending.getOrPut(track.id) {
            scope.async(start = CoroutineStart.LAZY) {
                val audio = withTimeout(20_000) { fetch(track) }
                val expiry = audio.url.toHttpUrlOrNull()?.queryParameter("expire")?.toLongOrNull()?.times(1000)?.minus(60_000)
                cache[track.id] = Cached(audio, minOf(now() + 5 * 60_000, expiry ?: Long.MAX_VALUE))
                while (cache.size > 8) cache.remove(cache.keys.first())
                audio
            }
        }
        try { return task.await() }
        finally { if (task.isCompleted && pending[track.id] === task) pending.remove(track.id) }
    }
    fun prefetch(track: YouTubeTrack) {
        if (pending.isNotEmpty() || cache[track.id]?.until?.let { it > now() } == true) return
        scope.launch { try { resolve(track) } catch (_: Exception) { /* Speculation never changes playback/UI. */ } }
    }
    fun prioritize(id: String) { pending.keys.filter { it != id }.forEach { pending.remove(it)?.cancel() } }
    fun cancelPending() { pending.values.forEach { it.cancel() };pending.clear() }
    fun invalidate(id: String) { cache.remove(id) }
}
