
package com.example.blooddononerapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
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
import java.util.Locale

class BloodBankAvailabilityActivity : AppCompatActivity() {

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private lateinit var bankContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var btnNearby: TextView
    private lateinit var btnAll: TextView

    private var hospitalListener: ListenerRegistration? = null
    private var profileListener: ListenerRegistration? = null

    private var active = false
    private var generation = 0
    private var hospitalsReady = false
    private var profileReady = false

    private var hospitalError: String? = null
    private var profileError: String? = null

    private var myDistrict = ""
    private var selectedBloodGroup = "All"
    private var showMyDistrict = true

    private val hospitals = mutableListOf<BloodBank>()

    private val bloodGroups = linkedMapOf(
        "A+" to "APlus",
        "A-" to "AMinus",
        "B+" to "BPlus",
        "B-" to "BMinus",
        "AB+" to "ABPlus",
        "AB-" to "ABMinus",
        "O+" to "OPlus",
        "O-" to "OMinus"
    )

    private data class BloodBank(
        val id: String,
        val name: String,
        val area: String,
        val district: String,
        val location: String,
        val acceptingDonors: Boolean?,
        val stock: Map<String, Long?>
    ) {
        fun units(key: String): Long? = stock[key]

        fun totalUnits(): Long? {
            if (stock.values.any { it == null }) {
                return null
            }

            return stock.values.filterNotNull().sum()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_blood_bank_availability
        )

        bankContainer = findViewById(R.id.bankContainer)
        searchInput = findViewById(R.id.searchInput)
        btnNearby = findViewById(R.id.btnNearby)
        btnAll = findViewById(R.id.btnAll)

        btnNearby.text = "My District"
        btnAll.text = "All Hospitals"

        selectedBloodGroup =
            savedInstanceState?.getString("bloodGroup")
                ?: "All"

        showMyDistrict =
            savedInstanceState?.getBoolean("myDistrict")
                ?: true

        findViewById<TextView>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        btnNearby.setOnClickListener {
            showMyDistrict = true
            updateTabUI()
            renderBanks()
        }

        btnAll.setOnClickListener {
            showMyDistrict = false
            updateTabUI()
            renderBanks()
        }

        setupBloodGroupFilters()

        searchInput.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    renderBanks()
                }

