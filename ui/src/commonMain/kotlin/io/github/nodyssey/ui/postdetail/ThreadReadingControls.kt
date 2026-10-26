package io.github.nodyssey.ui.postdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.common.PlazaSheet
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.page_jump_go
import io.github.nodyssey.ui.resources.page_jump_latest
import io.github.nodyssey.ui.resources.page_jump_resume_title
import io.github.nodyssey.ui.resources.post_floor_entry
import io.github.nodyssey.ui.resources.post_floor_jump
import io.github.nodyssey.ui.resources.post_floor_position
import io.github.nodyssey.ui.resources.post_floor_range
import io.github.nodyssey.ui.resources.post_open_original
import io.github.nodyssey.ui.resources.post_reading_original
import io.github.nodyssey.ui.resources.post_reading_original_position
import io.github.nodyssey.ui.resources.post_reply_action
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.LocalEinkMode
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import org.jetbrains.compose.resources.stringResource

/** The position and reply share a fixed row; scrolling never moves either control. */
@Composable
internal fun ThreadReadingControls(
    currentFloor: Int,
    lastFloor: Int?,
    onJump: () -> Unit,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layers = LocalPlazaLayers.current
    val einkMode = LocalEinkMode.current
    val positionLabel = if (currentFloor == 0) {
        stringResource(Res.string.post_reading_original_position)
    } else {
        stringResource(Res.string.post_floor_position, currentFloor)
    }
    Box(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).heightIn(min = 48.dp)) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 60.dp).align(Alignment.Center),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = onJump,
                modifier = Modifier.widthIn(min = 104.dp).heightIn(min = 48.dp)
                    .semantics { contentDescription = positionLabel },
                shape = CircleShape,
                color = layers.raised,
                shadowElevation = if (einkMode) 0.dp else 2.dp,
            ) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (currentFloor == 0) stringResource(Res.string.post_reading_original) else "$currentFloor / ${lastFloor ?: "…"}",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
                        maxLines = 1,
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = onReply,
            modifier = Modifier.align(Alignment.CenterEnd).size(48.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = if (einkMode) 0.dp else 2.dp),
        ) {
            Icon(PlazaIcons.Reply, stringResource(Res.string.post_reply_action), Modifier.size(24.dp))
        }
    }
}

/** The thread exposes floors; its transport's page selector stays out of the reading controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadFloorJumpSheet(
    currentFloor: Int,
    maxFloor: Int,
    onDismiss: () -> Unit,
    onGo: (Int) -> Unit,
    onOriginal: () -> Unit,
    onLatest: () -> Unit,
    onResume: (() -> Unit)?,
) {
    var input by rememberSaveable { mutableStateOf(currentFloor.toString()) }
    val target = input.toIntOrNull()?.takeIf { it in 0..maxFloor }
    val invalid = input.isNotEmpty() && target == null
    val title = stringResource(Res.string.post_floor_jump)
    PlazaSheet(
        onDismiss = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp).semantics { paneTitle = title },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter(Char::isDigit).take(10) },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(Res.string.post_floor_entry)) },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) ({ Text(stringResource(Res.string.post_floor_range, maxFloor)) }) else null,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { target?.let(onGo) }),
                )
                FilledTonalButton(onClick = { target?.let(onGo) }, enabled = target != null) {
                    Text(stringResource(Res.string.page_jump_go))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onOriginal, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.post_open_original))
                }
                FilledTonalButton(onClick = onLatest, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.page_jump_latest))
                }
            }
            if (onResume != null) {
                TextButton(onClick = onResume, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(Res.string.page_jump_resume_title))
                }
            }
        }
    }
}
