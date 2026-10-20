
package com.example.blooddononerapp

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class StaffProfileActivity : AppCompatActivity() {

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private lateinit var txtStaffName: TextView
    private lateinit var txtStaffId: TextView
    private lateinit var txtStaffHospital: TextView
    private lateinit var txtStaffRole: TextView
    private lateinit var txtStaffEmail: TextView
    private lateinit var txtStaffPhone: TextView
    private lateinit var txtAccountStatus: TextView
    private lateinit var txtProfileSync: TextView
    private lateinit var btnRefreshProfile: MaterialCardView

    private var screenActive = false
    private var requestVersion = 0
    private var loading = false
    private var forcedLogout = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_staff_profile)

        txtStaffName = findViewById(R.id.txtStaffName)
        txtStaffId = findViewById(R.id.txtStaffId)
        txtStaffHospital = findViewById(R.id.txtStaffHospital)
        txtStaffRole = findViewById(R.id.txtStaffRole)
        txtStaffEmail = findViewById(R.id.txtStaffEmail)
        txtStaffPhone = findViewById(R.id.txtStaffPhone)

        txtAccountStatus =
            findViewById(R.id.txtAccountStatus)

        txtProfileSync =
            findViewById(R.id.txtProfileSync)

        btnRefreshProfile =
            findViewById(R.id.btnRefreshProfile)

        findViewById<TextView>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        // Refresh Firebase profile manually.
        btnRefreshProfile.setOnClickListener {
            loadProfile(manual = true)
        }

        // Notification settings screen.
        findViewById<MaterialCardView>(
            R.id.cardNotificationSettings
        ).setOnClickListener {

            openScreen(
                NotificationSettingsActivity::class.java
            )
        }

        // Help and support screen.
        findViewById<MaterialCardView>(
            R.id.cardHelp
        ).setOnClickListener {

            openScreen(
                HelpSupportActivity::class.java
            )
        }

        // Professional logout confirmation.
        findViewById<MaterialCardView>(
            R.id.cardLogout
        ).setOnClickListener {

            confirmLogout()
        }

        showLoadingState()
    }

    override fun onStart() {
        super.onStart()

        screenActive = true

        loadProfile(manual = false)
    }

    override fun onStop() {
        screenActive = false
        requestVersion++

        super.onStop()
    }

    // =====================================
    // FIREBASE STAFF PROFILE
    // =====================================

    private fun loadProfile(manual: Boolean) {

        if (loading || forcedLogout) return

        val user = auth.currentUser ?: run {
            navigateToLogin()
            return
        }

        val token = ++requestVersion

        loading = true
        btnRefreshProfile.isEnabled = false

        txtProfileSync.text =
            "Checking Firebase profile…"

        db.collection("users")
            .document(user.uid)
            .get(Source.SERVER)
            .addOnSuccessListener { doc ->

                if (!isCurrent(token)) {
                    return@addOnSuccessListener
                }

                loading = false
                btnRefreshProfile.isEnabled = true

                val isValidStaff =
                    doc.exists() &&
                            doc.getString("role").equals(
                                "STAFF",
                                ignoreCase = true
                            ) &&
                            doc.getString("status").equals(
                                "Active",
                                ignoreCase = true
                            )

                if (!isValidStaff) {
                    forceLogoutForInvalidAccount()
                    return@addOnSuccessListener
                }

                val fullName =
                    doc.getString("fullName")
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: "Hospital Staff"

                val staffId =
                    doc.getString("staffId")
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: "Not assigned"

                val hospital =
                    doc.getString("hospital")
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: "Not assigned"

                val email =
                    doc.getString("email")
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: user.email?.takeIf {
                            it.isNotBlank()
                        }
                        ?: "Not available"

                val phone =
                    doc.getString("phone")
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: "Not available"

                // Update Staff UI.
                txtStaffName.text = fullName

                txtStaffId.text =
                    "Staff ID: $staffId"

                txtStaffHospital.text = hospital

                txtStaffRole.text =
                    "Hospital Staff"

                txtStaffEmail.text = email

                txtStaffPhone.text = phone

                txtAccountStatus.text =
                    "● Active staff"

                txtProfileSync.text =
                    "✓ Profile synced with Firebase"

                // Show success only for manual refresh.
                if (manual) {
                    StaffFeedbackDialogs.show(
                        this,
                        StaffFeedbackDialogs.Type.SUCCESS,
                        "Profile refreshed",
                        "Your staff information has been updated from Firebase."
                    )
                }
            }
            .addOnFailureListener { error ->

                if (!isCurrent(token)) {
                    return@addOnFailureListener
                }

                loading = false
                btnRefreshProfile.isEnabled = true

                txtProfileSync.text =
                    "Profile sync failed — tap Refresh"

                // Network failure does not sign out
                // a previously authenticated account.
                StaffFeedbackDialogs.show(
                    this,
                    StaffFeedbackDialogs.Type.ERROR,
                    "Unable to load profile",
                    "We couldn't verify the latest staff information. " +
                            "Check your connection and try again.\n\n" +
                            (error.localizedMessage
                                ?: "Firebase unavailable"),
                    positiveText = "Retry",
                    negativeText = "Close",
                    onPositive = {
                        loadProfile(manual = true)
                    }
                )
            }
    }

    private fun showLoadingState() {

        txtStaffName.text = "Hospital Staff"
        txtStaffId.text = "Staff ID: —"
        txtStaffHospital.text = "Loading…"
        txtStaffRole.text = "Hospital Staff"
        txtStaffEmail.text = "Loading…"
        txtStaffPhone.text = "Loading…"

        txtAccountStatus.text = "● Verifying"

        txtProfileSync.text =
            "Checking Firebase profile…"
    }

    // =====================================
    // INVALID / SUSPENDED ACCOUNT
    // =====================================

    private fun forceLogoutForInvalidAccount() {

        if (forcedLogout) return

        forcedLogout = true
        requestVersion++

        clearSession()
        showLoadingState()

        txtAccountStatus.text =
            "● Access unavailable"

        txtProfileSync.text = "Session ended"

        StaffFeedbackDialogs.show(
            this,
            StaffFeedbackDialogs.Type.ERROR,
            "Staff access unavailable",
            "This account is no longer an active STAFF account. " +
                    "Please contact your administrator.",
            positiveText = "Go to Login",
            cancelable = false,
            onPositive = {
                navigateToLogin()
            }
        )
    }

    // =====================================
    // PROFESSIONAL LOGOUT
    // =====================================

    private fun confirmLogout() {

        if (forcedLogout) return

        StaffFeedbackDialogs.show(
            this,
            StaffFeedbackDialogs.Type.LOGOUT,
            "Log out of staff account?",
            "You'll return to the login screen. " +
                    "Your saved hospital and donation records " +
                    "will remain in Firebase.",
            positiveText = "Yes, Log Out",
            negativeText = "Cancel",
            onPositive = {

                clearSession()
                navigateToLogin()
            }
        )
    }

    private fun clearSession() {

        auth.signOut()

        getSharedPreferences(
            "login_session",
            MODE_PRIVATE
        )
            .edit()
            .clear()
            .apply()
    }

    private fun navigateToLogin() {

        val intent = Intent(
            this,
            LoginActivity::class.java
        )

        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
    }

    private fun openScreen(destination: Class<*>) {

        if (forcedLogout) return

        startActivity(
            Intent(this, destination)
        )
    }

    private fun isCurrent(token: Int): Boolean {

        return token == requestVersion &&
                screenActive &&
                !isFinishing &&
                !isDestroyed
    }
}
