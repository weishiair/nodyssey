package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/** The native platform owns presentation, dragging, keyboard avoidance and dismissal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlazaModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
    shape: Shape = PlazaSheetDefaults.shape,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    dragHandle: (@Composable () -> Unit)? = { PlazaSheetDefaults.DragHandle() },
    nativeExpandFully: Boolean = false,
    dismissEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (nativeModalBottomSheet(onDismissRequest, modifier, nativeExpandFully, dismissEnabled, dragHandle != null, content)) return
    ModalBottomSheet(onDismissRequest, modifier, sheetState = sheetState, shape = shape, containerColor = containerColor, dragHandle = dragHandle, content = content)
}

@Composable
internal expect fun nativeModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    expandFully: Boolean,
    dismissEnabled: Boolean,
    showDragHandle: Boolean,
    content: @Composable ColumnScope.() -> Unit,
): Boolean
