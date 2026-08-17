package com.examplenativeandroidapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel : ViewModel() {

    private val _adRequestId = MutableStateFlow(0)
    private val _isAutoLoadEnabled = MutableStateFlow(true)

    val adRequestId: StateFlow<Int> = _adRequestId.asStateFlow()
    val isAutoLoadEnabled: StateFlow<Boolean> = _isAutoLoadEnabled.asStateFlow()

    fun setAutoLoadEnabled(enabled: Boolean) {
        _isAutoLoadEnabled.value = enabled
    }

    fun generateAd() {
        _adRequestId.value += 1
    }

    fun cancelAd() {
        _adRequestId.value = 0
    }
}
