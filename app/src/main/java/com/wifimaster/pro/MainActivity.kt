
package com.wifimaster.pro

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.mlkit.codescanner.GmsBarcodeScanning

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var connectivityManager: ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE)
                    as ConnectivityManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 50, 35, 30)
            setBackgroundColor(0xFF101827.toInt())
        }

        val title = TextView(this).apply {
            text = "WiFi Master Pro"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
        }

        status = TextView(this).apply {
            text = "Welcome! Scan an authorized Wi-Fi QR code."
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 35, 0, 35)
        }

        val scanButton = Button(this).apply {
            text = "SCAN WI-FI QR CODE"
            setOnClickListener {
                scanQRCode()
            }
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(scanButton)

        setContentView(layout)
    }

    private fun scanQRCode() {
        val scanner = GmsBarcodeScanning.getClient(this)

        status.text = "Opening QR scanner..."

        scanner.startScan()
            .addOnSuccessListener { barcode ->

                val qrData = barcode.rawValue

                if (qrData.isNullOrBlank()) {
                    status.text = "No Wi-Fi information found."
                    return@addOnSuccessListener
                }

                processWifiQR(qrData)
            }
            .addOnFailureListener {
                status.text = "Scanner failed. Please try again."
            }
    }

    private fun processWifiQR(data: String) {

        if (!data.startsWith("WIFI:")) {
            status.text = "This is not a standard Wi-Fi QR code."
            return
        }

        val ssid = Regex("""(?:^|;)S:((?:\\.|[^;])*)""")
            .find(data)?.groupValues?.get(1)
            ?.replace("\\;", ";")
            ?.replace("\\:", ":")
            ?.replace("\\\\", "\\")

        val password = Regex("""(?:^|;)P:((?:\\.|[^;])*)""")
            .find(data)?.groupValues?.get(1)
            ?.replace("\\;", ";")
            ?.replace("\\:", ":")
            ?.replace("\\\\", "\\")

        val security = Regex("""(?:^|;)T:([^;]*)""")
            .find(data)?.groupValues?.get(1)

        if (ssid.isNullOrBlank()) {
            status.text = "Wi-Fi network name is missing."
            return
        }

        status.text = "Network found: $ssid\nRequesting connection..."

        connectToWifi(ssid, password, security)
    }

    private fun connectToWifi(
        ssid: String,
        password: String?,
        security: String?
    ) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            status.text =
                "Automatic QR connection requires Android 10 or newer."
            return
        }

        try {
            val builder = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)

            when (security?.uppercase()) {
                "WPA", "WPA2" -> {
                    if (password.isNullOrEmpty()) {
                        status.text = "Wi-Fi password is missing."
                        return
                    }
                    builder.setWpa2Passphrase(password)
                }

                "WPA3" -> {
                    if (password.isNullOrEmpty()) {
                        status.text = "Wi-Fi password is missing."
                        return
                    }
                    builder.setWpa3Passphrase(password)
                }

                "NOPASS" -> {
                    // Open network: no password required.
                }

                else -> {
                    status.text =
                        "Unsupported Wi-Fi security type: $security"
                    return
                }
            }

            val specifier = builder.build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            networkCallback?.let {
                connectivityManager.unregisterNetworkCallback(it)
            }

            networkCallback =
                object : ConnectivityManager.NetworkCallback() {

                    override fun onAvailable(network: Network) {
                        runOnUiThread {
                            status.text =
                                "Connection established to $ssid"
                        }
                    }

                    override fun onUnavailable() {
                        runOnUiThread {
                            status.text =
                                "Connection unavailable. Check the QR code and try again."
                        }
                    }

                    override fun onLost(network: Network) {
                        runOnUiThread {
                            status.text = "Wi-Fi connection lost."
                        }
                    }
                }

            connectivityManager.requestNetwork(
                request,
                networkCallback!!
            )

            status.text =
                "Waiting for Android connection approval..."

        } catch (e: Exception) {
            status.text = "Connection error: ${e.message}"
        }
    }

    override fun onDestroy() {
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
        super.onDestroy()
    }
}
