package com.everything.long_scanning_ble

import android.app.*
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ReadRequest
import no.nordicsemi.android.support.v18.scanner.*
import java.util.*


// TODO: Rename actions, choose action names that describe tasks that this
// IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
private const val ACTION_FOO = "com.everything.long_scanning_ble.action.FOO"
private const val ACTION_BAZ = "com.everything.long_scanning_ble.action.BAZ"

// TODO: Rename parameters
private const val EXTRA_PARAM1 = "com.everything.long_scanning_ble.extra.PARAM1"
private const val EXTRA_PARAM2 = "com.everything.long_scanning_ble.extra.PARAM2"

/**
 * An [IntentService] subclass for handling asynchronous task requests in
 * a service on a separate handler thread.

 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.

 */
private const val CHANNEL_DEFAULT_IMPORTANCE: String = "general"
private const val ONGOING_NOTIFICATION_ID: Int = 5151512

class BleService : Service() {

    private lateinit var bluetoothObserver: BroadcastReceiver
    private var mHandlerUpdate: Handler? = null

    private val clientManagers = mutableMapOf<String, ClientManager>()

    private var bluetoothManager: BluetoothManager? = null
    private var clientManager: ClientManager? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()

        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        clientManager = ClientManager()

        mHandlerUpdate = Handler(Looper.getMainLooper())

        // Setup as a foreground service

        createNotification()

        // Observe OS state changes in BLE

