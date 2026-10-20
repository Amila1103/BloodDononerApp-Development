
package com.example.blooddononerapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source

class DonorDetailsActivity : AppCompatActivity() {

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val firestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private lateinit var txtName: TextView
    private lateinit var txtBloodGroup: TextView
    private lateinit var txtLocation: TextView
    private lateinit var txtLocationTop: TextView
    private lateinit var txtDistance: TextView
    private lateinit var txtAge: TextView
    private lateinit var txtLastDonation: TextView
    private lateinit var txtEligibility: TextView
    private lateinit var txtAvailability: TextView
    private lateinit var txtLastActive: TextView

    private lateinit var btnCall: MaterialButton
    private lateinit var btnMessage: MaterialButton
    private lateinit var btnSendRequest: MaterialButton

    private var donorUid = ""
    private var donorName = ""
    private var donorBloodGroup = ""

    private var active = false
    private var verified = false
    private var busy = false
    private var generation = 0

    private var donorListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_donor_details
        )

        donorUid = intent.getStringExtra("donorUid")
            .orEmpty()

        txtName = findViewById(R.id.txtDonorName)
        txtBloodGroup = findViewById(R.id.txtBloodGroup)
        txtLocation = findViewById(R.id.txtLocation)
        txtLocationTop = findViewById(R.id.txtLocationTop)
        txtDistance = findViewById(R.id.txtDistance)
        txtAge = findViewById(R.id.txtAge)
        txtLastDonation = findViewById(R.id.txtLastDonation)
        txtEligibility = findViewById(R.id.txtEligibility)
        txtAvailability = findViewById(R.id.txtAvailability)
        txtLastActive = findViewById(R.id.txtLastActive)

        btnCall = findViewById(R.id.btnCall)
        btnMessage = findViewById(R.id.btnMessage)
        btnSendRequest = findViewById(R.id.btnSendRequest)

        findViewById<TextView>(
            R.id.btnBack
        ).setOnClickListener {
            finish()
        }

        btnCall.setOnClickListener {
            verifyBeforeAction("CALL")
        }

        btnMessage.setOnClickListener {
            verifyBeforeAction("MESSAGE")
        }

        btnSendRequest.setOnClickListener {
            verifyBeforeAction("REQUEST")
        }

        setupNavigation()
        showLoading()
    }

    override fun onStart() {
        super.onStart()

        active = true
        listenToDonor()
    }

    override fun onStop() {
        active = false
        generation++

        donorListener?.remove()
        donorListener = null

        verified = false
        busy = false

        super.onStop()
    }

    private fun isCurrent(token: Int): Boolean {
        return active &&
                token == generation &&
                !isFinishing &&
                !isDestroyed
    }

    // ======================================
    // VERIFY DONOR ELIGIBILITY
    // ======================================

    private fun validDonor(
        doc: DocumentSnapshot
    ): Boolean {

        return doc.exists() &&
                doc.id == donorUid &&
                doc.id != auth.currentUser?.uid &&
                doc.getString("role") == "USER" &&
                doc.getString("status") == "Active" &&
                doc.getBoolean("profileCompleted") == true &&
                DonorEligibility.isAdult(
                    doc.getString("dateOfBirth")
                ) &&
                doc.getBoolean(
                    "notificationSettings.donorPrivacy.allowRequesterContact"
                ) == true
    }

    private fun isAvailable(
        doc: DocumentSnapshot
    ): Boolean {

        return doc.getBoolean(
            "availableToDonate"
        ) == true
    }

    private fun canSharePhone(
        doc: DocumentSnapshot
    ): Boolean {

        return validDonor(doc) &&
                isAvailable(doc) &&
                doc.getBoolean(
                    "notificationSettings.donorPrivacy.showPhone"
                ) == true &&
                !doc.getString("phone").isNullOrBlank()
    }

    // ======================================
    // LOAD DONOR PROFILE
    // ======================================

    private fun listenToDonor() {

        val currentUid = auth.currentUser?.uid

        if (
            currentUid == null ||
            donorUid.isBlank() ||
            donorUid == currentUid
        ) {

            showUnavailable(
                "Please sign in and select a valid donor."
            )

            return
        }

        val token = ++generation

        donorListener?.remove()

        verified = false
        showLoading()

        donorListener = firestore.collection("users")
            .document(donorUid)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { doc, error ->

                if (!isCurrent(token)) {
                    return@addSnapshotListener
                }

                if (error != null) {

                    showUnavailable(
                        "Unable to verify donor profile: " +
                                error.localizedMessage
                    )

                    return@addSnapshotListener
                }

                if (
                    doc == null ||
                    doc.metadata.isFromCache ||
                    doc.metadata.hasPendingWrites()
                ) {
                    return@addSnapshotListener
                }

                if (!validDonor(doc)) {

                    showUnavailable(
                        "Donor not eligible (18+ required) " +
                                "or contact consent unavailable."
                    )

                    return@addSnapshotListener
                }

                verified = true

                renderDonor(doc)
            }
    }

    // ======================================
    // DISPLAY VERIFIED DONOR DETAILS
    // ======================================

    private fun renderDonor(
        doc: DocumentSnapshot
    ) {

        donorName = doc.getString("fullName")
            .orEmpty()
            .ifBlank {
                "Blood Donor"
            }

        donorBloodGroup = doc.getString("bloodGroup")
            .orEmpty()

        val approximate = doc.getBoolean(
            "shareApproximateLocation"
        ) == true

        val shareLocation = doc.getBoolean(
            "notificationSettings.donorPrivacy.shareLocation"
        ) ?: approximate

        val area =
            if (shareLocation && approximate) {
                doc.getString("area").orEmpty()
            } else {
                ""
            }

        val district =
            if (shareLocation) {
                doc.getString("district").orEmpty()
            } else {
                ""
            }

        val location = listOf(
            area,
            district
        )
            .filter {
                it.isNotBlank()
            }
            .distinct()
            .joinToString(", ")
            .ifBlank {
                "Location not shared"
            }

        val available = isAvailable(doc)
        val phoneAllowed = canSharePhone(doc)

        txtName.text = donorName

        txtBloodGroup.text = donorBloodGroup.ifBlank {
            "—"
        }

        txtLocation.text = location
        txtLocationTop.text = location

        txtDistance.text =
            "Exact distance not measured"

        txtAge.text =
            "18+ (based on entered DOB)"

        txtLastDonation.text =
            "Not publicly available"

        txtEligibility.text =
            "Hospital staff screening required"

        txtAvailability.text =
            if (available) {
                "● Available for contact"
            } else {
                "● Currently unavailable"
            }

        txtLastActive.text =
            if (available) {
                "Self-reported availability"
            } else {
                "Donor availability is OFF"
            }

        btnCall.isEnabled =
            verified &&
                    !busy &&
                    phoneAllowed

        btnMessage.isEnabled =
            verified &&
                    !busy &&
                    phoneAllowed

        btnSendRequest.isEnabled =
            verified &&
                    !busy &&
                    available

        updateButtonAlpha()
    }

    private fun updateButtonAlpha() {

        btnCall.alpha =
            if (btnCall.isEnabled) 1f else 0.45f

        btnMessage.alpha =
            if (btnMessage.isEnabled) 1f else 0.45f

        btnSendRequest.alpha =
            if (btnSendRequest.isEnabled) 1f else 0.45f
    }

    private fun disableActions() {

        btnCall.isEnabled = false
        btnMessage.isEnabled = false
        btnSendRequest.isEnabled = false

        updateButtonAlpha()
    }

    // ======================================
    // LOADING AND ERROR STATES
    // ======================================

    private fun showLoading() {

        verified = false

        txtName.text = "Verifying donor..."
        txtBloodGroup.text = "—"
        txtLocation.text = "Loading..."
        txtLocationTop.text = "Loading..."
        txtDistance.text = "Not measured"
        txtAge.text = "—"
        txtLastDonation.text = "—"
        txtEligibility.text = "Screening required"
        txtAvailability.text = "Checking donor availability"
        txtLastActive.text = "Syncing Firebase..."

        disableActions()
    }

    private fun showUnavailable(
        reason: String
    ) {

        verified = false

        txtName.text = "Donor Unavailable"
        txtBloodGroup.text = "—"
        txtLocation.text = "Not shared"
        txtLocationTop.text = "Not shared"
        txtDistance.text = "Not measured"
        txtAge.text = "—"
        txtLastDonation.text = "—"
        txtEligibility.text = reason
        txtAvailability.text = "Unavailable"
        txtLastActive.text = "Contact not permitted"

        disableActions()
    }

    // ======================================
    // RECHECK REQUESTER BEFORE CONTACT
    // ======================================

    private fun verifyBeforeAction(
        action: String
    ) {

        if (!verified || busy) {
            return
        }

        val requesterUid = auth.currentUser?.uid ?: run {

            showNotice(
                "Session Unavailable",
                "Please sign in again."
            )

            return
        }

        busy = true
        disableActions()

        val token = generation

        firestore.collection("users")
            .document(requesterUid)
            .get(Source.SERVER)
            .addOnSuccessListener { requester ->

                if (!isCurrent(token)) {
                    return@addOnSuccessListener
                }

                if (
                    !requester.exists() ||
                    requester.getString("role") != "USER" ||
                    requester.getString("status") != "Active"
                ) {

                    busy = false

                    showUnavailable(
                        "An active requester account is required."
                    )

                    showNotice(
                        "Access Unavailable",
                        "Please check your USER account."
                    )

                    return@addOnSuccessListener
                }

                verifyDonorAgain(
                    action,
                    token
                )
            }
            .addOnFailureListener { error ->

                if (!isCurrent(token)) {
                    return@addOnFailureListener
                }

                busy = false

                showUnavailable(
                    "Requester verification failed"
                )

                showNotice(
                    "Firebase Error",
                    error.localizedMessage ?: "Network error"
                )
            }
    }

    // ======================================
    // RECHECK DONOR BEFORE CONTACT
    // ======================================

    private fun verifyDonorAgain(
        action: String,
        token: Int
    ) {

        firestore.collection("users")
            .document(donorUid)
            .get(Source.SERVER)
            .addOnSuccessListener { doc ->

                if (!isCurrent(token)) {
                    return@addOnSuccessListener
                }

                busy = false

                if (
                    !validDonor(doc) ||
                    !isAvailable(doc)
                ) {

                    showUnavailable(
                        "Donor is not currently eligible " +
                                "or available for contact."
                    )

                    showNotice(
                        "Contact Unavailable",
                        "Donor eligibility, availability " +
                                "or consent changed."
                    )

                    return@addOnSuccessListener
                }

                verified = true
                renderDonor(doc)

                if (
                    action == "CALL" ||
                    action == "MESSAGE"
                ) {

                    if (!canSharePhone(doc)) {

                        showNotice(
                            "Phone Sharing Disabled",
                            "This donor has not allowed " +
                                    "phone sharing."
                        )

                        return@addOnSuccessListener
                    }

                    openContact(
                        action,
                        doc.getString("phone")
                            .orEmpty()
                            .trim()
                    )

                } else if (action == "REQUEST") {

                    explainEmergencyRequest()
                }
            }
            .addOnFailureListener { error ->

                if (!isCurrent(token)) {
                    return@addOnFailureListener
                }

                busy = false

                showUnavailable(
                    "Unable to recheck donor eligibility"
                )

                showNotice(
                    "Firebase Error",
                    error.localizedMessage ?: "Network error"
                )
            }
    }

    // ======================================
    // PHONE CALL AND SMS
    // ======================================

    private fun openContact(
        action: String,
        phone: String
    ) {

        try {

            val contactIntent =
                if (action == "CALL") {

                    Intent(
                        Intent.ACTION_DIAL,
                        Uri.parse(
                            "tel:${Uri.encode(phone)}"
                        )
                    )

                } else {

                    Intent(
                        Intent.ACTION_SENDTO,
                        Uri.parse(
                            "smsto:${Uri.encode(phone)}"
                        )
                    ).apply {

                        putExtra(
                            "sms_body",
                            "Hello, I found your blood donor " +
                                    "profile through the Blood Donor app."
                        )
                    }
                }

            startActivity(contactIntent)

        } catch (error: Exception) {

            showNotice(
                "Contact App Unavailable",
                error.localizedMessage
                    ?: "No supported app found"
            )
        }
    }

    // ======================================
    // EMERGENCY REQUEST
    // ======================================

    private fun explainEmergencyRequest() {

        MaterialAlertDialogBuilder(this)
            .setTitle(
                "Create Emergency Blood Request?"
            )
            .setMessage(
                "This opens the hospital emergency " +
                        "request form. It does not " +
                        "automatically message the selected donor."
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Continue"
            ) { _, _ ->

                startActivity(
                    Intent(
                        this,
                        EmergencyRequestActivity::class.java
                    ).apply {

                        putExtra(
                            "bloodGroup",
                            donorBloodGroup
                        )

                        putExtra(
                            "selectedDonorUid",
                            donorUid
                        )

                        putExtra(
                            "selectedDonorName",
                            donorName
                        )
                    }
                )
            }
            .show()
    }

    // ======================================
    // BOTTOM NAVIGATION
    // ======================================

    private fun setupNavigation() {

        findViewById<LinearLayout>(
            R.id.navHome
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    HomeActivity::class.java
                ).apply {

                    flags =
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )

            finish()
        }

        findViewById<LinearLayout>(
            R.id.navSearch
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    FindDonorActivity::class.java
                )
            )

            finish()
        }

        findViewById<LinearLayout>(
            R.id.navRequests
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    MyRequestsActivity::class.java
                )
            )
        }

        findViewById<LinearLayout>(
            R.id.navNotifications
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    NotificationsActivity::class.java
                )
            )
        }

        findViewById<LinearLayout>(
            R.id.navProfile
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    ProfileActivity::class.java
                )
            )
        }
    }

    private fun showNotice(
        title: String,
        message: String
    ) {

        if (
            !active ||
            isFinishing ||
            isDestroyed
        ) {
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(
                "OK",
                null
            )
            .show()
    }
}
