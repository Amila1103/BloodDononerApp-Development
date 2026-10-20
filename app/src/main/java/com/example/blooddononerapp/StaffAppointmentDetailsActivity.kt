
package com.example.blooddononerapp

import android.content.Intent
import android.os.Bundle
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

class StaffAppointmentDetailsActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var txtAppointmentId: TextView
    private lateinit var txtBloodGroup: TextView
    private lateinit var txtDonorName: TextView
    private lateinit var txtDonorId: TextView
    private lateinit var txtStatus: TextView
    private lateinit var txtDate: TextView
    private lateinit var txtTime: TextView
    private lateinit var txtCenter: TextView
    private lateinit var btnCheckIn: MaterialButton

    private var documentId = ""
    private var hospital = ""
    private var current: DocumentSnapshot? = null
    private var appointmentListener: ListenerRegistration? = null

    private var active = false
    private var verified = false
    private var serverLoaded = false
    private var busy = false
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_staff_appointment_details)

        documentId = intent.getStringExtra("DOCUMENT_ID").orEmpty()

        txtAppointmentId = findViewById(R.id.txtAppointmentId)
        txtBloodGroup = findViewById(R.id.txtBloodGroup)
        txtDonorName = findViewById(R.id.txtDonorName)
        txtDonorId = findViewById(R.id.txtDonorId)
        txtStatus = findViewById(R.id.txtStatus)
        txtDate = findViewById(R.id.txtAppointmentDate)
        txtTime = findViewById(R.id.txtAppointmentTime)
        txtCenter = findViewById(R.id.txtCenter)
        btnCheckIn = findViewById(R.id.btnCheckIn)

        // Display initial information while Firebase loads.
        txtAppointmentId.text =
            intent.getStringExtra("APPOINTMENT_ID") ?: "—"

        txtBloodGroup.text =
            intent.getStringExtra("BLOOD_GROUP") ?: "—"

        txtDonorName.text =
            intent.getStringExtra("DONOR_NAME") ?: "Donor"

        txtDonorId.text =
            intent.getStringExtra("DONOR_ID") ?: "—"

        txtDate.text =
            intent.getStringExtra("DATE") ?: "—"

        txtTime.text =
            intent.getStringExtra("TIME") ?: "—"

        txtCenter.text =
            intent.getStringExtra("CENTER") ?: "—"

        findViewById<TextView>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        btnCheckIn.setOnClickListener {
            handleAction()
        }

        showUnavailable("Loading verified appointment...")
    }

    override fun onStart() {
        super.onStart()

        active = true
        verifyStaffAndListen()
    }

    override fun onStop() {
        active = false
        generation++

        appointmentListener?.remove()
        appointmentListener = null

        current = null
        verified = false
        serverLoaded = false
        busy = false

        super.onStop()
    }

    private fun isCurrent(token: Int): Boolean {
        return active &&
                generation == token &&
                !isFinishing &&
                !isDestroyed
    }

    // ==========================================
    // FIREBASE STAFF VERIFICATION
    // ==========================================

    private fun verifyStaffAndListen() {

        val token = ++generation

        appointmentListener?.remove()
        appointmentListener = null

        verified = false
        hospital = ""

        showUnavailable("Verifying staff account...")

        val uid = auth.currentUser?.uid

        if (uid == null) {
            showUnavailable("Staff login required")
            return
        }

        if (documentId.isBlank()) {
            showUnavailable(
                "Appointment document ID is missing"
            )
            return
        }

        db.collection("users")
            .document(uid)
            .get(Source.SERVER)
            .addOnSuccessListener { staff ->

                if (!isCurrent(token)) {
                    return@addOnSuccessListener
                }

                val isActive =
                    staff.exists() &&
                            staff.getString("role") == "STAFF" &&
                            staff.getString("status")
                                .equals("Active", true)

                val assigned =
                    staff.getString("hospital")
                        .orEmpty()
                        .trim()

                if (
                    !isActive ||
                    assigned.isBlank() ||
                    assigned.equals("Not Assigned", true)
                ) {
                    showUnavailable(
                        "Active STAFF account and assigned hospital required"
                    )
                    return@addOnSuccessListener
                }

                hospital = assigned
                verified = true

                listenToAppointment(token)
            }
            .addOnFailureListener { error ->

                if (!isCurrent(token)) {
                    return@addOnFailureListener
                }

                showUnavailable(
                    "Staff verification unavailable"
                )

                showFailure(
                    "Could not verify staff account",
                    error.localizedMessage
                )
            }
    }

    // ==========================================
    // LIVE APPOINTMENT STATUS
    // ==========================================

    private fun listenToAppointment(token: Int) {

        appointmentListener = db
            .collection("donationAppointments")
            .document(documentId)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { doc, error ->

                if (!isCurrent(token)) {
                    return@addSnapshotListener
                }

                if (error != null) {

                    showUnavailable(
                        "Appointment data unavailable"
                    )

                    showFailure(
                        "Unable to load appointment",
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

                if (!doc.exists()) {
                    showUnavailable("Appointment not found")
                    return@addSnapshotListener
                }

                if (
                    doc.getString("hospital") != hospital
                ) {
                    showUnavailable(
                        "This appointment belongs to another hospital"
                    )
                    return@addSnapshotListener
                }

                current = doc
                serverLoaded = true

                render(doc)
            }
    }

    // ==========================================
    // APPOINTMENT VALIDATION
    // ==========================================

    private fun isCompleted(
        doc: DocumentSnapshot
    ): Boolean {

        return doc.getString("status")
            .equals("Completed", true) ||
                doc.getBoolean("completed") == true ||
                doc.getBoolean("inventoryUpdated") == true
    }

    private fun hasEligibleScreening(
        doc: DocumentSnapshot
    ): Boolean {

        return doc.getString("screeningDecision")
            .equals("Eligible", true) &&
                doc.getString("screening.decision")
                    .equals("Eligible", true) &&
                doc.getTimestamp("screening.screenedAt") != null
    }

    // ==========================================
    // UPDATE UI FROM FIREBASE
    // ==========================================

    private fun render(doc: DocumentSnapshot) {

        txtAppointmentId.text =
            doc.getString("appointmentId") ?: doc.id

        txtBloodGroup.text =
            doc.getString("bloodGroup") ?: "—"

        txtDonorName.text =
            doc.getString("donorName") ?: "Donor"

        txtDonorId.text =
            doc.getString("donorId") ?: "—"

        txtDate.text =
            doc.getString("donationDate") ?: "—"

        txtTime.text =
            doc.getString("donationTime") ?: "—"

        txtCenter.text =
            doc.getString("hospital") ?: "—"

        val status =
            doc.getString("status")
                .orEmpty()
                .trim()

        val checkedIn =
            doc.getBoolean("checkedIn") == true

        val ready =
            verified && serverLoaded && !busy

        btnCheckIn.isEnabled = false

        when {

            isCompleted(doc) -> {

                txtStatus.text = "Completed"
                btnCheckIn.text = "Donation Completed"
            }

            status.equals("Cancelled", true) ||
                    status.equals("Rejected", true) -> {

                txtStatus.text = status
                btnCheckIn.text = "Appointment Closed"
            }

            status.equals("Deferred", true) -> {

                txtStatus.text = "Deferred"
                btnCheckIn.text = "Screening Deferred"
            }

            status.equals("Eligible", true) &&
                    checkedIn &&
                    hasEligibleScreening(doc) -> {

                txtStatus.text = "Eligible"
                btnCheckIn.text = "Record Donation"
                btnCheckIn.isEnabled = ready
            }

            status.equals("Checked In", true) &&
                    checkedIn -> {

                txtStatus.text = "Checked In"
                btnCheckIn.text = "Continue Screening"
                btnCheckIn.isEnabled = ready
            }

            status.equals("Pending", true) &&
                    !checkedIn -> {

                txtStatus.text = "Pending"
                btnCheckIn.text = "Check In & Start Screening"
                btnCheckIn.isEnabled = ready
            }

            else -> {

                txtStatus.text =
                    status.ifBlank { "Unknown" } +
                            " — Review Required"

                btnCheckIn.text = "Action Unavailable"
            }
        }

        if (busy) {
            btnCheckIn.isEnabled = false
            btnCheckIn.text = "Please Wait..."
        }
    }

    private fun showUnavailable(message: String) {

        serverLoaded = false
        current = null

        txtStatus.text = message

        btnCheckIn.isEnabled = false
        btnCheckIn.text = "Action Unavailable"
    }

    // ==========================================
    // HANDLE SCREEN ACTION
    // ==========================================

    private fun handleAction() {

        if (
            busy ||
            !verified ||
            !serverLoaded
        ) {
            return
        }

        val doc = current ?: return

        val status =
            doc.getString("status").orEmpty()

        val checkedIn =
            doc.getBoolean("checkedIn") == true

        when {

            isCompleted(doc) -> Unit

            status.equals("Pending", true) &&
                    !checkedIn -> {

                confirmCheckIn(doc)
            }

            status.equals("Checked In", true) &&
                    checkedIn -> {

                verifyAndNavigate("SCREENING")
            }

            status.equals("Eligible", true) &&
                    checkedIn &&
                    hasEligibleScreening(doc) -> {

                verifyAndNavigate("DONATION")
            }

            else -> {

                showWarning(
                    "Action unavailable",
                    "This appointment status needs review."
                )
            }
        }
    }

    // ==========================================
    // PROFESSIONAL CHECK-IN CONFIRMATION
    // ==========================================

    private fun confirmCheckIn(doc: DocumentSnapshot) {

        val donor =
            doc.getString("donorName") ?: "Donor"

        val reference =
            doc.getString("appointmentId") ?: doc.id

        StaffFeedbackDialogs.show(
            this,
            StaffFeedbackDialogs.Type.WARNING,
            "Confirm Donor Check-in",
            "Donor: $donor\n" +
                    "Appointment: $reference\n" +
                    "Hospital: $hospital\n\n" +
                    "Confirm the donor has arrived " +
                    "and their identity has been checked.",
            positiveText = "Confirm Check-in",
            negativeText = "Cancel",
            onPositive = {
                checkInDonor()
            }
        )
    }

    // ==========================================
    // SAFE FIREBASE CHECK-IN
    // ==========================================

    private fun checkInDonor() {

        if (
            busy ||
            !verified ||
            !serverLoaded
        ) {
            return
        }

        val uid = auth.currentUser?.uid ?: run {

            showFailure(
                "Session expired",
                "Please sign in again."
            )

            return
        }

        val token = generation

        setBusy(true)

        val staffRef =
            db.collection("users")
                .document(uid)

        val appointmentRef =
            db.collection("donationAppointments")
                .document(documentId)

        db.runTransaction { tx ->

            // Read both documents before writing.
            val staff = tx.get(staffRef)
            val appointment = tx.get(appointmentRef)

            val validStaff =
                staff.exists() &&
                        staff.getString("role") == "STAFF" &&
                        staff.getString("status")
                            .equals("Active", true) &&
                        staff.getString("hospital") == hospital

            if (!validStaff) {
                throw IllegalStateException(
                    "Active staff permission is required"
                )
            }

            val correctAppointment =
                appointment.exists() &&
                        appointment.getString("hospital") == hospital

            if (!correctAppointment) {
                throw IllegalStateException(
                    "Appointment hospital does not match staff"
                )
            }

            val status =
                appointment.getString("status").orEmpty()

            val checkedIn =
                appointment.getBoolean("checkedIn") == true

            val isPending =
                status.equals("Pending", true)

            val completed =
                isCompleted(appointment)

            if (
                !isPending ||
                checkedIn ||
                completed
            ) {
                throw IllegalStateException(
                    "Appointment status changed. " +
                            "Please review it again"
                )
            }

            tx.update(
                appointmentRef,
                mapOf(
                    "checkedIn" to true,
                    "status" to "Checked In",
                    "checkedInAt" to
                            FieldValue.serverTimestamp(),
                    "updatedAt" to
                            FieldValue.serverTimestamp()
                )
            )

            true
        }
            .addOnSuccessListener {

                if (!isCurrent(token)) {
                    return@addOnSuccessListener
                }

                // Do not reuse the old Pending snapshot.
                showUnavailable(
                    "Check-in saved. Waiting for Firebase refresh..."
                )

                busy = false

                StaffFeedbackDialogs.show(
                    this,
                    StaffFeedbackDialogs.Type.SUCCESS,
                    "Check-in Successful",
                    "Donor check-in was saved to Firebase. " +
                            "Continue to donor screening?",
                    positiveText = "Continue Screening",
                    negativeText = "Later",
                    cancelable = false,
                    onPositive = {
                        verifyAndNavigate("SCREENING")
                    }
                )
            }
            .addOnFailureListener { error ->

                if (!isCurrent(token)) {
                    return@addOnFailureListener
                }

                setBusy(false)

                showFailure(
                    "Check-in not saved",
                    error.localizedMessage
                )
            }
    }

    // ==========================================
    // VERIFY BEFORE SCREENING / DONATION
    // ==========================================

    private fun verifyAndNavigate(next: String) {

        if (busy) return

        val uid = auth.currentUser?.uid ?: run {

            showFailure(
                "Session expired",
                "Please sign in again."
            )

            return
        }

        val token = generation

        setBusy(true)

        db.collection("users")
            .document(uid)
            .get(Source.SERVER)
            .addOnSuccessListener { staff ->

                if (!isCurrent(token)) {
                    return@addOnSuccessListener
                }

                val allowed =
                    staff.exists() &&
                            staff.getString("role") == "STAFF" &&
                            staff.getString("status")
                                .equals("Active", true) &&
                            staff.getString("hospital") == hospital

                if (!allowed) {

                    setBusy(false)

                    showFailure(
                        "Staff access unavailable",
                        "Active staff access is required."
                    )

                    return@addOnSuccessListener
                }

                db.collection("donationAppointments")
                    .document(documentId)
                    .get(Source.SERVER)
                    .addOnSuccessListener { doc ->

                        if (!isCurrent(token)) {
                            return@addOnSuccessListener
                        }

                        setBusy(false)

                        val validAppointment =
                            doc.exists() &&
                                    doc.getString("hospital") == hospital &&
                                    !isCompleted(doc)

                        if (!validAppointment) {

                            showWarning(
                                "Appointment changed",
                                "This appointment is no longer " +
                                        "available for this action."
                            )

                            return@addOnSuccessListener
                        }

                        current = doc
                        serverLoaded = true

                        render(doc)

                        val status =
                            doc.getString("status").orEmpty()

                        val checkedIn =
                            doc.getBoolean("checkedIn") == true

                        val canScreen =
                            status.equals("Checked In", true) &&
                                    checkedIn

                        val canDonate =
                            status.equals("Eligible", true) &&
                                    checkedIn &&
                                    hasEligibleScreening(doc)

                        when {

                            next == "SCREENING" && canScreen -> {

                                openNext(
                                    StaffDonorScreeningActivity::class.java,
                                    doc
                                )
                            }

                            next == "DONATION" && canDonate -> {

                                openNext(
                                    StaffRecordDonationActivity::class.java,
                                    doc
                                )
                            }

                            else -> {

                                showWarning(
                                    "Appointment changed",
                                    "Please review the latest " +
                                            "appointment status."
                                )
                            }
                        }
                    }
                    .addOnFailureListener { error ->

                        if (!isCurrent(token)) {
                            return@addOnFailureListener
                        }

                        setBusy(false)

                        showFailure(
                            "Cannot verify appointment",
                            error.localizedMessage
                        )
                    }
            }
            .addOnFailureListener { error ->

                if (!isCurrent(token)) {
                    return@addOnFailureListener
                }

                setBusy(false)

                showFailure(
                    "Cannot verify staff access",
                    error.localizedMessage
                )
            }
    }

    // ==========================================
    // SCREEN NAVIGATION
    // ==========================================

    private fun openNext(
        destination: Class<*>,
        doc: DocumentSnapshot
    ) {

        val nextIntent = Intent(
            this,
            destination
        ).apply {

            putExtra("DOCUMENT_ID", doc.id)

            putExtra(
                "APPOINTMENT_ID",
                doc.getString("appointmentId").orEmpty()
            )

            putExtra(
                "DONOR_NAME",
                doc.getString("donorName").orEmpty()
            )

            putExtra(
                "DONOR_ID",
                doc.getString("donorId").orEmpty()
            )

            putExtra(
                "BLOOD_GROUP",
                doc.getString("bloodGroup").orEmpty()
            )

            putExtra(
                "DATE",
                doc.getString("donationDate").orEmpty()
            )

            putExtra(
                "TIME",
                doc.getString("donationTime").orEmpty()
            )

            putExtra(
                "CENTER",
                doc.getString("hospital").orEmpty()
            )

            putExtra(
                "STATUS",
                doc.getString("status").orEmpty()
            )

            putExtra(
                "CHECKED_IN",
                doc.getBoolean("checkedIn") == true
            )
        }

        startActivity(nextIntent)

        overridePendingTransition(
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )

        finish()
    }

    // ==========================================
    // BUTTON LOADING STATE
    // ==========================================

    private fun setBusy(value: Boolean) {

        busy = value

        val doc = current

        if (
            doc != null &&
            serverLoaded
        ) {

            render(doc)

        } else {

            btnCheckIn.isEnabled = false

            btnCheckIn.text =
                if (value) {
                    "Please Wait..."
                } else {
                    "Action Unavailable"
                }
        }
    }

    // ==========================================
    // PROFESSIONAL ERROR DIALOG
    // ==========================================

    private fun showFailure(
        title: String,
        message: String?
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
            message
                ?: "Please check your connection and try again.",
            positiveText = "OK"
        )
    }

    // ==========================================
    // PROFESSIONAL WARNING DIALOG
    // ==========================================

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
