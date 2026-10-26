package io.github.nodyssey.ios

import io.github.nodyssey.data.dns.DnsResolution
import io.github.nodyssey.data.dns.DnsResolutionTester
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.remoteAddressOf
import io.github.plaza.core.runCatchingExceptCancellation
import platform.Foundation.NSURLSession

/** Probe the selected native transport and report its connected address; proxy/off keeps URLSession. */
class IosDnsResolutionTester(
    private val session: () -> NSURLSession,
    private val url: String,
    private val host: String,
    private val clock: AppClock,
    private val encrypted: suspend () -> String?,
    private val usesEncrypted: suspend () -> Boolean,
) : DnsResolutionTester {
    override suspend fun resolve(): Result<DnsResolution> =
        runCatchingExceptCancellation {
            val started = clock.nowMillis()
            val address = if (usesEncrypted()) encrypted() else session().remoteAddressOf(url)
            DnsResolution(
                host = host,
                addresses = listOfNotNull(address),
                elapsedMillis = clock.nowMillis() - started,
            )
        }
}
