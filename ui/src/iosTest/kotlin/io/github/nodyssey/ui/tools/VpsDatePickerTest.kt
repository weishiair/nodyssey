package io.github.nodyssey.ui.tools

import kotlinx.datetime.LocalDate
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import kotlin.test.Test
import kotlin.test.assertEquals

class VpsDatePickerTest {
    @Test
    fun `calendar days round trip at UTC midnight`() {
        // Leap day and the US spring and autumn clock changes.
        for ((day, midnight) in listOf("2024-02-29" to 1709164800.0, "2024-03-10" to 1710028800.0, "2024-11-03" to 1730592000.0)) {
            val date = LocalDate.parse(day)
            val selected = date.toVpsPickerDate()
            assertEquals(midnight, selected.timeIntervalSince1970)
            assertEquals(date, selected.toVpsLocalDate())
            assertEquals(date, NSDate.dateWithTimeIntervalSince1970(midnight + 86_399).toVpsLocalDate())
        }
    }
}
