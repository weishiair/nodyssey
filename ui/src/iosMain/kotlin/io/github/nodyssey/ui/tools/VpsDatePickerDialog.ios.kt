@file:OptIn(ExperimentalForeignApi::class)

package io.github.nodyssey.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import io.github.nodyssey.ui.common.topmostViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toNSDate
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDate
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneForSecondsFromGMT
import platform.UIKit.NSFontAttributeName
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonItemStyle
import platform.UIKit.UIColor
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle
import platform.UIKit.UIFont
import platform.UIKit.UIFontMetrics
import platform.UIKit.UIFontWeightSemibold
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UINavigationController
import platform.UIKit.UIPresentationController
import platform.UIKit.UIScrollView
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.UIViewController
import platform.UIKit.bottomAnchor
import platform.UIKit.leadingAnchor
import platform.UIKit.navigationController
import platform.UIKit.navigationItem
import platform.UIKit.presentationController
import platform.UIKit.safeAreaLayoutGuide
import platform.UIKit.sheetPresentationController
import platform.UIKit.systemBackgroundColor
import platform.UIKit.topAnchor
import platform.UIKit.trailingAnchor
import platform.UIKit.translatesAutoresizingMaskIntoConstraints
import platform.UIKit.widthAnchor

internal fun LocalDate.toVpsPickerDate(): NSDate = atStartOfDayIn(TimeZone.UTC).toNSDate()

internal fun NSDate.toVpsLocalDate(): LocalDate = toKotlinInstant().toLocalDateTime(TimeZone.UTC).date

@Composable
internal actual fun VpsDatePickerDialog(
    title: String,
    initialDate: LocalDate?,
    fallbackDate: LocalDate,
    confirmText: String,
    cancelText: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val dismiss by rememberUpdatedState(onDismiss)
    val confirm by rememberUpdatedState(onConfirm)
    DisposableEffect(Unit) {
        val host = topmostViewController()
        if (host == null) {
            dismiss()
            return@DisposableEffect onDispose {}
        }
        val calendar = VpsCalendarController(initialDate ?: fallbackDate) { date ->
            if (date == null) dismiss() else confirm(date)
        }
        val cancelFont = UIFont.fontWithName("PingFangSC-Regular", size = 17.0) ?: UIFont.systemFontOfSize(17.0)
        val titleFont = UIFont.fontWithName("PingFangSC-Semibold", size = 17.0) ?: UIFont.systemFontOfSize(17.0, weight = UIFontWeightSemibold)
        val regular = mapOf<Any?, Any?>(NSFontAttributeName to UIFontMetrics.defaultMetrics.scaledFontForFont(cancelFont))
        val semibold = mapOf<Any?, Any?>(NSFontAttributeName to UIFontMetrics.defaultMetrics.scaledFontForFont(titleFont))
        calendar.title = title
        calendar.navigationItem.leftBarButtonItem =
            UIBarButtonItem(cancelText, UIBarButtonItemStyle.UIBarButtonItemStylePlain, calendar, NSSelectorFromString("cancel")).apply {
                setTitleTextAttributes(regular, forState = UIControlStateNormal)
            }
        calendar.navigationItem.rightBarButtonItem =
            UIBarButtonItem(confirmText, UIBarButtonItemStyle.UIBarButtonItemStyleDone, calendar, NSSelectorFromString("confirm")).apply {
                setTitleTextAttributes(semibold, forState = UIControlStateNormal)
            }
        // The navigation stack owns the calendar, which is both the button target and the swipe delegate.
        val sheet = UINavigationController(rootViewController = calendar)
        sheet.navigationBar.titleTextAttributes = semibold
        sheet.modalPresentationStyle = UIModalPresentationPageSheet
        sheet.overrideUserInterfaceStyle = host.overrideUserInterfaceStyle
        sheet.sheetPresentationController?.apply {
            prefersGrabberVisible = true
            detents = listOf(
                UISheetPresentationControllerDetent.customDetentWithIdentifier(null) { context ->
                    val maximum = requireNotNull(context).maximumDetentValue
                    calendar.fittingHeight?.coerceAtMost(maximum) ?: maximum
                },
                UISheetPresentationControllerDetent.largeDetent(),
            )
        }
        sheet.presentationController?.delegate = calendar
        host.presentViewController(sheet, animated = true, completion = null)
        onDispose { calendar.dispose(sheet) }
    }
}

private const val CALENDAR_PADDING = 12.0

@OptIn(BetaInteropApi::class)
private class VpsCalendarController(
    initialDate: LocalDate,
    private val onResult: (LocalDate?) -> Unit,
) : UIViewController(nibName = null, bundle = null),
    UIAdaptivePresentationControllerDelegateProtocol {
    private var settled = false
    var fittingHeight: Double? = null
        private set

    private val picker = UIDatePicker().apply {
        datePickerMode = UIDatePickerMode.UIDatePickerModeDate
        preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleInline
        val utc = NSTimeZone.timeZoneForSecondsFromGMT(0)
        calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian).apply { timeZone = utc }
        timeZone = utc
        setDate(initialDate.toVpsPickerDate(), animated = false)
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.systemBackgroundColor
        // Keep the full calendar reachable when the available height is smaller than the picker.
        val scroll = UIScrollView()
        view.addSubview(scroll)
        scroll.addSubview(picker)
        scroll.translatesAutoresizingMaskIntoConstraints = false
        picker.translatesAutoresizingMaskIntoConstraints = false
        val safe = view.safeAreaLayoutGuide
        scroll.topAnchor.constraintEqualToAnchor(safe.topAnchor).active = true
        scroll.bottomAnchor.constraintEqualToAnchor(safe.bottomAnchor).active = true
        scroll.leadingAnchor.constraintEqualToAnchor(safe.leadingAnchor, CALENDAR_PADDING).active = true
        scroll.trailingAnchor.constraintEqualToAnchor(safe.trailingAnchor, -CALENDAR_PADDING).active = true
        val content = scroll.contentLayoutGuide
        picker.topAnchor.constraintEqualToAnchor(content.topAnchor, CALENDAR_PADDING).active = true
        picker.bottomAnchor.constraintEqualToAnchor(content.bottomAnchor, -CALENDAR_PADDING).active = true
        picker.leadingAnchor.constraintEqualToAnchor(content.leadingAnchor).active = true
        picker.trailingAnchor.constraintEqualToAnchor(content.trailingAnchor).active = true
        picker.widthAnchor.constraintEqualToAnchor(scroll.frameLayoutGuide.widthAnchor).active = true
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        val height = picker.bounds.useContents { size.height }
        if (height <= 0) return
        val navigation = navigationController ?: return
        val calendarTop = view.convertRect(view.safeAreaLayoutGuide.layoutFrame, toView = navigation.view).useContents { origin.y }
        val required = calendarTop + height + 2 * CALENDAR_PADDING
        if (fittingHeight != required) {
            fittingHeight = required
            navigation.sheetPresentationController?.invalidateDetents()
        }
    }

    @ObjCAction
    fun cancel() = finish(null)

    @ObjCAction
    fun confirm() = finish(picker.date.toVpsLocalDate())

    private fun finish(date: LocalDate?) {
        if (settled) return
        settled = true
        navigationController?.dismissViewControllerAnimated(true, completion = null)
        onResult(date)
    }

    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
        if (settled) return
        settled = true
        onResult(null)
    }

    fun dispose(sheet: UINavigationController) {
        sheet.presentationController?.delegate = null
        if (settled) return
        settled = true
        if (sheet.presentingViewController != null) sheet.dismissViewControllerAnimated(false, completion = null)
    }
}
