package com.yeex.dlof.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BackgroundTaskType { DOWNLOAD, PUBLISH }
enum class BackgroundTaskStatus { RUNNING, SUCCESS, ERROR }

data class BackgroundTask(
    val id: String,
    val type: BackgroundTaskType,
    val label: String,
    val progress: Float = 0f,       // 0f..1f
    val status: BackgroundTaskStatus = BackgroundTaskStatus.RUNNING,
    val resultMessage: String? = null
)

/**
 * Runs downloads and publishes on an app-level [CoroutineScope] instead of a
 * screen's `rememberCoroutineScope()`, so a task keeps going — with a real
 * progress bar — even after the sheet/screen that started it is dismissed or
 * navigated away from. That's the whole point: the person can swipe on to
 * the next paragraph in the feed while a download or a publish finishes in
 * the background, rather than being stuck waiting on the originating screen.
 *
 * [GlobalTaskProgressBar] renders whatever's in [tasks] as a slim overlay
 * that floats above every screen, so progress stays visible no matter where
 * the person navigates to next.
 */
object TaskProgressManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _tasks = MutableStateFlow<Map<String, BackgroundTask>>(emptyMap())
    val tasks: StateFlow<Map<String, BackgroundTask>> = _tasks

    /**
     * Starts [block] in the background under a fresh task entry.
     *
     * [block] receives an `updateProgress(0f..1f)` callback to report staged
     * progress (there's no byte-level progress from either the Realtime
     * Database write or the local bitmap/PDF work here, so callers report
     * progress in logical stages — e.g. "encoding" -> "uploading"). [block]
     * returns the success message to show, or throws to report failure.
     */
    fun launch(
        id: String,
        type: BackgroundTaskType,
        label: String,
        block: suspend (updateProgress: (Float) -> Unit) -> String
    ) {
        _tasks.update { it + (id to BackgroundTask(id, type, label)) }
        scope.launch {
            try {
                val message = block { p ->
                    _tasks.update { tasks ->
                        val current = tasks[id] ?: return@update tasks
                        tasks + (id to current.copy(progress = p.coerceIn(0f, 1f)))
                    }
                }
                _tasks.update { tasks ->
                    val current = tasks[id] ?: return@update tasks
                    tasks + (id to current.copy(progress = 1f, status = BackgroundTaskStatus.SUCCESS, resultMessage = message))
                }
            } catch (e: Exception) {
                _tasks.update { tasks ->
                    val current = tasks[id] ?: return@update tasks
                    tasks + (id to current.copy(status = BackgroundTaskStatus.ERROR, resultMessage = e.message))
                }
            } finally {
                // Leave the finished/failed state visible briefly so the
                // person actually sees the outcome, then clear it.
                delay(2500)
                _tasks.update { it - id }
            }
        }
    }
}
