package com.vibemusic.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun SpotifyImportDialog(onDismiss: () -> Unit, onChooseFile: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    fun open(url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { onMessage("No hay un navegador disponible para abrir la guía") } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Traer una playlist de Spotify") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("1. Exporta tu playlist como CSV con Exportify, o descarga el JSON de playlists desde los datos de tu cuenta de Spotify.", fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Text("2. Selecciona ese archivo aquí. Conservaremos el orden, los títulos y artistas. Las canciones que ya tengas se vinculan automáticamente si coinciden sin ambigüedad.", fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Text("3. Abre la playlist en Vibe para buscar y descargar coincidencias autorizadas, o vincular tus propios archivos. Las demás quedan como pendientes.", fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            Text("La exportación trae la lista, no los archivos de audio de Spotify. Un enlace por sí solo no permite importar las canciones. Vibe no pide tu contraseña ni extrae audio de Spotify.", color = VibeMuted, fontSize = 12.sp)
            TextButton({ open("https://exportify.net/") }) { Icon(Icons.Rounded.OpenInNew, null);Spacer(Modifier.width(8.dp));Text("Exportar CSV con Exportify") }
            Text("Exportify es un servicio externo; revisa allí el acceso que autorizas a tu cuenta.", fontSize = 11.sp, color = VibeMuted)
            TextButton({ open("https://www.spotify.com/account/privacy/") }) { Text("Solicitar mis datos a Spotify") }
        }
    }, confirmButton = { TextButton(onChooseFile) { Text("Seleccionar CSV o JSON") } }, dismissButton = { TextButton(onDismiss) { Text("Cerrar") } })
}
