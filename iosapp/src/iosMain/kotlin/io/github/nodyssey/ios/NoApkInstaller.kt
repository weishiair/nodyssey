package io.github.nodyssey.ios

import io.github.plaza.core.update.ApkInstaller

/** iOS packages are distributed as unsigned IPAs; installation is handled by the user's signing tool. */
internal object NoApkInstaller : ApkInstaller {
    override fun canInstallPackages(): Boolean = false

    override suspend fun install(apkPath: String): Boolean = false
}
