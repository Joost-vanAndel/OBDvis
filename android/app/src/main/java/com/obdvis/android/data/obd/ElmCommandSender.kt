package com.obdvis.android.data.obd

import com.obdvis.android.data.bluetooth.BluetoothSocketStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

class ElmCommandSender(private val stream: BluetoothSocketStream) {

    /**
     * Sends [command] and returns the response text (everything before '>').
     *
     * Uses runInterruptible so that when the timeout fires, Thread.interrupt() is called
     * on the IO thread — this actually unblocks the native InputStream.read() call inside
     * BluetoothSocketStream. withTimeoutOrNull converts the TimeoutCancellationException
     * to null so callers see a plain IOException instead of a CancellationException,
     * keeping the polling loop alive on per-PID timeouts.
     */
    suspend fun send(command: String, timeoutMs: Long = 1500): String =
        withTimeoutOrNull(timeoutMs) {
            runInterruptible(Dispatchers.IO) {
                stream.sendAndReceive(command)
            }
        } ?: throw IOException("ELM327 did not respond within ${timeoutMs}ms")
}
