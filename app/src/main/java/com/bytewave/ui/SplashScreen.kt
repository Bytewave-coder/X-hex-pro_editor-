package com.bytewave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    
    val alphaAnim = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 800,
            easing = androidx.compose.animation.core.LinearEasing
        ),
        label = "AlphaAnim"
    )

    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(1500L) // Show static splash for words, but icon fades in
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer(alpha = alphaAnim.value)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF0284C7), Color(0xFF1D4ED8))
                        )
                    )
                    .shadow(16.dp, spotColor = Color(0x66164E63)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "XH",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "X-HEX PRO",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "ADVANCED HEX EDITOR",
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
        }
    }
}
