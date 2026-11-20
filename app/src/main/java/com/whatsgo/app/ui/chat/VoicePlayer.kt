package com.whatsgo.app.ui.chat

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VoicePlayer {
    data class State(
        val messageId: String? = null,
        val playing: Boolean = false,
        val positionMs: Int = 0,
        val durationMs: Int = 0
    )

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    fun toggle(messageId: String, path: String) {
        val current = mutableState.value
        val activePlayer = player
        if (current.messageId == messageId && activePlayer != null) {
            if (current.playing) {
                activePlayer.pause()
                mutableState.value = current.copy(playing = false)
                ticker?.cancel()
            } else {
                activePlayer.start()
                mutableState.value = current.copy(playing = true)
                startTicker()
            }
            return
        }

        stop()
        val nextPlayer = MediaPlayer()
        try {
            nextPlayer.setDataSource(path)
            nextPlayer.prepare()
            nextPlayer.setOnCompletionListener { stop() }
            nextPlayer.start()
        } catch (_: Exception) {
            nextPlayer.release()
            return
        }
        player = nextPlayer
        mutableState.value = State(messageId, true, 0, nextPlayer.duration)
        startTicker()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val activePlayer = player ?: break
                mutableState.value = mutableState.value.copy(
                    positionMs = runCatching { activePlayer.currentPosition }.getOrDefault(0)
                )
                delay(100)
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        runCatching { player?.release() }
        player = null
        mutableState.value = State()
    }
}
