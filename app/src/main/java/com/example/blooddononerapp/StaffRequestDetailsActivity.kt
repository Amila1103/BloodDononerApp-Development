
package com.example.blooddononerapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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

class StaffRequestDetailsActivity : AppCompatActivity() {

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private lateinit var txtRequestId: TextView
    private lateinit var txtBloodGroup: TextView
    private lateinit var txtUnits: TextView
    private lateinit var txtRequiredBefore: TextView
    private lateinit var txtRequester: TextView
    private lateinit var txtContact: TextView
    private lateinit var txtArea: TextView
    private lateinit var txtHospital: TextView
    private lateinit var txtUrgency: TextView
    private lateinit var txtAdditionalInfo: TextView

    private lateinit var btnVerify: MaterialButton
    private lateinit var btnFindDonors: MaterialButton
    private lateinit var btnFulfilled: MaterialButton
    private lateinit var btnReject: MaterialButton

    private var badge: TextView? = null
    private var requestListener: ListenerRegistration? = null

    private var documentId = ""
    private var staffHospital = ""
    private var requestHospital = ""
    private var requestId = ""
    private var requestBloodGroup = ""
    private var requestArea = ""
    private var requestStatus = ""

    private var active = false
    private var verified = false
    private var loaded = false
    private var busy = false
    private var generation = 0
    private var loadErrorShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_staff_request_details
        )

        documentId =
            intent.getStringExtra(
                "REQUEST_DOCUMENT_ID"
            ).orEmpty()

        txtRequestId = findViewById(R.id.txtRequestId)
        txtBloodGroup = findViewById(R.id.txtBloodGroup)
        txtUnits = findViewById(R.id.txtUnits)
        txtRequiredBefore = findViewById(R.id.txtRequiredBefore)
        txtRequester = findViewById(R.id.txtRequester)
        txtContact = findViewById(R.id.txtContact)
        txtArea = findViewById(R.id.txtArea)
        txtHospital = findViewById(R.id.txtHospital)
        txtUrgency = findViewById(R.id.txtUrgency)
        txtAdditionalInfo = findViewById(R.id.txtAdditionalInfo)

        btnVerify = findViewById(R.id.btnVerify)
        btnFindDonors = findViewById(R.id.btnFindDonors)
        btnFulfilled = findViewById(R.id.btnFulfilled)
        btnReject = findViewById(R.id.btnReject)

        badge = findHeaderBadge()

        findViewById<TextView>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        btnVerify.setOnClickListener {
            confirmStatus(
                "Verified",
                "Verify this request?",
                "Confirm the blood group, hospital, " +
                        "requester and contact details " +
                        "have been reviewed."
            )
        }

        btnFindDonors.setOnClickListener {

            if (
                !loaded ||
                busy ||
                !verified
            ) {
                return@setOnClickListener
            }

            if (
                requestStatus.equals(
                    "Verified",
                    true
                )
            ) {
                confirmStatus(
                    "Matching",
                    "Start donor matching?",
                    "The request will change to Matching. " +
                            "You can then search for donors."
                )
            } else if (
                requestStatus.equals(
                    "Matching",
                    true
                )
            ) {
                verifyAndOpenMatching()
            } else {
                showWarning(
                    "Action unavailable",
                    "Verify the request before matching donors."
                )
            }
        }

        btnFulfilled.setOnClickListener {
            confirmStatus(
                "Fulfilled",
                "Confirm request fulfilled?",
                "Only continue if the requested blood " +
                        "has actually been arranged and verified " +
                        "by authorized hospital staff."
            )
        }

        btnReject.setOnClickListener {
            confirmStatus(
                "Rejected",
                "Reject this request?",
                "This will close the emergency request. " +
                        "Confirm the rejection has been reviewed."
            )
        }

        unavailable(
            "Checking staff access..."
        )
    }

    override fun onStart() {
        super.onStart()

        active = true
        loadStaff()
    }

    override fun onStop() {

        active = false
        generation++

        requestListener?.remove()
        requestListener = null

        loaded = false
        verified = false

        super.onStop()
    }

    private fun valid(token: Int): Boolean {

        return active &&
                !isFinishing &&
                !isDestroyed &&
                token == generation
    }

    // ==========================================
    // VERIFY STAFF PROFILE
    // ==========================================

    private fun loadStaff() {

        val user = auth.currentUser

        if (
            user == null ||
            documentId.isBlank()
        ) {
            unavailable(
                "Staff login or request document ID is missing"
            )
            return
        }

        val token = ++generation

        loadErrorShown = false

        requestListener?.remove()
        requestListener = null

        verified = false
        loaded = false

        unavailable(
            "Verifying staff account..."
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
                        ?.trim()
                        .orEmpty()

                val validStaff =
                    staff.exists() &&
                            staff.getString("role") == "STAFF" &&
                            staff.getString("status")
                                .equals("Active", true) &&
                            assigned.isNotBlank() &&
                            !assigned.equals("Not Assigned", true)

                if (!validStaff) {

                    unavailable(
                        "An active STAFF account with " +
                                "an assigned hospital is required"
                    )

                    return@addOnSuccessListener
                }

                staffHospital = assigned
                verified = true

                listenToRequest(token)
            }
            .addOnFailureListener { error ->

                if (valid(token)) {
                    loadError(
                        "Staff verification failed",
                        error.localizedMessage
                            ?: "Check your connection and retry"
                    )
                }
            }
    }

    // ==========================================
    // REALTIME REQUEST DETAILS
    // ==========================================

    private fun listenToRequest(token: Int) {

        requestListener = db
            .collection("emergencyRequests")
            .document(documentId)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { doc, error ->

                if (!valid(token)) {
                    return@addSnapshotListener
                }

                if (error != null) {
                    loadError(
                        "Request unavailable",
                        error.localizedMessage
                            ?: "Firebase read failed"
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

                if (
                    !doc.exists() ||
                    doc.getString("hospital") != staffHospital
                ) {
                    unavailable(
                        "Request not found or not assigned " +
                                "to your hospital"
                    )
                    return@addSnapshotListener
                }

                loaded = true

                render(doc)
            }
    }

    private fun render(doc: DocumentSnapshot) {

        requestId =
            doc.getString("requestId") ?: doc.id

        requestHospital =
            doc.getString("hospital").orEmpty()

        requestBloodGroup =
            doc.getString("bloodGroup") ?: "—"

        requestArea =
            doc.getString("area").orEmpty()

        requestStatus =
            doc.getString("status") ?: "Pending"

        txtRequestId.text =
            "REQ-${requestId.takeLast(8).uppercase(Locale.ROOT)}"

        txtBloodGroup.text = requestBloodGroup

        txtUrgency.text =
            doc.getString("urgency")
                ?: "Not available"

        txtUnits.text =
            when (val value = doc.get("units")) {

                is Number ->
                    "${value.toLong()} Units"

                is String ->
                    if (value.contains("unit", true)) {
                        value
                    } else {
                        "$value Units"
                    }

                else -> "—"
            }

        val date =
            doc.getString("requiredDate").orEmpty()

        val time =
            doc.getString("requiredBefore").orEmpty()

        txtRequiredBefore.text =
            listOf(date, time)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
                .ifBlank { "Not available" }

        txtRequester.text =
            doc.getString("userEmail")
                ?: "Not available"

        val contacts = listOf(
            doc.getString("primaryContact").orEmpty(),
            doc.getString("secondaryContact").orEmpty()
        ).filter {
            it.isNotBlank()
        }

        txtContact.text =
            contacts.joinToString(" / ")
                .ifBlank { "Not available" }

        txtArea.text =
            listOf(
                requestArea,
                doc.getString("district").orEmpty(),
                doc.getString("province").orEmpty()
            )
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")
                .ifBlank { "Not available" }

        txtHospital.text = requestHospital

        txtAdditionalInfo.text =
            doc.getString("additionalInfo")
                ?.takeIf { it.isNotBlank() }
                ?: "No additional information"

        showActions()
    }

    // ==========================================
    // ACTION BUTTONS AND STATUS COLORS
    // ==========================================

    private fun showActions() {

        val pending =
            requestStatus.equals("Pending", true)

        val verifiedStatus =
            requestStatus.equals("Verified", true)

        val matching =
            requestStatus.equals("Matching", true)

        val enabled =
            active &&
                    verified &&
                    loaded &&
                    !busy

        btnVerify.visibility =
            if (pending) View.VISIBLE else View.GONE

        btnFindDonors.visibility =
            if (verifiedStatus || matching) {
                View.VISIBLE
            } else {
                View.GONE
            }

        btnFulfilled.visibility =
            if (matching) View.VISIBLE else View.GONE

        btnReject.visibility =
            if (pending || verifiedStatus || matching) {
                View.VISIBLE
            } else {
                View.GONE
            }

        btnFindDonors.text =
            if (matching) {
                "View Matching Donors"
            } else {
                "Find Matching Donors"
            }

        listOf(
            btnVerify,
            btnFindDonors,
            btnFulfilled,
            btnReject
        ).forEach {
            it.isEnabled = enabled
        }

        badge?.apply {

            text = requestStatus

            val lower =
                requestStatus.lowercase(Locale.ROOT)

            val background = when (lower) {
                "pending" -> R.drawable.bg_status_pending
                "verified" -> R.drawable.bg_status_verified
                "matching" -> R.drawable.bg_status_matching
                "fulfilled" -> R.drawable.bg_status_fulfilled
                else -> R.drawable.bg_staff_filter
            }

            setBackgroundResource(background)

            setTextColor(
                when (lower) {

                    "verified", "fulfilled" ->
                        Color.rgb(21, 128, 61)

                    "matching" ->
                        Color.rgb(37, 99, 235)

                    "pending" ->
                        Color.rgb(217, 119, 6)

                    else ->
                        Color.rgb(220, 38, 38)
                }
            )
        }
    }

    private fun findHeaderBadge(): TextView? {

        val content =
            findViewById<ViewGroup>(
                android.R.id.content
            )

        val scroll =
            content.getChildAt(0) as? ViewGroup
                ?: return null

        val root =
            scroll.getChildAt(0) as? ViewGroup
                ?: return null

        val header =
            root.getChildAt(0) as? ViewGroup
                ?: return null

        return header.getChildAt(2) as? TextView
    }

    // ==========================================
    // STATUS VALIDATION
    // ==========================================

    private fun transitionAllowed(
        from: String,
        to: String
    ): Boolean {

        return when (to) {

            "Verified" ->
                from.equals("Pending", true)

            "Matching" ->
                from.equals("Verified", true)

            "Fulfilled" ->
                from.equals("Matching", true)

            "Rejected" ->
                listOf(
                    "Pending",
                    "Verified",
                    "Matching"
                ).any {
                    from.equals(it, true)
                }

            else -> false
        }
    }

    // ==========================================
    // PROFESSIONAL CONFIRMATION
    // ==========================================

    private fun confirmStatus(
        target: String,
        title: String,
        message: String
    ) {

        if (
            !loaded ||
            !verified ||
            busy
        ) {
            return
        }

        if (!transitionAllowed(requestStatus, target)) {

            showWarning(
                "Status changed",
                "This action is no longer available."
            )

            return
        }

        StaffFeedbackDialogs.show(
            this,
            StaffFeedbackDialogs.Type.WARNING,
            title,
            "Request: ${txtRequestId.text}\n" +
                    "Hospital: $requestHospital\n\n" +
                    message,

            positiveText = when (target) {
                "Rejected" -> "Reject Request"
                "Fulfilled" -> "Confirm Fulfilled"
                "Matching" -> "Start Matching"
                else -> "Verify Request"
            },

            negativeText = "Cancel",

            onPositive = {
                saveStatus(target)
            }
        )
    }

    // ==========================================
    // SAFE FIRESTORE TRANSACTION
    // ==========================================

    private fun saveStatus(target: String) {

        if (
            busy ||
            !verified ||
            !loaded
        ) {
            return
        }

        val uid =
            auth.currentUser?.uid ?: run {

                showError(
                    "Session expired",
                    "Please log in again"
                )

                return
            }

        val token = generation

        busy = true
        showActions()

        val staffRef =
            db.collection("users")
                .document(uid)

        val requestRef =
            db.collection("emergencyRequests")
                .document(documentId)

        val assigned = staffHospital

        db.runTransaction { tx ->

            val staff = tx.get(staffRef)
            val request = tx.get(requestRef)

            val validStaff =
                staff.exists() &&
                        staff.getString("role") == "STAFF" &&
                        staff.getString("status")
                            .equals("Active", true) &&
                        staff.getString("hospital") == assigned

            if (!validStaff) {

                throw IllegalStateException(
                    "Active staff and matching hospital required"
                )
            }

            if (
                !request.exists() ||
                request.getString("hospital") != assigned
            ) {

                throw IllegalStateException(
                    "Request is no longer assigned to this hospital"
                )
            }

            val latest =
                request.getString("status").orEmpty()

            if (!transitionAllowed(latest, target)) {

                throw IllegalStateException(
                    "Request is now $latest. " +
                            "Cannot change to $target"
                )
            }

            val changes =
                mutableMapOf<String, Any>(
                    "status" to target,
                    "updatedAt" to
                            FieldValue.serverTimestamp()
                )

            when (target) {

                "Verified" -> {
                    changes["verifiedBy"] = uid
                    changes["verifiedAt"] =
                        FieldValue.serverTimestamp()
                }

                "Matching" -> {
                    changes["matchingStartedBy"] = uid
                    changes["matchingStartedAt"] =
                        FieldValue.serverTimestamp()
                }

                "Fulfilled" -> {
                    changes["fulfilled"] = true
                    changes["fulfilledBy"] = uid
                    changes["fulfilledAt"] =
                        FieldValue.serverTimestamp()
                }

                "Rejected" -> {
                    changes["rejectedBy"] = uid
                    changes["rejectedAt"] =
                        FieldValue.serverTimestamp()
                }
            }

            tx.update(requestRef, changes)

            true
        }
            .addOnSuccessListener {

                if (!valid(token)) {
                    return@addOnSuccessListener
                }

                busy = false
                requestStatus = target

                showActions()

                StaffFeedbackDialogs.show(
                    this,
                    StaffFeedbackDialogs.Type.SUCCESS,
                    "Request $target",
                    "The emergency request was updated " +
                            "successfully in Firebase.",

                    positiveText =
                        if (target == "Matching") {
                            "Find Donors"
                        } else {
                            "OK"
                        },

                    onPositive =
                        if (target == "Matching") {
                            { verifyAndOpenMatching() }
                        } else {
                            null
                        }
                )
            }
            .addOnFailureListener { error ->

                if (!valid(token)) {
                    return@addOnFailureListener
                }

                busy = false
                showActions()

                showError(
                    "Update not saved",
                    error.localizedMessage
                        ?: "Unable to change request status"
                )
            }
    }

    // ==========================================
    // VERIFY DONOR MATCHING
    // ==========================================

    private fun verifyAndOpenMatching() {

        if (
            !verified ||
            !loaded ||
            busy
        ) {
            return
        }

        val token = generation

        busy = true
        showActions()

        db.collection("emergencyRequests")
            .document(documentId)
            .get(Source.SERVER)
            .addOnSuccessListener { doc ->

                if (!valid(token)) {
                    return@addOnSuccessListener
                }

                busy = false

                val correctRequest =
                    doc.exists() &&
                            doc.getString("hospital") == staffHospital &&
                            doc.getString("status")
                                .equals("Matching", true)

                if (!correctRequest) {

                    showWarning(
                        "Matching unavailable",
                        "This request is no longer in Matching status."
                    )

                    return@addOnSuccessListener
                }

                loaded = true
                render(doc)

                val next = Intent(
                    this,
                    StaffDonorMatchingActivity::class.java
                ).apply {

                    putExtra(
                        "REQUEST_DOCUMENT_ID",
                        documentId
                    )

                    putExtra(
                        "REQUEST_ID",
                        requestId
                    )

                    putExtra(
                        "BLOOD_GROUP",
                        requestBloodGroup
                    )

                    putExtra(
                        "AREA",
                        requestArea
                    )

                    putExtra(
                        "HOSPITAL",
                        requestHospital
                    )
                }

                startActivity(next)
            }
            .addOnFailureListener { error ->

                if (!valid(token)) {
                    return@addOnFailureListener
                }

                busy = false
                showActions()

                showError(
                    "Matching unavailable",
                    error.localizedMessage
                        ?: "Cannot check request status"
                )
            }
    }

    // ==========================================
    // UNAVAILABLE / ERROR STATES
    // ==========================================

    private fun unavailable(message: String) {

        loaded = false

        requestListener?.remove()
        requestListener = null

        badge?.text = "Unavailable"

        txtRequestId.text = "—"
        txtBloodGroup.text = "—"
        txtUnits.text = "—"
        txtRequiredBefore.text = "—"
        txtRequester.text = "—"
        txtContact.text = "—"
        txtArea.text = "—"
        txtHospital.text = "—"
        txtUrgency.text = "—"
        txtAdditionalInfo.text = message

        listOf(
            btnVerify,
            btnFindDonors,
            btnFulfilled,
            btnReject
        ).forEach {

            it.visibility = View.GONE
            it.isEnabled = false
        }
    }

    private fun loadError(
        title: String,
        message: String
    ) {

        unavailable(message)

        if (!loadErrorShown) {

            loadErrorShown = true

            showError(title, message)
        }
    }

    private fun showError(
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

        StaffFeedbackDialogs.show(
            this,
            StaffFeedbackDialogs.Type.ERROR,
            title,
            message,

            positiveText = "Retry",
            negativeText = "Close",

            onPositive = {
                loadStaff()
            }
        )
    }

    private fun showWarning(
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

        StaffFeedbackDialogs.show(
            this,
            StaffFeedbackDialogs.Type.WARNING,
            title,
            message
        )
    }
}
