package com.yeex.dlof.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeex.dlof.R
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexCrimson
import com.yeex.dlof.ui.theme.YeexNavy
import com.yeex.dlof.ui.theme.YeexNavyDark
import kotlinx.coroutines.delay

/**
 * Branded splash screen — the nav graph's actual start destination, shown
 * right after the brief OS-level splash (see Theme.Yeex.Splash in themes.xml)
 * hands off. Fades/scales the "yeex" wordmark in over a navy → black brand
 * gradient, holds briefly, then calls [onFinished] once (with whether a
 * session is already signed in) so the caller can navigate to FEED or LOGIN.
 */
@Composable
fun SplashScreen(
    isLoggedIn: Boolean,
    onFinished: (loggedIn: Boolean) -> Unit
) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.85f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(550, easing = LinearOutSlowInEasing))
        scale.animateTo(1f, animationSpec = tween(550, easing = LinearOutSlowInEasing))
        delay(500)
        onFinished(isLoggedIn)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(YeexNavyDark, YeexNavy, Color.Black))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(alpha.value)
                .scale(scale.value)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(YeexAccent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "y",
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(YeexCrimson)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.community_name),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp
                )
            }
        }
    }
}
