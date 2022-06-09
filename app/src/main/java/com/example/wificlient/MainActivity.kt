package com.example.wificlient

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.wificlient.net.SocketListener
import com.example.wificlient.net.WifiClient
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import java.util.*
import com.example.wificlient.net.WifiConstant.PORT_NUM

class MainActivity : AppCompatActivity() {

    private var sbLog = StringBuilder()
    private val wifiClient: WifiClient = WifiClient()

    private lateinit var svLogView: ScrollView
    private lateinit var tvLogView: TextView
    private lateinit var etMessage: EditText

    private var ipAddress = ""
    private var port = PORT_NUM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermission()
        }

        // WiFi 상태 변화 감지
        val filter = IntentFilter()
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        registerReceiver(receiver, filter)

        // 네트워크 상태 변화 감지
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val cm = getSystemService(ConnectivityManager::class.java)
            val wifiNetworkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            cm.registerNetworkCallback(wifiNetworkRequest, networkCallback)
        }

        initUI()
        setListener()

        wifiClient.setOnSocketListener(mOnSocketListener)
    }

    private fun initUI() {
        svLogView = findViewById(R.id.svLogView)
        tvLogView = findViewById(R.id.tvLogView)
        etMessage = findViewById(R.id.etCommand)
    }

    private fun setListener() {
        findViewById<Button>(R.id.btnSendData).setOnClickListener {
            if (etMessage.text.toString().isNotEmpty()) {
                wifiClient.sendData(etMessage.text.toString())
            }
        }

        findViewById<Button>(R.id.btnConnect).setOnClickListener {

            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val layout: View = inflater.inflate(R.layout.ip_address_view, null)
            val etIpAddress = layout.findViewById<View>(R.id.etIpAddress) as EditText
            val etPort = layout.findViewById<View>(R.id.etPort) as EditText

            if (ipAddress == "") etIpAddress.setText("192.168.")
            else etIpAddress.setText(ipAddress)
            etPort.setText(port.toString())

            AlertDialog.Builder(this)
                .setTitle("Input IP Address")
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK") { dialogInterface, i ->
                    ipAddress = etIpAddress.text.toString()
                    wifiClient.connectToServer(ipAddress, etPort.text.toString().toIntOrNull())
                }.show()
        }

        findViewById<Button>(R.id.btnDisconnect).setOnClickListener {
            wifiClient.disconnectFromServer()
        }
    }

    private fun log(message: String) {
        sbLog.append(message.trimIndent() + "\n")
        runOnUiThread {
            tvLogView.text = sbLog.toString()
            svLogView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private val mOnSocketListener: SocketListener = object : SocketListener {
        override fun onConnect() {
            log("Connect!\n")
        }

        override fun onDisconnect() {
            log("Disconnect!\n")
        }

        override fun onError(e: Exception?) {
            e?.let { log(e.toString() + "\n") }
        }

        override fun onReceive(msg: String?) {
            msg?.let { log("Receive : $it\n") }
        }

        override fun onSend(msg: String?) {
            msg?.let { log("Send : $it\n") }
        }

        override fun onLogPrint(msg: String?) {
            msg?.let { log("$it\n") }
        }
    }

    private var receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {

                    // WiFi 상태 변화 감지
                    when (intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN
                    )) {
                        WifiManager.WIFI_STATE_DISABLING -> System.err.println("[WIFI] WIFI_STATE_DISABLING")
                        WifiManager.WIFI_STATE_DISABLED -> System.err.println("[WIFI] WIFI_STATE_DISABLED")
                        WifiManager.WIFI_STATE_ENABLING -> System.err.println("[WIFI] WIFI_STATE_ENABLING")
                        WifiManager.WIFI_STATE_ENABLED -> System.err.println("[WIFI] WIFI_STATE_ENABLED")
                        else -> System.err.println("[WIFI] Unknown")
                    }
                }
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        // 네트워크 상태 변화 감지
        override fun onAvailable(network: Network) {
            System.err.println("[Network] CONNECTED")
        }

        override fun onLost(network: Network) {
            System.err.println("[Network] DISCONNECTED")
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun checkPermission() {
        val permissions = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )

        for (permission in permissions) {
            val chk = checkCallingOrSelfPermission(Manifest.permission.WRITE_CONTACTS)
            if (chk == PackageManager.PERMISSION_DENIED) {
                requestPermissions(permissions, 0)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0) {
            for (element in grantResults) {
                if (element == PackageManager.PERMISSION_GRANTED) {
                } else {
                    TedPermission(this)
                        .setPermissionListener(object : PermissionListener {
                            override fun onPermissionGranted() {

                            }

                            override fun onPermissionDenied(deniedPermissions: ArrayList<String?>) {

                            }
                        })
                        .setDeniedMessage("You have permission to set up.")
                        .setPermissions(
                            Manifest.permission.INTERNET,
                            Manifest.permission.ACCESS_NETWORK_STATE,
                            Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.CHANGE_WIFI_STATE
                        )
                        .setGotoSettingButton(true)
                        .check();
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        wifiClient.disconnectFromServer()
    }
}