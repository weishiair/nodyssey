@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package io.github.plaza.designsys.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.SystemFont

// Han glyphs name PingFang SC at the weight asked for rather than leaving it to the system's
// fallback, which on iOS 16 sometimes hands back the regular face inside a semibold title: Latin
// bold, Chinese regular, in one line. The system face stays first, so Latin text is unchanged.
internal actual val platformTextFontFamily: FontFamily? =
    FontFamily(
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).flatMap { weight ->
            listOf(SystemFont(".AppleSystemUIFont", weight), SystemFont("PingFang SC", weight))
        },
    )
