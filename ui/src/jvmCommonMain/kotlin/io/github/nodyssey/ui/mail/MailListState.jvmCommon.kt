package io.github.nodyssey.ui.mail

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberMailListState(): LazyListState = rememberLazyListState()
