package io.github.nodyssey.ui.mail

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberMailAttachmentSaver(
    canSave: () -> Boolean,
    onResult: (Boolean?) -> Unit,
): ((String, ByteArray) -> Boolean)?
