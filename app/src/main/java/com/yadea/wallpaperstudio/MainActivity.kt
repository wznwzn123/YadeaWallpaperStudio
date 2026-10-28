package com.yadea.wallpaperstudio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private val seen = linkedMapOf<String, BluetoothDevice>()
    private var gatt: BluetoothGatt? = null
    private val adapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val scanner get() = adapter?.bluetoothLeScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }
        scroll.addView(root)
        setContentView(scroll)
        root.addView(TextView(this).apply {
            text = "Yadea Wallpaper Studio"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "G5Ⅱpro-M · BLE 只读诊断"
            textSize = 16f
        })
        status = TextView(this).apply { text = "仅扫描、连接与读取；不会向车辆设备写入数据。" }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "扫描附近 BLE 设备"
            setOnClickListener { ensurePermissionsAndScan() }
        })
        root.addView(Button(this).apply {
            text = "断开设备"
            setOnClickListener {
                gatt?.disconnect()
                gatt?.close()
                gatt = null
                status.text = "已请求断开连接"
            }
        })
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun ensurePermissionsAndScan() {
        val missing = requiredPermissions().filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 10)
            return
        }
        startScan()
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (adapter?.isEnabled != true) {
            status.text = "请先在手机系统设置中打开蓝牙。"
            return
        }
        val bleScanner = scanner
        if (bleScanner == null) {
            status.text = "当前手机不支持 BLE 扫描。"
            return
        }
        seen.clear()
        removeDeviceRows()
        status.text = "正在扫描，约 12 秒后自动停止……"
        try {
            bleScanner.startScan(scanCallback)
            root.postDelayed({
                try { bleScanner.stopScan(scanCallback) } catch (_: Exception) {}
                status.text = "扫描结束：发现 ${seen.size} 台设备。选择设备连接。"
            }, 12000)
        } catch (e: Exception) {
            status.text = "扫描失败：${e.message ?: "请检查蓝牙权限"}"
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val key = device.address ?: return
            if (seen.containsKey(key)) return
            seen[key] = device
            runOnUiThread {
                val name = try { device.name?.takeIf { it.isNotBlank() } ?: "未命名设备" }
                    catch (_: SecurityException) { "蓝牙设备" }
                root.addView(Button(this@MainActivity).apply {
                    text = "$name\n$key · RSSI ${result.rssi} dBm"
                    setOnClickListener { connect(device, name) }
                })
            }
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread { status.text = "扫描失败，错误码：$errorCode" }
        }
    }

    private fun removeDeviceRows() {
        // Keep the title, status, and the two fixed control buttons.
        while (root.childCount > 5) root.removeViewAt(5)
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice, name: String) {
        gatt?.close()
        status.text = "正在连接：$name"
        gatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, newState: Int) {
                runOnUiThread {
                    status.text = if (newState == BluetoothProfile.STATE_CONNECTED) {
                        "已连接：$name，正在读取服务列表……"
                    } else {
                        "连接状态变化：$newState（状态码 $statusCode）"
                    }
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices()
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
                runOnUiThread {
                    if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                        status.text = "服务发现失败，状态码：$statusCode"
                        return@runOnUiThread
                    }
                    status.text = "服务发现完成。下方列出服务与特征；读取需手动点击。"
                    g.services.forEach { service ->
                        root.addView(TextView(this@MainActivity).apply {
                            text = "\n服务 UUID：${service.uuid}"
                            textSize = 16f
                        })
                        service.characteristics.forEach { characteristic ->
                            val props = buildList {
                                val p = characteristic.properties
                                if ((p and BluetoothGattCharacteristic.PROPERTY_READ) != 0) add("可读")
                                if ((p and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) add("通知")
                                if ((p and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) add("指示")
                                if ((p and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) add("可写")
                                if ((p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) add("无响应写")
                            }.joinToString("、").ifBlank { "无常见属性" }
                            val row = LinearLayout(this@MainActivity).apply {
                                orientation = LinearLayout.VERTICAL
                            }
                            row.addView(TextView(this@MainActivity).apply {
                                text = "${characteristic.uuid}\n属性：$props"
                            })
                            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                                row.addView(Button(this@MainActivity).apply {
                                    text = "读取此特征（只读）"
                                    setOnClickListener {
                                        @Suppress("DEPRECATION")
                                        val accepted = g.readCharacteristic(characteristic)
                                        status.text = if (accepted) "已发起读取请求。" else "读取请求未被设备接受。"
                                    }
                                })
                            }
                            root.addView(row)
                        }
                    }
                }
            }

            @Deprecated("Deprecated in Android API")
            override fun onCharacteristicRead(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                statusCode: Int
            ) {
                showReadResult(characteristic.uuid, characteristic.value, statusCode)
            }

            override fun onCharacteristicRead(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                statusCode: Int
            ) {
                showReadResult(characteristic.uuid, value, statusCode)
            }
        })
    }

    private fun showReadResult(uuid: UUID, bytes: ByteArray?, statusCode: Int) {
        val hex = bytes?.joinToString(" ") { "%02X".format(it) } ?: "(空)"
        runOnUiThread {
            status.text = if (statusCode == BluetoothGatt.GATT_SUCCESS) {
                "读取完成：$uuid"
            } else {
                "读取失败：$uuid，状态码 $statusCode"
            }
            root.addView(TextView(this).apply {
                text = "读取结果 $uuid\n状态：$statusCode\nHEX：$hex"
            })
        }
    }

    override fun onDestroy() {
        try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) {}
        gatt = null
        super.onDestroy()
    }
}
