package io.github.nodyssey.data.session

/** Native storage failure with a fixed domain and number, never userInfo or file paths. */
class AccountNativeFailure(domainName: String, val code: Long?) :
    IllegalStateException(
        "Account storage native operation failed",
    ) {
    val safeDomain: String = domainName.takeIf { it in setOf("NSCocoaErrorDomain", "NSPOSIXErrorDomain", "NSOSStatusErrorDomain") } ?: "Other"
}
