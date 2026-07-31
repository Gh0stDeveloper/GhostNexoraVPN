package com.ghostnexora.vpn.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.ghostnexora.vpn.security.HtmlNoteSanitizer
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlNoteView(
    html: String,
    modifier: Modifier = Modifier
) {
    val safeHtml = HtmlNoteSanitizer.sanitize(html)
    val document = noteDocument(safeHtml)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false
                settings.apply {
                    javaScriptEnabled = false
                    javaScriptCanOpenWindowsAutomatically = false
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    loadsImagesAutomatically = false
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
            Column {
                HtmlNoteView(
                    html = html,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                )
            }
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
        body {
          margin: 0; padding: 12px; background: transparent; color: #E8F2F5;
          font-family: sans-serif; line-height: 1.5; overflow-wrap: anywhere;
        }
        a { color: #00DDEB; }
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
