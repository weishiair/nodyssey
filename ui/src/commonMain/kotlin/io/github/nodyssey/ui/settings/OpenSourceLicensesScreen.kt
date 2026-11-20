package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.nodyssey.ui.common.appName
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.licenses_app_section
import io.github.nodyssey.ui.resources.licenses_dependencies_section
import io.github.nodyssey.ui.resources.licenses_lockfile_note
import io.github.nodyssey.ui.resources.licenses_summary
import io.github.nodyssey.ui.resources.settings_licenses
import io.github.plaza.designsys.settings.SettingsItem
import io.github.plaza.designsys.settings.SettingsPage
import io.github.plaza.designsys.settings.SettingsSection
import io.github.plaza.designsys.theme.PlazaTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun OpenSourceLicensesScreen(
    onBack: () -> Unit,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appLicense =
        SettingsItem.Link(
            key = "app-license",
            title = appName(),
            description = "GNU General Public License v3.0",
            onClick = { onOpenUri(AppLinks.LICENSE) },
            external = true,
        )
    val dependencyLicenses =
        SHIPPED_LIBRARIES.map { library ->
            SettingsItem.Link(
                key = library.key,
                title = library.name,
                description = library.license,
                onClick = { onOpenUri(library.sourceUrl) },
                external = true,
            )
        }
    val page =
        SettingsPage(
            title = stringResource(Res.string.settings_licenses),
            intro = stringResource(Res.string.licenses_summary),
            sections =
            listOf(
                SettingsSection(
                    key = "application",
                    header = stringResource(Res.string.licenses_app_section),
                    items = listOf(appLicense),
                ),
                SettingsSection(
                    key = "dependencies",
                    header = stringResource(Res.string.licenses_dependencies_section),
                    items = dependencyLicenses,
                    footer = stringResource(Res.string.licenses_lockfile_note),
                ),
            ),
        )
    SettingsPageScreen(
        page = page,
        onBack = onBack,
        modifier = modifier,
        layout = SettingsPageLayout.Licenses,
    )
}

private data class LibraryLicense(
    val key: String,
    val name: String,
    val license: String,
    val sourceUrl: String,
)

// One HTML parser ships now. Every post, list, search result and terms page is read by Ksoup, and so
// is the one call that strips markup to plain text for a user's comment excerpt — that call was the
// last jsoup left in the APK, and step A7 moved its repository into `:shared` with the rest.
private val SHIPPED_LIBRARIES =
    listOf(
        LibraryLicense(
            key = "androidx-jetpack-compose",
            name = "AndroidX · Jetpack Compose",
            license = "Apache License 2.0",
            sourceUrl = "https://android.googlesource.com/platform/frameworks/support",
        ),
        LibraryLicense(
            key = "kotlin-coroutines-serialization",
            name = "Kotlin · Coroutines · Serialization",
            license = "Apache License 2.0",
            sourceUrl = "https://github.com/JetBrains/kotlin",
        ),
        LibraryLicense("okhttp", "OkHttp", "Apache License 2.0", "https://github.com/square/okhttp"),
        LibraryLicense("coil", "Coil", "Apache License 2.0", "https://github.com/coil-kt/coil"),
        LibraryLicense("ksoup", "Ksoup", "MIT License", "https://github.com/fleeksoft/ksoup"),
        LibraryLicense("kotlinx-datetime", "kotlinx-datetime", "Apache License 2.0", "https://github.com/Kotlin/kotlinx-datetime"),
    )

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun OpenSourceLicensesPreview() {
    PlazaTheme { OpenSourceLicensesScreen(onBack = {}, onOpenUri = {}) }
}
