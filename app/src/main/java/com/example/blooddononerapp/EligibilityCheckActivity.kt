
package com.example.blooddononerapp

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale

class EligibilityCheckActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var qHealthy: RadioGroup
    private lateinit var qRecentDonation: RadioGroup
    private lateinit var qMedication: RadioGroup
    private lateinit var qRecentTreatment: RadioGroup
    private lateinit var btnContinue: MaterialButton

    private var saving = false
    private var saved = false
    private var active = false
    private var generation = 0

    private val hospitalName by lazy {
        intent.getStringExtra("hospital_name")
            .orEmpty().trim()
    }

    private val hospitalDocumentId by lazy {
        intent.getStringExtra("hospital_document_id")
            .orEmpty()
    }

    private val hospitalLocation by lazy {
        intent.getStringExtra("hospital_location")
            .orEmpty().trim()
    }

    private val selectedBloodGroup by lazy {
        intent.getStringExtra("blood_group")
            .orEmpty().trim()
    }

    private val selectedDate by lazy {
        intent.getStringExtra("donation_date")
            .orEmpty().trim()
    }

    private val selectedTime by lazy {
        intent.getStringExtra("donation_time")
            .orEmpty().trim()
    }

    private data class Answers(
        val healthyToday: String,
        val recentDonation: String,
        val medication: String,
        val recentTreatment: String
    ) {

        fun requiresStaffReview(): Boolean {
            return healthyToday.equals("No", true) ||
                    recentDonation.equals("Yes", true) ||
                    medication.equals("Yes", true) ||
                    recentTreatment.equals("Yes", true)
        }

        fun asMap(): Map<String, String> {

            return mapOf(
                "healthyToday" to healthyToday,
                "recentDonation" to recentDonation,
                "medication" to medication,
                "recentTreatment" to recentTreatment
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_eligibility_check)

        qHealthy = findViewById(R.id.qHealthy)
        qRecentDonation = findViewById(R.id.qRecentDonation)
        qMedication = findViewById(R.id.qMedication)
        qRecentTreatment = findViewById(R.id.qRecentTreatment)
        btnContinue = findViewById(R.id.btnContinue)

        findViewById<TextView>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        btnContinue.setOnClickListener {

            if (saving || saved) {
                return@setOnClickListener
            }

            val answers = collectAnswers()
                ?: return@setOnClickListener

            showConfirmation(answers)
        }
    }

    override fun onStart() {
        super.onStart()
        active = true

        if (saving) {
            saving = false
            btnContinue.isEnabled = !saved
        }
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

    // ========================================
    // PRELIMINARY HEALTH QUESTIONS
    // ========================================

    private fun answer(group: RadioGroup): String? {

        val id = group.checkedRadioButtonId

        if (id == -1) {
            return null
        }

        return findViewById<RadioButton>(id)
            .text.toString().trim()
    }

    private fun collectAnswers(): Answers? {

        val values = listOf(
            qHealthy,
            qRecentDonation,
            qMedication,
            qRecentTreatment
        ).map { answer(it) }

        if (values.any { it == null }) {

            message(
                "Complete Health Questions",
                "Please answer all four preliminary " +
                        "screening questions."
            )

            return null
        }

        if (
            values.any {
                it != "Yes" && it != "No"
            }
        ) {

            message(
                "Invalid Answer",
                "Select Yes or No for each question."
            )

            return null
        }

        return Answers(
            values[0]!!,
            values[1]!!,
            values[2]!!,
            values[3]!!
        )
    }

    // ========================================
    // FUTURE APPOINTMENT VALIDATION
    // ========================================

    private fun futureSlot(): Boolean {

        val timeOptions = listOf(
            "08:00 AM - 10:00 AM",
            "10:00 AM - 12:00 PM",
            "01:00 PM - 03:00 PM",
            "03:00 PM - 05:00 PM"
        )

        if (selectedTime !in timeOptions) {
            return false
        }

        val parser = SimpleDateFormat(
            "dd MMM yyyy hh:mm a",
            Locale.ENGLISH
        )

        parser.isLenient = false

        val start = selectedTime.substringBefore(" - ")

        val instant = runCatching {
            parser.parse("$selectedDate $start")?.time
        }.getOrNull() ?: return false

        return instant > System.currentTimeMillis()
    }

    // ========================================
    // CONFIRM BOOKING
    // ========================================

    private fun showConfirmation(answers: Answers) {

        if (
            hospitalName.isBlank() ||
            hospitalDocumentId.isBlank() ||
            selectedBloodGroup.isBlank() ||
            !futureSlot()
        ) {

            message(
                "Appointment Details Changed",
                "The hospital, blood group, date or " +
                        "time is invalid. Go back and choose " +
                        "a future slot."
            )

            return
        }

        val dialog = Dialog(this)

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        dialog.setContentView(
            R.layout.dialog_confirm_donation
        )

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

        dialog.findViewById<TextView>(
            R.id.txtConfirmHospital
        ).text = hospitalName

        dialog.findViewById<TextView>(
            R.id.txtConfirmLocation
        ).text = hospitalLocation

        dialog.findViewById<TextView>(
            R.id.txtConfirmBloodGroup
        ).text = selectedBloodGroup

        dialog.findViewById<TextView>(
            R.id.txtConfirmDate
        ).text = selectedDate

        dialog.findViewById<TextView>(
            R.id.txtConfirmTime
        ).text = selectedTime

        val confirm =
            dialog.findViewById<MaterialButton>(
                R.id.btnConfirmAppointment
            )

        dialog.findViewById<MaterialButton>(
            R.id.btnEditAppointment
        ).setOnClickListener {
            dialog.dismiss()
        }

        confirm.text = "Submit Booking Request"

        confirm.setOnClickListener {

            if (saving || saved) {
                return@setOnClickListener
            }

            if (!futureSlot()) {

                message(
                    "Time Slot Has Passed",
                    "Go back and choose another " +
                            "future time slot."
                )

                return@setOnClickListener
            }

            if (answers.requiresStaffReview()) {

                MaterialAlertDialogBuilder(this)
                    .setTitle("Medical Review Required")
                    .setMessage(
                        "One or more of your answers " +
                                "indicate information that must " +
                                "be reviewed by qualified medical " +
                                "staff.\n\n" +
                                "Submitting is not medical clearance. " +
                                "If you are unwell, contact the " +
                                "donation center before visiting.\n\n" +
                                "Continue with a Pending " +
                                "booking request?"
                    )
                    .setNegativeButton(
                        "Go Back",
                        null
                    )
                    .setPositiveButton(
                        "Submit for Review"
                    ) { _, _ ->

                        bookAppointment(
                            answers,
                            dialog,
                            confirm
                        )
                    }
                    .show()

            } else {

                bookAppointment(
                    answers,
                    dialog,
                    confirm
                )
            }
        }

        dialog.show()

        dialog.window?.setLayout(
            (
                    resources.displayMetrics.widthPixels *
                            0.90f
                    ).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        dialog.window?.addFlags(
            WindowManager.LayoutParams.FLAG_DIM_BEHIND
        )

        dialog.window?.let { window ->

            val params = window.attributes

            params.dimAmount = 0.45f

            window.attributes = params
        }
    }

    // ========================================
    // DUPLICATE BOOKING ID
    // ========================================

    private fun deterministicDocumentId(uid: String): String {

        val value =
            "$uid|$hospitalDocumentId|$selectedDate|$selectedTime"

        val hash = MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte ->

                "%02x".format(
                    byte.toInt() and 0xff
                )
            }

        return "booking_${hash.take(32)}"
    }

    // ========================================
    // FIREBASE BOOKING TRANSACTION
    // ========================================

    private fun bookAppointment(
        answers: Answers,
        dialog: Dialog,
        confirm: MaterialButton
    ) {

        if (saving || saved) return

        val user = auth.currentUser ?: run {

            message(
                "Sign-in Required",
                "Please sign in again to request " +
                        "an appointment."
            )

            return
        }

        if (!futureSlot()) {

            message(
                "Time Slot Has Passed",
                "Choose another future time slot."
            )

            return
        }

        val token = generation

        saving = true
        confirm.isEnabled = false
        confirm.text = "Saving Request..."
        btnContinue.isEnabled = false

        val documentId =
            deterministicDocumentId(user.uid)

        val appointmentId =
            "APT-${documentId.takeLast(8).uppercase(Locale.ROOT)}"

        val donorId =
            "D-${user.uid.takeLast(6).uppercase(Locale.ROOT)}"

        val profileRef = db.collection("users")
            .document(user.uid)

        val hospitalRef = db.collection("hospitals")
            .document(hospitalDocumentId)

        val appointmentRef =
            db.collection("donationAppointments")
                .document(documentId)

        db.runTransaction { transaction ->

            // Read all documents before writing.
            val profile = transaction.get(profileRef)
            val hospital = transaction.get(hospitalRef)
            val appointment = transaction.get(appointmentRef)

            val status =
                profile.getString("status")
                    ?.ifBlank { "Active" }
                    ?: "Active"

            val activeUser =
                profile.exists() &&
                        profile.getString("role")
                            .equals("USER", true) &&
                        status.equals("Active", true)

            if (!activeUser) {

                throw IllegalStateException(
                    "An active USER account is required."
                )
            }

            val actualGroup =
                profile.getString("bloodGroup")
                    .orEmpty()
                    .trim()

            if (
                !actualGroup.equals(
                    selectedBloodGroup,
                    true
                )
            ) {

                throw IllegalStateException(
                    "Your registered blood group " +
                            "has changed. Please restart booking."
                )
            }

            val validHospital =
                hospital.exists() &&
                        hospital.getString("status")
                            .equals("Active", true) &&
                        hospital.getString("name") == hospitalName

            if (!validHospital) {

                throw IllegalStateException(
                    "Selected hospital is no longer " +
                            "active or available."
                )
            }

            if (appointment.exists()) {

                throw IllegalStateException(
                    "A booking already exists for " +
                            "this hospital, date and time slot. " +
                            "Check My Requests before booking again."
                )
            }

            if (!futureSlot()) {

                throw IllegalStateException(
                    "Selected booking time has passed."
                )
            }

            val details = hashMapOf<String, Any>(

                "documentId" to documentId,

                "appointmentId" to appointmentId,

                "userId" to user.uid,

                "userEmail" to (user.email ?: ""),

                "donorName" to profile
                    .getString("fullName")
                    .orEmpty()
                    .ifBlank { "Donor" },

                "donorId" to donorId,

                "phone" to profile
                    .getString("phone")
                    .orEmpty(),

                "bloodGroup" to actualGroup,

                "hospital" to hospitalName,

                "hospitalDocumentId" to hospitalDocumentId,

                "location" to hospitalLocation,

                "donationDate" to selectedDate,

                "donationTime" to selectedTime,

                "status" to "Pending",

                "checkedIn" to false,

                "completed" to false,

                "inventoryUpdated" to false,

                "eligibilityAnswers" to answers.asMap(),

                "eligibilityReviewNeeded" to
                        answers.requiresStaffReview(),

                "createdAt" to
                        FieldValue.serverTimestamp(),

                "updatedAt" to
                        FieldValue.serverTimestamp()
            )

            transaction.set(
                appointmentRef,
                details
            )

            true
        }
            .addOnSuccessListener {

                if (!valid(token)) {
                    return@addOnSuccessListener
                }

                saving = false
                saved = true

                dialog.dismiss()

                val success = Intent(
                    this,
                    DonationSuccessActivity::class.java
                )

                success.putExtra(
                    "DOCUMENT_ID",
                    documentId
                )

                success.putExtra(
                    "APPOINTMENT_ID",
                    appointmentId
                )

                success.putExtra(
                    "hospital_name",
                    hospitalName
                )

                success.putExtra(
                    "hospital_location",
                    hospitalLocation
                )

                success.putExtra(
                    "blood_group",
                    selectedBloodGroup
                )

                success.putExtra(
                    "donation_date",
                    selectedDate
                )

                success.putExtra(
                    "donation_time",
                    selectedTime
                )

                startActivity(success)
                finish()
            }
            .addOnFailureListener { error ->

                if (!valid(token)) {
                    return@addOnFailureListener
                }

                saving = false

                confirm.isEnabled = true
                confirm.text = "Submit Booking Request"
                btnContinue.isEnabled = true

                message(
                    "Booking Not Saved",
                    error.localizedMessage
                        ?: "Please try again."
                )
            }
    }

    // ========================================
    // PROFESSIONAL MESSAGE
    // ========================================

    private fun message(
        title: String,
        body: String
    ) {

        if (!isFinishing && !isDestroyed) {

            MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(body)
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
