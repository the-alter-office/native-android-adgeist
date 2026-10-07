package com.examplecomposeapp.ui.components.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adgeistkit.ads.AdListener
import com.adgeistkit.ads.AdView
import com.adgeistkit.ads.AdgeistEvent
import com.examplecomposeapp.R
import com.examplecomposeapp.ui.components.SectionLabel

@Stable
class AdEventLog(private val text: MutableState<String>) {

    val value: String get() = text.value

    fun append(line: String) {
        text.value = if (text.value.isEmpty()) line else "${text.value}\n\n$line"
    }

    fun listener(tag: String? = null): (AdView) -> AdListener = {
        val prefix = tag?.let { "[$it] " }.orEmpty()
        object : AdListener() {
            override fun onAdEvent(event: AdgeistEvent) {
                append("$prefix$event")
            }
        }
    }
}

@Composable
fun rememberAdEventLog(): AdEventLog {
    val text = rememberSaveable { mutableStateOf("") }
    return remember(text) { AdEventLog(text) }
}

@Composable
fun AdEventLogPanel(log: AdEventLog, modifier: Modifier = Modifier) {
    SectionLabel(stringResource(R.string.ad_event_log_title), modifier.padding(top = 24.dp))
    Text(
        text = log.value,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            .padding(12.dp),
    )
}
