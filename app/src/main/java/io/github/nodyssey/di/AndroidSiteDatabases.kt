package io.github.nodyssey.di

import android.content.Context
import io.github.nodyssey.core.Site
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.local.createNodeSeekDatabase

/** One Room handle per site's existing file, retained when its graph is replaced. */
class AndroidSiteDatabases(context: Context) {
    private val context = context.applicationContext
    private val databases = mutableMapOf<Site, NodeSeekDatabase>()

    @Synchronized
    fun get(site: Site): NodeSeekDatabase = databases.getOrPut(site) { createNodeSeekDatabase(context, site) }
}
