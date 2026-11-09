@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package io.github.plaza.core.net

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSPropertyListBinaryFormat_v1_0
import platform.Foundation.NSPropertyListSerialization
import platform.Foundation.base64EncodedStringWithOptions
import kotlin.test.Test
import kotlin.test.assertTrue

/** Temporary investigation: these objects are constructed here and contain no device data. */
class ApplePropertyListInvestigationTest {
    @Test
    fun `inspect synthetic property list shapes`() {
        val fixtures = mapOf(
            "empty" to emptyList<String>(),
            "strings" to listOf("test-only"),
            "dictionary" to listOf(mapOf("Name" to "session", "Value" to "test-only")),
            "date" to listOf(mapOf("Expires" to NSDate())),
        )
        val failures = buildList {
            fixtures.forEach { (label, fixture) ->
                memScoped {
                    val failure = alloc<ObjCObjectVar<NSError?>>()
                    failure.value = null
                    val data = NSPropertyListSerialization.dataWithPropertyList(
                        fixture,
                        format = NSPropertyListBinaryFormat_v1_0,
                        options = 0u,
                        error = failure.ptr,
                    )
                    if (data == null) {
                        add("$label encode: ${failure.value}")
                    } else {
                        failure.value = null
                        val decoded = NSPropertyListSerialization.propertyListWithData(data, options = 0u, format = null, error = failure.ptr)
                        if (decoded == null) add("$label decode: ${failure.value}; syntheticPlist=${data.base64EncodedStringWithOptions(0u)}")
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
