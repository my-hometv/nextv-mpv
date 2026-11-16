package com.softppj.myview

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private var player: ExoPlayer? = null
    private var browser: WebView? = null
    private var fullscreen: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var mode = "menu"
    private val prefs by lazy { getSharedPreferences("myview_test", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this)
        setContentView(root)
        showMenu()
    }

    private fun showMenu() {
        releasePlayback()
        mode = "menu"
        root.removeAllViews()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(android.graphics.Color.rgb(18, 26, 38))
        }
        panel.addView(TextView(this).apply {
            text = "MyView • Android TV playback test"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
        })
        panel.addView(TextView(this).apply {
            text = "Test an authorized stream in Media3, or a provider webpage in WebView. DRM-protected streams need provider-authorized licenses."
            setTextColor(android.graphics.Color.LTGRAY)
        })
        fun addButton(label: String, action: () -> Unit) {
            panel.addView(Button(this).apply {
                text = label
                isFocusable = true
                setOnClickListener { action() }
            })
        }
        addButton("Play stream (Media3)") { askUrl("stream", "HTTPS .mpd / .m3u8 / .mp4 URL") }
        addButton("Open provider webpage (WebView)") { askUrl("web", "HTTPS webpage URL") }
        addButton("Exit") { finish() }
        root.addView(panel, FrameLayout.LayoutParams(-1, -1))
        panel.getChildAt(2).requestFocus()
    }

    private fun askUrl(type: String, title: String) {
        val field = EditText(this).apply {
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.getString("last_" + type, ""))
            hint = "https://"
        }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(field)
            .setPositiveButton("Open", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = field.text.toString().trim()
                val uri = android.net.Uri.parse(url)
                if (uri.scheme != "https" || uri.host.isNullOrBlank() ||
                    uri.encodedAuthority?.contains("@") == true || url.length > 4096) {
                    field.error = "Use a valid HTTPS URL"
                } else {
                    prefs.edit().putString("last_" + type, url).apply()
                    dialog.dismiss()
                    if (type == "web") openWeb(url) else openMedia(url)
                }
            }
        }
        dialog.show()
    }

    private fun openMedia(url: String) {
        releasePlayback()
        mode = "media"
        root.removeAllViews()
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        val view = PlayerView(this).apply {
            player = exo
            useController = true
            isFocusable = true
        }
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        val builder = MediaItem.Builder().setUri(url)
        val path = android.net.Uri.parse(url).path.orEmpty().lowercase()
        if (path.endsWith(".mpd")) builder.setMimeType(MimeTypes.APPLICATION_MPD)
        if (path.endsWith(".m3u8")) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        exo.setMediaItem(builder.build())
        exo.prepare()
        exo.playWhenReady = true
        view.requestFocus()
    }

    private fun openWeb(url: String) {
        releasePlayback()
        mode = "web"
        root.removeAllViews()
        val web = WebView(this)
        browser = web
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return request.url.scheme != "https"
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullscreen != null) { callback.onCustomViewHidden(); return }
                fullscreen = view
                fullscreenCallback = callback
                root.addView(view, FrameLayout.LayoutParams(-1, -1))
                web.visibility = View.GONE
            }
            override fun onHideCustomView() { hideFullscreen() }
        }
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        web.loadUrl(url)
        web.requestFocus()
    }

    private fun hideFullscreen() {
        val view = fullscreen ?: return
        root.removeView(view)
        fullscreen = null
        browser?.visibility = View.VISIBLE
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
    }

    private fun releasePlayback() {
        hideFullscreen()
        player?.release()
        player = null
        browser?.apply { stopLoading(); loadUrl("about:blank"); destroy() }
        browser = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && mode != "menu") {
            if (fullscreen != null) hideFullscreen() else showMenu()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() { browser?.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); browser?.onResume() }
    override fun onDestroy() { releasePlayback(); super.onDestroy() }
}
