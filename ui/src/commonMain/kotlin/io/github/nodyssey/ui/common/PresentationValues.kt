package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.nodyssey.ui.compare.LocalOpenReportCompare
import io.github.nodyssey.ui.compare.LocalReportCompareBasket
import io.github.nodyssey.ui.compare.LocalReportOrigin
import io.github.nodyssey.ui.composer.LocalImageHostRepository
import io.github.nodyssey.ui.richtext.LocalReportFormat
import io.github.nodyssey.ui.sticker.LocalImageActions
import io.github.nodyssey.ui.sticker.LocalStickerLibrary
import io.github.nodyssey.ui.sticker.LocalStickerNavigation
import io.github.plaza.designsys.component.LocalLinkPrefetcher
import io.github.plaza.designsys.richtext.LocalStickerSizing

/** Captured at the presentation site, so nested sheets keep the page's handlers and repositories. */
@Composable
internal fun appPresentationValues(): Array<ProvidedValue<*>> = arrayOf(
    LocalUriHandler provides LocalUriHandler.current,
    LocalAppName provides LocalAppName.current,
    LocalReadMarkPaint provides LocalReadMarkPaint.current,
    LocalUserNotes provides LocalUserNotes.current,
    LocalOpenNetworkCheck provides LocalOpenNetworkCheck.current,
    LocalOpenReportCompare provides LocalOpenReportCompare.current,
    LocalReportCompareBasket provides LocalReportCompareBasket.current,
    LocalReportOrigin provides LocalReportOrigin.current,
    LocalImageHostRepository provides LocalImageHostRepository.current,
    LocalReportFormat provides LocalReportFormat.current,
    LocalImageActions provides LocalImageActions.current,
    LocalStickerLibrary provides LocalStickerLibrary.current,
    LocalStickerNavigation provides LocalStickerNavigation.current,
    LocalLinkPrefetcher provides LocalLinkPrefetcher.current,
    LocalStickerSizing provides LocalStickerSizing.current,
) + listOfNotNull(LocalViewModelStoreOwner.current?.let { LocalViewModelStoreOwner provides it })