        bluetoothObserver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val bluetoothState = intent.getIntExtra(
                            BluetoothAdapter.EXTRA_STATE,
                            -1
                        )
                        when (bluetoothState) {
                            BluetoothAdapter.STATE_ON -> enableBleServices("high_power")
                            BluetoothAdapter.STATE_OFF -> disableBleServices()
                        }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device =
                            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        Log.d(
                            BleService::class.java.simpleName,
                            "Bond state changed for device ${device?.address}: ${device?.bondState}"
                        )
                        when (device?.bondState) {
                            BluetoothDevice.BOND_BONDED -> {
                                log(
                                    0,
                                    "This device bonded and it is connected (${clientManagers[device.address]?.isConnected})"
                                )
                            }
                            BluetoothDevice.BOND_NONE -> removeDevice(device)
                        }
                    }

                }
            }
        }
        registerReceiver(bluetoothObserver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        registerReceiver(bluetoothObserver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))

        // Startup BLE if we have it

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (bluetoothManager.adapter?.isEnabled == true) enableBleServices("high_power")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothObserver)
        disableBleServices()
        log(Log.INFO, "Service destroyed")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotification() {
        val notificationChannel = NotificationChannel(
            BleService::class.java.simpleName,
            getString(R.string.notification_title),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val notificationService =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationService.createNotificationChannel(notificationChannel)

        val notification = NotificationCompat.Builder(this, BleService::class.java.simpleName)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_message))
            .setAutoCancel(true)

        startForeground(1, notification.build())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FOO -> {
                val param1 = intent.getStringExtra(EXTRA_PARAM1)
                handleActionFoo(param1)
            }
            ACTION_BAZ -> {
                val param1 = intent.getStringExtra(EXTRA_PARAM1)
                val param2 = intent.getStringExtra(EXTRA_PARAM2)
                handleActionBaz(param1, param2)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    //region ScanCallback
    private val mScanCallback: ScanCallback = object : ScanCallback() {
        //region Scan Result
        override fun onScanResult(callbackType: Int, result: ScanResult) {

            val btDevice: BluetoothDevice = result.device
            Log.d(
                BleService::class.java.simpleName,
                "onScanResult: ${btDevice.name} - ${btDevice.address}"
            )
        }

        override fun onScanFailed(errorCode: Int) {

            log(Log.INFO, "scan failed with error: $errorCode")
        } //endregion
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun enableBleServices(strategyScan: String?) {
        if (bluetoothManager?.adapter?.isEnabled == true) {
            Log.i(BleService::class.java.simpleName, "Enabling BLE services: $strategyScan")
            val scanner = BluetoothLeScannerCompat.getScanner()
            val settings: ScanSettings = ScanSettings.Builder()
                .setScanMode(
                    if (strategyScan == "low_scan")
                        ScanSettings.SCAN_MODE_LOW_POWER
                    else
                        ScanSettings.SCAN_MODE_LOW_LATENCY
                )
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()
            val filters: MutableList<ScanFilter> = ArrayList()
            filters.add(ScanFilter.Builder().setDeviceAddress("FE:29:9D:7F:75:5C").build())

            scanner.stopScan(mScanCallback)

            scanner.startScan(
                filters,
                settings,
                mScanCallback
            )

            mHandlerUpdate?.removeCallbacksAndMessages(null)

            mHandlerUpdate?.postDelayed(object : Runnable {
                override fun run() {
                    Log.d(BleService::class.java.simpleName, "run: in runnable handler")
                    scanner.stopScan(mScanCallback)

                    scanner.startScan(
                        filters,
                        settings,
                        mScanCallback
                    )
                    mHandlerUpdate?.postDelayed(this, 60000)
                }
            }, 60000)


        } else {
            Log.w(
                BleService::class.java.simpleName,
                "Cannot enable BLE services as either there is no Bluetooth adapter or it is disabled"
            )
        }
    }

    private fun disableBleServices() {
        clientManagers.values.forEach { clientManager ->
            clientManager.close()
        }
        clientManagers.clear()
    }

    private fun addDevice(device: BluetoothDevice) {
        Log.d("Device name: ", device.name)
        if (clientManagers[device.address] == null) {

            clientManager?.connect(device)?.useAutoConnect(true)?.timeout(50000)?.enqueue()
            clientManagers[device.address] = clientManager!!

            BluetoothLeScannerCompat.getScanner().stopScan(
                mScanCallback
            )
        }
    }

    private fun removeDevice(device: BluetoothDevice) {
        clientManagers.remove(device.address)?.close()
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun handleActionFoo(param1: String?) {
        enableBleServices(param1)
    }

    /**
     * Handle action Baz in the provided background thread with the provided
     * parameters.
     */
    private fun handleActionBaz(param1: String?, param2: String?) {
        TODO("Handle action Baz")
    }

    companion object {
        /**
         * Starts this service to perform action Foo with the given parameters. If
         * the service is already performing a task this action will be queued.
         *
         * @see IntentService
         */
        // TODO: Customize helper method
        @JvmStatic
        fun startActionFoo(context: Context, param1: String?) {
            val intent = Intent(context, BleService::class.java).apply {
                action = ACTION_FOO
                putExtra(EXTRA_PARAM1, param1)
            }
            context.startService(intent)
        }

        /**
         * Starts this service to perform action Baz with the given parameters. If
         * the service is already performing a task this action will be queued.
         *
         * @see IntentService
         */
        // TODO: Customize helper method
        @JvmStatic
        fun startActionBaz(context: Context, param1: String, param2: String) {
            val intent = Intent(context, BleService::class.java).apply {
                action = ACTION_BAZ
                putExtra(EXTRA_PARAM1, param1)
                putExtra(EXTRA_PARAM2, param2)
            }
            context.startService(intent)
        }
    }

    fun log(priority: Int, message: String) {
        if (BuildConfig.DEBUG || priority == Log.ERROR) {
            Log.println(priority, BleService::class.java.simpleName, message)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private inner class ClientManager : BleManager(this@BleService) {
        override fun getGattCallback(): BleManagerGattCallback = GattCallback()

        override fun log(priority: Int, message: String) {
            if (no.nordicsemi.android.support.v18.scanner.BuildConfig.DEBUG || priority == Log.ERROR) {
                Log.println(priority, BleService::class.java.simpleName, message)
            }
        }

        override fun readCharacteristic(characteristic: BluetoothGattCharacteristic?): ReadRequest {
            val readRequest = super.readCharacteristic(characteristic)

            readRequest.enqueue()

            if (characteristic?.value != null) {
                Log.d(
                    BleService::class.java.simpleName,
                    "receive data with no null char: ${characteristic.value}"
                )
                try {
                    Log.d(
                        BleService::class.java.simpleName,
                        "readCharacteristic UUID: ${characteristic.uuid}"
                    )
                    Log.d(
                        BleService::class.java.simpleName,
                        "readCharacteristic VALUE FORMATTED: ${
                            characteristic.getIntValue(
                                BluetoothGattCharacteristic.FORMAT_UINT8,
                                0
                            )
                        }"
                    )
                } catch (e: Exception) {
                    Log.d(BleService::class.java.simpleName, e.toString(), e)
                }
            } else
                log(Log.INFO, "Null value in reading char")
            return readRequest
        }

        private inner class GattCallback : BleManagerGattCallback() {

            private var myCharacteristic: BluetoothGattCharacteristic? = null
            override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                Log.d(BleService::class.java.simpleName, "isRequiredServiceSupported: ")
                return true
            }

            override fun onServicesInvalidated() {
                Log.d(BleService::class.java.simpleName, "onServicesInvalidated: ")
            }

        }
    }
}