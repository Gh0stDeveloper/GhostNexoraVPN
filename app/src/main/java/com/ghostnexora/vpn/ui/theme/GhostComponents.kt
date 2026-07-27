package com.ghostnexora.vpn.ui.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ghostnexora.vpn.data.model.LogLevel
import com.ghostnexora.vpn.data.model.VpnConnectionState

@Composable
fun GhostCard(
    modifier: Modifier = Modifier,
    borderColor: Color = BorderSubtle,
    glowColor: Color? = null,
    backgroundColor: Color = SurfaceVariant,
    contentPadding: PaddingValues = PaddingValues(Dimens.CardPadding),
    content: @Composable ColumnScope.() -> Unit
) {
    val decorated = if (glowColor != null) modifier.neonGlow(glowColor, 12.dp, 0.3f) else modifier
    Column(
        modifier = decorated
            .clip(ProfileCardShape)
            .background(backgroundColor)
            .border(Dimens.BorderNormal, borderColor, ProfileCardShape)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun StatusDot(
    state: VpnConnectionState,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.StatusDotSize
) {
    val color = stateColor(state)
    val pulsing = state is VpnConnectionState.Connecting || state is VpnConnectionState.Reconnecting
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "vpn-status-pulse")
        val animated by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "vpn-status-alpha"
        )
        animated
    } else 1f

    Box(
        modifier = modifier.size(size).clip(CircleShape).background(color.copy(alpha = alpha))
    )
}

@Composable
fun LogLevelBadge(level: LogLevel, modifier: Modifier = Modifier) {
    val (background, foreground) = when (level) {
        LogLevel.DEBUG -> SurfaceElevated to TextTertiary
        LogLevel.INFO -> NeonBlue.copy(alpha = 0.15f) to NeonBlue
        LogLevel.SUCCESS -> NeonGreen.copy(alpha = 0.15f) to NeonGreen
        LogLevel.WARNING -> NeonAmber.copy(alpha = 0.15f) to NeonAmber
        LogLevel.ERROR -> NeonRed.copy(alpha = 0.15f) to NeonRed
    }
    Box(
        modifier = modifier.clip(TagShape).background(background).padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(level.label, style = MaterialTheme.typography.labelSmall, color = foreground, maxLines = 1)
    }
}

@Composable
fun ProfileTagChip(tag: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(TagShape)
            .background(NeonCyan.copy(alpha = 0.1f))
            .border(Dimens.BorderThin, NeonCyanDim.copy(alpha = 0.5f), TagShape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            tag,
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyanDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun NeonDivider(modifier: Modifier = Modifier, color: Color = BorderSubtle) {
    HorizontalDivider(modifier = modifier, thickness = Dimens.BorderThin, color = color)
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = NeonCyan,
    contentColor: Color = TextOnAccent
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = SurfaceElevated,
            disabledContentColor = TextDisabled
        ),
        shape = InputFieldShape
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GhostOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: Color = NeonCyan,
    contentColor: Color = NeonCyan
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            disabledContentColor = TextDisabled
        ),
        border = BorderStroke(Dimens.BorderNormal, if (enabled) borderColor else BorderNormal),
        shape = InputFieldShape
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GhostTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isError: Boolean = false,
    errorMessage: String = "",
    singleLine: Boolean = true,
    enabled: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = placeholder.takeIf(String::isNotEmpty)?.let { text -> { Text(text, color = TextTertiary) } },
            isError = isError,
            enabled = enabled,
            singleLine = singleLine,
            trailingIcon = trailingIcon,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth(),
            shape = InputFieldShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = BorderNormal,
                errorBorderColor = NeonRed,
                focusedLabelColor = NeonCyan,
                unfocusedLabelColor = TextSecondary,
                cursorColor = NeonCyan,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                disabledTextColor = TextDisabled,
                focusedContainerColor = SurfaceVariant,
                unfocusedContainerColor = SurfaceVariant,
                disabledContainerColor = SurfaceDark
            )
        )
        if (isError && errorMessage.isNotEmpty()) {
            Text(
                errorMessage,
                style = MaterialTheme.typography.labelSmall,
                color = NeonRed,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

fun stateColor(state: VpnConnectionState): Color = when (state) {
    is VpnConnectionState.Connected -> StateConnected
    is VpnConnectionState.Connecting -> StateConnecting
    is VpnConnectionState.Reconnecting -> NeonAmber
    is VpnConnectionState.Disconnecting -> StateConnecting
    VpnConnectionState.Disconnected -> StateDisconnected
    is VpnConnectionState.Error -> StateError
}

fun Modifier.neonGlow(
    color: Color,
    radius: Dp = 16.dp,
    alpha: Float = 0.4f
): Modifier = drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.TRANSPARENT
                setShadowLayer(radius.toPx(), 0f, 0f, color.copy(alpha = alpha).toArgb())
            }
        }
        canvas.drawRoundRect(
            left = 0f,
            top = 0f,
            right = size.width,
            bottom = size.height,
            radiusX = 16.dp.toPx(),
            radiusY = 16.dp.toPx(),
            paint = paint
        )
    }
}

fun backgroundGradient() = Brush.verticalGradient(listOf(BackgroundDeep, BackgroundDark))
fun actionButtonGradient(color: Color) = Brush.radialGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent))
