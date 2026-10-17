package com.vibemusic.data

import android.net.Uri

/** URLs and content for the official player. No user text is inserted into JavaScript. */
object YouTubeWeb {
    // Android WebViews identify the embedding application with its package as the HTTPS origin.
    // loadDataWithBaseURL supplies that origin and the corresponding Referer to the iframe.
    const val ORIGIN = "https://com.vibemusic"
    const val BASE_URL = "$ORIGIN/"
    const val EVENT_PREFIX = "VIBE_YOUTUBE:"
    private val pageHosts = setOf("www.youtube.com", "youtube.com", "m.youtube.com", "music.youtube.com", "www.youtube-nocookie.com", "consent.youtube.com", "consent.google.com")

    fun allowsPage(url: String): Boolean = runCatching {
        val uri = Uri.parse(url)
        uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
            (uri.host in pageHosts || url == BASE_URL)
    }.getOrDefault(false)

    fun thumbnail(id: String): String {
        YouTubeSearch.videoUrl(id) // Validate before interpolating into a URL.
        return "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    }

    fun html(id: String): String {
        YouTubeSearch.videoUrl(id)
        return """
            <!doctype html><html lang="es"><head>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="referrer" content="strict-origin-when-cross-origin">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' https://www.youtube.com https://s.ytimg.com; style-src 'unsafe-inline'; frame-src https://www.youtube.com https://www.youtube-nocookie.com; img-src https: data:; connect-src https:">
            <style>html,body,#player{margin:0;width:100%;height:100%;background:#000;overflow:hidden}</style>
            </head><body><div id="player"></div><script>
            var player;
            function signal(value){console.log('${EVENT_PREFIX}'+value);}
            function selection(target){
              if(target && target.getVideoUrl){
                var data=target.getVideoData ? target.getVideoData() : {};
                signal('video:'+JSON.stringify({url:target.getVideoUrl(),title:data.title || '',duration:target.getDuration ? target.getDuration() : 0}));
              }
            }
            function onYouTubeIframeAPIReady(){
              player=new YT.Player('player',{
                width:'100%',height:'100%',videoId:'$id',
                playerVars:{autoplay:0,playsinline:1,rel:0,fs:0,origin:'$ORIGIN'},
                events:{onReady:function(e){signal('ready');selection(e.target);},onStateChange:function(e){selection(e.target);},onError:function(e){signal('error:'+e.data);}}
              });
            }
            function vibePause(){if(player && player.pauseVideo){player.pauseVideo();}}
            var script=document.createElement('script');script.src='https://www.youtube.com/iframe_api';
            script.onerror=function(){signal('network');};document.head.appendChild(script);
            </script></body></html>
        """.trimIndent()
    }

    fun playerError(code: Int): String = when (code) {
        100 -> "El vídeo se ha eliminado o es privado. Prueba otra grabación."
        101, 150 -> "Este vídeo no permite el reproductor integrado. Prueba «Ver página aquí»."
        153 -> "YouTube no pudo identificar el reproductor. Prueba «Ver página aquí» o actualiza Android System WebView."
        else -> "YouTube no pudo reproducir este vídeo. Prueba «Ver página aquí» o vuelve a intentarlo."
    }
}
