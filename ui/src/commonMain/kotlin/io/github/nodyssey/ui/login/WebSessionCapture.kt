package io.github.nodyssey.ui.login

/** Read only from the main frame, after the platform and the script both verify its origin. */
internal class WebSessionCapture(
    val isOrigin: (String) -> Boolean,
    val prepareScript: String,
    val readScript: String,
    val onToken: (String) -> Unit,
)
