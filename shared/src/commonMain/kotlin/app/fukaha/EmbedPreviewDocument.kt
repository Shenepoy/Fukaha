package app.fukaha

/** A local player document, never a provider's scripts/page or a downloaded media blob. */
object EmbedPreviewDocument {
    fun render(media: EmbedPreviewMedia, playLabel: String, errorLabel: String): String {
        fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
        val image = media.imageUrl?.let(::escape)
        val content = if (media.videoUrl != null) {
            """<video hidden playsinline preload="none" controlslist="nodownload noremoteplayback" disablepictureinpicture
                data-src="${escape(media.videoUrl)}" ${image?.let { "poster=\"$it\"" }.orEmpty()}></video>
                ${image?.let { """<img id="poster" src="$it" alt="" referrerpolicy="no-referrer">""" }
                    ?: """<div id="poster" class="placeholder" aria-hidden="true">▶</div>"""}
                <button id="play" type="button" aria-label="${escape(playLabel)}">▶</button>"""
        } else image?.let { """<img src="$it" alt="" referrerpolicy="no-referrer">""" }.orEmpty()
        return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="referrer" content="no-referrer">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https:; media-src https:; style-src 'unsafe-inline'; script-src 'nonce-fukaha-preview'; base-uri 'none'; form-action 'none'">
            <style>html,body{margin:0;width:100vw;height:var(--preview-height,100vh);overflow:hidden;background:#111;color:#fff;font:15px system-ui}
            body{position:relative}[hidden]{display:none!important}
            video,img,.placeholder{display:block;width:100vw;height:var(--preview-height,100vh);object-fit:contain}
            .placeholder{background:#1d1d1d;font-size:0}
            button{position:fixed;left:50vw;top:calc(var(--preview-height,100vh)/2);transform:translate(-50%,-50%);z-index:2;margin:0;width:64px;height:64px;border:0;border-radius:50%;background:#ffffffe6;color:#111;font-size:28px;cursor:pointer}
            #error{display:none;position:absolute;inset:auto 0 0;margin:0;padding:8px;background:#000c;text-align:center;overflow-wrap:anywhere}</style></head><body>
            $content<p id="error" role="status">${escape(errorLabel)}</p>
            <script nonce="fukaha-preview">
            (()=>{
              // Android WebView can resolve vh to zero during its first Compose measure.
              // Use the actual viewport after layout, and keep it in sync on rotation/resize.
              const fit=()=>{if(innerHeight>0)document.documentElement.style.setProperty('--preview-height',innerHeight+'px');};
              fit();requestAnimationFrame(fit);window.addEventListener('load',fit);window.addEventListener('resize',fit);
              const video=document.querySelector('video'),button=document.getElementById('play'),poster=document.getElementById('poster');
              if(!video)return;
              let position=0,releasing=false;
              function release(){
                if(releasing||!video.hasAttribute('src'))return;
                releasing=true;position=video.ended?0:video.currentTime;
                video.pause();video.removeAttribute('src');video.load();video.controls=false;
                video.hidden=true;poster.hidden=false;button.hidden=false;releasing=false;
              }
              button.onclick=()=>{
                document.getElementById('error').style.display='none';
                video.src=video.dataset.src;video.hidden=false;poster.hidden=true;video.controls=true;button.hidden=true;
                video.onloadedmetadata=()=>{if(position>0)video.currentTime=position;};
                video.play().catch(()=>{release();document.getElementById('error').style.display='block';});
              };
              video.addEventListener('pause',release);
              video.addEventListener('ended',release);
              video.addEventListener('error',()=>{if(video.hasAttribute('src')){release();document.getElementById('error').style.display='block';}});
              document.addEventListener('visibilitychange',()=>{if(document.hidden)release();});
              window.addEventListener('pagehide',release);
            })();
            </script></body></html>""".trimIndent()
    }
}
