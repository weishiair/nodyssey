package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import io.github.nodyssey.ui.compare.LocalOpenReportCompare
import io.github.nodyssey.ui.compare.LocalReportCompareBasket
import io.github.nodyssey.ui.compare.LocalReportOrigin
import io.github.nodyssey.ui.composer.LocalImageHostRepository
import io.github.nodyssey.ui.notifications.LocalSelectedConversationUid
import io.github.nodyssey.ui.notifications.LocalSelectedInteractionId
import io.github.nodyssey.ui.postlist.LocalSelectedPostId
import io.github.nodyssey.ui.richtext.LocalReportFormat
import io.github.nodyssey.ui.sticker.LocalImageActions
import io.github.nodyssey.ui.sticker.LocalStickerLibrary
import io.github.nodyssey.ui.sticker.LocalStickerNavigation

/**
 * The `:ui` locals a native iOS sheet hands on to its content, read where the sheet is opened.
 *
 * On iOS 26+ `AdaptiveModalSheet` presents its content in a composition of its own, which inherits
 * nothing from the screen underneath. `:designsys` forwards the theme and its own locals; this is the
 * `:ui` half. On Android the same content sits in Material's sheet and inherits every local, so a
 * local missing here would read as its default on iOS alone, with nothing failing to say so.
 * `NativeSheetLocalsTest` fails when a local declared in `:ui` is neither listed here nor excluded
 * there with the reason it must stay behind.
 */
@Composable
internal fun nativeSheetLocals(): Array<ProvidedValue<*>> = arrayOf(
    LocalAppName provides LocalAppName.current,
    LocalBottomBarPosition provides LocalBottomBarPosition.current,
    LocalImageActions provides LocalImageActions.current,
    LocalImageHostRepository provides LocalImageHostRepository.current,
    LocalIosPad provides LocalIosPad.current,
    LocalOpenNetworkCheck provides LocalOpenNetworkCheck.current,
    LocalOpenReportCompare provides LocalOpenReportCompare.current,
    LocalReadMarkPaint provides LocalReadMarkPaint.current,
    LocalReportCompareBasket provides LocalReportCompareBasket.current,
    LocalReportFormat provides LocalReportFormat.current,
    LocalReportOrigin provides LocalReportOrigin.current,
    LocalSelectedConversationUid provides LocalSelectedConversationUid.current,
    LocalSelectedInteractionId provides LocalSelectedInteractionId.current,
    LocalSelectedPostId provides LocalSelectedPostId.current,
    LocalStickerLibrary provides LocalStickerLibrary.current,
    LocalStickerNavigation provides LocalStickerNavigation.current,
    LocalUserNotes provides LocalUserNotes.current,
)
