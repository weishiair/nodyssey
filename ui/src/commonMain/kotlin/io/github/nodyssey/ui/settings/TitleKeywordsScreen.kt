package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.model.TitleKeywordKind
import io.github.nodyssey.ui.common.withoutBottom
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.keywords_add_action
import io.github.nodyssey.ui.resources.keywords_add_placeholder
import io.github.nodyssey.ui.resources.keywords_alert_footer
import io.github.nodyssey.ui.resources.keywords_alert_footer_no_polling
import io.github.nodyssey.ui.resources.keywords_alert_title
import io.github.nodyssey.ui.resources.keywords_block_footer
import io.github.nodyssey.ui.resources.keywords_block_title
import io.github.nodyssey.ui.resources.keywords_empty
import io.github.nodyssey.ui.resources.keywords_remove
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.PlazaFieldDefaults
import io.github.plaza.designsys.component.SectionNote
import io.github.plaza.designsys.component.groupedRowTitleStyle
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.nativeChrome
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

@Composable
fun TitleKeywordsRoute(
    viewModel: TitleKeywordsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TitleKeywordsScreen(
        state = state,
        onBack = onBack,
        onInputChange = viewModel::onInputChange,
        onAdd = viewModel::add,
        onRemove = viewModel::remove,
        modifier = modifier,
    )
}

/** The field to add a word, the words, and one line on what they do and where they live. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleKeywordsScreen(
    state: TitleKeywordsUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    backgroundPollingSupported: Boolean = platformHasBackgroundNotificationPolling,
) {
    val layers = LocalPlazaLayers.current
    val appBarState = rememberOneHandAppBarState()
    val alerts = state.kind == TitleKeywordKind.ALERT
    Scaffold(
        modifier = if (layers.nativeChrome) modifier else modifier.nestedScroll(appBarState.nestedScrollConnection),
        containerColor = settingsPageColor(),
        topBar = {
            SettingsTopBar(
                title = stringResource(if (alerts) Res.string.keywords_alert_title else Res.string.keywords_block_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back),
                appBarState = appBarState,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding.withoutBottom())
                .consumeWindowInsets(padding.withoutBottom())
                .imePadding()
                .fillMaxSize()
                .then(if (layers.nativeChrome) Modifier.clipToBounds() else Modifier)
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(settingsScrollPadding(padding)),
            verticalArrangement = Arrangement.spacedBy(SettingsItemGap),
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.keywords_add_placeholder)) },
                shape = PlazaFieldDefaults.shape,
                colors = PlazaFieldDefaults.colors(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (state.input.isNotBlank()) onAdd() }),
                trailingIcon = {
                    TextButton(
                        onClick = onAdd,
                        enabled = state.input.isNotBlank(),
                        modifier = Modifier.padding(end = Spacing.xs),
                    ) {
                        Text(stringResource(Res.string.keywords_add_action))
                    }
                },
            )
            if (state.keywords.isEmpty()) {
                SettingsGroup {
                    GroupedListItem(
                        first = true,
                        last = true,
                        headlineContent = {
                            Text(
                                stringResource(Res.string.keywords_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            } else {
                SettingsGroup {
                    state.keywords.forEachIndexed { index, keyword ->
                        GroupedListItem(
                            first = index == 0,
                            last = index == state.keywords.lastIndex,
                            headlineContent = {
                                Text(
                                    keyword,
                                    style = groupedRowTitleStyle(),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { onRemove(keyword) }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.keywords_remove, keyword))
                                }
                            },
                        )
                    }
                }
            }
            SectionNote(
                stringResource(
                    when {
                        !alerts -> Res.string.keywords_block_footer
                        backgroundPollingSupported -> Res.string.keywords_alert_footer
                        else -> Res.string.keywords_alert_footer_no_polling
                    },
                ),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun TitleKeywordsPreview() {
    PlazaTheme {
        TitleKeywordsScreen(
            state = TitleKeywordsUiState(kind = TitleKeywordKind.BLOCK, keywords = listOf("这是关键词预览")),
            onBack = {},
            onInputChange = {},
            onAdd = {},
            onRemove = {},
        )
    }
}
