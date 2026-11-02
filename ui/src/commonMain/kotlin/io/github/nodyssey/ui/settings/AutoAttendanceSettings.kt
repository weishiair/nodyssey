package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import io.github.nodyssey.data.AttendanceMode
import io.github.nodyssey.data.attendance.AutoAttendanceConfig
import io.github.nodyssey.data.attendance.AutoAttendanceFailure
import io.github.nodyssey.data.attendance.AutoAttendanceState
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.auto_attendance_done
import io.github.nodyssey.ui.resources.auto_attendance_done_gain
import io.github.nodyssey.ui.resources.auto_attendance_failed
import io.github.nodyssey.ui.resources.auto_attendance_fixed
import io.github.nodyssey.ui.resources.auto_attendance_hint
import io.github.nodyssey.ui.resources.auto_attendance_limit
import io.github.nodyssey.ui.resources.auto_attendance_mode
import io.github.nodyssey.ui.resources.auto_attendance_network
import io.github.nodyssey.ui.resources.auto_attendance_pending
import io.github.nodyssey.ui.resources.auto_attendance_random
import io.github.nodyssey.ui.resources.auto_attendance_running
import io.github.nodyssey.ui.resources.auto_attendance_sign_in
import io.github.nodyssey.ui.resources.auto_attendance_status
import io.github.nodyssey.ui.resources.auto_attendance_title
import io.github.nodyssey.ui.resources.auto_attendance_verify
import io.github.plaza.designsys.component.GroupedListItemSwitch
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SectionLabel
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AutoAttendanceSettings(
    config: AutoAttendanceConfig,
    state: AutoAttendanceState,
    onEnabledChange: (Boolean) -> Unit,
    onModeChange: (AttendanceMode) -> Unit,
) {
    SectionLabel(stringResource(Res.string.auto_attendance_title))
    SettingsGroup {
        GroupedRow(
            icon = PlazaIcons.EventAvailable,
            title = stringResource(Res.string.auto_attendance_title),
            subtitle = stringResource(Res.string.auto_attendance_hint),
            first = true,
            checked = config.enabled,
            onCheckedChange = onEnabledChange,
            trailing = { GroupedListItemSwitch(checked = config.enabled) },
        )
        val choices = listOf(
            AttendanceMode.RANDOM to stringResource(Res.string.auto_attendance_random),
            AttendanceMode.FIXED_FIVE to stringResource(Res.string.auto_attendance_fixed),
        )
        SettingsMenuRow(
            icon = PlazaIcons.Casino,
            title = stringResource(Res.string.auto_attendance_mode),
            subtitle = choices.first { it.first == config.mode }.second,
            choices = choices,
            selected = config.mode,
            onSelect = onModeChange,
            last = !config.enabled,
        )
        if (config.enabled) {
            val status = when {
                state.running -> stringResource(Res.string.auto_attendance_running)

                state.completed -> state.gain?.let { stringResource(Res.string.auto_attendance_done_gain, it) }
                    ?: stringResource(Res.string.auto_attendance_done)

                state.failure == AutoAttendanceFailure.VERIFICATION -> stringResource(Res.string.auto_attendance_verify)

                state.failure == AutoAttendanceFailure.SIGN_IN -> stringResource(Res.string.auto_attendance_sign_in)

                state.exhausted -> stringResource(Res.string.auto_attendance_limit)

                state.failure == AutoAttendanceFailure.NETWORK -> stringResource(Res.string.auto_attendance_network)

                state.failure != null -> stringResource(Res.string.auto_attendance_failed)

                else -> stringResource(Res.string.auto_attendance_pending)
            }
            GroupedRow(
                icon = PlazaIcons.EventAvailable,
                title = stringResource(Res.string.auto_attendance_status),
                subtitle = status,
                last = true,
                showChevron = false,
            )
        }
    }
}
