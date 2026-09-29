package com.examplefragmentapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel : ViewModel() {

    private val _adRequestId = MutableStateFlow(0)

    val adRequestId: StateFlow<Int> = _adRequestId.asStateFlow()

    fun generateAd() {
        _adRequestId.value += 1
    }

    fun cancelAd() {
        _adRequestId.value = 0
    }
}
