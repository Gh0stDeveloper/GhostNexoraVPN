package com.ghostnexora.vpn.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import com.ghostnexora.vpn.security.HtmlNoteSanitizer
import java.io.ByteArrayInputStream

@Composable
fun ProfileNoteContent(
    note: String,
    modifier: Modifier = Modifier,
    htmlViewportHeight: Dp = 224.dp,
    textMaxHeight: Dp = Dp.Unspecified
) {
    val safeNote = remember(note) { HtmlNoteSanitizer.sanitize(note) }
    if (safeNote.isBlank()) return

    val hasMarkup = remember(safeNote) { HtmlNoteSanitizer.hasPresentationMarkup(safeNote) }
    if (hasMarkup) {
        HtmlNoteView(safeNote, modifier.height(htmlViewportHeight))
    } else {
        val textModifier = if (textMaxHeight == Dp.Unspecified) {
            modifier
        } else {
            modifier.heightIn(max = textMaxHeight).verticalScroll(rememberScrollState())
        }
        SelectionContainer {
            Text(safeNote, modifier = textModifier, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlNoteView(
    safeHtml: String,
    modifier: Modifier = Modifier
) {
    val document = remember(safeHtml) { noteDocument(safeHtml) }
    val nestedScrollInteropConnection = rememberNestedScrollInteropConnection()
    AndroidView(
        modifier = modifier.testTag("profile-note-html").nestedScroll(nestedScrollInteropConnection),
        factory = { context ->
            ScrollableNoteWebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                setNetworkAvailable(false)
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false
                isScrollbarFadingEnabled = false
                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                ViewCompat.setNestedScrollingEnabled(this, true)
                settings.apply {
                    javaScriptEnabled = false
                    javaScriptCanOpenWindowsAutomatically = false
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    loadsImagesAutomatically = true
                    defaultTextEncodingName = "UTF-8"
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    setSupportMultipleWindows(false)
                    safeBrowsingEnabled = true
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse = blockedResponse()

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val uri = request?.url ?: return true
                        if (uri.scheme == "about" && uri.fragment != null) return false
                        val allowed = uri.scheme?.lowercase() in setOf(
                            "https", "http", "mailto", "tel"
                        )
                        if (allowed) {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString()))
                                )
                            }
                        }
                        return true
                    }
                }
            }
        },
        update = { webView ->
            if (webView.tag != document) {
                webView.tag = document
                webView.loadDataWithBaseURL(
                    "about:blank",
                    document,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    )
}

/**
 * Keeps vertical gestures inside a long creator note while it can still move,
 * then returns the gesture to the scrollable Compose dashboard at either edge.
 */
private class ScrollableNoteWebView(context: Context) : WebView(context) {
    private var previousTouchY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousTouchY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                val direction = if (event.y < previousTouchY) 1 else -1
                parent?.requestDisallowInterceptTouchEvent(canScrollVertically(direction))
                previousTouchY = event.y
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.onTouchEvent(event)
    }
}

@Composable
fun HtmlNoteDialog(
    title: String,
    html: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            ProfileNoteContent(
                note = html,
                modifier = Modifier.fillMaxWidth(),
                htmlViewportHeight = 380.dp,
                textMaxHeight = 380.dp
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

private fun noteDocument(body: String): String = """
    <!doctype html>
    <html>
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <meta http-equiv="Content-Security-Policy"
            content="default-src 'none'; style-src 'unsafe-inline'; img-src data:; font-src 'none'; connect-src 'none'; media-src 'none'; frame-src 'none'; object-src 'none'; form-action 'none'; base-uri 'none'">
      <style>
        :root { color-scheme: dark; }
        html, body {
          max-width: 100%; min-height: 100%; overflow-x: hidden; overflow-y: auto;
          -webkit-overflow-scrolling: touch;
        }
        body {
          margin: 0; padding: 12px; background: transparent; color: #E8F2F5;
          font-family: sans-serif; line-height: 1.5; overflow-wrap: anywhere;
        }
        a { color: #00DDEB; }
        img { display: block; max-width: 100%; height: auto; }
        pre, code { white-space: pre-wrap; background: #11242A; border-radius: 6px; }
        pre { padding: 10px; }
        table { max-width: 100%; border-collapse: collapse; }
        td, th { border: 1px solid #38515A; padding: 6px; }
      </style>
    </head>
    <body>$body</body>
    </html>
""".trimIndent()

private fun blockedResponse(): WebResourceResponse =
    WebResourceResponse(
        "text/plain",
        "UTF-8",
        ByteArrayInputStream(ByteArray(0))
    )
