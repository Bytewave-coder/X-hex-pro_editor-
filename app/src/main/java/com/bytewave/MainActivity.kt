package com.bytewave

// Cache busting comment
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bytewave.ui.HexEditorScreen
import com.bytewave.ui.theme.MyApplicationTheme
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow

class MainActivity : ComponentActivity() {
  private val viewModel: HexEditorViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val state by viewModel.state.collectAsStateWithLifecycle()
      var showSplash by remember { mutableStateOf(true) }
      MyApplicationTheme(isDarkMode = state.isDarkMode, appThemeColor = state.themeColor) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            androidx.compose.animation.Crossfade(
                targetState = showSplash, 
                animationSpec = androidx.compose.animation.core.tween(800),
                label = "splash_crossfade"
            ) { isSplash ->
                if (isSplash) {
                    AnimatedSplashScreen(onSplashFinished = { showSplash = false })
                } else {
                    HexEditorScreen(viewModel)
                }
            }
        }
      }
    }
  }
}

fun Modifier.animatedDialogEntry() = this.then(
    Modifier.composed {
        val alpha = remember { Animatable(0f) }
        val scale = remember { Animatable(0.85f) }
        LaunchedEffect(Unit) {
            alpha.animateTo(1f, tween(150))
            scale.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 400f))
        }
        Modifier.graphicsLayer {
            this.alpha = alpha.value
            this.scaleX = scale.value
            this.scaleY = scale.value
        }
    }
)

fun Modifier.bounceClick() = this.then(
    Modifier.composed {
        var isPressed by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.90f else 1f,
            animationSpec = tween(150),
            label = "bounce"
        )
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(isPressed) {
                awaitPointerEventScope {
                    isPressed = if (isPressed) {
                        waitForUpOrCancellation()
                        false
                    } else {
                        awaitFirstDown(false)
                        true
                    }
                }
            }
    }
)

@Composable
fun AnimatedDialogSurface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    tonalElevation: androidx.compose.ui.unit.Dp = 8.dp,
    border: androidx.compose.foundation.BorderStroke? = null,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    androidx.compose.animation.AnimatedVisibility(
        visible = isVisible,
        enter = androidx.compose.animation.fadeIn(tween(150)) + androidx.compose.animation.scaleIn(spring(dampingRatio = 0.7f, stiffness = 400f), initialScale = 0.85f),
        exit = androidx.compose.animation.fadeOut(tween(100)) + androidx.compose.animation.scaleOut(tween(100), targetScale = 0.85f)
    ) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            tonalElevation = tonalElevation,
            border = border,
            content = content
        )
    }
}

@Composable
fun AnimatedSplashScreen(onSplashFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "alpha_anim"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "infinite")
    val scaleAnim by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_anim"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2500) // 2.5s for loading and giving app enough time to organize buffers
        onSplashFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Code,
                contentDescription = "Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(96.dp)
                    .scale(scaleAnim)
                    .alpha(alphaAnim.value)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "X-HEX PRO",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.alpha(alphaAnim.value)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Initializing workspace...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alpha(alphaAnim.value)
            )
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp).alpha(alphaAnim.value),
                strokeWidth = 2.dp
            )
        }
    }
}
