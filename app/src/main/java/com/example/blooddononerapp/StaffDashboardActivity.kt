
package com.example.blooddononerapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StaffDashboardActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var greeting: TextView
    private lateinit var staffName: TextView
    private lateinit var hospitalName: TextView
    private lateinit var syncStatus: TextView
    private lateinit var requestsCount: TextView
    private lateinit var appointmentsCount: TextView
    private lateinit var recordsCount: TextView
    private lateinit var lowCount: TextView
    private lateinit var alertTitle: TextView
    private lateinit var alertBody: TextView

    private lateinit var stockViews: Map<String, TextView>

    private var profileListener: ListenerRegistration? = null

    private val hospitalListeners =
        mutableListOf<ListenerRegistration>()

    private val sourceStatus =
        mutableMapOf<String, Boolean?>()

    private var active = false
    private var generation = 0
    private var staffVerified = false
    private var assignedHospital = ""

    private val groups = linkedMapOf(
        "A+" to "APlus",
        "A-" to "AMinus",
        "B+" to "BPlus",
        "B-" to "BMinus",
        "AB+" to "ABPlus",
        "AB-" to "ABMinus",
        "O+" to "OPlus",
        "O-" to "OMinus"
    )

    private val red = Color.rgb(239, 38, 55)
    private val green = Color.rgb(21, 128, 61)
    private val gray = Color.rgb(110, 110, 118)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_staff_dashboard)

        greeting = findViewById(R.id.txtGreeting)
        staffName = findViewById(R.id.txtStaffName)
        hospitalName = findViewById(R.id.txtHospitalName)
        syncStatus = findViewById(R.id.txtSyncStatus)

        requestsCount =
            findViewById(R.id.txtPendingEmergencyCount)

        appointmentsCount =
            findViewById(R.id.txtAppointmentCount)

        recordsCount =
            findViewById(R.id.txtDonorCount)

        lowCount =
            findViewById(R.id.txtLowStockCount)

        alertTitle =
            findViewById(R.id.txtRecentAlertTitle)

        alertBody =
            findViewById(R.id.txtRecentAlertBody)

        stockViews = linkedMapOf(
            "A+" to findViewById(R.id.txtStockAPlus),
            "A-" to findViewById(R.id.txtStockAMinus),
            "B+" to findViewById(R.id.txtStockBPlus),
            "B-" to findViewById(R.id.txtStockBMinus),
            "AB+" to findViewById(R.id.txtStockABPlus),
            "AB-" to findViewById(R.id.txtStockABMinus),
            "O+" to findViewById(R.id.txtStockOPlus),
            "O-" to findViewById(R.id.txtStockOMinus)
        )

        findViewById<TextView>(R.id.btnRefreshDashboard)
            .setOnClickListener {
                subscribeToProfile()
            }

        linkCard(
            R.id.btnStaffNotifications,
            StaffNotificationsActivity::class.java
        )

        linkCard(
            R.id.btnStaffProfile,
            StaffProfileActivity::class.java
        )

        linkCard(
            R.id.cardEmergencyRequests,
            StaffEmergencyRequestsActivity::class.java
        )

        linkCard(
            R.id.cardStaffAppointments,
            StaffAppointmentsActivity::class.java
        )

        linkCard(
            R.id.cardStaffInventory,
            StaffBloodInventoryActivity::class.java
        )

        linkCard(
            R.id.cardDonorRecords,
            StaffDonorRecordsActivity::class.java
        )

        // Choose an eligible appointment before donation.
        linkCard(
            R.id.cardRecordDonation,
            StaffAppointmentsActivity::class.java
        )

        linkCard(
            R.id.cardStockSummary,
            StaffBloodInventoryActivity::class.java
        )

        linkCard(
            R.id.cardRecentAlert,
            StaffBloodInventoryActivity::class.java
        )

        findViewById<TextView>(R.id.txtViewAllStock)
            .setOnClickListener {
                openScreen(
                    StaffBloodInventoryActivity::class.java
                )
            }

        linkNavigation(
            R.id.navStaffRequests,
            StaffEmergencyRequestsActivity::class.java
        )

        linkNavigation(
            R.id.navStaffAppointments,
            StaffAppointmentsActivity::class.java
        )

        linkNavigation(
            R.id.navStaffInventory,
            StaffBloodInventoryActivity::class.java
        )

        linkNavigation(
            R.id.navStaffProfile,
            StaffProfileActivity::class.java
        )

        findViewById<LinearLayout>(R.id.navStaffDashboard)
            .setOnClickListener {
                findViewById<
                        androidx.core.widget.NestedScrollView
                        >(R.id.staffDashboardScroll)
                    .smoothScrollTo(0, 0)
            }

        resetDashboard()
    }

    override fun onStart() {
        super.onStart()

        active = true

        val hour = Calendar.getInstance()
            .get(Calendar.HOUR_OF_DAY)

        greeting.text = when (hour) {
            in 5..11 -> "Good morning,"
            in 12..16 -> "Good afternoon,"
            else -> "Good evening,"
        }

        subscribeToProfile()
    }

    override fun onStop() {
        active = false
        generation++

        profileListener?.remove()
        profileListener = null

        clearHospitalListeners()

        staffVerified = false

        super.onStop()
    }

    private fun linkCard(
        id: Int,
        screen: Class<*>
    ) {
        findViewById<MaterialCardView>(id)
            .setOnClickListener {
                openScreen(screen)
            }
    }

    private fun linkNavigation(
        id: Int,
        screen: Class<*>
    ) {
        findViewById<LinearLayout>(id)
            .setOnClickListener {
                openScreen(screen)
            }
    }

    private fun openScreen(screen: Class<*>) {
        if (!staffVerified) {
            Snackbar.make(
                syncStatus,
                "Verify your active staff account first",
                Snackbar.LENGTH_LONG
            ).show()

            return
        }

        startActivity(
            Intent(this, screen)
        )
    }

    private fun subscribeToProfile() {
        val user = auth.currentUser

        if (user == null) {
            lockDashboard(
                "Please sign in as hospital staff"
            )
            return
        }

        generation++

        profileListener?.remove()
        clearHospitalListeners()

        staffVerified = false
        assignedHospital = ""

        resetDashboard()

        val currentGeneration = generation

        profileListener = db
            .collection("users")
            .document(user.uid)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { doc, error ->

                if (!validGeneration(currentGeneration)) {
                    return@addSnapshotListener
                }

                if (error != null) {
                    lockDashboard(
                        "Staff verification unavailable: " +
                                error.localizedMessage
                    )
                    return@addSnapshotListener
                }

                if (
                    doc == null ||
                    doc.metadata.isFromCache
                ) {
                    return@addSnapshotListener
                }

                val validStaff =
                    doc.exists() &&
                            doc.getString("role") == "STAFF" &&
                            doc.getString("status")
                                .equals("Active", true)

                val hospital =
                    doc.getString("hospital")
                        .orEmpty()
                        .trim()

                if (
                    !validStaff ||
                    hospital.isBlank() ||
                    hospital.equals("Not Assigned", true)
                ) {
                    lockDashboard(
                        "Active STAFF account with " +
                                "hospital assignment required"
                    )
                    return@addSnapshotListener
                }

                staffVerified = true

                val firstName =
                    doc.getString("fullName")
                        .orEmpty()
                        .trim()
                        .substringBefore(" ")
                        .ifBlank { "Staff" }

                staffName.text = "$firstName!"
                hospitalName.text = hospital

                if (
                    assignedHospital != hospital ||
                    hospitalListeners.isEmpty()
                ) {
                    assignedHospital = hospital

                    listenToHospitalData(
                        hospital,
                        currentGeneration
                    )
                }
            }
    }

    private fun validGeneration(value: Int): Boolean {
        return active &&
                !isFinishing &&
                !isDestroyed &&
                generation == value
    }

    private fun clearHospitalListeners() {
        hospitalListeners.forEach {
            it.remove()
        }

        hospitalListeners.clear()
    }

    private fun resetDashboard() {
        staffName.text = "Hospital Staff!"
        hospitalName.text = "Verifying hospital..."

        requestsCount.text = "—"
        appointmentsCount.text = "—"
        recordsCount.text = "—"
        lowCount.text = "—"

        stockViews.values.forEach {
            it.text = "—"
            it.setTextColor(gray)
        }

        alertTitle.text = "Checking inventory"
        alertBody.text = "Waiting for verified Firestore data"

        sourceStatus.clear()

        syncStatus.text = "● Connecting to Firebase..."
        syncStatus.setTextColor(gray)
    }

    private fun lockDashboard(message: String) {
        staffVerified = false
        assignedHospital = ""

        clearHospitalListeners()
        resetDashboard()

        hospitalName.text = "Access unavailable"
        syncStatus.text = message
        syncStatus.setTextColor(red)

        alertTitle.text =
            "Unable to load hospital information"

        alertBody.text =
            "Check staff status and internet connection"
    }

    private fun listenToHospitalData(
        hospital: String,
        token: Int
    ) {
        clearHospitalListeners()
        sourceStatus.clear()

        listOf(
            "requests",
            "appointments",
            "donations",
            "inventory"
        ).forEach {
            sourceStatus[it] = null
        }

        updateSyncLabel()

        // Emergency Requests
        hospitalListeners.add(
            db.collection("emergencyRequests")
                .whereEqualTo("hospital", hospital)
                .addSnapshotListener(
                    MetadataChanges.INCLUDE
                ) { snapshot, error ->

                    if (!validHospital(token, hospital)) {
                        return@addSnapshotListener
                    }

                    if (error != null) {
                        requestsCount.text = "Unavailable"
                        updateSource("requests", false)
                        return@addSnapshotListener
                    }

                    if (
                        snapshot == null ||
                        snapshot.metadata.isFromCache
                    ) {
                        return@addSnapshotListener
                    }

                    val count = snapshot.documents.count {
                        it.getString("status")
                            .orEmpty()
                            .lowercase(Locale.ROOT) in setOf(
                            "pending",
                            "verified",
                            "matching"
                        )
                    }

                    requestsCount.text = if (count == 1) {
                        "1 request needs attention"
                    } else {
                        "$count requests need attention"
                    }

                    updateSource("requests", true)
                }
        )

        // Open Appointments
        hospitalListeners.add(
            db.collection("donationAppointments")
                .whereEqualTo("hospital", hospital)
                .addSnapshotListener(
                    MetadataChanges.INCLUDE
                ) { snapshot, error ->

                    if (!validHospital(token, hospital)) {
                        return@addSnapshotListener
                    }

                    if (error != null) {
                        appointmentsCount.text = "Unavailable"
                        updateSource("appointments", false)
                        return@addSnapshotListener
                    }

                    if (
                        snapshot == null ||
                        snapshot.metadata.isFromCache
                    ) {
                        return@addSnapshotListener
                    }

                    val count = snapshot.documents.count {
                        it.getString("status")
                            .orEmpty()
                            .lowercase(Locale.ROOT) in setOf(
                            "pending",
                            "checked in",
                            "eligible"
                        )
                    }

                    appointmentsCount.text =
                        "$count open appointments"

                    updateSource("appointments", true)
                }
        )

        // Donation Records
        hospitalListeners.add(
            db.collection("donationRecords")
                .whereEqualTo("hospital", hospital)
                .addSnapshotListener(
                    MetadataChanges.INCLUDE
                ) { snapshot, error ->

                    if (!validHospital(token, hospital)) {
                        return@addSnapshotListener
                    }

                    if (error != null) {
                        recordsCount.text = "Unavailable"
                        updateSource("donations", false)
                        return@addSnapshotListener
                    }

                    if (
                        snapshot == null ||
                        snapshot.metadata.isFromCache
                    ) {
                        return@addSnapshotListener
                    }

                    recordsCount.text =
                        "${snapshot.size()} donation records"

                    updateSource("donations", true)
                }
        )

        // Hospital Inventory
        hospitalListeners.add(
            db.collection("hospitals")
                .whereEqualTo("name", hospital)
                .limit(2)
                .addSnapshotListener(
                    MetadataChanges.INCLUDE
                ) { snapshot, error ->

                    if (!validHospital(token, hospital)) {
                        return@addSnapshotListener
                    }

                    if (
                        error != null ||
                        snapshot == null
                    ) {
                        unavailableInventory(
                            "Inventory data could not be loaded"
                        )

                        updateSource("inventory", false)
                        return@addSnapshotListener
                    }

                    if (
                        snapshot.metadata.isFromCache ||
                        snapshot.metadata.hasPendingWrites()
                    ) {
                        return@addSnapshotListener
                    }

                    if (snapshot.size() != 1) {
                        unavailableInventory(
                            "Hospital not found or duplicated"
                        )

                        updateSource("inventory", false)
                        return@addSnapshotListener
                    }

                    val doc = snapshot.documents.first()

                    if (
                        !doc.getString("status")
                            .equals("Active", true)
                    ) {
                        unavailableInventory(
                            "Hospital is not active"
                        )

                        updateSource("inventory", false)
                        return@addSnapshotListener
                    }

                    val raw =
                        doc.get("bloodStock") as? Map<*, *>

                    if (raw == null) {
                        unavailableInventory(
                            "Hospital blood stock map is missing"
                        )

                        updateSource("inventory", false)
                        return@addSnapshotListener
                    }

                    val low = mutableListOf<String>()
                    var missing = 0

                    for ((group, key) in groups) {
                        val count =
                            (raw[key] as? Number)
                                ?.toLong()
                                ?.takeIf { it >= 0L }

                        stockViews[group]?.apply {
                            text = count?.let {
                                "$it units"
                            } ?: "—"

                            setTextColor(
                                when {
                                    count == null -> gray
                                    count <= 2L -> red
                                    else -> green
                                }
                            )
                        }

                        if (count == null) {
                            missing++
                        } else if (count <= 2L) {
                            low.add(group)
                        }
                    }

                    lowCount.text =
                        "${low.size} low stock"

                    alertTitle.text = when {
                        missing > 0 ->
                            "Inventory needs review"

                        low.isEmpty() ->
                            "Blood stock is within threshold"

                        low.size == 1 ->
                            "1 blood group is running low"

                        else ->
                            "${low.size} blood groups are running low"
                    }

                    alertBody.text = when {
                        missing > 0 ->
                            "$missing group values unavailable; contact Admin"

                        low.isEmpty() ->
                            "All recorded groups are above 2 units"

                        else ->
                            "Low (0–2 units): " +
                                    low.joinToString(", ")
                    }

                    updateSource(
                        "inventory",
                        missing == 0
                    )
                }
        )
    }

    private fun unavailableInventory(message: String) {
        lowCount.text = "Unavailable"

        stockViews.values.forEach {
            it.text = "—"
            it.setTextColor(gray)
        }

        alertTitle.text = "Blood inventory unavailable"
        alertBody.text = message
    }

    private fun validHospital(
        token: Int,
        hospital: String
    ): Boolean {
        return validGeneration(token) &&
                staffVerified &&
                assignedHospital == hospital
    }

    private fun updateSource(
        name: String,
        success: Boolean
    ) {
        sourceStatus[name] = success
        updateSyncLabel()
    }

    private fun updateSyncLabel() {
        val successful =
            sourceStatus.values.count { it == true }

        val failures =
            sourceStatus.values.count { it == false }

        when {
            failures > 0 -> {
                syncStatus.text =
                    "● Partial data • $successful/4 sources loaded • Tap Refresh"

                syncStatus.setTextColor(red)
            }

            successful == 4 -> {
                val time = SimpleDateFormat(
                    "h:mm a",
                    Locale.getDefault()
                ).format(Date())

                syncStatus.text =
                    "● Firebase synced • $time"

                syncStatus.setTextColor(green)
            }

            else -> {
                syncStatus.text =
                    "● Syncing Firebase data • $successful/4"

                syncStatus.setTextColor(gray)
            }
        }
    }
}
