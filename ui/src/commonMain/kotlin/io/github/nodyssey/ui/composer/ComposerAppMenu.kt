package io.github.nodyssey.ui.composer

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.composer_app_menu
import io.github.nodyssey.ui.resources.composer_insert_stardust
import io.github.nodyssey.ui.resources.composer_insert_vote
import io.github.plaza.designsys.component.AdaptiveNativeMenu
import io.github.plaza.designsys.component.GlassMenuAction
import io.github.plaza.designsys.component.PlazaIcons
import org.jetbrains.compose.resources.stringResource

/**
 * The editors' APP menu: the things NodeSeek lets a post embed rather than merely say.
 *
 * The site keeps both behind one entry in its own editor, and so does this — two keys on a strip that
 * already scrolls would be two more things to swipe past, and neither is reached mid-sentence.
 */
@Composable
fun ComposerAppMenu(
    onInsertVote: () -> Unit,
    onInsertStardust: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val menuLabel = stringResource(Res.string.composer_app_menu)
    val voteLabel = stringResource(Res.string.composer_insert_vote)
    val stardustLabel = stringResource(Res.string.composer_insert_stardust)

    AdaptiveNativeMenu(
        label = menuLabel,
        actions = listOf(
            GlassMenuAction(label = voteLabel, onClick = onInsertVote, symbol = "chart.bar"),
            GlassMenuAction(label = stardustLabel, onClick = onInsertStardust, symbol = "qrcode"),
        ),
        modifier = modifier,
        cooperativeInScroll = true,
        fallback = { expanded, onDismiss ->
            DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
                DropdownMenuItem(
                    text = { Text(voteLabel) },
                    leadingIcon = { Icon(PlazaIcons.Poll, contentDescription = null) },
                    onClick = {
                        onDismiss()
                        onInsertVote()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stardustLabel) },
                    leadingIcon = { Icon(PlazaIcons.QrCode, contentDescription = null) },
                    onClick = {
                        onDismiss()
                        onInsertStardust()
                    },
                )
            }
        },
        trigger = { onClick ->
            IconButton(onClick = onClick, modifier = Modifier.size(KEY_SIZE), shape = MaterialTheme.shapes.medium) {
                Icon(
                    PlazaIcons.Apps,
                    contentDescription = menuLabel,
                    modifier = Modifier.size(KEY_SIZE / 2),
                )
            }
        },
    )
}

/** The quick bar's key size, so this button measures the same as the keys beside it. */
private val KEY_SIZE = 40.dp
