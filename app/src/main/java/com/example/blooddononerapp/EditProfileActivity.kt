
package com.example.blooddononerapp

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditProfileActivity : AppCompatActivity() {

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private lateinit var inputFullName: TextInputEditText
    private lateinit var inputPhone: TextInputEditText
    private lateinit var inputEmail: TextInputEditText

    private lateinit var inputBloodGroup: AutoCompleteTextView
    private lateinit var inputDateOfBirth: TextInputEditText
    private lateinit var inputGender: AutoCompleteTextView

    private lateinit var inputArea: TextInputEditText
    private lateinit var inputEmergencyContact: TextInputEditText

    private lateinit var txtProfileName: TextView
    private lateinit var txtProfileInitial: TextView

    private lateinit var btnSaveChanges: MaterialButton

    private var active = false
    private var loaded = false
    private var saving = false
    private var generation = 0

    private var originalBloodGroup = ""

    private val bloodGroups = listOf(
        "A+", "A-", "B+", "B-",
        "AB+", "AB-", "O+", "O-"
    )

    private val genders = listOf(
        "Male",
        "Female",
        "Other",
        "Prefer not to say"
    )

    private data class ProfileInput(
        val fullName: String,
        val phone: String,
        val dateOfBirth: String,
        val gender: String,
        val area: String,
        val emergencyContact: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_edit_profile
        )

        inputFullName =
            findViewById(R.id.inputFullName)

        inputPhone =
            findViewById(R.id.inputPhone)

        inputEmail =
            findViewById(R.id.inputEmail)

        inputBloodGroup =
            findViewById(R.id.inputBloodGroup)

        inputDateOfBirth =
            findViewById(R.id.inputDateOfBirth)

        inputGender =
            findViewById(R.id.inputGender)

        inputArea =
            findViewById(R.id.inputArea)

        inputEmergencyContact =
            findViewById(R.id.inputEmergencyContact)

        txtProfileName =
            findViewById(R.id.txtProfileName)

        txtProfileInitial =
            findViewById(R.id.txtProfileInitial)

        btnSaveChanges =
            findViewById(R.id.btnSaveChanges)

        findViewById<TextView>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        findViewById<TextView>(R.id.btnCancel)
            .setOnClickListener {
                finish()
            }

        findViewById<TextView>(R.id.btnChangePhoto)
            .setOnClickListener {

                message(
                    "Profile Photo",
                    "Photo upload is not enabled in " +
                            "this project version. Your " +
                            "existing profile data is unchanged."
                )
            }

        setupDropdowns()

        // Email changes require a separate verified
        // Firebase Authentication workflow.
        inputEmail.isFocusable = false
        inputEmail.isEnabled = false

        // Blood group is medically important.
        // Do not let an unverified profile edit
        // change donation or matching data.
        inputBloodGroup.isFocusable = false
        inputBloodGroup.isEnabled = false

        inputDateOfBirth.isFocusable = false

        inputDateOfBirth.setOnClickListener {
            showDatePicker()
        }

        inputFullName.setOnFocusChangeListener { _, hasFocus ->

            if (!hasFocus) {
                updateHeader(
                    inputFullName.text
                        ?.toString()
                        ?.trim()
                        .orEmpty()
                )
            }
        }

        btnSaveChanges.setOnClickListener {

            if (!saving && loaded) {

                val values = validateInputs()
                    ?: return@setOnClickListener

                confirmSave(values)
            }
        }

        setBusy(true, "LOADING...")
    }

    override fun onStart() {
        super.onStart()

        active = true
        loadProfile()
    }

    override fun onStop() {
        active = false
        generation++

        super.onStop()
    }

    private fun isCurrent(
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
    // DROPDOWN SETUP
    // ==========================================

    private fun setupDropdowns() {

        inputBloodGroup.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                bloodGroups
            )
        )

        inputGender.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                genders
            )
        )

        inputGender.setOnClickListener {
            inputGender.showDropDown()
        }
    }

    // ==========================================
    // LOAD PROFILE FROM FIREBASE
    // ==========================================

    private fun loadProfile() {

        val user = auth.currentUser ?: run {

            loaded = false
            setBusy(true, "SIGN-IN REQUIRED")

            message(
                "Sign-in Required",
                "Please sign in before editing your profile."
            )

            return
        }

        val token = ++generation

        loaded = false
        setBusy(true, "LOADING...")

        db.collection("users")
            .document(user.uid)
            .get(Source.SERVER)
            .addOnSuccessListener { document ->

                if (!isCurrent(token, user.uid)) {
                    return@addOnSuccessListener
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

                    setBusy(true, "PROFILE UNAVAILABLE")

                    message(
                        "Profile Unavailable",
                        "An active USER profile is required."
                    )

                    return@addOnSuccessListener
                }

                originalBloodGroup =
                    document.getString("bloodGroup")
                        .orEmpty()
                        .trim()

                inputFullName.setText(
                    document.getString("fullName")
                        .orEmpty()
                )

                inputPhone.setText(
                    normalizePhone(
                        document.getString("phone")
                            .orEmpty()
                    )
                )

                inputEmail.setText(
                    user.email
                        ?: document.getString("email")
                            .orEmpty()
                )

                inputBloodGroup.setText(
                    originalBloodGroup,
                    false
                )

                inputDateOfBirth.setText(
                    document.getString("dateOfBirth")
                        .orEmpty()
                )

                inputGender.setText(
                    document.getString("gender")
                        .orEmpty(),
                    false
                )

                inputArea.setText(
                    document.getString("area")
                        .orEmpty()
                )

                inputEmergencyContact.setText(
                    normalizePhone(
                        document.getString(
                            "emergencyContact"
                        ).orEmpty()
                    )
                )

                updateHeader(
                    document.getString("fullName")
                        .orEmpty()
                )

                loaded = true
                setBusy(false, "SAVE CHANGES")
            }
            .addOnFailureListener { error ->

                if (!isCurrent(token, user.uid)) {
                    return@addOnFailureListener
                }

                loaded = false
                setBusy(true, "RETRY REQUIRED")

                message(
                    "Cannot Load Profile",
                    error.localizedMessage
                        ?: "Check your internet connection " +
                        "and reopen this screen."
                )
            }
    }

    // ==========================================
    // PHONE NORMALIZATION
    // ==========================================

    private fun normalizePhone(
        phone: String
    ): String {

        val cleaned =
            phone.replace(" ", "")
                .replace("-", "")

        return when {

            cleaned.startsWith("+94") ->
                cleaned.removePrefix("+94")

            cleaned.startsWith("94") &&
                    cleaned.length == 11 ->
                cleaned.removePrefix("94")

            cleaned.startsWith("0") &&
                    cleaned.length == 10 ->
                cleaned.drop(1)

            else -> cleaned
        }
    }

    private fun validMobile(
        phone: String
    ): Boolean {

        return Regex("7\\d{8}")
            .matches(phone)
    }

    // ==========================================
    // VALIDATE DATE OF BIRTH
    // ==========================================

    private fun validBirthDate(
        value: String
    ): Boolean {

        val patterns = listOf(
            "dd/MM/yyyy",
            "yyyy-MM-dd"
        )

        for (pattern in patterns) {

            val parser = SimpleDateFormat(
                pattern,
                Locale.ENGLISH
            )

            parser.isLenient = false

            val parsed =
                runCatching {
                    parser.parse(value)
                }.getOrNull()

            if (
                parsed != null &&
                parsed.time < System.currentTimeMillis()
            ) {
                return true
            }
        }

        return false
    }

    private fun showDatePicker() {

        val now = Calendar.getInstance()

        val storedDate =
            inputDateOfBirth.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val existingDate =
            listOf("dd/MM/yyyy", "yyyy-MM-dd")
                .firstNotNullOfOrNull { pattern ->

                    val parser = SimpleDateFormat(
                        pattern,
                        Locale.ENGLISH
                    )

                    parser.isLenient = false

                    runCatching {
                        parser.parse(storedDate)
                    }.getOrNull()
                }

        if (existingDate != null) {
            now.time = existingDate
        }

        val picker = DatePickerDialog(
            this,
            { _, year, month, day ->

                val value = String.format(
                    Locale.ENGLISH,
                    "%02d/%02d/%04d",
                    day,
                    month + 1,
                    year
                )

                inputDateOfBirth.setText(value)
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        )

        picker.datePicker.maxDate =
            System.currentTimeMillis()

        picker.show()
    }

    // ==========================================
    // VALIDATE PROFILE INPUTS
    // ==========================================

    private fun validateInputs(): ProfileInput? {

        val name =
            inputFullName.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val phone =
            normalizePhone(
                inputPhone.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()
            )

        val dob =
            inputDateOfBirth.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val gender =
            inputGender.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val area =
            inputArea.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val emergencyContact =
            normalizePhone(
                inputEmergencyContact.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()
            )

        when {

            name.length < 2 ||
                    name.length > 80 -> {

                inputFullName.error =
                    "Enter a name with 2–80 characters"

                inputFullName.requestFocus()
            }

            !validMobile(phone) -> {

                inputPhone.error =
                    "Enter 9 digits beginning with 7"

                inputPhone.requestFocus()
            }

            originalBloodGroup !in bloodGroups -> {

                message(
                    "Blood Group Verification Required",
                    "Your registered blood group is " +
                            "missing or invalid. Please have " +
                            "it verified before editing " +
                            "your donor profile."
                )
            }

            !validBirthDate(dob) -> {

                message(
                    "Invalid Date of Birth",
                    "Select a valid past date of birth."
                )
            }

            gender !in genders -> {

                message(
                    "Select Gender",
                    "Choose a valid gender option."
                )
            }

            area.isBlank() ||
                    area.length > 100 -> {

                inputArea.error =
                    "Enter an area within 100 characters"

                inputArea.requestFocus()
            }

            emergencyContact.isNotBlank() &&
                    !validMobile(emergencyContact) -> {

                inputEmergencyContact.error =
                    "Enter 9 digits beginning with 7"

                inputEmergencyContact.requestFocus()
            }

            else -> {

                return ProfileInput(
                    fullName = name,
                    phone = "+94$phone",
                    dateOfBirth = dob,
                    gender = gender,
                    area = area,
                    emergencyContact =
                        if (emergencyContact.isBlank()) {
                            ""
                        } else {
                            "+94$emergencyContact"
                        }
                )
            }
        }

        return null
    }

    // ==========================================
    // CONFIRM PROFILE UPDATE
    // ==========================================

    private fun confirmSave(values: ProfileInput) {

        MaterialAlertDialogBuilder(this)
            .setTitle("Save Profile Changes?")
            .setMessage(
                "Name: ${values.fullName}\n" +
                        "Phone: ${values.phone}\n" +
                        "Area: ${values.area}\n\n" +
                        "Your verified blood group and " +
                        "login email will not be changed.\n\n" +
                        "Save these updates to Firebase?"
            )
            .setNegativeButton(
                "Review",
                null
            )
            .setPositiveButton(
                "Save Changes"
            ) { _, _ ->

                saveProfile(values)
            }
            .show()
    }

    // ==========================================
    // FIREBASE TRANSACTION
    // ==========================================

    private fun saveProfile(values: ProfileInput) {

        if (!loaded || saving) {
            return
        }

        val user = auth.currentUser ?: run {

            message(
                "Session Expired",
                "Please sign in again."
            )

            return
        }

        val token = generation

        setBusy(true, "SAVING...")

        val reference =
            db.collection("users")
                .document(user.uid)

        db.runTransaction { transaction ->

            val current =
                transaction.get(reference)

            val status =
                current.getString("status")
                    ?.ifBlank { "Active" }
                    ?: "Active"

            val authorized =
                current.exists() &&
                        current.getString("role")
                            .equals("USER", true) &&
                        status.equals("Active", true)

            if (!authorized) {

                throw IllegalStateException(
                    "An active USER profile is required."
                )
            }

            val latestBloodGroup =
                current.getString("bloodGroup")
                    .orEmpty()
                    .trim()

            if (
                latestBloodGroup != originalBloodGroup
            ) {

                throw IllegalStateException(
                    "Your blood group changed. " +
                            "Reopen Edit Profile before saving."
                )
            }

            val updates = mapOf<String, Any>(
                "fullName" to values.fullName,
                "phone" to values.phone,
                "dateOfBirth" to values.dateOfBirth,
                "gender" to values.gender,
                "area" to values.area,
                "emergencyContact" to
                        values.emergencyContact,
                "profileCompleted" to true,
                "updatedAt" to
                        FieldValue.serverTimestamp()
            )

            // Do not update:
            // - Authentication email
            // - Verified blood group
            // - USER role or account status
            // - Privacy and availability settings

            transaction.update(
                reference,
                updates
            )

            true
        }
            .addOnSuccessListener {

                if (!isCurrent(token, user.uid)) {
                    return@addOnSuccessListener
                }

                saving = false

                updateHeader(values.fullName)

                MaterialAlertDialogBuilder(this)
                    .setTitle("Profile Updated")
                    .setMessage(
                        "Your profile information was " +
                                "saved successfully to Firebase."
                    )
                    .setCancelable(false)
                    .setPositiveButton(
                        "Done"
                    ) { _, _ ->
                        finish()
                    }
                    .show()
            }
            .addOnFailureListener { error ->

                if (!isCurrent(token, user.uid)) {
                    return@addOnFailureListener
                }

                setBusy(false, "SAVE CHANGES")

                message(
                    "Profile Update Failed",
                    error.localizedMessage
                        ?: "Your changes were not saved."
                )
            }
    }

    // ==========================================
    // UI HELPERS
    // ==========================================

    private fun setBusy(
        value: Boolean,
        label: String
    ) {

        saving = value

        btnSaveChanges.isEnabled =
            !value && loaded

        btnSaveChanges.text = label
    }

    private fun updateHeader(
        name: String
    ) {

        val displayName =
            name.trim().ifBlank {
                "Blood Donor"
            }

        txtProfileName.text = displayName

        txtProfileInitial.text =
            displayName.first()
                .uppercaseChar()
                .toString()
    }

    private fun message(
        title: String,
        body: String
    ) {

        if (!active || isFinishing || isDestroyed) {
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton("OK", null)
            .show()
    }
}
