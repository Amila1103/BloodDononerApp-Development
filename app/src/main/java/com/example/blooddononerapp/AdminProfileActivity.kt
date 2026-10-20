
package com.example.blooddononerapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.Locale

class AdminProfileActivity : AppCompatActivity() {

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private var logoutDialog: AlertDialog? = null

    private var nameViews: List<TextView> = emptyList()
    private var adminIdViews: List<TextView> = emptyList()
    private var emailViews: List<TextView> = emptyList()
    private var statusViews: List<TextView> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_admin_profile)

        val root = findViewById<ViewGroup>(
            android.R.id.content
        )

        nameViews = findViewsByText(
            root,
            "System Administrator"
        )

        adminIdViews = findViewsByText(
            root,
            "ADMIN-001"
        )

        emailViews = findViewsByText(
            root,
            "admin@bloodcare.lk"
        )

        statusViews = findViewsByText(
            root,
            "Active"
        )

        findViewById<MaterialCardView>(
            R.id.btnBack
        ).setOnClickListener {
            finish()
        }

        findViewById<MaterialCardView>(
            R.id.cardAdminNotificationSettings
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    NotificationSettingsActivity::class.java
                )
            )
        }

        findViewById<MaterialCardView>(
            R.id.cardAdminHelp
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    HelpSupportActivity::class.java
                )
            )
        }

        val btnLogout = findViewById<MaterialButton>(
            R.id.btnAdminLogout
        )

        btnLogout.isEnabled = true
        btnLogout.isClickable = true

        btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    override fun onStart() {
        super.onStart()
        loadAdminProfile()
    }

    private fun loadAdminProfile() {

        val user = auth.currentUser

        if (user == null) {
            performLogout(
                "Please login again"
            )
            return
        }

        db.collection("users")
            .document(user.uid)
            .get(Source.SERVER)
            .addOnSuccessListener { document ->

                if (isFinishing || isDestroyed) {
                    return@addOnSuccessListener
                }

                if (!document.exists()) {
                    performLogout(
                        "Admin profile not found"
                    )
                    return@addOnSuccessListener
                }

                val role = document.getString("role")
                    ?: ""

                val status = document.getString("status")
                    ?: ""

                val active = document.getBoolean("active")
                    ?: true

                if (
                    role != "ADMIN" ||
                    !status.equals(
                        "Active",
                        ignoreCase = true
                    ) ||
                    !active
                ) {
                    performLogout(
                        "Admin account is not active"
                    )
                    return@addOnSuccessListener
                }

                val adminName = document.getString("fullName")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Administrator"

                val adminId = document.getString("adminId")
                    ?.takeIf { it.isNotBlank() }
                    ?: "ADMIN-${user.uid.take(8).uppercase(Locale.ROOT)}"

                val adminEmail = document.getString("email")
                    ?.takeIf { it.isNotBlank() }
                    ?: user.email.orEmpty()

                for (view in nameViews) {
                    view.text = adminName
                }

                for (view in adminIdViews) {
                    view.text = adminId
                }

                for (view in emailViews) {
                    view.text = adminEmail
                }

                for (view in statusViews) {
                    view.text = status
                }
            }
            .addOnFailureListener { error ->

                if (!isFinishing && !isDestroyed) {

                    Toast.makeText(
                        this,
                        "Cannot load Admin Profile: ${error.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun findViewsByText(
        view: View,
        target: String
    ): List<TextView> {

        val foundViews = mutableListOf<TextView>()

        if (view is TextView) {

            if (view.text.toString() == target) {
                foundViews.add(view)
            }
        }

        if (view is ViewGroup) {

            for (index in 0 until view.childCount) {

                foundViews.addAll(
                    findViewsByText(
                        view.getChildAt(index),
                        target
                    )
                )
            }
        }

        return foundViews
    }

    private fun showLogoutConfirmation() {

        if (isFinishing || isDestroyed) {
            return
        }

        if (logoutDialog?.isShowing == true) {
            return
        }

        logoutDialog = AlertDialog.Builder(this)
            .setTitle("Admin Logout")
            .setMessage(
                "Are you sure you want to logout from your Admin account?"
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Logout") { _, _ ->

                performLogout(
                    "Admin logged out successfully"
                )
            }
            .create()

        logoutDialog?.show()
    }

    private fun performLogout(message: String) {

        if (isFinishing || isDestroyed) {
            return
        }

        // Firebase Authentication logout
        auth.signOut()

        // Clear stored login session
        getSharedPreferences(
            "login_session",
            MODE_PRIVATE
        ).edit()
            .clear()
            .apply()

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()

        // Redirect to Login Screen
        val intent = Intent(
            this,
            LoginActivity::class.java
        ).apply {

            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
    }

    override fun onDestroy() {

        logoutDialog?.dismiss()
        logoutDialog = null

        super.onDestroy()
    }
}
