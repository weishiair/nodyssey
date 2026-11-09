package io.github.nodyssey.ui.mail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.nodyssey.core.SeekMailSite
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.ui.login.WebViewScreen
import io.github.nodyssey.ui.login.rememberWebViewCookieBridge
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.mail_authorize
import io.github.plaza.core.net.UserAgent
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun MailSignInRoute(
    session: SessionRepository,
    userAgent: UserAgent,
    onToken: (String) -> Unit,
    onClose: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier,
) {
    WebViewScreen(
        url = SeekMailSite.LOGIN,
        title = stringResource(Res.string.mail_authorize),
        userAgent = userAgent,
        bridge = rememberWebViewCookieBridge(session),
        onClose = onClose,
        onOpenExternal = onOpenExternal,
        isInScope = SeekMailSite::isAuthorizationUrl,
        sessionCapture = mailSessionCapture(onToken),
        modifier = modifier,
    )
}
