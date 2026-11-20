package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A confirmation with a title, an optional message and one or two buttons: Material's AlertDialog on
 * Android and the desktop, the system alert on iOS. Shown while it is in composition; both callbacks
 * must take it out of composition, because the system alert closes itself on any tap.
 * [icon] is drawn by Material only — a system alert has none. [destructive] colours the confirm
 * button as the error / system red; [prominentConfirm] makes it Material's filled button.
 */
@Composable
expect fun AdaptiveAlertDialog(
    title: String,
    message: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
    destructive: Boolean = false,
    prominentConfirm: Boolean = false,
    icon: ImageVector? = null,
)

internal enum class AlertButtonRole { Cancel, Default, Destructive }

internal data class AlertButton(
    val label: String,
    val role: AlertButtonRole,
    val confirms: Boolean,
    val preferred: Boolean,
)

/**
 * Cancel first — UIKit puts it on the leading side of two — and the confirm action bold unless it
 * destroys something, where the safe answer stays the default.
 */
internal fun alertButtons(confirmLabel: String, dismissLabel: String?, destructive: Boolean): List<AlertButton> =
    buildList {
        if (dismissLabel != null) {
            add(AlertButton(dismissLabel, AlertButtonRole.Cancel, confirms = false, preferred = false))
        }
        add(
            AlertButton(
                confirmLabel,
                if (destructive) AlertButtonRole.Destructive else AlertButtonRole.Default,
                confirms = true,
                preferred = !destructive && dismissLabel != null,
            ),
        )
    }
