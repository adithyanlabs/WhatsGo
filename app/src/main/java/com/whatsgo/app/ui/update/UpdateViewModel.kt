package com.whatsgo.app.ui.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whatsgo.app.core.update.DownloadState
import com.whatsgo.app.core.update.UpdateDownloadService
import com.whatsgo.app.core.update.UpdateInfo
import com.whatsgo.app.core.update.Updater
import com.whatsgo.app.ui.settings.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val version: String, val path: String) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = app.applicationContext
    private val settingsRepo = SettingsRepository(appContext)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null
    private var pendingInfo: UpdateInfo? = null
    private var hidden = false   // user chose "Hide": download continues, dialog stays closed

    init {
        viewModelScope.launch {
            UpdateDownloadService.state.collect { d ->
                if (hidden) return@collect
                when (d) {
                    is DownloadState.Downloading -> _state.value = UpdateState.Downloading(
                        pendingInfo ?: UpdateInfo(d.version, "", "", 0L), d.progress
                    )
                    is DownloadState.Ready -> _state.value = UpdateState.Ready(d.version, d.path)
                    is DownloadState.Failed ->
                        _state.value = UpdateState.Failed("Download failed: ${d.message}")
                    DownloadState.Idle ->
                        if (_state.value is UpdateState.Downloading) _state.value = UpdateState.Idle
                }
            }
        }
        check(manual = false)   // runs each time the app is opened
    }

    private suspend fun isNightlyEnabled(): Boolean =
        settingsRepo.getBoolean("nightly_builds", false)

    fun check(manual: Boolean) {
        val s = _state.value
        if (s is UpdateState.Available || s is UpdateState.Downloading || s is UpdateState.Ready) return
        if (UpdateDownloadService.state.value is DownloadState.Downloading) return
        job?.cancel()
        job = viewModelScope.launch {
            if (manual) _state.value = UpdateState.Checking
            try {
                val nightly = isNightlyEnabled()
                val info = Updater.fetchLatest(appContext, nightly)
                _state.value = when {
                    info != null -> UpdateState.Available(info)
                    manual -> UpdateState.UpToDate(Updater.currentVersion(appContext))
                    else -> UpdateState.Idle
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = if (manual)
                    UpdateState.Failed("Couldn't check for updates. Check your connection and try again.")
                else UpdateState.Idle
            }
        }
    }

    fun update() {
        val info = (_state.value as? UpdateState.Available)?.info ?: return
        pendingInfo = info
        hidden = false
        _state.value = UpdateState.Downloading(info, 0f)
        UpdateDownloadService.start(appContext, info)
    }

    fun install() {
        val ready = _state.value as? UpdateState.Ready ?: return
        if (Updater.install(appContext, File(ready.path))) _state.value = UpdateState.Idle
    }

    /** Closes the downloading dialog; the service and its notification keep going. */
    fun hide() {
        hidden = true
        _state.value = UpdateState.Idle
    }

    fun cancelDownload() {
        UpdateDownloadService.cancel(appContext)
        hidden = false
        _state.value = UpdateState.Idle
    }

    fun dismiss() {
        job?.cancel()
        _state.value = UpdateState.Idle
    }
}