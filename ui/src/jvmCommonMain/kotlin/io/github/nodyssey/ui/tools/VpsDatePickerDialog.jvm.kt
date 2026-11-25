package io.github.nodyssey.ui.tools

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun VpsDatePickerDialog(
    title: String,
    initialDate: LocalDate?,
    fallbackDate: LocalDate,
    confirmText: String,
    cancelText: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = initialDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
        initialDisplayedMonthMillis = (initialDate ?: fallbackDate).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { onConfirm(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date) }
            }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelText) } },
    ) {
        DatePicker(
            state = picker,
            title = { Text(title, Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
        )
    }
}