                override fun afterTextChanged(
                    s: Editable?
                ) = Unit
            }
        )

        updateTabUI()
        showMessageInList("Loading hospital inventory...")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(
            "bloodGroup",
            selectedBloodGroup
        )

        outState.putBoolean(
            "myDistrict",
            showMyDistrict
        )

        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()

        active = true
        startListeners()
    }

    override fun onStop() {
        active = false
        generation++

        profileListener?.remove()
        hospitalListener?.remove()

        profileListener = null
        hospitalListener = null

        super.onStop()
    }

    private fun current(token: Int): Boolean {
        return active &&
                generation == token &&
                !isFinishing &&
                !isDestroyed
    }

    // ==========================================
    // FIREBASE REALTIME CONNECTION
    // ==========================================

    private fun startListeners() {

        val token = ++generation

        profileListener?.remove()
        hospitalListener?.remove()

        profileListener = null
        hospitalListener = null

        hospitalsReady = false
        profileReady = false

        hospitalError = null
        profileError = null

        hospitals.clear()
        myDistrict = ""

        renderBanks()

        listenToUserDistrict(token)
        listenToHospitals(token)
    }

    private fun listenToUserDistrict(token: Int) {

        val uid = auth.currentUser?.uid

        if (uid == null) {
            profileReady = true
            profileError = "Sign in to use My District"
            renderBanks()
            return
        }

        profileListener = db.collection("users")
            .document(uid)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { document, error ->

                if (!current(token)) {
                    return@addSnapshotListener
                }

                if (error != null) {
                    profileReady = true
                    profileError =
                        "Cannot load saved district"
                    myDistrict = ""
                    renderBanks()
                    return@addSnapshotListener
                }

                if (
                    document == null ||
                    document.metadata.isFromCache ||
                    document.metadata.hasPendingWrites()
                ) {
                    return@addSnapshotListener
                }

                profileReady = true

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
                    profileError = "Active USER account required"
                    myDistrict = ""
                } else {
                    profileError = null

                    myDistrict =
                        document.getString("district")
                            .orEmpty()
                            .trim()
                }

                renderBanks()
            }
    }

    private fun listenToHospitals(token: Int) {

        hospitalListener = db
            .collection("hospitals")
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { snapshot, error ->

                if (!current(token)) {
                    return@addSnapshotListener
                }

                if (error != null) {
                    hospitalsReady = false

                    hospitalError =
                        error.localizedMessage
                            ?: "Unable to load hospitals"

                    renderBanks()
                    return@addSnapshotListener
                }

                if (
                    snapshot == null ||
                    snapshot.metadata.isFromCache ||
                    snapshot.metadata.hasPendingWrites()
                ) {
                    return@addSnapshotListener
                }

                val updated = mutableListOf<BloodBank>()

                for (doc in snapshot.documents) {

                    if (
                        !doc.getString("status")
                            .equals("Active", true)
                    ) {
                        continue
                    }

                    val name =
                        doc.getString("name")
                            .orEmpty()
                            .trim()

                    if (name.isBlank()) {
                        continue
                    }

                    val area =
                        doc.getString("area")
                            .orEmpty()
                            .trim()

                    val district =
                        doc.getString("district")
                            .orEmpty()
                            .trim()

                    val location =
                        listOf(area, district)
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(", ")
                            .ifBlank {
                                doc.getString("location")
                                    ?.takeIf {
                                        it.isNotBlank()
                                    }
                                    ?: "Location not provided"
                            }

                    val stock =
                        doc.get("bloodStock")
                                as? Map<*, *>

                    val values = bloodGroups.mapValues { entry ->
                        readStock(
                            stock?.get(entry.value)
                        )
                    }

                    updated.add(
                        BloodBank(
                            id = doc.id,
                            name = name,
                            area = area,
                            district = district,
                            location = location,
                            acceptingDonors =
                                doc.getBoolean(
                                    "acceptingDonors"
                                ),
                            stock = values
                        )
                    )
                }

                hospitals.clear()
                hospitals.addAll(
                    updated.sortedWith(
                        compareBy<BloodBank> {
                            it.district.lowercase(Locale.ROOT)
                        }.thenBy {
                            it.name.lowercase(Locale.ROOT)
                        }
                    )
                )

                hospitalError = null
                hospitalsReady = true

                renderBanks()
            }
    }

    private fun readStock(value: Any?): Long? {

        val number = when (value) {

            is Long -> value

            is Int -> value.toLong()

            else -> null
        }

        return number?.takeIf {
            it in 0L..1000000L
        }
    }

    // ==========================================
    // FILTERS
    // ==========================================

    private fun setupBloodGroupFilters() {

        val buttons = linkedMapOf(
            R.id.btnBloodAll to "All",
            R.id.btnAPlus to "A+",
            R.id.btnAMinus to "A-",
            R.id.btnBPlus to "B+",
            R.id.btnBMinus to "B-",
            R.id.btnABPlus to "AB+",
            R.id.btnABMinus to "AB-",
            R.id.btnOPlus to "O+",
            R.id.btnOMinus to "O-"
        )

        buttons.forEach { (id, group) ->

            findViewById<TextView>(id)
                .setOnClickListener {
                    selectedBloodGroup = group
                    updateBloodGroupUI()
                    renderBanks()
                }
        }

        updateBloodGroupUI()
    }

    private fun updateBloodGroupUI() {

        val buttons = mapOf(
            R.id.btnBloodAll to "All",
            R.id.btnAPlus to "A+",
            R.id.btnAMinus to "A-",
            R.id.btnBPlus to "B+",
            R.id.btnBMinus to "B-",
            R.id.btnABPlus to "AB+",
            R.id.btnABMinus to "AB-",
            R.id.btnOPlus to "O+",
            R.id.btnOMinus to "O-"
        )

        buttons.forEach { (id, group) ->

            val label =
                findViewById<TextView>(id)

            val chosen =
                group == selectedBloodGroup

            label.setBackgroundResource(
                if (chosen) {
                    R.drawable.bg_blood_group_selected
                } else {
                    R.drawable.bg_blood_group_normal
                }
            )

            label.setTextColor(
                if (chosen) {
                    getColor(android.R.color.white)
                } else {
                    getColor(R.color.text_primary)
                }
            )
        }
    }

    private fun updateTabUI() {

        btnNearby.setBackgroundResource(
            if (showMyDistrict) {
                R.drawable.bg_tab_selected
            } else {
                android.R.color.transparent
            }
        )

        btnAll.setBackgroundResource(
            if (showMyDistrict) {
                android.R.color.transparent
            } else {
                R.drawable.bg_tab_selected
            }
        )

        btnNearby.setTextColor(
            if (showMyDistrict) {
                getColor(android.R.color.white)
            } else {
                getColor(R.color.text_secondary)
            }
        )

        btnAll.setTextColor(
            if (showMyDistrict) {
                getColor(R.color.text_secondary)
            } else {
                getColor(android.R.color.white)
            }
        )
    }

    // ==========================================
    // RENDER HOSPITALS
    // ==========================================

    private fun renderBanks() {

        if (!::bankContainer.isInitialized) {
            return
        }

        bankContainer.removeAllViews()

        if (!hospitalsReady) {

            showMessageInList(
                hospitalError
                    ?: "Syncing verified hospital inventory..."
            )

            return
        }

        if (showMyDistrict && !profileReady) {

            showMessageInList(
                "Loading your saved district..."
            )

            return
        }

        if (showMyDistrict && myDistrict.isBlank()) {

            showMessageInList(
                profileError
                    ?: "Your profile has no saved district. " +
                    "Choose All Hospitals or update your profile."
            )

            return
        }

        val search =
            searchInput.text.toString()
                .trim()
                .lowercase(Locale.ROOT)

        val filtered = hospitals.filter { bank ->

            val matchesDistrict =
                !showMyDistrict ||
                        bank.district.equals(
                            myDistrict,
                            true
                        )

            val matchesSearch =
                search.isBlank() ||
                        bank.name.lowercase(Locale.ROOT)
                            .contains(search) ||
                        bank.location.lowercase(Locale.ROOT)
                            .contains(search)

            val matchesGroup =
                if (selectedBloodGroup == "All") {
                    true
                } else {
                    val key =
                        bloodGroups[selectedBloodGroup]

                    key != null &&
                            (bank.units(selectedBloodGroup) ?: 0L) > 0L
                }

            matchesDistrict &&
                    matchesSearch &&
                    matchesGroup
        }

        if (filtered.isEmpty()) {

            showMessageInList(
                if (selectedBloodGroup != "All") {
                    "No matching hospitals with verified " +
                            "$selectedBloodGroup stock found. " +
                            "This does not prove hospitals have " +
                            "no blood. Contact the hospital directly."
                } else if (showMyDistrict) {
                    "No matching active hospitals in " +
                            "$myDistrict. Try All Hospitals."
                } else {
                    "No matching active hospitals found."
                }
            )

            return
        }

        filtered.forEach {
            addBankCard(it)
        }
    }

    // ==========================================
    // HOSPITAL CARD
    // ==========================================

    private fun addBankCard(bank: BloodBank) {

        val card = layoutInflater.inflate(
            R.layout.item_blood_bank,
            bankContainer,
            false
        )

        val total = bank.totalUnits()

        val lowGroups =
            bank.stock.filter {
                it.value != null &&
                        it.value!! <= 2L
            }.keys.toList()

        card.findViewById<TextView>(
            R.id.txtBankName
        ).text = bank.name

        card.findViewById<TextView>(
            R.id.txtLocation
        ).text = bank.location

        card.findViewById<TextView>(
            R.id.txtStock
        ).text =
            if (total == null) {
                "Blood Stock: Some data unavailable"
            } else {
                "Blood Stock: $total recorded units"
            }

        card.findViewById<TextView>(
            R.id.txtDonorNeed
        ).text =
            if (lowGroups.isEmpty()) {
                "Low stock: No verified alerts"
            } else {
                "Low stock: ${lowGroups.size} group(s)"
            }

        card.findViewById<TextView>(
            R.id.txtNeededGroups
        ).text =
            if (lowGroups.isEmpty()) {
                "Stock threshold alerts: None verified"
            } else {
                "Stock threshold alerts: " +
                        lowGroups.joinToString(", ")
            }

        card.findViewById<TextView>(
            R.id.txtDonationStatus
        ).text =
            when (bank.acceptingDonors) {
                true ->
                    "● Hospital reports accepting donors"
                false ->
                    "● Not accepting donor bookings"
                null ->
                    "● Confirm donor acceptance with hospital"
            }

        val donate =
            card.findViewById<MaterialButton>(
                R.id.btnDonate
            )

        donate.isEnabled =
            bank.acceptingDonors != false

        donate.alpha =
            if (donate.isEnabled) 1f else 0.45f

        donate.setOnClickListener {
            confirmDonation(bank)
        }

        card.findViewById<MaterialButton>(
            R.id.btnNeedBlood
        ).setOnClickListener {
            openEmergencyRequest(bank)
        }

        card.findViewById<TextView>(
            R.id.btnViewDetails
        ).setOnClickListener {
            showBankDetails(bank)
        }

        bankContainer.addView(card)
    }

    // ==========================================
    // BANK DETAILS
    // ==========================================

    private fun showBankDetails(bank: BloodBank) {

        val details = buildString {

            append("Location: ${bank.location}\n")
            append("\nRecorded blood inventory:\n")

            bloodGroups.forEach { (group, _) ->

                val value = bank.units(group)

                append(
                    "$group: " +
                            if (value == null) {
                                "Not verified\n"
                            } else {
                                "$value units\n"
                            }
                )
            }

            append("\n")

            val total = bank.totalUnits()

            append(
                "Total: " +
                        (total?.let { "$it units" }
                            ?: "Not fully verified")
            )

            append("\n\nDonor acceptance: ")

            append(
                when (bank.acceptingDonors) {
                    true -> "Reported as accepting"
                    false -> "Not accepting"
                    null -> "Not confirmed"
                }
            )

            append(
                "\n\nInventory figures may change. " +
                        "They do not confirm availability " +
                        "for a particular patient or " +
                        "clinical compatibility. " +
                        "Contact the hospital for confirmation."
            )
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(bank.name)
            .setMessage(details)
            .setNegativeButton("Close", null)
            .setNeutralButton("Need Blood") { _, _ ->
                openEmergencyRequest(bank)
            }
            .setPositiveButton("Donate Blood") { _, _ ->
                confirmDonation(bank)
            }
            .show()
    }

    // ==========================================
    // NAVIGATION
    // ==========================================

    private fun confirmDonation(bank: BloodBank) {

        if (bank.acceptingDonors == false) {

            showDialog(
                "Donation Booking Unavailable",
                "This hospital currently reports " +
                        "that it is not accepting donors."
            )

            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Request Donation Appointment?")
            .setMessage(
                "Hospital: ${bank.name}\n" +
                        "Location: ${bank.location}\n\n" +
                        "Submitting a preferred date and time " +
                        "creates a Pending appointment request. " +
                        "It does not confirm a hospital slot " +
                        "or medical eligibility.\n\n" +
                        "Confirm acceptance with the hospital " +
                        "before travelling."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->

                startActivity(
                    Intent(
                        this,
                        DonateBloodActivity::class.java
                    ).apply {

                        putExtra(
                            "hospital_name",
                            bank.name
                        )

                        putExtra(
                            "hospital_location",
                            bank.location
                        )
                    }
                )
            }
            .show()
    }

    private fun openEmergencyRequest(bank: BloodBank) {

        MaterialAlertDialogBuilder(this)
            .setTitle("Emergency Blood Request")
            .setMessage(
                "The emergency request form will open.\n\n" +
                        "Please select ${bank.name} from " +
                        "the hospital list and confirm " +
                        "the blood group, units and contact details.\n\n" +
                        "For immediate medical emergencies, " +
                        "contact a hospital or emergency " +
                        "medical service directly."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->

                startActivity(
                    Intent(
                        this,
                        EmergencyRequestActivity::class.java
                    ).apply {

                        putExtra(
                            "hospital_name",
                            bank.name
                        )

                        putExtra(
                            "hospital_location",
                            bank.location
                        )
                    }
                )
            }
            .show()
    }

    // ==========================================
    // MESSAGES
    // ==========================================

    private fun showMessageInList(message: String) {

        bankContainer.removeAllViews()

        val label = TextView(this).apply {

            text = message
            textSize = 13f
            gravity = Gravity.CENTER

            setTextColor(
                getColor(R.color.text_secondary)
            )

            val padding =
                (22 * resources.displayMetrics.density)
                    .toInt()

            setPadding(
                padding,
                padding * 3,
                padding,
                padding * 3
            )
        }

        bankContainer.addView(label)
    }

    private fun showDialog(
        title: String,
        message: String
    ) {

        if (!active || isFinishing || isDestroyed) {
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
