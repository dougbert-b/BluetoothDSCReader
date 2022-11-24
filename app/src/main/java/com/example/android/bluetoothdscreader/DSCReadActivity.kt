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

import android.app.Activity
import android.bluetooth.*
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View

import com.example.android.bluetoothdscreader.DSCReadService.LocalBinder


import android.widget.TextView
import java.lang.StringBuilder
import java.util.*

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display DSCcdata provided by the device.  The Activity
 * ultimately communicates with the Bluetooth LE API.
 */
class DSCReadActivity : Activity() {

    private var mDSCReadService: DSCReadService? = null

    private var mConnected = false

    private var mDeviceName: String? = null
    private var mDeviceAddress: String? = null

    private var mConnectionState: TextView? = null
    private var mAzField: TextView? = null
    private var mElField: TextView? = null
    private var mAzResField: TextView? = null
    private var mElResField: TextView? = null

    private val TAG = DSCReadActivity::class.java.simpleName

    companion object {
        // Labels for parameters of this Activity
        const val EXTRAS_DEVICE_NAME = "DEVICE_NAME"
        const val EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS"

        private fun makeDSCUpdateIntentFilter(): IntentFilter {
            val intentFilter = IntentFilter()
            intentFilter.addAction(DSCReadService.ACTION_DSC_CONNECTED)
            intentFilter.addAction(DSCReadService.ACTION_DSC_DISCONNECTED)
            intentFilter.addAction(DSCReadService.ACTION_DSC_AZ_AVAILABLE)
            intentFilter.addAction(DSCReadService.ACTION_DSC_EL_AVAILABLE)
            intentFilter.addAction(DSCReadService.ACTION_DSC_AZEL_AVAILABLE)
            return intentFilter
        }
    }


    // Code to manage Service lifecycle.
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            Log.d(TAG, "Initializing DSC Server")
            mDSCReadService = (service as LocalBinder).service
            if (! mDSCReadService!!.initialize()) {
                Log.e(TAG, "Unable to initialize DSC Server")
                finish()
            }
            // Automatically connects to the device upon successful start-up initialization.
            mDSCReadService!!.connect(mDeviceAddress)
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            mDSCReadService = null
        }

        override fun onNullBinding(name: ComponentName) {
            assert(false)
        }
    }

    // Handles various events fired by the Service.
    // ACTION_DSC_CONNECTED: connected to a DSC server.
    // ACTION_DSC_DISCONNECTED: disconnected from a DSC server.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private val mDSCUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (DSCReadService.ACTION_DSC_CONNECTED == action) {
                mConnected = true
                updateConnectionState(R.string.connected)
                invalidateOptionsMenu()
            } else if (DSCReadService.ACTION_DSC_DISCONNECTED == action) {
                mConnected = false
                updateConnectionState(R.string.disconnected)
                invalidateOptionsMenu()
                clearUI()
            } else if (DSCReadService.ACTION_DSC_AZEL_AVAILABLE == action) {
                val azel : FloatArray? = intent.getFloatArrayExtra(DSCReadService.EXTRA_DATA)
                displayAzEl(azel!![0], azel!![1]);
            }
        }
    }


    private fun clearUI() {
        mAzField!!.setText(R.string.no_data)
        mElField!!.setText(R.string.no_data)
        mAzResField!!.setText(R.string.no_data)
        mElResField!!.setText(R.string.no_data)

    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dsc_service_data)
        val intent = getIntent()
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME)
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS)

        // Sets up UI references.
        (findViewById<View>(R.id.device_address) as TextView).text = mDeviceAddress
        mConnectionState = findViewById(R.id.connection_state)
        mAzField = findViewById(R.id.az_value)
        mElField = findViewById(R.id.el_value)
        mAzResField = findViewById(R.id.az_res_value)
        mElResField = findViewById(R.id.el_res_value)
        actionBar!!.title = mDeviceName
        actionBar!!.setDisplayHomeAsUpEnabled(true)

        val dscReadServiceIntent = Intent(this, DSCReadService::class.java)
        Log.d(TAG, "Binding DSCReadService")
        var success = bindService(dscReadServiceIntent, mServiceConnection, BIND_AUTO_CREATE)
        assert(success)
    }


    override fun onResume() {
        super.onResume()
        registerReceiver(mDSCUpdateReceiver, makeDSCUpdateIntentFilter())
        if (mDSCReadService != null) {
            val result = mDSCReadService!!.connect(mDeviceAddress)
            Log.d(TAG, "Connect request result=$result")
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(mDSCUpdateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(mServiceConnection)
        mDSCReadService = null
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.dsc_services, menu)
        if (mConnected) {
            menu.findItem(R.id.menu_connect).isVisible = false
            menu.findItem(R.id.menu_disconnect).isVisible = true
        } else {
            menu.findItem(R.id.menu_connect).isVisible = true
            menu.findItem(R.id.menu_disconnect).isVisible = false
        }
        return true
    }

    private fun updateConnectionState(resourceId: Int) {
        runOnUiThread { mConnectionState!!.setText(resourceId) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        assert(mDSCReadService != null)
        when (item.itemId) {
                    R.id.menu_connect -> {
                mDSCReadService!!.connect(mDeviceAddress)
                return true
            }
            R.id.menu_disconnect -> {
                mDSCReadService!!.disconnect()
                return true
            }
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun displayAzEl(az : Float, el : Float) {
        mAzField!!.text = az.toString()
        mElField!!.text = el.toString()
    }


}