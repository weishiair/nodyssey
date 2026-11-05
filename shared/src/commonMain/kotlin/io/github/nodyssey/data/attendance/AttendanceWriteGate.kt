package io.github.nodyssey.data.attendance

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Manual and automatic sign-in share the account lock, then recheck the server's daily ledger. */
class AttendanceWriteGate {
    private val lock = Mutex()
    private val accounts = mutableMapOf<Long, Mutex>()

    suspend fun <T> run(uid: Long, block: suspend () -> T): T {
        val account = lock.withLock { accounts.getOrPut(uid) { Mutex() } }
        return account.withLock { block() }
    }
}
