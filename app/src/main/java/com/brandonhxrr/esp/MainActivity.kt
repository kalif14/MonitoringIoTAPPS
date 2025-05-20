package com.brandonhxrr.esp

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var tvLastUpdate: TextView
    private lateinit var tvUserInfo: TextView
    private lateinit var tvDepth: TextView
    private lateinit var tvVolume: TextView
    private lateinit var tvPercentage: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var txtFirebaseUrl: TextInputEditText
    private lateinit var btnConnect: Button

    // Firebase
    private lateinit var database: DatabaseReference
    private var valueEventListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views and Firebase
        initializeViews()
        initializeFirebase()
        setupClickListeners()

        // Set initial timestamp and user info
        updateTimestamp()
        tvUserInfo.text = "User: kalif14"
    }

    private fun initializeViews() {
        try {
            // Find all views by their IDs
            tvLastUpdate = findViewById(R.id.tvLastUpdate)
            tvUserInfo = findViewById(R.id.tvUserInfo)
            tvDepth = findViewById(R.id.tvTemperature)
            tvVolume = findViewById(R.id.tvVoltage)
            tvPercentage = findViewById(R.id.tvRPM)
            tvConnectionStatus = findViewById(R.id.tvCapacitance)
            txtFirebaseUrl = findViewById(R.id.txt_ip)
            btnConnect = findViewById(R.id.btn_connect)

            // Set initial values
            tvDepth.text = "0.0 cm"
            tvVolume.text = "0.0 L"
            tvPercentage.text = "0.0%"
            tvConnectionStatus.text = "Disconnected"
        } catch (e: Exception) {
            showToast("Error initializing views: ${e.message}")
        }
    }

    private fun initializeFirebase() {
        try {
            database = FirebaseDatabase.getInstance().reference.child("fuelData")
        } catch (e: Exception) {
            showToast("Firebase initialization error: ${e.message}")
        }
    }

    private fun setupClickListeners() {
        btnConnect.setOnClickListener {
            val firebaseUrl = txtFirebaseUrl.text.toString().trim()
            if (firebaseUrl.isNotEmpty()) {
                connectToFirebase()
            } else {
                showToast("Please enter Firebase URL")
            }
        }
    }

    private fun connectToFirebase() {
        try {
            // Remove existing listener if any
            valueEventListener?.let {
                database.removeEventListener(it)
            }

            valueEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        if (!snapshot.exists()) {
                            updateConnectionStatus("No Data")
                            return
                        }

                        // Get values with null safety
                        val depth = snapshot.child("depth").getValue(Float::class.java) ?: 0f
                        val volume = snapshot.child("volume").getValue(Float::class.java) ?: 0f
                        val percentage = snapshot.child("percentage").getValue(Float::class.java) ?: 0f

                        // Update UI
                        updateValues(depth, volume, percentage)
                        updateConnectionStatus("Connected")
                        updateTimestamp()

                    } catch (e: Exception) {
                        updateConnectionStatus("Error")
                        showToast("Error reading data: ${e.message}")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    handleDatabaseError(error)
                }
            }

            // Add the listener to Firebase
            database.addValueEventListener(valueEventListener!!)
            showToast("Connecting to Firebase...")

        } catch (e: Exception) {
            updateConnectionStatus("Error")
            showToast("Connection error: ${e.message}")
        }
    }

    private fun updateValues(depth: Float, volume: Float, percentage: Float) {
        runOnUiThread {
            tvDepth.text = String.format("%.1f cm", depth)
            tvVolume.text = String.format("%.1f L", volume)
            tvPercentage.text = String.format("%.1f%%", percentage)
        }
    }

    private fun updateConnectionStatus(status: String) {
        runOnUiThread {
            tvConnectionStatus.text = status
        }
    }

    private fun updateTimestamp() {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())
        tvLastUpdate.text = "Last Update: $timestamp UTC"
    }

    private fun handleDatabaseError(error: DatabaseError) {
        val message = when (error.code) {
            DatabaseError.PERMISSION_DENIED -> "Permission denied"
            DatabaseError.NETWORK_ERROR -> "Network error"
            DatabaseError.DISCONNECTED -> "Disconnected"
            else -> "Database Error: ${error.message}"
        }
        updateConnectionStatus("Error")
        showToast(message)
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up listener
        valueEventListener?.let {
            database.removeEventListener(it)
        }
    }
}