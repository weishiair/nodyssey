package io.github.nodyssey.di

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import io.github.nodyssey.core.Site
import io.github.nodyssey.data.ReadingHistoryAccounts
import io.github.nodyssey.data.local.ReadMarkEntity
import io.github.nodyssey.data.local.ReadingPositionEntity
import io.github.nodyssey.data.local.UserNoteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SiteStorageRetentionTest {
    @Test
    fun `round trips reuse each sites draft files without overwriting the same post id`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val draftKey = stringPreferencesKey("draft-123")
        val nsReply = context.commentComposerDataStore(Site.NODESEEK)
        val nsPost = context.postComposerDataStore(Site.NODESEEK)
        nsReply.edit { it[draftKey] = "NodeSeek reply" }
        nsPost.edit { it[draftKey] = "NodeSeek post" }
        val dfReply = context.commentComposerDataStore(Site.DEEPFLOOD)
        val dfPost = context.postComposerDataStore(Site.DEEPFLOOD)
        assertNotSame(nsReply, dfReply)
        assertEquals(null, dfReply.data.first()[draftKey])
        dfReply.edit { it[draftKey] = "DeepFlood reply" }
        dfPost.edit { it[draftKey] = "DeepFlood post" }
        repeat(3) {
            assertSame(nsReply, context.commentComposerDataStore(Site.NODESEEK))
            assertEquals("NodeSeek reply", context.commentComposerDataStore(Site.NODESEEK).data.first()[draftKey])
            assertEquals("NodeSeek post", context.postComposerDataStore(Site.NODESEEK).data.first()[draftKey])
            assertEquals("DeepFlood reply", context.commentComposerDataStore(Site.DEEPFLOOD).data.first()[draftKey])
            assertEquals("DeepFlood post", context.postComposerDataStore(Site.DEEPFLOOD).data.first()[draftKey])
        }
    }

    @Test
    fun `returning to a site reuses its database and keeps overlapping user ids separate`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databases = AndroidSiteDatabases(context)
        val ns = databases.get(Site.NODESEEK)
        val df = databases.get(Site.DEEPFLOOD)
        try {
            ns.userNoteDao().upsert(UserNoteEntity(42, "NodeSeek note", 100))
            df.userNoteDao().upsert(UserNoteEntity(42, "DeepFlood note", 200))
            ReadingHistoryAccounts(ns).apply {
                activateSession(true, 101)
                identify(101, 42)
            }
            ReadingHistoryAccounts(df).apply {
                activateSession(true, 202)
                identify(202, 42)
            }
            ns.readMarkDao().upsert(ReadMarkEntity(123, 100, 23, title = "NodeSeek thread"))
            df.readMarkDao().upsert(ReadMarkEntity(123, 200, 4, title = "DeepFlood thread"))
            ns.readingPositionDao().upsert(ReadingPositionEntity(123, 3, "#23", 100))
            df.readingPositionDao().upsert(ReadingPositionEntity(123, 1, "#4", 200))
            repeat(3) {
                assertSame(ns, databases.get(Site.NODESEEK))
                assertSame(df, databases.get(Site.DEEPFLOOD))
                assertEquals("NodeSeek note", databases.get(Site.NODESEEK).userNoteDao().observeAll().first().single().note)
                assertEquals("DeepFlood note", databases.get(Site.DEEPFLOOD).userNoteDao().observeAll().first().single().note)
                ReadingHistoryAccounts(ns).apply {
                    activateSession(true, 101)
                    identify(101, 42)
                }
                ReadingHistoryAccounts(df).apply {
                    activateSession(true, 202)
                    identify(202, 42)
                }
                assertEquals("NodeSeek thread", ns.readMarkDao().find(123)?.title)
                assertEquals("DeepFlood thread", df.readMarkDao().find(123)?.title)
                assertEquals("#23", ns.readingPositionDao().find(123)?.floor)
                assertEquals("#4", df.readingPositionDao().find(123)?.floor)
            }
        } finally {
            ns.close()
            df.close()
        }
    }
}
