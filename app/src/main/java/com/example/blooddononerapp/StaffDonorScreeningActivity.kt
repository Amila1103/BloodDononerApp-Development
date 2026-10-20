
package com.example.blooddononerapp

import android.content.Intent
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class StaffDonorScreeningActivity : AppCompatActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private lateinit var rgFeelingWell: RadioGroup
    private lateinit var rgRecentIllness: RadioGroup
    private lateinit var rgMedication: RadioGroup
    private lateinit var rgRecentDonation: RadioGroup
    private lateinit var rgFinalDecision: RadioGroup
    private lateinit var edtNotes: TextInputEditText
    private lateinit var btnSave: MaterialButton

    private var documentId = ""
    private var appointmentId = ""
    private var donorName = ""
    private var donorId = ""
    private var bloodGroup = ""
    private var hospital = ""

    private var busy = false
    private var active = false
    private var requestVersion = 0

    private data class StaffAnswers(
        val feelingWell: String,
        val recentIllness: String,
        val medication: String,
        val recentDonation: String,
        val decision: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_donor_screening)

        documentId = intent.getStringExtra("DOCUMENT_ID").orEmpty()
        appointmentId = intent.getStringExtra("APPOINTMENT_ID").orEmpty()
        donorName = intent.getStringExtra("DONOR_NAME").orEmpty()
        donorId = intent.getStringExtra("DONOR_ID").orEmpty()
        bloodGroup = intent.getStringExtra("BLOOD_GROUP").orEmpty()
        hospital = intent.getStringExtra("CENTER").orEmpty()

        rgFeelingWell = findViewById(R.id.rgFeelingWell)
        rgRecentIllness = findViewById(R.id.rgRecentIllness)
        rgMedication = findViewById(R.id.rgMedication)
        rgRecentDonation = findViewById(R.id.rgRecentDonation)
        rgFinalDecision = findViewById(R.id.rgFinalDecision)
        edtNotes = findViewById(R.id.edtScreeningNotes)
        btnSave = findViewById(R.id.btnSaveScreening)

        findViewById<TextView>(R.id.txtDonorName).text = donorName
        findViewById<TextView>(R.id.txtDonorId).text = donorId
        findViewById<TextView>(R.id.txtBloodGroup).text = bloodGroup
        findViewById<TextView>(R.id.txtAppointmentId).text = appointmentId

        findViewById<TextView>(R.id.btnBack)
            .setOnClickListener { finish() }

        btnSave.setOnClickListener {
            if (busy) return@setOnClickListener

            val answers = collectAnswers()
                ?: return@setOnClickListener

            val notes = edtNotes.text
                ?.toString()
                ?.trim()
                .orEmpty()

            reviewBeforeSave(answers, notes)
        }
    }

    override fun onStart() {
        super.onStart()
        active = true
    }

    override fun onStop() {
        active = false
        requestVersion++
        super.onStop()
    }

    private fun validScreen(token: Int): Boolean {
        return active &&
                requestVersion == token &&
                !isFinishing &&
                !isDestroyed
    }

    private fun selected(group: RadioGroup): String {
        val selectedId = group.checkedRadioButtonId
        if (selectedId == -1) return ""

        return findViewById<RadioButton>(selectedId)
            .text.toString().trim()
    }

    private fun collectAnswers(): StaffAnswers? {

        val questions = listOf(
            rgFeelingWell to "Feeling well today",
            rgRecentIllness to "Recent illness",
            rgMedication to "Current medication",
            rgRecentDonation to "Recent donation",
            rgFinalDecision to "Final decision"
        )

        for ((group, label) in questions) {
            if (group.checkedRadioButtonId == -1) {
                showWarning(
                    "Incomplete screening",
                    "Please answer: $label"
                )
                return null
            }
        }

        val result = StaffAnswers(
            feelingWell = selected(rgFeelingWell),
            recentIllness = selected(rgRecentIllness),
            medication = selected(rgMedication),
            recentDonation = selected(rgRecentDonation),
            decision = selected(rgFinalDecision)
        )

        if (result.decision !in listOf("Eligible", "Deferred")) {
            showWarning(
                "Invalid decision",
                "Choose Eligible or Deferred."
            )
            return null
        }

        return result
    }

    private fun donorAnswers(
        document: DocumentSnapshot
    ): Map<String, String> {

        val raw = document.get("eligibilityAnswers")
                as? Map<*, *> ?: return emptyMap()

        return listOf(
            "healthyToday",
            "medication",
            "recentDonation",
            "recentTreatment"
        ).associateWith { key ->
            (raw[key] as? String).orEmpty().trim()
        }
    }

    private fun reviewFindings(
        donor: Map<String, String>,
        staff: StaffAnswers
    ): List<String> {

        val findings = mutableListOf<String>()

        if (
            donor.isEmpty() ||
            donor.values.all { it.isBlank() }
        ) {
            findings.add(
                "Donor booking health answers are missing."
            )
        }

        fun compare(
            key: String,
            label: String,
            staffAnswer: String
        ) {
            val donorAnswer = donor[key].orEmpty()

            if (
                donorAnswer.isNotBlank() &&
                !donorAnswer.equals(staffAnswer, true)
            ) {
                findings.add(
                    "$label: donor answered $donorAnswer, " +
                            "staff recorded $staffAnswer"
                )
            }
        }

        compare(
            "healthyToday",
            "Feeling well",
            staff.feelingWell
        )

        compare(
            "medication",
            "Medication",
            staff.medication
        )

        compare(
            "recentDonation",
            "Recent blood donation",
            staff.recentDonation
        )

        if (
            donor["recentTreatment"].equals("Yes", true)
        ) {
            findings.add(
                "Donor reported recent medical treatment."
            )
        }

        if (staff.feelingWell.equals("No", true)) {
            findings.add(
                "Staff recorded that the donor is not feeling well."
            )
        }

        if (staff.recentIllness.equals("Yes", true)) {
            findings.add(
                "Staff recorded recent illness or infection."
            )
        }

        if (staff.medication.equals("Yes", true)) {
            findings.add(
                "Staff recorded current medication use."
            )
        }

        if (staff.recentDonation.equals("Yes", true)) {
            findings.add(
                "Staff recorded a recent blood donation."
            )
        }

        return findings.distinct()
    }

    private fun validateAppointment(
        document: DocumentSnapshot
    ): String? {

        if (!document.exists()) {
            return "Appointment not found."
        }

        val status = document.getString("status").orEmpty()

        val completed =
            status.equals("Completed", true) ||
                    document.getBoolean("completed") == true ||
                    document.getBoolean("inventoryUpdated") == true ||
                    !document.getString("donationRecordId").isNullOrBlank()

        if (completed) {
            return "This donation was already completed."
        }

        if (
            status.equals("Cancelled", true) ||
            status.equals("Rejected", true) ||
            status.equals("Deferred", true)
        ) {
            return "This appointment is $status."
        }

        if (
            document.getBoolean("checkedIn") != true ||
            (
                    !status.equals("Checked In", true) &&
                            !status.equals("Eligible", true)
                    )
        ) {
            return "Donor check-in is required before screening."
        }

        val detailsMatch =
            document.getString("hospital").orEmpty() == hospital &&
                    document.getString("appointmentId").orEmpty() == appointmentId &&
                    document.getString("donorId").orEmpty() == donorId &&
                    document.getString("donorName").orEmpty() == donorName &&
                    document.getString("bloodGroup").orEmpty() == bloodGroup

        if (!detailsMatch) {
            return "Appointment information changed. Please reopen it."
        }

        return null
    }

    private fun reviewBeforeSave(
        answers: StaffAnswers,
        notes: String
    ) {

        if (
            documentId.isBlank() ||
            hospital.isBlank()
        ) {
            showWarning(
                "Missing information",
                "Appointment or hospital information is missing."
            )
            return
        }

        val uid = auth.currentUser?.uid ?: run {
            showError(
                "Staff session unavailable",
                "Please sign in again."
            )
            return
        }

        val token = requestVersion
        setBusy(true)

        val reference = db.collection("donationAppointments")
            .document(documentId)

        reference.get(Source.SERVER)
            .addOnSuccessListener { document ->

                if (!validScreen(token)) {
                    return@addOnSuccessListener
                }

                setBusy(false)

                val invalid = validateAppointment(document)

                if (invalid != null) {
                    showWarning(
                        "Appointment review",
                        invalid
                    )
                    return@addOnSuccessListener
                }

                val reported = donorAnswers(document)
                val findings = reviewFindings(reported, answers)

                if (
                    (
                            findings.isNotEmpty() ||
                                    answers.decision == "Deferred"
                            ) &&
                    notes.isBlank()
                ) {
                    edtNotes.error =
                        "Document the review or reason here"

                    edtNotes.requestFocus()

                    showWarning(
                        "Screening notes required",
                        "Enter screening notes before saving."
                    )

                    return@addOnSuccessListener
                }

                val details = if (findings.isEmpty()) {
                    "No differences were identified in " +
                            "the matching self-report questions."
                } else {
                    findings.joinToString("\n") { "• $it" }
                }

                StaffFeedbackDialogs.show(
                    this,
                    if (findings.isEmpty()) {
                        StaffFeedbackDialogs.Type.INFO
                    } else {
                        StaffFeedbackDialogs.Type.WARNING
                    },
                    if (findings.isEmpty()) {
                        "Confirm Donor Screening"
                    } else {
                        "Review Health Differences"
                    },
                    "Donor: $donorName\n" +
                            "Appointment: $appointmentId\n" +
                            "Decision: ${answers.decision}\n\n" +
                            details +
                            "\n\nA qualified clinical professional " +
                            "must determine donation eligibility.",
                    positiveText = if (findings.isEmpty()) {
                        "Confirm & Save"
                    } else {
                        "Reviewed — Save"
                    },
                    negativeText = "Go Back",
                    onPositive = {
                        saveScreening(
                            uid,
                            answers,
                            notes,
                            reported,
                            findings
                        )
                    }
                )
            }
            .addOnFailureListener { error ->

                if (validScreen(token)) {
                    setBusy(false)

                    showError(
                        "Verification failed",
                        error.localizedMessage
                            ?: "Cannot load appointment."
                    )
                }
            }
    }

    private fun saveScreening(
        uid: String,
        answers: StaffAnswers,
        notes: String,
        reported: Map<String, String>,
        reviewedFindings: List<String>
    ) {

        if (busy) return

        val token = requestVersion
        setBusy(true)

        val staffRef = db.collection("users").document(uid)

        val appointmentRef =
            db.collection("donationAppointments")
                .document(documentId)

        db.runTransaction { transaction ->

            val staff = transaction.get(staffRef)
            val appointment = transaction.get(appointmentRef)

            val validStaff =
                staff.exists() &&
                        staff.getString("role") == "STAFF" &&
                        staff.getString("status").equals("Active", true) &&
                        staff.getString("hospital") == hospital

            if (!validStaff) {
                throw IllegalStateException(
                    "Active staff permission is required."
                )
            }

            val invalid = validateAppointment(appointment)

            if (invalid != null) {
                throw IllegalStateException(invalid)
            }

            val latestDonorAnswers = donorAnswers(appointment)

            if (latestDonorAnswers != reported) {
                throw IllegalStateException(
                    "Donor answers changed. Please review again."
                )
            }

            val latestFindings =
                reviewFindings(latestDonorAnswers, answers)

            if (latestFindings != reviewedFindings) {
                throw IllegalStateException(
                    "Screening findings changed. Review again."
                )
            }

            if (
                (
                        latestFindings.isNotEmpty() ||
                                answers.decision == "Deferred"
                        ) &&
                notes.isBlank()
            ) {
                throw IllegalStateException(
                    "Screening notes are required."
                )
            }

            val screening = hashMapOf<String, Any>(
                "feelingWell" to answers.feelingWell,
                "recentIllness" to answers.recentIllness,
                "medication" to answers.medication,
                "recentDonation" to answers.recentDonation,
                "decision" to answers.decision,
                "notes" to notes,
                "reviewRequired" to latestFindings.isNotEmpty(),
                "reviewAcknowledged" to true,
                "selfReportFindings" to latestFindings,
                "screenedBy" to uid,
                "screenedAt" to FieldValue.serverTimestamp()
            )

            transaction.update(
                appointmentRef,
                mapOf(
                    "screening" to screening,
                    "screeningDecision" to answers.decision,
                    "status" to answers.decision,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            true
        }
            .addOnSuccessListener {

                if (!validScreen(token)) {
                    return@addOnSuccessListener
                }

                setBusy(false)

                val eligible = answers.decision == "Eligible"

                StaffFeedbackDialogs.show(
                    this,
                    StaffFeedbackDialogs.Type.SUCCESS,
                    "Screening Saved",
                    if (eligible) {
                        "Screening result saved as Eligible. " +
                                "You can now continue to Record Donation."
                    } else {
                        "Screening result saved as Deferred. " +
                                "The appointment will not continue to donation."
                    },
                    positiveText = if (eligible) {
                        "Continue to Donation"
                    } else {
                        "Back to Appointments"
                    },
                    cancelable = false,
                    onPositive = {
                        if (eligible) {
                            openRecordDonation()
                        } else {
                            finish()
                        }
                    }
                )
            }
            .addOnFailureListener { error ->

                if (validScreen(token)) {
                    setBusy(false)

                    showError(
                        "Screening Not Saved",
                        error.localizedMessage
                            ?: "Firebase could not save the result."
                    )
                }
            }
    }

    private fun openRecordDonation() {

        val next = Intent(
            this,
            StaffRecordDonationActivity::class.java
        ).apply {
            putExtra("DOCUMENT_ID", documentId)
            putExtra("APPOINTMENT_ID", appointmentId)
            putExtra("DONOR_NAME", donorName)
            putExtra("DONOR_ID", donorId)
            putExtra("BLOOD_GROUP", bloodGroup)
            putExtra("CENTER", hospital)
        }

        startActivity(next)
        finish()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        btnSave.isEnabled = !value

        btnSave.text = if (value) {
            "Checking Firebase..."
        } else {
            "Save Screening Result"
        }
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
