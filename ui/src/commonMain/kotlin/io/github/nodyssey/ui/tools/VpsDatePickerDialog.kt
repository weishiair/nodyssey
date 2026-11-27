package io.github.nodyssey.ui.tools

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate

@Composable
internal expect fun VpsDatePickerDialog(
    title: String,
    initialDate: LocalDate?,
    fallbackDate: LocalDate,
    confirmText: String,
    cancelText: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
)
