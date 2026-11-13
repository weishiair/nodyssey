package io.github.nodyssey.ui.postdetail

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.common.FloatingBottomBarBandHeight
import io.github.nodyssey.ui.common.FloatingBottomBarBottomGap
import io.github.nodyssey.ui.common.FloatingBottomBarTopGap
import io.github.nodyssey.ui.common.FloatingBottomBarVisibility
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
import io.github.plaza.designsys.theme.LocalLiquidGlassBackdrop
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.glassChromeSurface
import org.jetbrains.compose.resources.stringResource

/** Keep the original reading row; glass enters as a whole, like the bottom navigation. */
@Composable
internal fun ThreadReadingControls(
    currentFloor: Int,
    lastFloor: Int?,
    onJump: () -> Unit,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    if (LocalPlazaLayers.current.frostedGlass) {
        val visibility = remember { MutableTransitionState(false) }
        visibility.targetState = visible
        FloatingBottomBarVisibility(visibleState = visibility, modifier = modifier) {
            ThreadReadingControlsRow(currentFloor, lastFloor, onJump, onReply)
        }
    } else if (visible) {
        ThreadReadingControlsRow(currentFloor, lastFloor, onJump, onReply, modifier)
    }
}

@Composable
private fun ThreadReadingControlsRow(
    currentFloor: Int,
    lastFloor: Int?,
    onJump: () -> Unit,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layers = LocalPlazaLayers.current
    val darkTheme = LocalPlazaDarkTheme.current
    val einkMode = LocalEinkMode.current
    val backdrop = LocalLiquidGlassBackdrop.current
    val positionLabel = if (currentFloor == 0) {
        stringResource(Res.string.post_reading_original_position)
    } else {
        stringResource(Res.string.post_floor_position, currentFloor)
    }
    Box(
        modifier.fillMaxWidth().padding(
            start = 12.dp,
            end = 12.dp,
            top = if (layers.frostedGlass) FloatingBottomBarTopGap else 10.dp,
            bottom = if (layers.frostedGlass) FloatingBottomBarBottomGap else 10.dp,
        ).heightIn(min = if (layers.frostedGlass) FloatingBottomBarBandHeight else 48.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 60.dp).align(Alignment.Center),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = onJump,
                modifier = Modifier
                    .widthIn(min = 104.dp)
                    .heightIn(min = 48.dp)
                    .then(
                        if (layers.frostedGlass) {
                            Modifier.glassChromeSurface(
                                layers = layers,
                                darkTheme = darkTheme,
                                shape = CircleShape,
                                elevated = true,
                                backdrop = backdrop,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .semantics { contentDescription = positionLabel },
                shape = CircleShape,
                color = if (layers.frostedGlass) Color.Transparent else layers.raised,
                shadowElevation = if (einkMode || layers.frostedGlass) 0.dp else 2.dp,
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
        val fabShape = RoundedCornerShape(16.dp)
        FloatingActionButton(
            onClick = onReply,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(48.dp)
                .then(
                    if (layers.frostedGlass) {
                        Modifier.glassChromeSurface(
                            layers = layers,
                            darkTheme = darkTheme,
                            shape = fabShape,
                            elevated = true,
                            backdrop = backdrop,
                            surfaceTint =
                            MaterialTheme.colorScheme.primaryContainer.copy(
                                alpha = if (darkTheme) 0.44f else 0.42f,
                            ),
                        )
                    } else {
                        Modifier
                    },
                ),
            shape = fabShape,
            containerColor =
            if (layers.frostedGlass) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation =
            if (layers.frostedGlass) {
                FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
            } else {
                FloatingActionButtonDefaults.elevation(defaultElevation = if (einkMode) 0.dp else 2.dp)
            },
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
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp).semantics { paneTitle = title },
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onOriginal) {
                    Text(stringResource(Res.string.post_open_original))
                }
                TextButton(onClick = onLatest) {
                    Text(stringResource(Res.string.page_jump_latest))
                }
                if (onResume != null) {
                    TextButton(onClick = onResume) {
                        Text(stringResource(Res.string.page_jump_resume_title))
                    }
                }
            }
        }
    }
}
