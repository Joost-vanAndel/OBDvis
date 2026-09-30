package com.obdvis.android.ui

import com.obdvis.android.ui.components.AppHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.obdvis.android.ui.theme.Background
import com.obdvis.android.ui.theme.OBDvisTheme
import com.obdvis.android.ui.theme.OnSurface

@Composable
fun ThirdPartyNoticesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val notices = remember(context) {
        context.assets.open(NOTICES_ASSET_NAME).bufferedReader().use { it.readText() }
    }

    OBDvisTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background),
        ) {
            AppHeader("Open-source licenses", onBack = onBack)

            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = notices,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                )
            }
        }
    }
}

private const val NOTICES_ASSET_NAME = "THIRD_PARTY_NOTICES.txt"
