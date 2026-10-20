
package com.example.blooddononerapp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EmergencyRequestActivity : AppCompatActivity() {

    private data class Hospital(
        val id: String,
        val hospitalId: String,
        val name: String,
        val area: String,
        val district: String,
        val province: String
    ) {
        val locationKey: String
            get() = "${area.trim().lowercase(Locale.ROOT)}|${district.trim().lowercase(Locale.ROOT)}"
    }

    private data class Location(val area: String, val district: String, val province: String) {
        val key: String
            get() = "${area.trim().lowercase(Locale.ROOT)}|${district.trim().lowercase(Locale.ROOT)}"
        val label: String
            get() = "$area • $district"
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var blood: AutoCompleteTextView
    private lateinit var units: AutoCompleteTextView
    private lateinit var location: AutoCompleteTextView
    private lateinit var hospital: AutoCompleteTextView
    private lateinit var date: EditText
    private lateinit var time: EditText
    private lateinit var primary: EditText
    private lateinit var secondary: EditText
    private lateinit var urgency: AutoCompleteTextView
    private lateinit var notes: EditText
    private lateinit var submit: MaterialButton

    private val groups = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
    private val unitOptions = (1..10).map { if (it == 1) "1 Unit" else "$it Units" }
    private val urgencyOptions = listOf("Critical", "Urgent", "Normal")
    private val hospitals = mutableListOf<Hospital>()
    private val locations = mutableListOf<Location>()
    private var selectedLocation: Location? = null
    private var selectedHospital: Hospital? = null
    private var hospitalListener: ListenerRegistration? = null
    private var changingLocation = false
    private var active = false
    private var saving = false
    private var hospitalListReady = false
    private var version = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_request)

        blood = findViewById(R.id.bloodGroupDropdown)
        units = findViewById(R.id.unitsDropdown)
        location = findViewById(R.id.locationInput)
        hospital = findViewById(R.id.hospitalDropdown)
        date = findViewById(R.id.dateInput)
        time = findViewById(R.id.requiredBeforeInput)
        primary = findViewById(R.id.primaryContactInput)
        secondary = findViewById(R.id.secondaryContactInput)
        urgency = findViewById(R.id.urgencyDropdown)
        notes = findViewById(R.id.additionalInfoInput)
        submit = findViewById(R.id.btnSubmitEmergency)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        dropdown(blood, groups)
        dropdown(units, unitOptions)
        dropdown(urgency, urgencyOptions)

        // Carry the blood group chosen on Donor Details, when provided.
        val presetGroup = intent.getStringExtra("bloodGroup").orEmpty().trim()
        if (presetGroup in groups) blood.setText(presetGroup, false)

        location.threshold = 1
        location.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!changingLocation) {
                    selectedLocation = null
                    resetHospital()
                }
            }
        })
        location.setOnClickListener { if (locations.isNotEmpty()) location.showDropDown() }
        location.setOnItemClickListener { parent, _, position, _ ->
            val label = parent.getItemAtPosition(position).toString()
            locations.firstOrNull { it.label == label }?.let { chooseLocation(it) }
        }
        hospital.isEnabled = false
        hospital.setOnClickListener { if (hospital.isEnabled) hospital.showDropDown() }
        hospital.setOnItemClickListener { parent, _, position, _ ->
            val name = parent.getItemAtPosition(position).toString()
            selectedHospital = hospitals.firstOrNull {
                it.name == name && it.locationKey == selectedLocation?.key
            }
            if (selectedHospital == null) resetHospital()
        }
        date.setOnClickListener { chooseDate() }
        time.setOnClickListener { chooseTime() }
        submit.setOnClickListener { if (validate()) confirmRequest() }
        setSubmitting(false)
    }

    override fun onStart() {
        super.onStart()
        active = true
        listenToHospitals()
    }

    override fun onStop() {
        active = false
        version++
        hospitalListener?.remove()
        hospitalListener = null
        super.onStop()
    }

    private fun dropdown(view: AutoCompleteTextView, choices: List<String>) {
        view.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, choices))
        view.setOnClickListener { view.showDropDown() }
    }

    private fun listenToHospitals() {
        hospitalListener?.remove()
        hospitalListReady = false
        if (!saving) setSubmitting(false)
        location.hint = "Loading available hospitals..."
        hospitalListener = db.collection("hospitals")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (!active || isFinishing || isDestroyed) return@addSnapshotListener
                if (error != null) {
                    hospitalListReady = false
                    if (!saving) setSubmitting(false)
                    location.hint = "Unable to load hospitals"
                    message("Hospitals Unavailable", error.localizedMessage ?: "Check the connection and retry.")
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.metadata.isFromCache || snapshot.metadata.hasPendingWrites()) {
                    return@addSnapshotListener
                }
                val previousLocation = selectedLocation?.key
                val previousHospital = selectedHospital?.id
                hospitals.clear()
                snapshot.documents.forEach { doc ->
                    val name = doc.getString("name").orEmpty().trim()
                    val area = doc.getString("area").orEmpty().trim()
                    val district = doc.getString("district").orEmpty().trim()
                    if (name.isNotBlank() && area.isNotBlank() && district.isNotBlank() &&
                        doc.getString("status").equals("Active", true)) {
                        hospitals.add(Hospital(doc.id, doc.getString("hospitalId").orEmpty(), name,
                            area, district, doc.getString("province").orEmpty()))
                    }
                }
                locations.clear()
                locations.addAll(hospitals.map { Location(it.area, it.district, it.province) }
                    .distinctBy { it.key }.sortedWith(compareBy({ it.district }, { it.area })))
                location.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line,
                    locations.map { it.label }))
                hospitalListReady = true
                location.hint = if (locations.isEmpty()) "No active hospitals available" else "Choose city / area"
                selectedLocation = locations.firstOrNull { it.key == previousLocation }
                if (selectedLocation == null) {
                    changingLocation = true
                    location.setText("", false)
                    changingLocation = false
                    resetHospital()
                } else {
                    chooseLocation(selectedLocation!!)
                    val found = hospitals.firstOrNull { it.id == previousHospital &&
                            it.locationKey == selectedLocation?.key }
                    if (found != null) {
                        selectedHospital = found
                        hospital.setText(found.name, false)
                    }
                }
                if (!saving) setSubmitting(false)
            }
    }

    private fun chooseLocation(choice: Location) {
        selectedLocation = choice
        changingLocation = true
        location.setText(choice.label, false)
        changingLocation = false
        location.error = null
        resetHospital()
        val choices = hospitals.filter { it.locationKey == choice.key }
            .map { it.name }.distinct().sorted()
        hospital.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, choices))
        hospital.isEnabled = choices.isNotEmpty()
        hospital.hint = if (choices.isEmpty()) "No hospital in this area" else "Choose hospital"
    }

    private fun resetHospital() {
        selectedHospital = null
        hospital.setText("", false)
        hospital.isEnabled = false
        hospital.setAdapter(null)
        hospital.hint = "Choose an area first"
    }

    private fun chooseDate() {
        val now = Calendar.getInstance()
        val picker = DatePickerDialog(this, { _, year, month, day ->
            val selected = Calendar.getInstance().apply { set(year, month, day) }
            date.setText(SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).format(selected.time))
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
        val startToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        picker.datePicker.minDate = startToday.timeInMillis
        picker.show()
    }

    private fun chooseTime() {
        val now = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            val chosen = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            }
            time.setText(SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(chosen.time))
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
    }

    private fun validPhone(text: String): Boolean =
        Regex("^(?:\\+94|0)7\\d{8}$").matches(text.replace(" ", "").replace("-", ""))

    private fun validFutureDateTime(): Boolean {
        val parser = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.ENGLISH)
        parser.isLenient = false
        val chosen = runCatching {
            parser.parse("${date.text.toString().trim()} ${time.text.toString().trim()}")
        }.getOrNull()
        return chosen != null && chosen.time > System.currentTimeMillis()
    }

    private fun validate(): Boolean {
        when {
            !hospitalListReady -> message("Hospital Data Unavailable", "Wait for the current hospital list to load.")
            blood.text.toString().trim() !in groups -> message("Blood Group Required", "Select a valid blood group.")
            units.text.toString().trim() !in unitOptions -> message("Units Required", "Select an exact number of blood units.")
            selectedLocation == null -> { location.error = "Select a location from the list"; location.requestFocus() }
            selectedHospital == null || hospital.text.toString() != selectedHospital?.name ->
                message("Hospital Required", "Choose an active hospital from the list.")
            !validFutureDateTime() -> message("Invalid Date or Time", "Select a valid future date and time for the request.")
            !validPhone(primary.text.toString()) -> { primary.error = "Valid Sri Lankan mobile number required"; primary.requestFocus() }
            secondary.text.toString().isNotBlank() && !validPhone(secondary.text.toString()) -> {
                secondary.error = "Enter a valid Sri Lankan mobile number"; secondary.requestFocus()
            }
            urgency.text.toString().trim() !in urgencyOptions -> message("Urgency Required", "Choose Critical, Urgent or Normal.")
            else -> return true
        }
        return false
    }

    private fun confirmRequest() {
        val selected = selectedLocation ?: return
        val center = selectedHospital ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm Emergency Blood Request")
            .setMessage("Blood group: ${blood.text}\nUnits: ${units.text}\nUrgency: ${urgency.text}\n\n" +
                    "Hospital: ${center.name}\nArea: ${selected.area}, ${selected.district}\n" +
                    "Required: ${date.text} at ${time.text}\n\n" +
                    "Only submit accurate information. For an immediate emergency, contact the hospital directly.")
            .setNegativeButton("Edit", null)
            .setPositiveButton("Submit Request") { _, _ ->
                if (validate()) saveRequest()
            }.show()
    }

    private fun saveRequest() {
        if (saving) return
        val user = auth.currentUser ?: run {
            message("Sign-in Required", "Please sign in again before submitting.")
            return
        }
        val selected = selectedLocation ?: return
        val center = selectedHospital ?: return
        val token = version
        setSubmitting(true)

        db.collection("users").document(user.uid).get(Source.SERVER)
            .addOnSuccessListener { profile ->
                if (!valid(token)) return@addOnSuccessListener
                val status = profile.getString("status")?.ifBlank { "Active" } ?: "Active"
                if (!profile.exists() || !profile.getString("role").equals("USER", true) ||
                    !status.equals("Active", true)) {
                    failed("An active USER account is required.")
                    return@addOnSuccessListener
                }
                db.collection("hospitals").document(center.id).get(Source.SERVER)
                    .addOnSuccessListener { actual ->
                        if (!valid(token)) return@addOnSuccessListener
                        val current = actual.exists() && actual.getString("status").equals("Active", true) &&
                                actual.getString("name") == center.name &&
                                actual.getString("area").equals(selected.area, true) &&
                                actual.getString("district").equals(selected.district, true)
                        if (!current) {
                            failed("Hospital details changed. Please select the hospital again.")
                            resetHospital()
                            return@addOnSuccessListener
                        }
                        if (!validFutureDateTime()) {
                            failed("The required time has passed. Please select a future time.")
                            return@addOnSuccessListener
                        }
                        val reference = db.collection("emergencyRequests").document()
                        val values = hashMapOf<String, Any>(
                            "requestId" to reference.id,
                            "userId" to user.uid,
                            "userEmail" to (user.email ?: ""),
                            "bloodGroup" to blood.text.toString().trim(),
                            "units" to units.text.toString().trim(),
                            "province" to selected.province,
                            "district" to selected.district,
                            "area" to selected.area,
                            "hospital" to center.name,
                            "hospitalDocumentId" to center.id,
                            "hospitalId" to center.hospitalId,
                            "requiredDate" to date.text.toString().trim(),
                            "requiredBefore" to time.text.toString().trim(),
                            "primaryContact" to primary.text.toString().trim(),
                            "secondaryContact" to secondary.text.toString().trim(),
                            "urgency" to urgency.text.toString().trim(),
                            "additionalInfo" to notes.text.toString().trim(),
                            "status" to "Pending",
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                        reference.set(values).addOnSuccessListener {
                            if (!valid(token)) return@addOnSuccessListener
                            setSubmitting(false)
                            submit.isEnabled = false
                            MaterialAlertDialogBuilder(this)
                                .setTitle("Request Submitted")
                                .setMessage("Your emergency blood request was saved in Firebase as Pending. Hospital staff can now review it.")
                                .setCancelable(false)
                                .setPositiveButton("View My Requests") { _, _ ->
                                    startActivity(Intent(this, MyRequestsActivity::class.java))
                                    finish()
                                }.show()
                        }.addOnFailureListener { error ->
                            if (valid(token)) failed(error.localizedMessage ?: "Could not save the request.")
                        }
                    }.addOnFailureListener { error ->
                        if (valid(token)) failed(error.localizedMessage ?: "Could not verify the hospital.")
                    }
            }.addOnFailureListener { error ->
                if (valid(token)) failed(error.localizedMessage ?: "Could not verify the USER account.")
            }
    }

    private fun setSubmitting(value: Boolean) {
        saving = value
        submit.isEnabled = !value && hospitalListReady
        submit.text = if (value) "Submitting Request..." else "Submit Emergency Request"
    }

    private fun failed(text: String) {
        setSubmitting(false)
        message("Request Not Submitted", text)
    }

    private fun valid(token: Int): Boolean = active && token == version && !isFinishing && !isDestroyed

    private fun message(title: String, body: String) {
        if (!isFinishing && !isDestroyed)
            MaterialAlertDialogBuilder(this).setTitle(title).setMessage(body)
                .setPositiveButton("OK", null).show()
    }
}
