package io.github.nodyssey.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import io.github.nodyssey.data.session.SignInRefusal
import io.github.nodyssey.ui.common.MediumButton
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.sign_in_email_body
import io.github.nodyssey.ui.resources.sign_in_email_code
import io.github.nodyssey.ui.resources.sign_in_email_label
import io.github.nodyssey.ui.resources.sign_in_email_resend
import io.github.nodyssey.ui.resources.sign_in_email_send
import io.github.nodyssey.ui.resources.sign_in_email_sent
import io.github.nodyssey.ui.resources.sign_in_refused_generic
import io.github.nodyssey.ui.resources.sign_in_session_not_stored
import io.github.nodyssey.ui.resources.sign_in_submit
import io.github.nodyssey.ui.resources.sign_in_use_email
import io.github.nodyssey.ui.resources.sign_in_use_web
import io.github.plaza.designsys.component.InlineBanner
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.paddingWithKeyboard
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmailSignInScreen(
    state: SignInUiState,
    emailState: TextFieldState,
    codeState: TextFieldState,
    resendSeconds: Int,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSend: () -> Unit,
    onSubmit: () -> Unit,
    onUseWebSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    turnstile: @Composable () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = {}, navigationIcon = {
                IconButton(onClick = onBack, enabled = !state.isSubmitting) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back))
                }
            })
        },
    ) { padding ->
        Column(
            Modifier.paddingWithKeyboard(padding).fillMaxSize().readableWidth()
                .verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            AuthHeader(stringResource(Res.string.sign_in_use_email), stringResource(Res.string.sign_in_email_body))
            OutlinedTextField(
                state = emailState,
                enabled = state.isFormEnabled,
                label = { Text(stringResource(Res.string.sign_in_email_label)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            VerificationBlock(state.verification, turnstile)
            MediumButton(onClick = onSend, enabled = state.canRequestEmailCode && resendSeconds == 0, modifier = Modifier.fillMaxWidth()) {
                Text(if (resendSeconds > 0) stringResource(Res.string.sign_in_email_resend, resendSeconds) else stringResource(Res.string.sign_in_email_send))
            }
            state.emailCodeSentTo?.takeIf { it == state.email }?.let {
                Text(stringResource(Res.string.sign_in_email_sent, it), style = MaterialTheme.typography.bodySmall)
            }
            if (state.emailSendRefused) {
                InlineBanner(state.emailMessage ?: stringResource(Res.string.sign_in_refused_generic), icon = PlazaIcons.ErrorCircle)
            }
            OutlinedTextField(
                state = codeState,
                enabled = state.isFormEnabled,
                label = { Text(stringResource(Res.string.sign_in_email_code)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                isError = state.refusal?.reason == SignInRefusal.EmailCode,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.refusal?.reason == SignInRefusal.EmailCode) {
                InlineBanner(state.refusal.detail ?: stringResource(Res.string.sign_in_refused_generic), icon = PlazaIcons.ErrorCircle)
            }
            if (state.sessionNotStored) {
                InlineBanner(stringResource(Res.string.sign_in_session_not_stored), icon = PlazaIcons.ErrorCircle)
            }
            MediumButton(onClick = onSubmit, enabled = state.canSubmitEmailCode, busy = state.isSubmitting, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.sign_in_submit))
            }
            TextButton(onClick = onUseWebSignIn, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.sign_in_use_web)) }
            SignInDiagnosticsAction(state.failureReport)
        }
    }
}
