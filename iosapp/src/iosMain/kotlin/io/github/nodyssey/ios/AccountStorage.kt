@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.nodyssey.ios

import io.github.nodyssey.data.createPreferenceDataStore
import io.github.nodyssey.data.session.AccountNativeFailure
import io.github.nodyssey.di.AppContainer
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey

/**
 * Uses iOS Data Protection encryption and excludes the directory from backups. Keychain items
 * require entitlements absent from this app's self-signed installs; see KeychainLegacySecrets.
 * The protection belongs to the directory because DataStore atomically replaces its file.
 */
internal fun protectedAccountStore() = createPreferenceDataStore("accounts", keepOutOfBackup = true, prepareDirectory = { directory ->
    val files = NSFileManager.defaultManager
    memScoped {
        val failure = alloc<ObjCObjectVar<NSError?>>()
        failure.value = null
        if (!files.setAttributes(
                mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication),
                ofItemAtPath = directory,
                error = failure.ptr,
            )
        ) {
            throw AccountNativeFailure(failure.value?.domain.orEmpty(), failure.value?.code?.toLong())
        }
        failure.value = null
        if (!NSURL.fileURLWithPath(directory, isDirectory = true).setResourceValue(
                NSNumber(bool = true),
                forKey = NSURLIsExcludedFromBackupKey,
                error = failure.ptr,
            )
        ) {
            throw AccountNativeFailure(failure.value?.domain.orEmpty(), failure.value?.code?.toLong())
        }
    }
})

internal suspend fun <T> AppContainer.withAccountWork(block: suspend () -> T): T =
    accountRepository?.runWork(block) ?: block()
