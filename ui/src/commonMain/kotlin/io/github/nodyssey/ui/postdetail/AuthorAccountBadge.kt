package io.github.nodyssey.ui.postdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.post_author_age_days
import io.github.plaza.designsys.component.BadgeChip
import io.github.plaza.designsys.component.BadgeTone
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import org.jetbrains.compose.resources.stringResource

/** Account facts only: the colour identifies the rank, rather than judging the registration age. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun AuthorAccountBadge(level: Int?, days: Int?) {
    val darkTheme = LocalPlazaDarkTheme.current
    val parts = buildList {
        if (level != null) add("Lv $level")
        if (days != null) add(stringResource(Res.string.post_author_age_days, days))
    }
    // Start hidden even for cached facts, so entering the viewport and finishing a lookup use the
    // same reveal. Keeping this state remembered avoids replaying it on ordinary recompositions.
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = parts.isNotEmpty()
    val palette = when {
        level == null || level < 0 -> null
        level <= 1 -> AccountBadgePalette.Red
        level == 2 -> AccountBadgePalette.Orange
        level == 3 -> AccountBadgePalette.Green
        level == 4 -> AccountBadgePalette.Blue
        level == 5 -> AccountBadgePalette.Gold
        else -> AccountBadgePalette.Purple
    }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
            scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = 0.96f),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        label = "author-account-info",
    ) {
        BadgeChip(
            text = parts.joinToString(" | "),
            tone = BadgeTone.Neutral,
            containerBrush = if (darkTheme) palette?.darkBrush else palette?.brush,
            contentColor = (if (darkTheme) palette?.darkContent else palette?.content) ?: Color.Unspecified,
        )
    }
}

/**
 * Rank bands and gradient stops adapted from Woodll's Nodeseek Pro 1.0.8 (GPL-3.0):
 * https://greasyfork.org/scripts/567109-nodeseek-pro/code
 * Dark ink on the lighter gradients keeps the small account facts readable. At night, subdued
 * fills and softer ink keep the same rank families without competing with the reply text.
 */
private enum class AccountBadgePalette(
    start: Color,
    end: Color,
    val content: Color,
    darkStart: Color,
    darkEnd: Color,
    val darkContent: Color,
) {
    Red(Color(0xFFE53935), Color(0xFFB71C1C), Color.White, Color(0xFF3E2929), Color(0xFF342626), Color(0xFFCAA4A2)),
    Orange(Color(0xFFFD9346), Color(0xFFFD512C), Color(0xFF261504), Color(0xFF403126), Color(0xFF352B25), Color(0xFFD0B095)),
    Green(Color(0xFF12EB92), Color(0xFF0BA360), Color(0xFF062819), Color(0xFF273B31), Color(0xFF24322B), Color(0xFFA4C6B2)),
    Blue(Color(0xFF47ABFF), Color(0xFF1860FF), Color.White, Color(0xFF29384B), Color(0xFF273243), Color(0xFFA9C0DE)),
    Gold(Color(0xFFFFD700), Color(0xFFFF8C00), Color(0xFF302000), Color(0xFF403A29), Color(0xFF353125), Color(0xFFCABE9A)),
    Purple(Color(0xFFDB24FF), Color(0xFF2524FF), Color.White, Color(0xFF3A3047), Color(0xFF302B3F), Color(0xFFBFAED2)),
    ;

    val brush: Brush = Brush.linearGradient(listOf(start, end))
    val darkBrush: Brush = Brush.linearGradient(listOf(darkStart, darkEnd))
}
