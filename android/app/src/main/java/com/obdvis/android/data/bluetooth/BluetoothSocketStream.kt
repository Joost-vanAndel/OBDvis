package com.obdvis.android.data.bluetooth

import android.bluetooth.BluetoothSocket
import java.io.IOException

/**
 * Wraps a BluetoothSocket with simple blocking read/write helpers.
 * All methods are blocking — call them from Dispatchers.IO.
 */
class BluetoothSocketStream(private val socket: BluetoothSocket) {

    private val inputStream = socket.inputStream
    private val outputStream = socket.outputStream

    /**
     * Sends [command] bytes and reads the response until the ELM327 '>' prompt.
     * Strips carriage returns; returns trimmed response text.
     */
    fun sendAndReceive(command: String): String {
        outputStream.write(command.toByteArray(Charsets.US_ASCII))
        outputStream.flush()
        return readUntilPrompt()
    }

    /**
     * Reads bytes from the stream until '>' is found.
     */
    fun readUntilPrompt(): String {
        val sb = StringBuilder()
        while (true) {
            val byte = inputStream.read()
            if (byte == -1) throw IOException("Bluetooth stream closed")
            val ch = byte.toChar()
            when {
                ch == '>'            -> break
                ch == '\r'           -> { /* skip */ }
                else                 -> sb.append(ch)
            }
        }
        return sb.toString().trim()
    }

    fun close() {
        try { socket.close() } catch (_: Exception) {}
    }
}
