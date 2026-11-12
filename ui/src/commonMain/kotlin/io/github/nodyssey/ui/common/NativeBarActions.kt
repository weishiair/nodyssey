package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.plaza.designsys.component.PlatformBarAction
import io.github.plaza.designsys.component.PlatformBarSearch
import io.github.plaza.designsys.component.PlatformTopAppBar
import io.github.plaza.designsys.component.publishPlatformTopAppBar
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun backBarAction(onBack: () -> Unit): PlatformBarAction =
    PlatformBarAction(
        label = stringResource(Res.string.action_back),
        systemImage = "chevron.backward",
        onClick = onBack,
    )

@Composable
internal fun nativeTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: List<PlatformBarAction> = emptyList(),
    navigation: PlatformBarAction? = null,
    search: PlatformBarSearch? = null,
    subtitle: String? = null,
    allowsNavigationPop: Boolean = true,
): Boolean = publishPlatformTopAppBar(PlatformTopAppBar(title, subtitle, navigation ?: onBack?.let { backBarAction(it) }, actions, search, allowsNavigationPop = allowsNavigationPop))
