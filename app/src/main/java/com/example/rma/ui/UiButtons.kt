package com.example.rma.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun Game3DButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    corner: Dp = 18.dp,
    height: Dp = 56.dp,
    faceColor: Color = Color(0xFF4CAF50),
    sideColor: Color = Color(0xFF2E7D32),
    textColor: Color = Color.White,
    depth: Dp = 8.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val offsetY by animateDpAsState(
        targetValue = if (pressed) depth else 0.dp,
        animationSpec = tween(durationMillis = 90),
        label = "Game3DButton_offsetY"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "Game3DButton_scale"
    )

    Box(
        modifier = modifier
            .height(height + depth),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = depth)
                .clip(RoundedCornerShape(corner))
                .background(if (enabled) sideColor else sideColor.copy(alpha = 0.5f))
        )

        Surface(
            shape = RoundedCornerShape(corner),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            color = if (enabled) faceColor else faceColor.copy(alpha = 0.55f),
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = offsetY)
                .clip(RoundedCornerShape(corner))
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = null
                ) { onClick() }
                .then(
                    Modifier
                        .padding(0.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(RoundedCornerShape(corner))
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = if (enabled) textColor else textColor.copy(alpha = 0.6f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .then(
                            Modifier
                                .offset(y = 0.dp)
                        )
                )
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(corner))
                .clickable(
                    enabled = false,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                .then(
                    Modifier
                        .align(Alignment.TopCenter)
                )
                .graphicsLayer(scaleX = scale, scaleY = scale)
        )
    }
}

@Composable
fun M3ElevatedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    corner: Dp = 16.dp
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(corner),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 2.dp,
            disabledElevation = 0.dp
        ),
        modifier = modifier.height(height)
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
fun Game3DIconButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    corner: Dp = 18.dp,
    height: Dp = 56.dp,
    faceColor: Color = Color(0xFF3F51B5),
    sideColor: Color = Color(0xFF283593),
    textColor: Color = Color.White,
    depth: Dp = 8.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val offsetY by animateDpAsState(
        targetValue = if (pressed) depth else 0.dp,
        animationSpec = tween(durationMillis = 90),
        label = "Game3DIconButton_offsetY"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "Game3DIconButton_scale"
    )

    Box(
        modifier = modifier.height(height + depth),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = depth)
                .clip(RoundedCornerShape(corner))
                .background(if (enabled) sideColor else sideColor.copy(alpha = 0.5f))
        )

        Surface(
            shape = RoundedCornerShape(corner),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            color = if (enabled) faceColor else faceColor.copy(alpha = 0.55f),
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = offsetY)
                .clip(RoundedCornerShape(corner))
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = null
                ) { onClick() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                }

                Text(
                    text = text,
                    color = if (enabled) textColor else textColor.copy(alpha = 0.6f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.weight(1f))
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer(scaleX = scale, scaleY = scale)
        )
    }
}

