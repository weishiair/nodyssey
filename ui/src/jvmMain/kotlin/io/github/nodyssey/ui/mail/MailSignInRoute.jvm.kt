package io.github.nodyssey.ui.mail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import io.github.nodyssey.core.SeekMailSite
import io.github.nodyssey.data.session.SessionRepository
import io.github.plaza.core.net.UserAgent

@Composable
actual fun MailSignInRoute(
    session: SessionRepository,
    userAgent: UserAgent,
    onToken: (String) -> Unit,
    onClose: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier,
) {
    LaunchedEffect(Unit) {
        onOpenExternal(SeekMailSite.LOGIN)
        onClose()
    }
}
