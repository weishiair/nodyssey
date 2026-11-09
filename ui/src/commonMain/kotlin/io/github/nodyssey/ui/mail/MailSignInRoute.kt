package io.github.nodyssey.ui.mail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.nodyssey.core.SeekMailSite
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.ui.login.WebSessionCapture
import io.github.plaza.core.net.UserAgent

@Composable
expect fun MailSignInRoute(
    session: SessionRepository,
    userAgent: UserAgent,
    onToken: (String) -> Unit,
    onClose: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier,
)

/**
 * Clear the previous mail identity before each explicit authorization. Forum logout clears cookies,
 * not another origin's localStorage; silently importing its old token could reconnect another user.
 * evaluateJavaScript runs in the main frame. No JavaScript interface is exposed to email frames.
 */
internal fun mailSessionCapture(onToken: (String) -> Unit): WebSessionCapture = WebSessionCapture(
    isOrigin = SeekMailSite::isMailUrl,
    prepareScript = """
        (() => {
            if (location.origin !== 'https://seek.li') return false;
            localStorage.removeItem('token');
            location.replace('/login');
            return true;
        })()
    """.trimIndent(),
    readScript = """
        (() => {
            if (location.origin !== 'https://seek.li') return null;
            return localStorage.getItem('token');
        })()
    """.trimIndent(),
    onToken = onToken,
)
