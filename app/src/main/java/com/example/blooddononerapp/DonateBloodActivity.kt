
package com.example.blooddononerapp

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DonateBloodActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var txtHospitalName: TextView
    private lateinit var txtHospitalLocation: TextView
    private lateinit var bloodGroupDropdown: AutoCompleteTextView
    private lateinit var donationCenterDropdown: AutoCompleteTextView
    private lateinit var dateInput: TextView
    private lateinit var timeDropdown: AutoCompleteTextView
    private lateinit var btnContinue: MaterialButton

    private var hospitalName = ""
    private var hospitalLocation = ""
    private var busy = false
    private var active = false
    private var generation = 0

    private val bloodGroups = listOf(
        "A+", "A-", "B+", "B-",
        "AB+", "AB-", "O+", "O-"
    )

    private val preferredSlots = listOf(
        "08:00 AM - 10:00 AM",
        "10:00 AM - 12:00 PM",
        "01:00 PM - 03:00 PM",
        "03:00 PM - 05:00 PM"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_donate_blood)

        txtHospitalName = findViewById(R.id.txtHospitalName)
        txtHospitalLocation = findViewById(R.id.txtHospitalLocation)
        bloodGroupDropdown = findViewById(R.id.bloodGroupDropdown)
        donationCenterDropdown = findViewById(R.id.donationCenterDropdown)
        dateInput = findViewById(R.id.dateInput)
        timeDropdown = findViewById(R.id.timeDropdown)
        btnContinue = findViewById(R.id.btnContinue)

        hospitalName = intent.getStringExtra("hospital_name")
            .orEmpty().trim()

        hospitalLocation = intent.getStringExtra("hospital_location")
            .orEmpty().trim()

        txtHospitalName.text =
            hospitalName.ifBlank { "Choose a hospital again" }

        txtHospitalLocation.text =
            hospitalLocation.ifBlank { "Location not provided" }

        findViewById<TextView>(R.id.btnBack)
            .setOnClickListener { finish() }

        bloodGroupDropdown.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                bloodGroups
            )
        )

        donationCenterDropdown.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                listOfNotNull(
                    hospitalName.takeIf { it.isNotBlank() }
                )
            )
        )

        donationCenterDropdown.setText(hospitalName, false)
        donationCenterDropdown.isFocusable = false

        donationCenterDropdown.setOnClickListener {
            donationCenterDropdown.showDropDown()
        }

        timeDropdown.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                preferredSlots
            )
        )

        bloodGroupDropdown.setOnClickListener {
            bloodGroupDropdown.showDropDown()
        }

        timeDropdown.setOnClickListener {
            timeDropdown.showDropDown()
        }

        dateInput.setOnClickListener {
            showDatePicker()
        }

        btnContinue.setOnClickListener {
            verifyBeforeContinue()
        }

        setBusy(false)
    }

    override fun onStart() {
        super.onStart()
        active = true

        if (busy) {
            setBusy(false)
        }
    }

    override fun onStop() {
        active = false
        generation++
        super.onStop()
    }

    private fun isCurrent(token: Int): Boolean {
        return active &&
                token == generation &&
                !isFinishing &&
                !isDestroyed
    }

    // ========================================
    // DONATION DATE PICKER
    // ========================================

    private fun showDatePicker() {
        val today = Calendar.getInstance()

        val picker = DatePickerDialog(
            this,
            { _, year, month, day ->

                val selected = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                dateInput.text = SimpleDateFormat(
                    "dd MMM yyyy",
                    Locale.ENGLISH
                ).format(selected.time)
            },
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH),
            today.get(Calendar.DAY_OF_MONTH)
        )

        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        picker.datePicker.minDate = today.timeInMillis
        picker.show()
    }

    // ========================================
    // VALIDATE FUTURE TIME SLOT
    // ========================================

    private fun slotStartMillis(
        dateText: String,
        timeText: String
    ): Long? {

        if (timeText !in preferredSlots) {
            return null
        }

        val startText = timeText
            .substringBefore(" - ")
            .trim()

        val parser = SimpleDateFormat(
            "dd MMM yyyy hh:mm a",
            Locale.ENGLISH
        )

        parser.isLenient = false

        return runCatching {
            parser.parse("$dateText $startText")?.time
        }.getOrNull()
    }

    private fun validateInputs(): Boolean {

        val group = bloodGroupDropdown.text
            .toString().trim().uppercase(Locale.ROOT)

        val center = donationCenterDropdown.text
            .toString().trim()

        val chosenDate = dateInput.text
            .toString().trim()

        val chosenSlot = timeDropdown.text
            .toString().trim()

        val start = slotStartMillis(chosenDate, chosenSlot)

        when {

            hospitalName.isBlank() ||
                    center != hospitalName -> {

                showMessage(
                    "Choose a Hospital",
                    "Return to Blood Bank Availability " +
                            "and choose a valid hospital."
                )
            }

            group !in bloodGroups -> {

                showMessage(
                    "Blood Group Required",
                    "Choose your registered blood group."
                )
            }

            start == null -> {

                showMessage(
                    "Select Date and Time",
                    "Choose a valid donation date " +
                            "and preferred time slot."
                )
            }

            start <= System.currentTimeMillis() -> {

                showMessage(
                    "Time Slot Has Passed",
                    "Choose a future date or a time " +
                            "slot that has not started."
                )
            }

            else -> return true
        }

        return false
    }

    // ========================================
    // VERIFY FIREBASE PROFILE AND HOSPITAL
    // ========================================

    private fun verifyBeforeContinue() {

        if (busy || !validateInputs()) return

        val user = auth.currentUser ?: run {

            showMessage(
                "Login Required",
                "Please sign in before requesting " +
                        "a donation appointment."
            )

            return
        }

        val token = generation

        setBusy(true)

        db.collection("users")
            .document(user.uid)
            .get(Source.SERVER)
            .addOnSuccessListener { profile ->

                if (!isCurrent(token)) {
                    return@addOnSuccessListener
                }

                val status = profile.getString("status")
                    ?.ifBlank { "Active" }
                    ?: "Active"

                val activeUser =
                    profile.exists() &&
                            profile.getString("role")
                                .equals("USER", true) &&
                            status.equals("Active", true)

                if (!activeUser) {
                    setBusy(false)

                    showMessage(
                        "Access Unavailable",
                        "An active USER account is required."
                    )

                    return@addOnSuccessListener
                }

                val registeredGroup =
                    profile.getString("bloodGroup")
                        .orEmpty()
                        .trim()
                        .uppercase(Locale.ROOT)

                val selectedGroup =
                    bloodGroupDropdown.text
                        .toString()
                        .trim()
                        .uppercase(Locale.ROOT)

                if (
                    registeredGroup !in bloodGroups ||
                    registeredGroup != selectedGroup
                ) {
                    setBusy(false)

                    showMessage(
                        "Blood Group Mismatch",
                        "The selected blood group must " +
                                "match your registered profile. " +
                                "Update your profile if incorrect."
                    )

                    return@addOnSuccessListener
                }

                db.collection("hospitals")
                    .whereEqualTo("name", hospitalName)
                    .limit(2)
                    .get(Source.SERVER)
                    .addOnSuccessListener { hospitals ->

                        if (!isCurrent(token)) {
                            return@addOnSuccessListener
                        }

                        val hospitalDoc =
                            hospitals.documents.singleOrNull()

                        if (
                            hospitalDoc == null ||
                            !hospitalDoc.getString("status")
                                .equals("Active", true)
                        ) {
                            setBusy(false)

                            showMessage(
                                "Hospital Unavailable",
                                "The hospital is inactive, " +
                                        "missing or duplicated. " +
                                        "Choose another hospital."
                            )

                            return@addOnSuccessListener
                        }

                        if (!validateInputs()) {
                            setBusy(false)
                            return@addOnSuccessListener
                        }

                        setBusy(false)

                        val next = Intent(
                            this,
                            EligibilityCheckActivity::class.java
                        )

                        next.putExtra(
                            "hospital_name",
                            hospitalName
                        )

                        next.putExtra(
                            "hospital_document_id",
                            hospitalDoc.id
                        )

                        next.putExtra(
                            "hospital_location",
                            hospitalLocation
                        )

                        next.putExtra(
                            "blood_group",
                            registeredGroup
                        )

                        next.putExtra(
                            "donation_date",
                            dateInput.text.toString().trim()
                        )

                        next.putExtra(
                            "donation_time",
                            timeDropdown.text.toString().trim()
                        )

                        startActivity(next)
                    }
                    .addOnFailureListener { error ->

                        if (isCurrent(token)) {
                            setBusy(false)

                            showMessage(
                                "Hospital Verification Failed",
                                error.localizedMessage
                                    ?: "Please retry."
                            )
                        }
                    }
            }
            .addOnFailureListener { error ->

                if (isCurrent(token)) {
                    setBusy(false)

                    showMessage(
                        "Profile Verification Failed",
                        error.localizedMessage
                            ?: "Please retry."
                    )
                }
            }
    }

    // ========================================
    // BUTTON STATE
    // ========================================

    private fun setBusy(value: Boolean) {

        busy = value

        btnContinue.isEnabled =
            !value && hospitalName.isNotBlank()

        btnContinue.text =
            if (value) {
                "Verifying Booking..."
            } else {
                "Continue"
            }
    }

    private fun showMessage(
        title: String,
        message: String
    ) {

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
