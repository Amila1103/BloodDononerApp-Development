
package com.example.blooddononerapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import java.util.Locale

class StaffDonorMatchingActivity : AppCompatActivity() {

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private lateinit var txtRequestId: TextView
    private lateinit var txtRequiredBlood: TextView
    private lateinit var txtEmptyDonors: TextView
    private lateinit var donorContainer: LinearLayout

    private var requestDocumentId = ""
    private var requestId = ""
    private var bloodGroup = ""
    private var requestArea = ""
    private var requestHospital = ""

    private var staffUid = ""
    private var staffHospital = ""

    private var active = false
    private var ready = false
    private var generation = 0

    private var donorListener: ListenerRegistration? = null

    private val notifyingDonors =
        mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_staff_donor_matching
        )

        txtRequestId = findViewById(R.id.txtRequestId)
        txtRequiredBlood = findViewById(R.id.txtRequiredBlood)
        txtEmptyDonors = findViewById(R.id.txtEmptyDonors)
        donorContainer = findViewById(R.id.donorContainer)

        requestDocumentId =
            intent.getStringExtra("REQUEST_DOCUMENT_ID")
                .orEmpty()

        requestId =
            intent.getStringExtra("REQUEST_ID")
                .orEmpty()

        bloodGroup =
            intent.getStringExtra("BLOOD_GROUP")
                .orEmpty()

        requestArea =
            intent.getStringExtra("AREA")
                .orEmpty()

        requestHospital =
            intent.getStringExtra("HOSPITAL")
                .orEmpty()

        txtRequestId.text =
            requestId.ifBlank { "Emergency Request" }

        txtRequiredBlood.text =
            if (bloodGroup.isBlank()) {
                "Blood Required"
            } else {
                "$bloodGroup Required"
            }

        findViewById<TextView>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        showLoading(
            "Checking emergency request..."
        )
    }

    override fun onStart() {
        super.onStart()

        active = true
        loadStaffProfile()
    }

    override fun onStop() {
        active = false
        generation++

        donorListener?.remove()
        donorListener = null

        ready = false
        notifyingDonors.clear()

        super.onStop()
    }

    private fun valid(token: Int): Boolean {
        return active &&
                generation == token &&
                !isFinishing &&
                !isDestroyed
    }

    private fun loadStaffProfile() {

        val user = auth.currentUser ?: run {
            showLoading("Staff sign-in required")
            return
        }

        if (requestDocumentId.isBlank()) {
            showLoading(
                "Emergency request document ID is missing"
            )
            return
        }

        val token = ++generation

        donorListener?.remove()
        donorListener = null

        donorContainer.removeAllViews()
        ready = false

        staffUid = user.uid

        showLoading(
            "Verifying hospital staff..."
        )

        db.collection("users")
            .document(user.uid)
            .get(Source.SERVER)
            .addOnSuccessListener { staff ->

                if (!valid(token)) {
                    return@addOnSuccessListener
                }

                val assigned =
                    staff.getString("hospital")
                        .orEmpty()
                        .trim()

                val validStaff =
                    staff.exists() &&
                            staff.getString("role") == "STAFF" &&
                            staff.getString("status")
                                .equals("Active", true) &&
                            assigned.isNotBlank() &&
                            !assigned.equals("Not Assigned", true)

                if (!validStaff) {
                    blockAccess(
                        "An active STAFF account with " +
                                "an assigned hospital is required."
                    )
                    return@addOnSuccessListener
                }

                staffHospital = assigned

                verifyRequest(token)
            }
            .addOnFailureListener { error ->

                if (valid(token)) {
                    showError(
                        "Staff Verification Failed",
                        error.localizedMessage
                            ?: "Unable to verify staff account."
                    )
                }
            }
    }

    private fun verifyRequest(token: Int) {

        showLoading(
            "Verifying emergency request..."
        )

        db.collection("emergencyRequests")
            .document(requestDocumentId)
            .get(Source.SERVER)
            .addOnSuccessListener { request ->

                if (!valid(token)) {
                    return@addOnSuccessListener
                }

                if (!request.exists()) {
                    blockAccess(
                        "Emergency request no longer exists."
                    )
                    return@addOnSuccessListener
                }

                val assignedHospital =
                    request.getString("hospital")
                        .orEmpty()

                val status =
                    request.getString("status")
                        .orEmpty()

                if (assignedHospital != staffHospital) {
                    blockAccess(
                        "This request belongs to another hospital."
                    )
                    return@addOnSuccessListener
                }

                if (!status.equals("Matching", true)) {
                    blockAccess(
                        "Request status is $status. " +
                                "Donor matching is available only " +
                                "for Matching requests."
                    )
                    return@addOnSuccessListener
                }

                requestId =
                    request.getString("requestId") ?: request.id

                bloodGroup =
                    request.getString("bloodGroup")
                        .orEmpty()

                requestArea =
                    request.getString("area")
                        .orEmpty()

                requestHospital = assignedHospital

                if (bloodGroup.isBlank()) {
                    blockAccess(
                        "Request blood group is missing."
                    )
                    return@addOnSuccessListener
                }

                txtRequestId.text = requestId
                txtRequiredBlood.text =
                    "$bloodGroup Required"

                ready = true

                listenToMatchingDonors(token)
            }
            .addOnFailureListener { error ->

                if (valid(token)) {
                    showError(
                        "Request Verification Failed",
                        error.localizedMessage
                            ?: "Cannot load emergency request."
                    )
                }
            }
    }

    private fun listenToMatchingDonors(token: Int) {

        donorListener?.remove()
        donorListener = null

        donorContainer.removeAllViews()

        showLoading(
            "Finding available $bloodGroup donors..."
        )

        donorListener = db.collection("users")
            .whereEqualTo("bloodGroup", bloodGroup)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { snapshot, error ->

                if (!valid(token) || !ready) {
                    return@addSnapshotListener
                }

                if (error != null) {
                    showLoading(
                        "Unable to load matching donors"
                    )
                    return@addSnapshotListener
                }

                if (
                    snapshot == null ||
                    snapshot.metadata.isFromCache
                ) {
                    return@addSnapshotListener
                }

                donorContainer.removeAllViews()

                val candidates = snapshot.documents.filter {
                    isAvailableDonor(it)
                }.sortedBy {
                    it.getString("fullName")
                        .orEmpty()
                        .lowercase(Locale.ROOT)
                }

                if (candidates.isEmpty()) {
                    showLoading(
                        "No available $bloodGroup donors found"
                    )
                    return@addSnapshotListener
                }

                txtEmptyDonors.visibility = View.GONE

                for (donor in candidates) {
                    addDonorWithCount(donor, token)
                }
            }
    }

    private fun isAvailableDonor(
        document: DocumentSnapshot
    ): Boolean {

        val validRole =
            document.getString("role")
                .equals("USER", true)

        val available = when (
            val value = document.get("availableToDonate")
        ) {
            is Boolean -> value

            is String -> {
                value.equals("true", true) ||
                        value.equals("yes", true)
            }

            else -> false
        }

        val activeStatus =
            document.getString("status")
                .orEmpty()

        return validRole &&
                available &&
                !activeStatus.equals("Suspended", true) &&
                !activeStatus.equals("Inactive", true)
    }

    private fun addDonorWithCount(
        donor: DocumentSnapshot,
        token: Int
    ) {

        val uid = donor.id

        val name =
            donor.getString("fullName")
                ?.takeIf { it.isNotBlank() }
                ?: "Donor"

        val phone =
            donor.getString("phone")
                .orEmpty()
                .trim()

        val area =
            donor.getString("area")
                .orEmpty()
                .trim()

        val district =
            donor.getString("district")
                .orEmpty()
                .trim()

        val group =
            donor.getString("bloodGroup")
                ?: bloodGroup

        val displayId =
            "D-${uid.take(6).uppercase(Locale.ROOT)}"

        db.collection("donationRecords")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { records ->

                if (!valid(token) || !ready) {
                    return@addOnSuccessListener
                }

                addDonorCard(
                    uid,
                    name,
                    phone,
                    area,
                    district,
                    group,
                    displayId,
                    records.size()
                )
            }
            .addOnFailureListener {

                if (!valid(token) || !ready) {
                    return@addOnFailureListener
                }

                addDonorCard(
                    uid,
                    name,
                    phone,
                    area,
                    district,
                    group,
                    displayId,
                    null
                )
            }
    }

    private fun addDonorCard(
        donorUid: String,
        donorName: String,
        donorPhone: String,
        donorArea: String,
        donorDistrict: String,
        donorBloodGroup: String,
        donorDisplayId: String,
        previousDonations: Int?
    ) {

        val view = LayoutInflater.from(this)
            .inflate(
                R.layout.item_staff_matching_donor,
                donorContainer,
                false
            )

        view.findViewById<TextView>(
            R.id.txtDonorBloodGroup
        ).text = donorBloodGroup

        view.findViewById<TextView>(
            R.id.txtDonorName
        ).text = donorName

        val location =
            listOf(donorArea, donorDistrict)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")

        view.findViewById<TextView>(
            R.id.txtDonorInfo
        ).text =
            if (location.isBlank()) {
                "Donor ID: $donorDisplayId"
            } else {
                "$location • Donor ID: $donorDisplayId"
            }

        view.findViewById<TextView>(
            R.id.txtPreviousDonations
        ).text =
            if (previousDonations == null) {
                "Previous donations: Unavailable"
            } else {
                "Previous donations: $previousDonations"
            }

        val btnContact =
            view.findViewById<MaterialButton>(
                R.id.btnContact
            )

        val btnNotify =
            view.findViewById<MaterialButton>(
                R.id.btnNotify
            )

        btnContact.setOnClickListener {

            if (donorPhone.isBlank()) {
                showWarning(
                    "Phone Number Unavailable",
                    "This donor has not provided " +
                            "a contact phone number."
                )
            } else {
                openDialer(donorPhone)
            }
        }

        btnNotify.setOnClickListener {

            if (donorUid in notifyingDonors) {
                return@setOnClickListener
            }

            if (!ready) {
                showWarning(
                    "Matching Unavailable",
                    "Verify the request before notifying donors."
                )
                return@setOnClickListener
            }

            StaffFeedbackDialogs.show(
                this,
                StaffFeedbackDialogs.Type.WARNING,
                "Notify Donor?",
                "Donor: $donorName\n" +
                        "Blood group: $donorBloodGroup\n" +
                        "Hospital: $requestHospital\n\n" +
                        "Send an in-app emergency blood " +
                        "request notification to this donor?",
                positiveText = "Send Notification",
                negativeText = "Cancel",
                onPositive = {
                    sendDonorNotification(
                        donorUid,
                        donorName,
                        donorBloodGroup,
                        btnNotify
                    )
                }
            )
        }

        donorContainer.addView(view)
    }

    private fun openDialer(phone: String) {

        try {
            startActivity(
                Intent(
                    Intent.ACTION_DIAL,
                    Uri.parse("tel:${Uri.encode(phone)}")
                )
            )
        } catch (error: Exception) {
            showError(
                "Cannot Open Dialer",
                error.localizedMessage
                    ?: "No supported phone dialer was found."
            )
        }
    }

    private fun sendDonorNotification(
        donorUid: String,
        donorName: String,
        donorBloodGroup: String,
        button: MaterialButton
    ) {

        if (!ready || donorUid in notifyingDonors) return

        val uid = auth.currentUser?.uid ?: run {
            showError(
                "Staff Session Expired",
                "Please sign in again."
            )
            return
        }

        val token = generation

        notifyingDonors.add(donorUid)

        button.isEnabled = false
        button.text = "Sending..."

        val staffRef =
            db.collection("users").document(uid)

        val requestRef =
            db.collection("emergencyRequests")
                .document(requestDocumentId)

        val notificationRef =
            db.collection("notifications")
                .document()

        db.runTransaction { transaction ->

            val staff = transaction.get(staffRef)
            val request = transaction.get(requestRef)

            val activeStaff =
                staff.exists() &&
                        staff.getString("role") == "STAFF" &&
                        staff.getString("status")
                            .equals("Active", true) &&
                        staff.getString("hospital") == staffHospital

            if (!activeStaff) {
                throw IllegalStateException(
                    "Active staff access is required."
                )
            }

            val correctRequest =
                request.exists() &&
                        request.getString("hospital") == staffHospital &&
                        request.getString("status")
                            .equals("Matching", true) &&
                        request.getString("bloodGroup") == bloodGroup

            if (!correctRequest) {
                throw IllegalStateException(
                    "Request status or hospital has changed."
                )
            }

            val notification = hashMapOf<String, Any>(
                "notificationId" to notificationRef.id,
                "userId" to donorUid,
                "donorId" to donorUid,
                "donorName" to donorName,
                "donorBloodGroup" to donorBloodGroup,
                "requestDocumentId" to requestDocumentId,
                "requestId" to requestId,
                "bloodGroup" to bloodGroup,
                "area" to requestArea,
                "hospital" to requestHospital,
                "sentBy" to staffUid,
                "sentByRole" to "STAFF",
                "staffHospital" to staffHospital,
                "type" to "EMERGENCY_BLOOD_REQUEST",
                "title" to "Emergency Blood Request",
                "message" to
                        "$bloodGroup blood is urgently required " +
                        "at $requestHospital.",
                "status" to "Sent",
                "read" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )

            transaction.set(notificationRef, notification)

            true
        }
            .addOnSuccessListener {

                if (!valid(token)) {
                    return@addOnSuccessListener
                }

                notifyingDonors.remove(donorUid)

                button.isEnabled = false
                button.text = "Notified"

                StaffFeedbackDialogs.show(
                    this,
                    StaffFeedbackDialogs.Type.SUCCESS,
                    "Notification Sent",
                    "An in-app emergency notification " +
                            "was saved for $donorName in Firebase."
                )
            }
            .addOnFailureListener { error ->

                if (!valid(token)) {
                    return@addOnFailureListener
                }

                notifyingDonors.remove(donorUid)

                button.isEnabled = true
                button.text = "Notify Donor"

                showError(
                    "Notification Failed",
                    error.localizedMessage
                        ?: "Unable to save the notification."
                )
            }
    }

    private fun showLoading(message: String) {

        txtEmptyDonors.visibility = View.VISIBLE
        txtEmptyDonors.text = message
    }

    private fun blockAccess(message: String) {

        ready = false

        donorListener?.remove()
        donorListener = null

        donorContainer.removeAllViews()

        showLoading(message)

        if (!active) return

        StaffFeedbackDialogs.show(
            this,
            StaffFeedbackDialogs.Type.WARNING,
            "Donor Matching Unavailable",
            message,
            positiveText = "Go Back",
            onPositive = {
                finish()
            }
        )
    }

    private fun showWarning(
        title: String,
        message: String
    ) {

        if (!active || isFinishing || isDestroyed) return

        StaffFeedbackDialogs.show(
            this,
            StaffFeedbackDialogs.Type.WARNING,
            title,
            message
        )
    }

    private fun showError(
        title: String,
        message: String
    ) {

        if (!active || isFinishing || isDestroyed) return

        StaffFeedbackDialogs.show(
            this,
            StaffFeedbackDialogs.Type.ERROR,
            title,
            message
        )
    }
}
