/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import android.Manifest
import android.app.ListActivity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import android.view.*
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import java.util.*
import kotlin.collections.ArrayList

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
class DeviceScanActivity : ListActivity() {

    // Defined by me.
    val DSC_SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331924b")


    private var deviceListAdapter: LeDeviceListAdapter? = null
    private var adapter: BluetoothAdapter? = null

    private var scanner: BluetoothLeScanner? = null
    private val scanFilters: List<ScanFilter>
    private val scanSettings: ScanSettings

    private var scanning = false
    private var handler: Handler? = null

    init {
        // Setup scan filters and settings
        scanFilters = buildScanFilters()
        scanSettings = buildScanSettings()

        // Start a scan for BLE devices
       // startScan()
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar!!.setTitle(R.string.title_devices)
        handler = Handler()

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
            finish()
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        adapter = bluetoothManager.adapter

        // Checks if Bluetooth is supported on the device.
        if (adapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        scanner = adapter!!.bluetoothLeScanner


        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION),
            1
        )

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_ADMIN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Can't get BLUETOOTH_ADMIN", Toast.LENGTH_SHORT).show()
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Can't get BLUETOOTH_CONNECT", Toast.LENGTH_SHORT).show()
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Can't get BLUETOOTH_SCAN", Toast.LENGTH_SHORT).show()
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Can't get ACCESS_COARSE_LOCATION", Toast.LENGTH_SHORT).show()
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Can't get ACCESS_FINE_LOCATION", Toast.LENGTH_SHORT).show()
        }
        Log.i("Doug", "onCreate finished")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        if (!scanning) {
            menu.findItem(R.id.menu_stop).isVisible = false
            menu.findItem(R.id.menu_scan).isVisible = true
            menu.findItem(R.id.menu_refresh).actionView = null
        } else {
            menu.findItem(R.id.menu_stop).isVisible = true
            menu.findItem(R.id.menu_scan).isVisible = false
            menu.findItem(R.id.menu_refresh).setActionView(
                R.layout.actionbar_indeterminate_progress
            )
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_scan -> {
                deviceListAdapter!!.clear()
                scanLeDevice(true)
            }
            R.id.menu_stop -> scanLeDevice(false)
        }
        return true
    }

    override fun onResume() {
        super.onResume()

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!adapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }


        // Initializes list view adapter.
        deviceListAdapter = LeDeviceListAdapter()
        setListAdapter(deviceListAdapter)
        scanLeDevice(true)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_CANCELED) {
            finish()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onPause() {
        super.onPause()
        scanLeDevice(false)
        deviceListAdapter!!.clear()
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val device = deviceListAdapter!!.getDevice(position) ?: return
        val intent = Intent(this, DSCReadActivity::class.java)
        intent.putExtra(DSCReadActivity.EXTRAS_DEVICE_NAME, device.name)
        intent.putExtra(DSCReadActivity.EXTRAS_DEVICE_ADDRESS, device.address)
        if (scanning) {
            scanner!!.stopScan(scanCallback)
            scanning = false
        }
        startActivity(intent)
    }

    private fun buildScanFilters(): List<ScanFilter> {
        val builder = ScanFilter.Builder()
        // Comment out the below line to see all BLE devices around you
        builder.setServiceUuid(ParcelUuid(DSC_SERVICE_UUID))
        val filter = builder.build()
        return listOf(filter)
    }

    /**
     * Return a [ScanSettings] object.
     */
    private fun buildScanSettings(): ScanSettings {
        return ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
    }


    private fun scanLeDevice(enable: Boolean) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            handler!!.postDelayed({
                if (scanning) {
                    scanning = false
                    scanner!!.stopScan(scanCallback)
                    invalidateOptionsMenu()
                    Log.i("Doug", "scan timeout")
                }
            }, SCAN_PERIOD)
            scanning = true
            Log.i("Doug", "starting scan")
            scanner!!.startScan(scanFilters, scanSettings, scanCallback)
        } else {
            scanning = false
            scanner!!.stopScan(scanCallback)
        }
        invalidateOptionsMenu()
    }

    // Adapter for holding devices found through scanning.
    private inner class LeDeviceListAdapter : BaseAdapter() {
        private val devices: ArrayList<BluetoothDevice>
        private val inflator: LayoutInflater
        fun addDevice(device: BluetoothDevice) {
            if (!devices.contains(device)) {
                Log.i("Doug", "got " + device.address + "  " + device.name)
                devices.add(device)
            }
        }

        fun getDevice(position: Int): BluetoothDevice {
            return devices[position]
        }

        fun clear() {
            devices.clear()
        }

        override fun getCount(): Int {
            return devices.size
        }

        override fun getItem(i: Int): Any {
            return devices[i]
        }

        override fun getItemId(i: Int): Long {
            return i.toLong()
        }

        override fun getView(i: Int, convertView: View?, parent: ViewGroup?): View {

            //var view = view
            val viewHolder: ViewHolder
            val view: View

            // General ListView optimization code.
            if (convertView == null) {
                view = inflator.inflate(R.layout.listitem_device, null)
                viewHolder = ViewHolder()
                viewHolder.deviceAddress = view.findViewById (R.id.device_address)
                viewHolder.deviceName = view.findViewById (R.id.device_name)
                view.setTag(viewHolder)
                Log.i("Doug", "new View " + i.toString())

            } else {
                view = convertView
                viewHolder = convertView.tag as ViewHolder
            }

            val device = devices[i]
            val deviceName = device.name
            if (deviceName != null && deviceName.isNotEmpty()) {
                viewHolder.deviceName!!.text = deviceName
            } else {
                viewHolder.deviceName!!.setText(R.string.unknown_device)
            }
            viewHolder.deviceAddress!!.text = device.address
            return view
        }

        init {
            devices = ArrayList()
            inflator = this@DeviceScanActivity.layoutInflater
        }
    }

    // Device scan callback.
    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            runOnUiThread {
                deviceListAdapter!!.addDevice(result.device)
                deviceListAdapter!!.notifyDataSetChanged()
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            runOnUiThread {
                for (result in results) {
                    deviceListAdapter!!.addDevice(result.device)
                    deviceListAdapter!!.notifyDataSetChanged()
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.i("Doug", "scan failed code "+errorCode.toString())
            scanning = false
        }
    }

    internal class ViewHolder {
        var deviceName: TextView? = null
        var deviceAddress: TextView? = null
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1

        // Stops scanning after 10 seconds.
        private const val SCAN_PERIOD: Long = 10000
    }
}
