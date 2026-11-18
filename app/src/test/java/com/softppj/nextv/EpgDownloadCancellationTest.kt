package com.softppj.nextv

import java.io.Closeable
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class EpgDownloadCancellationTest {
    private class FakeConnection : HttpURLConnection(URL("https://example.com/guide.xml")) {
        val disconnected = CountDownLatch(1)
        override fun connect() {}
        override fun disconnect() { disconnected.countDown() }
        override fun usingProxy(): Boolean = false
    }

    private class FakeStream : Closeable {
        val closed = CountDownLatch(1)
        override fun close() { closed.countDown() }
    }

    @Test fun disablingClosesActiveNetworkConnectionAndStream() {
        val session = EpgDownloadCancellation()
        val conn = FakeConnection()
        val stream = FakeStream()
        assertTrue(session.attachConnection(conn))
        assertTrue(session.attachStream(stream))
        session.cancel()
        assertTrue(session.isCancelled())
        assertTrue(conn.disconnected.await(3, TimeUnit.SECONDS))
        assertTrue(stream.closed.await(3, TimeUnit.SECONDS))
    }

    @Test fun connectionsRegisteredAfterCancellationAreRejectedAndClosed() {
        val session = EpgDownloadCancellation()
        session.cancel()
        val lateConn = FakeConnection()
        val lateStream = FakeStream()
        assertFalse(session.attachConnection(lateConn))
        assertFalse(session.attachStream(lateStream))
        assertTrue(lateConn.disconnected.await(3, TimeUnit.SECONDS))
        assertTrue(lateStream.closed.await(3, TimeUnit.SECONDS))
    }

    @Test fun completedSourceIsNotClosedByLaterCancellation() {
        val session = EpgDownloadCancellation()
        val conn = FakeConnection()
        assertTrue(session.attachConnection(conn))
        session.releaseConnection(conn)
        session.cancel()
        assertEquals(1L, conn.disconnected.count)
    }

    @Test fun cancellingRepeatedlyIsSafe() {
        val session = EpgDownloadCancellation()
        session.cancel()
        session.cancel()
        assertTrue(session.isCancelled())
    }
}
