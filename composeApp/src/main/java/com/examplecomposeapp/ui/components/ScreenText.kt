package com.examplecomposeapp.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.titleLarge, modifier = modifier)
}

@Composable
fun ScreenDescription(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.padding(top = 8.dp),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, modifier = modifier)
}

@Composable
fun SectionDivider(modifier: Modifier = Modifier) {
    Divider(
        modifier = modifier.padding(top = 24.dp, bottom = 16.dp),
        thickness = 2.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
