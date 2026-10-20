
package com.example.blooddononerapp

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StaffRecordDonationActivity : AppCompatActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private var documentId = ""
    private var appointmentId = ""
    private var donorId = ""
    private var donorName = ""
    private var bloodGroup = ""
    private var hospital = ""

    private lateinit var edtDonationId: TextInputEditText
    private lateinit var edtAmount: TextInputEditText
    private lateinit var edtDateTime: TextInputEditText
    private lateinit var dropdownDonationType: AutoCompleteTextView
    private lateinit var saveButton: MaterialButton

    private var saving = false
    private var saved = false
    private var active = false
    private var generation = 0

    private val donationTypes = listOf(
        "Whole Blood", "Platelets", "Plasma"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_staff_record_donation)

        documentId = intent.getStringExtra("DOCUMENT_ID").orEmpty()
        appointmentId = intent.getStringExtra("APPOINTMENT_ID").orEmpty()
        donorId = intent.getStringExtra("DONOR_ID").orEmpty()
        donorName = intent.getStringExtra("DONOR_NAME").orEmpty()
        bloodGroup = intent.getStringExtra("BLOOD_GROUP").orEmpty()
        hospital = intent.getStringExtra("CENTER").orEmpty()

        edtDonationId = findViewById(R.id.edtDonationId)
        edtAmount = findViewById(R.id.edtAmount)
        edtDateTime = findViewById(R.id.edtDateTime)
        dropdownDonationType = findViewById(R.id.dropdownDonationType)
        saveButton = findViewById(R.id.btnSaveDonation)

        val edtDonorId =
            findViewById<TextInputEditText>(R.id.edtDonorId)

        val edtDonorName =
            findViewById<TextInputEditText>(R.id.edtDonorName)

        val edtHospital =
            findViewById<TextInputEditText>(R.id.edtHospital)

        val dropdownBloodGroup =
            findViewById<AutoCompleteTextView>(
                R.id.dropdownBloodGroup
            )

        dropdownBloodGroup.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                listOf(
                    "A+", "A-", "B+", "B-",
                    "AB+", "AB-", "O+", "O-"
                )
            )
        )

        dropdownDonationType.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                donationTypes
            )
        )

        val generatedId =
            "DON-${System.currentTimeMillis()}"

        edtDonationId.setText(generatedId)
        edtDonorId.setText(donorId)
        edtDonorName.setText(donorName)
        edtHospital.setText(hospital)
        dropdownBloodGroup.setText(bloodGroup, false)

        edtDateTime.setText(
            SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.ENGLISH
            ).format(Date())
        )

        edtDonorId.isFocusable = false
        edtDonorName.isFocusable = false
        edtHospital.isFocusable = false
        dropdownBloodGroup.isEnabled = false

        findViewById<TextView>(R.id.btnBack)
            .setOnClickListener { finish() }

        saveButton.setOnClickListener {
            confirmDonation()
        }
    }

    override fun onStart() {
        super.onStart()
        active = true
    }

    override fun onStop() {
        active = false
        generation++
        super.onStop()
    }

    private fun valid(token: Int): Boolean {
        return active &&
                generation == token &&
                !isFinishing &&
                !isDestroyed
    }

    private fun confirmDonation() {

        if (saving || saved) return

        val donationId = edtDonationId.text
            ?.toString()?.trim().orEmpty()

        val donationType = dropdownDonationType.text
            ?.toString()?.trim().orEmpty()

        val amountText = edtAmount.text
            ?.toString()?.trim().orEmpty()

        val amount = amountText.toIntOrNull()

        val dateTime = edtDateTime.text
            ?.toString()?.trim().orEmpty()

        if (
            documentId.isBlank() ||
            appointmentId.isBlank() ||
            donorId.isBlank() ||
            donorName.isBlank() ||
            bloodGroup.isBlank() ||
            hospital.isBlank()
        ) {
            showWarning(
                "Missing Appointment",
                "Open Record Donation from an eligible appointment."
            )
            return
        }

        if (donationId.isBlank()) {
            edtDonationId.error = "Donation ID is required"
            return
        }

        if (donationType !in donationTypes) {
            dropdownDonationType.error =
                "Select a valid donation type"
            return
        }

        if (amount == null || amount <= 0) {
            edtAmount.error = "Enter a valid amount in mL"
            edtAmount.requestFocus()
            return
        }

        if (dateTime.isBlank()) {
            edtDateTime.error = "Date and time are required"
            return
        }

        StaffFeedbackDialogs.show(
            this,
            StaffFeedbackDialogs.Type.WARNING,
            "Confirm Completed Donation",
            "Donor: $donorName\n" +
                    "Blood group: $bloodGroup\n" +
                    "Donation type: $donationType\n" +
                    "Amount: $amount mL\n" +
                    "Hospital: $hospital\n\n" +
                    "This will create a completed donation record " +
                    "and add 1 unit to the hospital blood stock.\n\n" +
                    "Continue only after verified blood collection.",
            positiveText = "Confirm & Save",
            negativeText = "Cancel",
            onPositive = {
                saveDonation(
                    donationId,
                    donationType,
                    amount,
                    dateTime
                )
            }
        )
    }

    private fun saveDonation(
        donationId: String,
        donationType: String,
        amount: Int,
        dateTime: String
    ) {

        if (saving || saved) return

        val user = auth.currentUser ?: run {
            showError(
                "Session Unavailable",
                "Please sign in with an active staff account."
            )
            return
        }

        val stockKey = bloodGroupKey(bloodGroup)

        if (stockKey == null) {
            showWarning(
                "Invalid Blood Group",
                "Cannot identify the inventory blood group."
            )
            return
        }

        val token = generation
        setSaving(true)

        db.collection("hospitals")
            .whereEqualTo("name", hospital)
            .limit(2)
            .get(Source.SERVER)
            .addOnSuccessListener { hospitals ->

                if (!valid(token)) {
                    return@addOnSuccessListener
                }

                if (hospitals.size() != 1) {
                    setSaving(false)

                    showError(
                        "Hospital Unavailable",
                        "Hospital inventory was not found " +
                                "or the hospital name is duplicated."
                    )
                    return@addOnSuccessListener
                }

                val hospitalRef =
                    hospitals.documents.first().reference

                val appointmentRef =
                    db.collection("donationAppointments")
                        .document(documentId)

                val staffRef =
                    db.collection("users").document(user.uid)

                val donationRef =
                    db.collection("donationRecords").document()

                db.runTransaction { transaction ->

                    // All document reads before writes.
                    val staff = transaction.get(staffRef)
                    val appointment =
                        transaction.get(appointmentRef)
                    val hospitalDocument =
                        transaction.get(hospitalRef)

                    val validStaff =
                        staff.exists() &&
                                staff.getString("role") == "STAFF" &&
                                staff.getString("status")
                                    .equals("Active", true)

                    if (!validStaff) {
                        throw IllegalStateException(
                            "An active STAFF account is required."
                        )
                    }

                    if (
                        !appointment.exists() ||
                        !hospitalDocument.exists()
                    ) {
                        throw IllegalStateException(
                            "Appointment or hospital no longer exists."
                        )
                    }

                    val matchingHospital =
                        hospital.isNotBlank() &&
                                staff.getString("hospital") == hospital &&
                                appointment.getString("hospital") == hospital &&
                                hospitalDocument.getString("name") == hospital

                    if (!matchingHospital) {
                        throw IllegalStateException(
                            "Staff, appointment and inventory " +
                                    "must belong to the same hospital."
                        )
                    }

                    if (
                        !hospitalDocument.getString("status")
                            .equals("Active", true)
                    ) {
                        throw IllegalStateException(
                            "Hospital is inactive."
                        )
                    }

                    val savedAppointmentId =
                        appointment.getString("appointmentId")
                            .orEmpty()

                    val savedDonorId =
                        appointment.getString("donorId").orEmpty()

                    val savedDonorName =
                        appointment.getString("donorName").orEmpty()

                    val savedBloodGroup =
                        appointment.getString("bloodGroup")
                            .orEmpty()

                    val donorUserId =
                        appointment.getString("userId").orEmpty()

                    if (
                        donorUserId.isBlank() ||
                        savedAppointmentId != appointmentId ||
                        savedDonorId != donorId ||
                        savedDonorName != donorName ||
                        savedBloodGroup != bloodGroup
                    ) {
                        throw IllegalStateException(
                            "Donor details changed or user ID is missing."
                        )
                    }

                    val status =
                        appointment.getString("status").orEmpty()

                    val alreadyCompleted =
                        status.equals("Completed", true) ||
                                appointment.getBoolean("completed") == true ||
                                appointment.getBoolean("inventoryUpdated") == true ||
                                !appointment.getString("donationRecordId")
                                    .isNullOrBlank()

                    if (alreadyCompleted) {
                        throw IllegalStateException(
                            "This donation was already completed."
                        )
                    }

                    if (
                        appointment.getBoolean("checkedIn") != true
                    ) {
                        throw IllegalStateException(
                            "Donor check-in is required."
                        )
                    }

                    val screeningDecision =
                        appointment.getString("screeningDecision")
                            .orEmpty()

                    val screeningResult =
                        appointment.getString("screening.decision")
                            .orEmpty()

                    val screenedAt =
                        appointment.getTimestamp(
                            "screening.screenedAt"
                        )

                    if (
                        !status.equals("Eligible", true) ||
                        !screeningDecision.equals(
                            "Eligible",
                            true
                        ) ||
                        !screeningResult.equals(
                            "Eligible",
                            true
                        ) ||
                        screenedAt == null
                    ) {
                        throw IllegalStateException(
                            "Verified eligible screening is required."
                        )
                    }

                    val stock =
                        hospitalDocument.getLong(
                            "bloodStock.$stockKey"
                        ) ?: throw IllegalStateException(
                            "Inventory value is missing."
                        )

                    if (
                        stock < 0L ||
                        stock == Long.MAX_VALUE
                    ) {
                        throw IllegalStateException(
                            "Invalid hospital inventory value."
                        )
                    }

                    val donationRecord = hashMapOf<String, Any>(
                        "documentId" to donationRef.id,
                        "donationId" to donationId,
                        "appointmentDocumentId" to documentId,
                        "appointmentId" to appointmentId,
                        "userId" to donorUserId,
                        "userEmail" to appointment
                            .getString("userEmail").orEmpty(),
                        "donorId" to donorId,
                        "donorName" to donorName,
                        "bloodGroup" to bloodGroup,
                        "donationType" to donationType,
                        "amountMl" to amount,
                        "hospital" to hospital,
                        "hospitalDocumentId" to hospitalRef.id,
                        "recordedDateTime" to dateTime,
                        "inventoryUnitsAdded" to 1,
                        "staffId" to user.uid,
                        "staffEmail" to (user.email ?: ""),
                        "status" to "Completed",
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                    val appointmentUpdates =
                        hashMapOf<String, Any>(
                            "status" to "Completed",
                            "completed" to true,
                            "completedAt" to
                                    FieldValue.serverTimestamp(),
                            "donationRecordId" to donationRef.id,
                            "donationId" to donationId,
                            "donationType" to donationType,
                            "amountMl" to amount,
                            "inventoryUpdated" to true,
                            "inventoryUnitsAdded" to 1,
                            "updatedAt" to
                                    FieldValue.serverTimestamp()
                        )

                    transaction.set(
                        donationRef,
                        donationRecord
                    )

                    transaction.update(
                        appointmentRef,
                        appointmentUpdates
                    )

                    transaction.update(
                        hospitalRef,
                        mapOf(
                            "bloodStock.$stockKey" to stock + 1L,
                            "updatedAt" to
                                    FieldValue.serverTimestamp()
                        )
                    )

                    true
                }
                    .addOnSuccessListener {

                        if (!valid(token)) {
                            return@addOnSuccessListener
                        }

                        saved = true
                        setSaving(false)
                        saveButton.isEnabled = false

                        StaffFeedbackDialogs.show(
                            this,
                            StaffFeedbackDialogs.Type.SUCCESS,
                            "Donation Recorded",
                            "The donation record was saved, " +
                                    "the appointment was completed, " +
                                    "and hospital inventory was updated.",
                            positiveText = "Done",
                            cancelable = false,
                            onPositive = {
                                finish()
                            }
                        )
                    }
                    .addOnFailureListener { error ->

                        if (valid(token)) {
                            setSaving(false)

                            showError(
                                "Donation Not Saved",
                                error.localizedMessage
                                    ?: "Firebase transaction failed."
                            )
                        }
                    }
            }
            .addOnFailureListener { error ->

                if (valid(token)) {
                    setSaving(false)

                    showError(
                        "Cannot Load Hospital",
                        error.localizedMessage
                            ?: "Firebase hospital lookup failed."
                    )
                }
            }
    }

    private fun bloodGroupKey(group: String): String? {
        return when (group.trim().uppercase(Locale.ROOT)) {
            "A+" -> "APlus"
            "A-" -> "AMinus"
            "B+" -> "BPlus"
            "B-" -> "BMinus"
            "AB+" -> "ABPlus"
            "AB-" -> "ABMinus"
            "O+" -> "OPlus"
            "O-" -> "OMinus"
            else -> null
        }
    }

    private fun setSaving(value: Boolean) {
        saving = value
        saveButton.isEnabled = !value && !saved

        saveButton.text = when {
            saved -> "Donation Completed"
            value -> "Saving to Firebase..."
            else -> "Save Donation Record"
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
