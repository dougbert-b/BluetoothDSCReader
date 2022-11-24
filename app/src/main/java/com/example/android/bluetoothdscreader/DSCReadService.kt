/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.bluetoothdscreader

import android.app.Service
import android.bluetooth.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.collections.ArrayList


class DSCReadService : Service() {

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothDeviceAddress: String? = null
    private var _gatt: BluetoothGatt? = null

    private val characteristics: ArrayList<ArrayList<BluetoothGattCharacteristic>>? =
        ArrayList()

    private var azElChar : BluetoothGattCharacteristic? = null
    private var azResChar : BluetoothGattCharacteristic? = null
    private var elResChar : BluetoothGattCharacteristic? = null

    private val charReadQueue : ArrayDeque<BluetoothGattCharacteristic> =
                   ArrayDeque<BluetoothGattCharacteristic>()

    private var mConnectionState = STATE_DISCONNECTED

    private val TAG = DSCReadService::class.java.simpleName

    companion object {
        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTING = 1
        private const val STATE_CONNECTED = 2

        const val ACTION_DSC_CONNECTED = "com.example.bluetooth.le.ACTION_DSC_CONNECTED"
        const val ACTION_DSC_DISCONNECTED = "com.example.bluetooth.le.ACTION_DSC_DISCONNECTED"
        const val ACTION_DSC_AZ_AVAILABLE = "com.example.bluetooth.le.ACTION_DSC_AZ_AVAILABLE"
        const val ACTION_DSC_EL_AVAILABLE = "com.example.bluetooth.le.ACTION_DSC_EL_AVAILABLE"
        const val ACTION_DSC_AZEL_AVAILABLE = "com.example.bluetooth.le.ACTION_DSC_AZEL_AVAILABLE"

        const val EXTRA_DATA = "com.example.bluetooth.le.EXTRA_DATA"

    }


    // Implements callback methods for GATT events that we care about.  For example,
    // connection change, services discovered, and data reception.
    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int)
        {
            assert(gatt == _gatt)

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectionState = STATE_CONNECTED
                broadcastUpdate(ACTION_DSC_CONNECTED)
                Log.i(TAG, "Connected to GATT server.")
                // Attempts to discover services after successful connection.
                val discovered = _gatt!!.discoverServices()
                Log.i(TAG, "Started service discovery: $discovered")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.")
                mConnectionState = STATE_DISCONNECTED
                broadcastUpdate(ACTION_DSC_DISCONNECTED)
            } else {
                Log.i(TAG, "Connection state change $newState")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int)
        {
            assert(gatt == _gatt)

            Log.i(TAG, "onServicesDiscovered status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                initDSCCharacteristics()
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            //value : ByteArray,
            status: Int)
        {
            assert(gatt == _gatt)

            val uuid = characteristic.uuid.toString()
            Log.i(TAG, "onCharacteristicRead received: $status  $uuid")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(characteristic)
            }

            // Trigger a read of any remaining characteristics
            readQueuedChars()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
            /*value : ByteArray*/)
        {
            assert(gatt == _gatt)

            val uuid = characteristic.uuid.toString()
            //Log.i(TAG, "onCharacteristicChanged received: $uuid")
            broadcastUpdate(characteristic)
        }
    }

    private fun broadcastUpdate(action : String)
    {
        var intent = Intent(action)
        sendBroadcast(intent)
    }


    private fun broadcastUpdate(
        characteristic: BluetoothGattCharacteristic
    ) {
        var intent = Intent()

        if (characteristic.uuid == UUID.fromString(DSCGattUuids.AZEL)) {
            val dataStr = characteristic.getStringValue(0)
            val arr = FloatArray(2)
            arr[0] = dataStr.substring(0, 6).toFloat()
            arr[1] = dataStr.substring(6).toFloat()
            intent.putExtra(EXTRA_DATA, arr)
            intent.setAction(ACTION_DSC_AZEL_AVAILABLE)
        } else if (characteristic.uuid == UUID.fromString(DSCGattUuids.AZIMUTH)) {
            intent.putExtra(EXTRA_DATA, characteristic.getValue().toString().toFloat())
            intent.setAction(ACTION_DSC_AZ_AVAILABLE)
        } else if (characteristic.uuid == UUID.fromString(DSCGattUuids.ELEVATION)) {
            intent.putExtra(EXTRA_DATA, characteristic.getValue().toString().toFloat())
            intent.setAction(ACTION_DSC_EL_AVAILABLE)
        }

        sendBroadcast(intent)
    }

    inner class LocalBinder : Binder() {
        val service: DSCReadService
            get() = this@DSCReadService
    }

    private val mBinder: IBinder = LocalBinder()

    override fun onBind(intent: Intent): IBinder? {
        return mBinder
    }

    override fun onUnbind(intent: Intent): Boolean {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close()
        return super.onUnbind(intent)
    }




    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
     fun initialize(): Boolean {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (bluetoothManager == null) {
            bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            if (bluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.")
                return false
            }
        }
        bluetoothAdapter = bluetoothManager!!.adapter
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        }
        return true
    }


    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * `BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)`
     * callback.
     */
     fun connect(address: String?): Boolean {

        if (bluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.")
            return false
        }

        // Previously connected device.  Try to reconnect.
        if (bluetoothDeviceAddress != null && address == bluetoothDeviceAddress && _gatt != null) {
            Log.d(TAG, "Trying to use the existing GATT for connection.")
            return if (_gatt!!.connect()) {
                mConnectionState = STATE_CONNECTING
                true
            } else {
                false
            }
        }
        val device = bluetoothAdapter!!.getRemoteDevice(address)
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.")
            return false
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        _gatt = device.connectGatt(this, false, mGattCallback)
        Log.d(TAG, "Created a new GATT connection.")
        bluetoothDeviceAddress = address
        mConnectionState = STATE_CONNECTING
        return true
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * `BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)`
     * callback.
     */
     fun disconnect() {
        if (bluetoothAdapter == null || _gatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized")
            return
        }
        Log.d(TAG, "Disconnecting.")

        _gatt!!.setCharacteristicNotification(azElChar, false)
        _gatt!!.disconnect()
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    private fun close() {
        if (_gatt == null) {
            return
        }
        Log.d(TAG, "Closing.")
        _gatt!!.close()
        _gatt = null
        charReadQueue.clear()
        azElChar = null
        azResChar = null
        elResChar = null

    }

    private fun readQueuedChars() {
        // Initiate a read of the first characteristic in the queue
        val char : BluetoothGattCharacteristic? = charReadQueue.removeFirstOrNull()
        if (char != null) {
            _gatt!!.readCharacteristic(char)
        }

    }

    private fun initDSCCharacteristics() {

        var service : BluetoothGattService? = _gatt!!.getService(UUID.fromString(DSCGattUuids.DOUG_DSC_SERVICE))

        if (service != null) {

            azElChar = service.getCharacteristic(UUID.fromString(DSCGattUuids.AZEL))
            if (azElChar != null) {
                _gatt!!.setCharacteristicNotification(azElChar, true)
            }

            azResChar = service.getCharacteristic(UUID.fromString(DSCGattUuids.AZ_RESOLUTION))
            if (azResChar != null) {
                charReadQueue.add(azResChar!!)
            }

            elResChar = service.getCharacteristic(UUID.fromString(DSCGattUuids.EL_RESOLUTION))
            if (elResChar != null) {
                charReadQueue.add(elResChar!!)
            }

            readQueuedChars()

        } else {
            Log.e(TAG, "DSC Service not found")
        }
    }
}