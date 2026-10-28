package io.github.nodyssey.core.report

import io.github.nodyssey.core.html.Fixtures
import io.github.plaza.core.ansi.AnsiDecoder
import kotlin.test.Test
import kotlin.test.assertEquals

class QualityReportPartsTest {
    private val ipv4 = Fixtures.load("reports/ip-quality.txt").replace("\r\n", "\n").trimEnd()
    private val ipv6 = ipv4.replace("86.53.*.*", "2402:6760:f000:*:*:*:*:*")

    @Test
    fun `two IP reports in one fence keep separate targets sections and source`() {
        val expected = checkNotNull(QualityReportParser.parse(ipv4))
        for (newline in listOf("\n", "\r\n")) {
            val first = ipv4.replace("\n", newline)
            val second = ipv6.replace("\n", newline)
            val parts = QualityReportParser.parseAll("$first$newline$newline$second")

            assertEquals(listOf("86.53.*.*", "2402:6760:f000:*:*:*:*:*"), parts.map { it.report.target })
            assertEquals(listOf(first, second), parts.map { it.source })
            parts.forEach { part ->
                assertEquals(expected.sections, part.report.sections)
                assertEquals(expected.footnotes, part.report.footnotes)
            }
        }
    }

    @Test
    fun `second report colour offsets are rebased and cannot bleed from the first report`() {
        val decoded = AnsiDecoder.decode("\u001B[31m$ipv4\u001B[0m\n\n\u001B[32m$ipv6\u001B[0m")
        val parts = QualityReportParser.parseAll(decoded.text, decoded.spans)

        assertEquals(2, parts.size)
        assertEquals(listOf(1, 2), parts.map { it.spans.single().fg })
        parts.forEach { part ->
            assertEquals(0, part.spans.single().start)
            assertEquals(part.source.length, part.spans.single().end)
        }
    }
}
