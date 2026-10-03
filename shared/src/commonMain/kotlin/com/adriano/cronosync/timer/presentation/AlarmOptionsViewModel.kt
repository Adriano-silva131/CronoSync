package com.adriano.cronosync.timer.presentation

import androidx.lifecycle.ViewModel
import com.adriano.cronosync.timer.data.AlarmPreferences
import com.adriano.cronosync.timer.data.AlarmPreferencesRepository
import kotlinx.coroutines.flow.StateFlow

sealed interface AlarmOptionsAction {
    data class SetSound(val enabled: Boolean) : AlarmOptionsAction
    data class SetVibration(val enabled: Boolean) : AlarmOptionsAction
}

/** Chaves "Som" e "Vibração" do alarme. O estado é a própria preferência salva. */
class AlarmOptionsViewModel(private val repository: AlarmPreferencesRepository) : ViewModel() {

    val uiState: StateFlow<AlarmPreferences> = repository.preferences

    fun onAction(action: AlarmOptionsAction) {
        when (action) {
            is AlarmOptionsAction.SetSound -> repository.setSound(action.enabled)
            is AlarmOptionsAction.SetVibration -> repository.setVibration(action.enabled)
        }
    }
}
