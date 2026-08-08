package com.yeex.dlof.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeex.dlof.ui.theme.YeexAccent
import com.yeex.dlof.ui.theme.YeexDarkCard
import com.yeex.dlof.ui.theme.YeexDislike
import com.yeex.dlof.util.BackgroundTaskStatus
import com.yeex.dlof.util.BackgroundTaskType
import com.yeex.dlof.util.TaskProgressManager

/**
 * Floating stack of slim progress cards, one per active [TaskProgressManager]
 * task — mounted once at the app root (see [com.yeex.dlof.navigation.YeexNavGraph])
 * so it stays visible over every screen. Deliberately compact and anchored to
 * the bottom edge, above the bottom nav bar, so it never blocks the feed's
 * swipe gestures or the action rail: the person can keep watching/swiping
 * through other paragraphs while a download or a publish finishes here.
 */
@Composable
fun GlobalTaskProgressBar(modifier: Modifier = Modifier) {
    val tasks by TaskProgressManager.tasks.collectAsState()
    val ordered = tasks.values.sortedBy { it.id }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ordered.forEach { task ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 2 }
            ) {
                val animatedProgress by animateFloatAsState(
                    targetValue = task.progress,
                    animationSpec = tween(280),
                    label = "taskProgress"
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = YeexDarkCard,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (task.status) {
                                BackgroundTaskStatus.RUNNING -> {
                                    Icon(
                                        imageVector = if (task.type == BackgroundTaskType.DOWNLOAD)
                                            Icons.Filled.CloudDownload else Icons.Filled.CloudUpload,
                                        contentDescription = null,
                                        tint = YeexAccent,
                                        modifier = Modifier.width(18.dp)
                                    )
                                }
                                BackgroundTaskStatus.SUCCESS -> {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = androidx.compose.ui.graphics.Color(0xFF34C759),
                                        modifier = Modifier.width(18.dp)
                                    )
                                }
                                BackgroundTaskStatus.ERROR -> {
                                    Icon(
                                        imageVector = Icons.Filled.Error,
                                        contentDescription = null,
                                        tint = YeexDislike,
                                        modifier = Modifier.width(18.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                task.resultMessage?.takeIf { task.status != BackgroundTaskStatus.RUNNING } ?: task.label,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            if (task.status == BackgroundTaskStatus.RUNNING) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${(animatedProgress * 100).toInt()}%",
                                    color = YeexAccent,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (task.status == BackgroundTaskStatus.RUNNING) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = YeexAccent,
                                trackColor = Color.White.copy(alpha = 0.15f)
                            )
                        }
                    }
                }
            }
        }
    }
}
