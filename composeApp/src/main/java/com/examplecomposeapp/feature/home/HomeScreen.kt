package com.examplecomposeapp.feature.home

import android.content.res.Configuration
import android.util.Log
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adgeistkit.ads.AdListener
import com.adgeistkit.ads.AdSize
import com.adgeistkit.ads.AdView
import com.examplecomposeapp.R
import com.examplecomposeapp.ui.components.AdPlaceholder
import com.examplecomposeapp.ui.components.SectionDivider
import com.examplecomposeapp.ui.components.ads.AdSlot
import com.examplecomposeapp.ui.theme.AdgeistTheme

@Composable
fun HomeScreen(
    form: HomeForm,
    uiState: HomeUiState,
    onFormChange: (HomeForm) -> Unit,
    onResponsiveChange: (Boolean) -> Unit,
    onConfigureClick: () -> Unit,
    onGetAdClick: () -> Unit,
    onCancelClick: () -> Unit,
    onAdCreated: (AdView) -> Unit,
    onAdFailed: (String) -> Unit,
    onDialogDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeaderText(R.string.home_sdk_configuration)
        FormField(form.packageId, { onFormChange(form.copy(packageId = it)) }, R.string.home_package_id)
        FormField(form.adgeistAppId, { onFormChange(form.copy(adgeistAppId = it)) }, R.string.home_adgeist_app_id)
        Button(
            onClick = onConfigureClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Text(stringResource(R.string.home_configure_sdk))
        }

        SectionDivider()

        HeaderText(R.string.home_load_advertisement)
        FormField(form.adspaceId, { onFormChange(form.copy(adspaceId = it)) }, R.string.home_adspace_id)
        FormField(
            value = form.width,
            onValueChange = { onFormChange(form.copy(width = it)) },
            label = R.string.home_width,
            numeric = true,
            enabled = !form.isResponsive,
        )
        FormField(
            value = form.height,
            onValueChange = { onFormChange(form.copy(height = it)) },
            label = R.string.home_height,
            numeric = true,
            enabled = !form.isResponsive,
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_responsive_ad),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(end = 8.dp),
            )
            Switch(checked = form.isResponsive, onCheckedChange = onResponsiveChange)
        }

        if (form.isResponsive) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_container_dimensions),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                )
                FormField(
                    value = form.containerWidth,
                    onValueChange = { onFormChange(form.copy(containerWidth = it)) },
                    label = R.string.home_container_width,
                    numeric = true,
                )
                FormField(
                    value = form.containerHeight,
                    onValueChange = { onFormChange(form.copy(containerHeight = it)) },
                    label = R.string.home_container_height,
                    numeric = true,
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(onClick = onGetAdClick, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_get_ad))
            }
            Button(
                onClick = onCancelClick,
                enabled = uiState.adRequest != null,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.home_cancel))
            }
        }

        uiState.adRequest?.let { request ->
            key(request.id) {
                HomeAd(request = request, onAdCreated = onAdCreated, onAdFailed = onAdFailed)
            }
        }
    }

    uiState.dialog?.let { dialog ->
        HomeAlertDialog(dialog = dialog, onDismiss = onDialogDismiss)
    }
}

@Composable
private fun HomeAlertDialog(dialog: HomeDialog, onDismiss: () -> Unit) {
    val title: String
    val message: String
    when (dialog) {
        HomeDialog.InvalidConfiguration -> {
            title = stringResource(R.string.dialog_invalid_configuration_title)
            message = stringResource(R.string.dialog_invalid_configuration_message)
        }
        is HomeDialog.Configured -> {
            title = stringResource(R.string.dialog_configured_title)
            message = stringResource(R.string.dialog_configured_message, dialog.packageId, dialog.adgeistAppId)
        }
        is HomeDialog.AdLoadFailed -> {
            title = stringResource(R.string.dialog_ad_failed_title)
            message = stringResource(R.string.dialog_ad_failed_message, dialog.reason)
        }
        is HomeDialog.InvalidFields -> {
            title = stringResource(R.string.dialog_invalid_fields_title)
            val fields = dialog.fields.map { stringResource(it.label) }.joinToString(", ")
            message = stringResource(R.string.dialog_invalid_fields_message, fields)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
    )
}

@Composable
private fun HomeAd(
    request: HomeAdRequest,
    onAdCreated: (AdView) -> Unit,
    onAdFailed: (String) -> Unit,
) {
    val boxSize = if (request.isResponsive) {
        Modifier.size(request.widthDp.dp, request.heightDp.dp)
    } else {
        Modifier.size(FIXED_AD_WRAPPER_DP.dp)
    }

    AdPlaceholder(
        Modifier
            .padding(top = 56.dp)
            .then(boxSize)
    ) {
        AdSlot(
            configure = {
                adUnitId = request.adUnitId
                if (request.isResponsive) {
                    adIsResponsive = true
                } else {
                    setAdDimension(AdSize(request.widthDp, request.heightDp))
                    reserveSpace = false
                }
            },
            listener = { adView -> homeAdListener(adView, onAdFailed) },
            modifier = Modifier.fillMaxSize(),
            onCreated = onAdCreated,
        )
    }
}

private fun homeAdListener(adView: AdView, onAdFailed: (String) -> Unit) = object : AdListener() {
    override fun onAdLoaded() {
        Log.d(AD_LOG_TAG, "Ad Loaded Successfully!")
        adView.visibility = View.VISIBLE
    }

    override fun onAdFailedToLoad(var1: String) {
        Log.e(AD_LOG_TAG, "Ad Failed to Load ('${adView.adUnitId}'): $var1")
        onAdFailed(var1)
    }

    override fun onAdClicked() {
        Log.d(AD_LOG_TAG, "Ad Clicked")
    }

    override fun onAdOpened() {
        Log.d(AD_LOG_TAG, "Ad Opened")
    }

    override fun onAdClosed() {
        Log.d(AD_LOG_TAG, "Ad Closed")
    }

    override fun onAdWarning(message: String) {
        Log.w(AD_LOG_TAG, message)
    }
}

private const val AD_LOG_TAG = "AdView"
private const val FIXED_AD_WRAPPER_DP = 360

@Composable
private fun HeaderText(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    numeric: Boolean = false,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenPreview() {
    AdgeistTheme {
        Surface {
            HomeScreen(
                form = HomeForm(adspaceId = "6aacd70d7935d686e83fb1aa"),
                uiState = HomeUiState(),
                onFormChange = {},
                onResponsiveChange = {},
                onConfigureClick = {},
                onGetAdClick = {},
                onCancelClick = {},
                onAdCreated = {},
                onAdFailed = {},
                onDialogDismiss = {},
            )
        }
    }
}
