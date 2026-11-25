@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package io.github.nodyssey.ui.tools

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.SystemFont

internal actual val vpsCalculatorFontFamily: FontFamily? =
    FontFamily(
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
            SystemFont("PingFang SC", weight)
        },
    )
