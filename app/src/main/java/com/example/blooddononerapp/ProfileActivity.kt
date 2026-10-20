
package com.example.blooddononerapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges

class ProfileActivity : AppCompatActivity() {

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private lateinit var txtProfileName: TextView
    private lateinit var txtProfileEmail: TextView
    private lateinit var txtProfileBloodGroup: TextView
    private lateinit var txtAvailability: TextView

    private lateinit var btnLogout: MaterialButton

    private var profileListener: ListenerRegistration? = null
    private var active = false
    private var verified = false
    private var generation = 0

    private val destinations by lazy {
        listOf(
            R.id.cardEditProfile to
                    EditProfileActivity::class.java,

            R.id.cardDonationHistory to
                    DonationHistoryActivity::class.java,

            R.id.cardMyRequests to
                    MyRequestsActivity::class.java,

            R.id.cardSavedBloodBanks to
                    BloodBankAvailabilityActivity::class.java,

            R.id.cardNotificationSettings to
                    NotificationSettingsActivity::class.java,

            R.id.cardPrivacySettings to
                    PrivacySettingsActivity::class.java,

            R.id.cardHelpSupport to
                    HelpSupportActivity::class.java
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_profile)

        txtProfileName =
            findViewById(R.id.txtProfileName)

        txtProfileEmail =
            findViewById(R.id.txtProfileEmail)

        txtProfileBloodGroup =
            findViewById(R.id.txtProfileBloodGroup)

        txtAvailability =
            findViewById(R.id.txtAvailability)

        btnLogout =
            findViewById(R.id.btnLogout)

        findViewById<TextView>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        destinations.forEach { (id, destination) ->

            findViewById<MaterialCardView>(id)
                .apply {

                    radius =
                        16f * resources.displayMetrics.density

                    cardElevation = 0f

                    setOnClickListener {
                        openActivity(destination)
                    }
                }
        }

        // This menu opens the hospital directory,
        // not a persisted list of saved hospitals.
        changeExistingLabel(
            findViewById(android.R.id.content),
            "Saved Blood Banks",
            "Blood Bank Directory"
        )

        btnLogout.setOnClickListener {
            confirmLogout()
        }

        showLoading()
    }

    override fun onStart() {
        super.onStart()

        active = true
        listenToProfile()
    }

    override fun onStop() {
        active = false
        generation++

        profileListener?.remove()
        profileListener = null

        verified = false

        super.onStop()
    }

    private fun valid(
        token: Int,
        uid: String
    ): Boolean {

        return active &&
                generation == token &&
                auth.currentUser?.uid == uid &&
                !isFinishing &&
                !isDestroyed
    }

    // ==========================================
    // FIREBASE USER PROFILE
    // ==========================================

    private fun listenToProfile() {

        val currentUser = auth.currentUser ?: run {

            showUnavailable(
                "Please sign in to view your profile."
            )

            return
        }

        val token = ++generation

        verified = false

        showLoading()

        profileListener?.remove()

        profileListener = db
            .collection("users")
            .document(currentUser.uid)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { document, error ->

                if (!valid(token, currentUser.uid)) {
                    return@addSnapshotListener
                }

                if (error != null) {

                    showUnavailable(
                        "Cannot load profile. Check your connection."
                    )

                    return@addSnapshotListener
                }

                if (
                    document == null ||
                    document.metadata.isFromCache ||
                    document.metadata.hasPendingWrites()
                ) {
                    return@addSnapshotListener
                }

                val status =
                    document.getString("status")
                        ?.ifBlank { "Active" }
                        ?: "Active"

                val validUser =
                    document.exists() &&
                            document.getString("role")
                                .equals("USER", true) &&
                            status.equals("Active", true)

                if (!validUser) {

                    verified = false

                    showUnavailable(
                        "Your USER account is unavailable."
                    )

                    return@addSnapshotListener
                }

                verified = true

                val fullName =
                    document.getString("fullName")
                        .orEmpty()
                        .trim()
                        .ifBlank { "Blood Donor" }

                val email =
                    currentUser.email
                        ?: document.getString("email")
                            .orEmpty()

                val bloodGroup =
                    document.getString("bloodGroup")
                        .orEmpty()

                val available =
                    document.getBoolean(
                        "availableToDonate"
                    ) == true

                txtProfileName.text = fullName

                txtProfileEmail.text =
                    email.ifBlank { "Email unavailable" }

                txtProfileBloodGroup.text =
                    bloodGroup.ifBlank { "—" }

                txtAvailability.text =
                    if (available) {
                        "● Available for Contact"
                    } else {
                        "● Not Available"
                    }

                txtAvailability.setTextColor(
                    if (available) {
                        Color.parseColor("#15803D")
                    } else {
                        Color.parseColor("#777777")
                    }
                )

                updateNavigation(true)
            }
    }

