@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.nodyssey.ios

import io.github.nodyssey.data.createPreferenceDataStore
import io.github.nodyssey.di.AppContainer
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
    check(
        files.setAttributes(
            mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication),
            ofItemAtPath = directory,
            error = null,
        ),
    )
    check(
        NSURL.fileURLWithPath(directory, isDirectory = true).setResourceValue(
            NSNumber(bool = true),
            forKey = NSURLIsExcludedFromBackupKey,
            error = null,
        ),
    )
})

internal suspend fun <T> AppContainer.withAccountWork(block: suspend () -> T): T =
    accountRepository?.runWork(block) ?: block()
