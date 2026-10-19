package io.github.nodyssey.ui.postdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.common.PlazaSheet
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_share
import io.github.nodyssey.ui.resources.page_jump_go
import io.github.nodyssey.ui.resources.page_jump_latest
import io.github.nodyssey.ui.resources.page_jump_resume_title
import io.github.nodyssey.ui.resources.post_collect_action
import io.github.nodyssey.ui.resources.post_collected_action
import io.github.nodyssey.ui.resources.post_floor_entry
import io.github.nodyssey.ui.resources.post_floor_jump
import io.github.nodyssey.ui.resources.post_floor_position
import io.github.nodyssey.ui.resources.post_floor_range
import io.github.nodyssey.ui.resources.post_only_author
import io.github.nodyssey.ui.resources.post_open_original
import io.github.nodyssey.ui.resources.post_reply_action
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.LocalEinkMode
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import org.jetbrains.compose.resources.stringResource

/** Overlays the list: opening the tools never resizes the reading viewport. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ThreadReadingControls(
    currentFloor: Int,
    lastFloor: Int?,
    expanded: Boolean,
    onlyAuthor: Boolean,
    collected: Boolean?,
    collectPending: Boolean,
    onJump: () -> Unit,
    onOriginal: () -> Unit,
    onToggleOnlyAuthor: () -> Unit,
    onCollect: () -> Unit,
    onShare: () -> Unit,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layers = LocalPlazaLayers.current
    val einkMode = LocalEinkMode.current
    val positionLabel = stringResource(Res.string.post_floor_position, currentFloor)
    val capsuleTarget = if (expanded) 78.dp else 4.dp
    val capsuleBottom by animateDpAsState(
        targetValue = capsuleTarget,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "reading-position",
    )
    Box(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).height(132.dp)) {
        // The toolbar's FAB overload grows its collapsed FAB to 80dp and ties both anchors together.
        // Separate native components keep the reply at 56dp and the counter centered independently.
        // Remove this split if the overload gains a fixed FAB size and an independent counter slot.
        AnimatedVisibility(
            visible = expanded,
            modifier = Modifier.align(Alignment.BottomStart).padding(end = 72.dp),
            enter = FloatingToolbarDefaults.verticalEnterTransition(Alignment.Bottom),
            exit = FloatingToolbarDefaults.verticalExitTransition(Alignment.Bottom),
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                contentPadding = PaddingValues(horizontal = 4.dp),
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors().copy(
                    toolbarContainerColor = layers.card,
                    toolbarContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                IconButton(onClick = onOriginal) {
                    Icon(PlazaIcons.VerticalAlignTop, stringResource(Res.string.post_open_original), Modifier.size(21.dp))
                }
                IconToggleButton(checked = onlyAuthor, onCheckedChange = { onToggleOnlyAuthor() }) {
                    Icon(PlazaIcons.PersonSearch, stringResource(Res.string.post_only_author), Modifier.size(21.dp))
                }
                IconButton(onClick = onCollect, enabled = collected != null && !collectPending) {
                    Icon(
                        if (collected == true) PlazaIcons.Bookmark else PlazaIcons.BookmarkBorder,
                        stringResource(if (collected == true) Res.string.post_collected_action else Res.string.post_collect_action),
                        Modifier.size(21.dp),
                    )
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, stringResource(Res.string.action_share), Modifier.size(21.dp))
                }
            }
        }
        Surface(
            onClick = onJump,
            modifier = Modifier.align(Alignment.BottomCenter)
                .offset { IntOffset(0, -(if (einkMode) capsuleTarget else capsuleBottom).roundToPx()) }
                .widthIn(min = 104.dp)
                .heightIn(min = 44.dp)
                .semantics { contentDescription = positionLabel },
            shape = CircleShape,
            color = layers.card,
            shadowElevation = if (einkMode) 0.dp else 2.dp,
        ) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(
                    "$currentFloor / ${lastFloor ?: "…"}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
                    maxLines = 1,
                )
            }
        }
        FloatingActionButton(
            onClick = onReply,
            modifier = Modifier.align(Alignment.BottomEnd).size(56.dp),
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
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