    private fun showLoading() {

        txtProfileName.text = "Loading profile..."
        txtProfileEmail.text = "Syncing Firebase..."
        txtProfileBloodGroup.text = "—"
        txtAvailability.text = "● Checking..."

        txtAvailability.setTextColor(
            Color.parseColor("#888888")
        )

        updateNavigation(false)
    }

    private fun showUnavailable(
        message: String
    ) {

        verified = false

        txtProfileName.text = "Profile Unavailable"
        txtProfileEmail.text = message
        txtProfileBloodGroup.text = "—"
        txtAvailability.text = "● Unavailable"

        txtAvailability.setTextColor(
            Color.parseColor("#DC2626")
        )

        updateNavigation(false)
    }

    private fun updateNavigation(enabled: Boolean) {

        destinations.forEach { (id, _) ->

            findViewById<MaterialCardView>(id)
                .apply {
                    isEnabled = enabled
                    alpha = if (enabled) 1f else 0.6f
                }
        }

        // Logout remains available even if the
        // profile document cannot be loaded.
        btnLogout.isEnabled =
            auth.currentUser != null
    }

    // ==========================================
    // PROFESSIONAL LOGOUT
    // ==========================================

    private fun confirmLogout() {

        if (auth.currentUser == null) {
            openLogin()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Log Out of Your Account?")
            .setMessage(
                "You will be securely signed out " +
                        "of your Blood Donor account.\n\n" +
                        "Your Firebase donation history and " +
                        "requests will remain saved."
            )
            .setNegativeButton(
                "Stay Logged In",
                null
            )
            .setPositiveButton(
                "Log Out"
            ) { _, _ ->

                logout()
            }
            .show()
    }

    private fun logout() {

        profileListener?.remove()
        profileListener = null

        verified = false

        auth.signOut()

        getSharedPreferences(
            "login_session",
            MODE_PRIVATE
        ).edit()
            .clear()
            .apply()

        openLogin()
    }

    private fun openLogin() {

        startActivity(
            Intent(
                this,
                LoginActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )

        overridePendingTransition(
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )

        finish()
    }

    // ==========================================
    // NAVIGATION
    // ==========================================

    private fun openActivity(
        destination: Class<*>
    ) {

        if (auth.currentUser == null) {
            openLogin()
            return
        }

        if (!verified) {

            MaterialAlertDialogBuilder(this)
                .setTitle("Profile Unavailable")
                .setMessage(
                    "Please wait until your active " +
                            "USER profile is verified."
                )
                .setPositiveButton("OK", null)
                .show()

            return
        }

        startActivity(
            Intent(this, destination)
        )

        overridePendingTransition(
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
    }

    private fun changeExistingLabel(
        root: View,
        oldText: String,
        newText: String
    ) {

        if (
            root is TextView &&
            root.text.toString() == oldText
        ) {
            root.text = newText
            return
        }

        if (root is ViewGroup) {

            for (index in 0 until root.childCount) {
                changeExistingLabel(
                    root.getChildAt(index),
                    oldText,
                    newText
                )
            }
        }
    }
}
