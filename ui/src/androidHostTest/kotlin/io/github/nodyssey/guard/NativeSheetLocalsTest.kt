package io.github.nodyssey.guard

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Every composition local reaches the content of a native iOS sheet, or is kept out on purpose.
 *
 * On iOS 26+ `AdaptiveModalSheet` hosts its content in a separate `ComposeUIViewController`, a
 * composition that inherits nothing from the screen that opened it, so each local has to be carried
 * across by name: `:designsys`'s in `AdaptiveModalSheet.ios.kt`, `:ui`'s in `nativeSheetLocals()`.
 * Android's Material sheet inherits every local, which is why a forgotten one cannot be caught on
 * Android or in review: the content reads the local's default on iOS alone — a sticker panel with
 * no library, a report with no origin to compare against.
 *
 * So the lists are checked against the declarations. A new local fails here until it is forwarded,
 * or added below with the reason it must stay behind.
 */
class NativeSheetLocalsTest {
    private val uiNotForwarded =
        mapOf(
            // The shared-element transition between list and thread. Its scope belongs to the
            // presenting composition's layout; the others only mean anything beside that scope.
            "LocalThreadTransition" to "transition scope of the presenting composition",
            "LocalThreadSharedContentConfig" to "transition scope of the presenting composition",
            "LocalThreadMatches" to "transition scope of the presenting composition",
            "LocalThreadSharedReturnEnabled" to "transition scope of the presenting composition",
            "LocalThreadBackGestureInProgress" to "transition scope of the presenting composition",
            // The tab bar under the presenting screen. The sheet covers it and keeps its own safe area.
            "LocalFloatingBottomBarHeight" to "tab bar under the presenting screen",
            "LocalFixedBottomBarHeight" to "tab bar under the presenting screen",
            "LocalBottomBarSafeAreaOverlap" to "tab bar under the presenting screen",
            "LocalKeepBottomBarVisibleOnScroll" to "tab bar under the presenting screen",
        )

    private val designsysNotForwarded =
        mapOf(
            // Bound to a selection container in the presenting composition.
            "LocalTextSelectionRegistry" to "selection container of the presenting composition",
            "LocalRichSelectionState" to "selection container of the presenting composition",
            // Provided by the component around its own children, inside whichever composition draws it.
            "LocalInGroupCard" to "set by a grouped card around its own rows",
            "LocalRichLinkLongClick" to "set by each RichContent around its own text",
        )

    @Test
    fun `every ui local is forwarded to native sheets or excluded with a reason`() {
        val root = repositoryRoot()
        check(
            declaredIn = File(root, "ui"),
            forwardingFile = File(root, "ui/src/commonMain/kotlin/io/github/nodyssey/ui/common/NativeSheetLocals.kt"),
            notForwarded = uiNotForwarded,
        )
    }

    @Test
    fun `every designsys local is forwarded to native sheets or excluded with a reason`() {
        val root = repositoryRoot()
        check(
            declaredIn = File(root, "designsys"),
            forwardingFile =
            File(root, "designsys/src/iosMain/kotlin/io/github/plaza/designsys/component/AdaptiveModalSheet.ios.kt"),
            notForwarded = designsysNotForwarded,
        )
    }

    @Test
    fun `the app sheet provides the ui locals to its native content`() {
        val sheet = File(repositoryRoot(), "ui/src/commonMain/kotlin/io/github/nodyssey/ui/common/PlazaSheet.kt")
        val code = codeOnly(sheet.readText())
        assertTrue("PlazaSheet no longer reads nativeSheetLocals()", "nativeSheetLocals()" in code)
        assertTrue("PlazaSheet no longer provides the forwarded locals", "CompositionLocalProvider(*sheetLocals)" in code)
    }

    private fun check(declaredIn: File, forwardingFile: File, notForwarded: Map<String, String>) {
        val declared =
            productionSources(declaredIn)
                // Only what an iOS composition can see: an Android-only local never reaches the sheet.
                .filter { file -> file.relativeTo(declaredIn).invariantSeparatorsPath.split('/')[1] in IOS_SOURCE_SETS }
                .flatMap { file -> DECLARATION.findAll(codeOnly(file.readText())).map { it.groupValues[1] } }
                .toSortedSet()
        val forwarded = PROVIDES.findAll(codeOnly(forwardingFile.readText())).map { it.groupValues[1] }.toSortedSet()
        assertTrue("found no composition locals under $declaredIn — the guard is scanning nothing", declared.isNotEmpty())

        val missing = declared - forwarded - notForwarded.keys
        val both = forwarded intersect notForwarded.keys
        val stale = notForwarded.keys - declared
        if (missing.isNotEmpty() || both.isNotEmpty() || stale.isNotEmpty()) {
            fail(
                buildString {
                    if (missing.isNotEmpty()) {
                        appendLine("Native iOS sheets would read these locals as their defaults:")
                        missing.forEach { appendLine("  $it") }
                        appendLine("Forward them in ${forwardingFile.name}, or exclude them in NativeSheetLocalsTest with the reason.")
                    }
                    if (both.isNotEmpty()) appendLine("Both forwarded and excluded: $both")
                    if (stale.isNotEmpty()) appendLine("Excluded but no longer declared: $stale")
                },
            )
        }
    }

    private companion object {
        val DECLARATION = Regex("""\bval\s+(Local\w+)\b[^=]*=\s*(?:staticCompositionLocalOf|compositionLocalOf)\b""")
        val IOS_SOURCE_SETS = setOf("commonMain", "nativeMain", "appleMain", "iosMain")
        val PROVIDES = Regex("""\b(Local\w+)\s+provides\b""")
    }
}
