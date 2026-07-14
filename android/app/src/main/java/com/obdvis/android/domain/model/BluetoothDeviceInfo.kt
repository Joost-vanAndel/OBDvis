package com.obdvis.android.domain.model

import android.bluetooth.BluetoothDevice

/** Snapshot of a paired Bluetooth device with the name already resolved. */
data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val device: BluetoothDevice,
)
