package com.ghostnexora.vpn.ui.components

import android.graphics.Typeface
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.ghostnexora.vpn.ui.theme.NeonCyan
import com.ghostnexora.vpn.ui.theme.TextPrimary

/**
 * Safe rich-text renderer for the optional SSH authentication banner.
 * JavaScript, WebView content and remote resources are deliberately absent.
 */
@Composable
fun SshServerMessageView(
    message: String,
    modifier: Modifier = Modifier
) {
    val safeHtml = remember(message) { LogPresentation.serverMessageHtml(message).orEmpty() }
    val styledText = remember(safeHtml) {
        HtmlCompat.fromHtml(safeHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                setTextColor(TextPrimary.toArgb())
                setLinkTextColor(NeonCyan.toArgb())
                textSize = 14f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                gravity = Gravity.START
                setLineSpacing(0f, 1.18f)
                movementMethod = LinkMovementMethod.getInstance()
                linksClickable = true
                setTextIsSelectable(true)
                includeFontPadding = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
        },
        update = { view ->
            if (view.tag != safeHtml) {
                view.tag = safeHtml
                view.text = styledText
            }
        }
    )
}
