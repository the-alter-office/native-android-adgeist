package com.examplecomposeapp.feature.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import com.adgeistkit.AdgeistCore
import com.adgeistkit.utilities.AdgeistInternalApi
import com.adgeistkit.utilities.CustomConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@OptIn(SavedStateHandleSaveableApi::class)
class HomeViewModel(
    private val application: Application,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    var form by savedStateHandle.saveable(stateSaver = HomeFormSaver) { mutableStateOf(HomeForm()) }
        private set

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var lastRequestId = 0

    fun onFormChange(updated: HomeForm) {
        form = updated
    }

    fun onResponsiveChange(isResponsive: Boolean) {
        form = if (isResponsive) {
            form.copy(isResponsive = true, containerWidth = "300", containerHeight = "250")
        } else {
            form.copy(isResponsive = false)
        }
    }

    @OptIn(AdgeistInternalApi::class)
    fun onConfigureClick() {
        val packageId = form.packageId.trim()
        val adgeistAppId = form.adgeistAppId.trim()

        if (packageId.isEmpty() || adgeistAppId.isEmpty()) {
            showDialog(HomeDialog.InvalidConfiguration)
            return
        }

        AdgeistCore.destroy()
        AdgeistCore.initialize(
            application,
            CustomConfig(
                backendDomain = BID_REQUEST_BACKEND_DOMAIN,
                packageOrBundleId = packageId,
                adgeistAppId = adgeistAppId,
            )
        )

        showDialog(HomeDialog.Configured(packageId, adgeistAppId))
    }

    fun onGetAdClick() {
        val current = form
        val missing = missingFields(current)

        if (missing.isNotEmpty()) {
            showDialog(HomeDialog.InvalidFields(missing))
            return
        }

        lastRequestId += 1
        val request = HomeAdRequest(
            id = lastRequestId,
            adUnitId = current.adspaceId.trim(),
            isResponsive = current.isResponsive,
            widthDp = (if (current.isResponsive) current.containerWidth else current.width).trim().toInt(),
            heightDp = (if (current.isResponsive) current.containerHeight else current.height).trim().toInt(),
        )
        _uiState.update { it.copy(adRequest = request) }
    }

    fun onCancelClick() {
        _uiState.update { it.copy(adRequest = null) }
    }

    fun onAdFailed(reason: String) {
        showDialog(HomeDialog.AdLoadFailed(reason))
    }

    fun onDialogDismiss() {
        _uiState.update { it.copy(dialog = null) }
    }

    private fun showDialog(dialog: HomeDialog) {
        _uiState.update { it.copy(dialog = dialog) }
    }

    private fun missingFields(form: HomeForm): List<HomeField> = buildList {
        if (form.adspaceId.isBlank()) add(HomeField.AdspaceId)
        if (form.isResponsive) {
            if (form.containerWidth.toPositiveIntOrNull() == null) add(HomeField.ContainerWidth)
            if (form.containerHeight.toPositiveIntOrNull() == null) add(HomeField.ContainerHeight)
        } else {
            if (form.width.toPositiveIntOrNull() == null) add(HomeField.Width)
            if (form.height.toPositiveIntOrNull() == null) add(HomeField.Height)
        }
    }

    private fun String.toPositiveIntOrNull(): Int? = trim().toIntOrNull()?.takeIf { it > 0 }

    companion object {
        private const val BID_REQUEST_BACKEND_DOMAIN = "https://beta.v2.bg-services.adgeist.ai"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]),
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}

private val HomeFormSaver = listSaver<HomeForm, Any>(
    save = {
        listOf(
            it.packageId,
            it.adgeistAppId,
            it.adspaceId,
            it.width,
            it.height,
            it.isResponsive,
            it.containerWidth,
            it.containerHeight,
        )
    },
    restore = {
        HomeForm(
            packageId = it[0] as String,
            adgeistAppId = it[1] as String,
            adspaceId = it[2] as String,
            width = it[3] as String,
            height = it[4] as String,
            isResponsive = it[5] as Boolean,
            containerWidth = it[6] as String,
            containerHeight = it[7] as String,
        )
    },
)
