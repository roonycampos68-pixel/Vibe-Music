package com.vibemusic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vibemusic.model.Track
import com.vibemusic.model.MusicRules
import com.vibemusic.data.YouTubeSearch
import com.vibemusic.data.YouTubeWeb
import com.vibemusic.data.Settings
import com.vibemusic.VibeApplication
import java.io.File
import kotlin.math.*

val VibeGreen = Color(0xFF1DB954)
val VibeBackground = Color(0xFF121212)
val VibeCard = Color(0xFF282828)
val VibeMuted = Color(0xFFB3B3B3)
@Composable fun VibeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = VibeGreen, onPrimary = Color.Black, background = VibeBackground, surface = VibeBackground, surfaceContainer = Color(0xFF181818), surfaceContainerHigh = VibeCard, onSurface = Color.White, onSurfaceVariant = VibeMuted, secondary = VibeGreen), content = content)
}

@Composable fun Art(track: Track? = null, modifier: Modifier = Modifier, label: String = "Vibe", color: Color = track?.let { Color(it.color) } ?: Color(0xFF37685F), path: String? = track?.artworkPath) {
    val youtubeId = track?.sourceUrl?.let(YouTubeSearch::videoId)
    if (youtubeId != null) {
        YouTubeCover(youtubeId, track.title, modifier, path)
        return
    }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(color)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(color.copy(alpha = 1f), Color(0xFF111719)), start = Offset(0f, 0f), end = Offset(size.width, size.height)))
            val seed = (track?.title ?: label).hashCode().absoluteValue
            val variant = seed % 3
            if (variant == 0) {
                for (i in 0..9) {
                    val radius = size.width * (.1f + i * .064f)
                    drawCircle(Color(0xFFD7DDC5).copy(alpha = .10f + i * .015f), radius, Offset(size.width * .68f, size.height * .35f), style = Stroke(size.width * .016f))
                }
                drawCircle(Brush.radialGradient(listOf(Color(0xFFF2C786), Color(0xFFBD8250))), size.width * .15f, Offset(size.width * .68f, size.height * .35f))
            } else if (variant == 1) {
                for (i in 0..30) {
                    val x = i * size.width / 26
                    drawLine(Color(0xFFB2E1CB).copy(alpha = .13f + (i % 4) * .05f), Offset(x, 0f), Offset(x - size.width * .6f, size.height), size.width * .027f)
                }
                drawCircle(Brush.radialGradient(listOf(Color(0xFFDAEDE2), Color(0xFF7DA99B))), size.width * .2f, Offset(size.width * .68f, size.height * .32f))
            } else {
                for (i in 0..8) {
                    val path = Path().apply {
                        moveTo(0f, size.height * (.36f + i * .07f))
                        cubicTo(size.width * .38f, size.height * (.05f + i * .07f), size.width * .6f, size.height * (.82f + i * .04f), size.width, size.height * (.25f + i * .07f))
                        lineTo(size.width, size.height);lineTo(0f, size.height);close()
                    }
                    drawPath(path, Color(0xFFB9C4C7).copy(alpha = .04f + i * .025f))
                }
            }
        }
        if (path != null) AsyncImage(model = File(path), contentDescription = "Portada de ${track?.album ?: label}", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else if (track?.source == "original" || label == "Vibe Originals") {
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text("VIBE / ORIGINALS", fontSize = 8.sp, letterSpacing = 2.sp, color = Color.White.copy(alpha = .7f))
                Text(track?.title ?: "VOL. 01", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2)
            }
        }
    }
}

@Composable fun YouTubeCover(id: String, title: String, modifier: Modifier = Modifier, path: String? = null) {
    val context = LocalContext.current
    val app = context.applicationContext as VibeApplication
    val settings by app.settings.settings.collectAsState(initial = Settings(offlineOnly = true))
    val local = path?.let(::File)?.takeIf { it.isFile }
        ?: File(app.repository.artDir, "youtube-$id.jpg").takeIf { it.isFile }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(VibeCard), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, Modifier.fillMaxSize(.3f), tint = VibeMuted)
        val model = local ?: if (!settings.offlineOnly) YouTubeWeb.thumbnail(id) else null
        if (model != null) AsyncImage(model, "Portada de $title", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    }
}

@Composable fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = Color.White, modifier: Modifier = Modifier, enabled: Boolean = true) {
    IconButton(onClick, modifier.size(48.dp), enabled = enabled) { Icon(icon, description, tint = if (enabled) tint else VibeMuted.copy(alpha = .35f)) }
}
@Composable fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = if (selected) VibeGreen else VibeCard) {
        Text(text, Modifier.padding(horizontal = 17.dp, vertical = 10.dp), color = if (selected) Color.Black else Color.White, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
@Composable fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp)
        if (action != null) TextButton(onAction) { Text(action, fontSize = 12.sp, color = VibeMuted) }
    }
}
@Composable fun TrackRow(track: Track, active: Boolean, onPlay: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().clickable(onClick = onPlay).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(track, Modifier.size(54.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, color = if (active) VibeGreen else Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, "Disponible sin conexión", tint = VibeGreen, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(5.dp))
                Text(track.artist, fontSize = 12.sp, color = VibeMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) trailing() else {
            Text(MusicRules.formatTime(track.durationMs), fontSize = 11.sp, color = VibeMuted)
            IconAction(Icons.Rounded.MoreVert, "Opciones de ${track.title}", onMore, VibeMuted)
        }
    }
}
@Composable fun EmptyState(icon: ImageVector, title: String, description: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(80.dp).clip(CircleShape).background(VibeCard), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(34.dp), tint = VibeGreen) }
        Spacer(Modifier.height(18.dp));Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp));Text(description, color = VibeMuted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(18.dp));Button(onAction, shape = CircleShape) { Text(action, fontWeight = FontWeight.SemiBold) } }
    }
}
