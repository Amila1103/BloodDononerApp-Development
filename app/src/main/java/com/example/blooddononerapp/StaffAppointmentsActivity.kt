
package com.example.blooddononerapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StaffAppointmentsActivity : AppCompatActivity() {

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private lateinit var filterToday: TextView
    private lateinit var filterUpcoming: TextView
    private lateinit var filterCompleted: TextView
    private lateinit var filterCancelled: TextView

    private lateinit var appointmentContainer: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var summary: TextView

    private data class Appointment(
        val documentId: String,
        val appointmentId: String,
        val donorName: String,
        val donorId: String,
        val phone: String,
        val bloodGroup: String,
        val hospital: String,
        val location: String,
        val date: String,
        val time: String,
        val status: String,
        val checkedIn: Boolean,
        val completed: Boolean
    )

    private val appointments =
        mutableListOf<Appointment>()

    private var listener: ListenerRegistration? = null

    private var hospital = ""
    private var filter = "Today"

    private var started = false
    private var generation = 0
    private var serverLoaded = false
    private var refreshRequested = false
    private var errorDialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_staff_appointments
        )

        filterToday = findViewById(R.id.filterToday)
        filterUpcoming = findViewById(R.id.filterUpcoming)
        filterCompleted = findViewById(R.id.filterCompleted)
        filterCancelled = findViewById(R.id.filterCancelled)

        appointmentContainer =
            findViewById(R.id.appointmentContainer)

        emptyText =
            findViewById(R.id.txtEmptyAppointments)

        summary =
            findViewById(R.id.txtAppointmentSummary)

        // Closed includes Cancelled, Rejected and Deferred.
        filterCancelled.text = "Closed"

        findViewById<MaterialCardView>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        findViewById<MaterialCardView>(R.id.btnRefresh)
            .setOnClickListener {
                reload(manual = true)
            }

        listOf(
            "Today" to filterToday,
            "Upcoming" to filterUpcoming,
            "Completed" to filterCompleted,
            "Closed" to filterCancelled
        ).forEach { (name, view) ->

            view.setOnClickListener {

                filter = name

                selectFilter()

                if (serverLoaded) {
                    render()
                } else {
                    showLoading()
                }
            }
        }

        filter =
            savedInstanceState?.getString(
                "staff_appointment_filter"
            ) ?: "Today"

        selectFilter()
        showLoading()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(
            "staff_appointment_filter",
            filter
        )

        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()

        started = true
        reload(manual = false)
    }

    override fun onStop() {
        started = false
        generation++

        listener?.remove()
        listener = null

        serverLoaded = false

        super.onStop()
    }

    private fun isCurrent(token: Int): Boolean {
        return started &&
                !isFinishing &&
                !isDestroyed &&
                generation == token
    }

    // =========================================
    // VERIFY STAFF AND REFRESH
    // =========================================

    private fun reload(manual: Boolean) {

        val user = auth.currentUser

        if (user == null) {
            showUnavailable(
                "Please sign in to your staff account"
            )
            return
        }

        val token = ++generation

        listener?.remove()
        listener = null

        hospital = ""
        appointments.clear()

        serverLoaded = false
        refreshRequested = manual
        errorDialogShown = false

        showLoading()

        db.collection("users")
            .document(user.uid)
            .get(Source.SERVER)
            .addOnSuccessListener { staff ->

                if (!isCurrent(token)) {
                    return@addOnSuccessListener
                }

                val active =
                    staff.exists() &&
                            staff.getString("role").equals(
                                "STAFF",
                                true
                            ) &&
                            staff.getString("status").equals(
                                "Active",
                                true
                            )

                val assigned =
                    staff.getString("hospital")
                        .orEmpty()
                        .trim()

                if (
                    !active ||
                    assigned.isBlank() ||
                    assigned.equals("Not Assigned", true)
                ) {

                    fail(
                        "Active staff account and assigned " +
                                "hospital are required",
                        false
                    )

                    return@addOnSuccessListener
                }

                hospital = assigned

                listenToAppointments(
                    token,
                    assigned
                )
            }
            .addOnFailureListener { error ->

                if (!isCurrent(token)) {
                    return@addOnFailureListener
                }

                fail(
                    "Unable to verify staff: " +
                            (error.localizedMessage
                                ?: "Network error"),
                    true
                )
            }
    }

    // =========================================
    // FIREBASE REALTIME APPOINTMENTS
    // =========================================

    private fun listenToAppointments(
        token: Int,
        assigned: String
    ) {

        summary.text =
            "Syncing appointments • $assigned"

        listener = db.collection("donationAppointments")
            .whereEqualTo("hospital", assigned)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { snapshot, error ->

                if (
                    !isCurrent(token) ||
                    hospital != assigned
                ) {
                    return@addSnapshotListener
                }

                if (error != null) {

                    fail(
                        "Unable to load appointments: " +
                                (error.localizedMessage
                                    ?: "Firebase error"),
                        true
                    )

                    return@addSnapshotListener
                }

                if (
                    snapshot == null ||
                    snapshot.metadata.isFromCache ||
                    snapshot.metadata.hasPendingWrites()
                ) {
                    return@addSnapshotListener
                }

                appointments.clear()

                appointments.addAll(
                    snapshot.documents.map(
                        ::appointmentFromDoc
                    )
                )

                serverLoaded = true

                render()

                // Display success only after server sync.
                if (refreshRequested) {

                    refreshRequested = false

                    StaffFeedbackDialogs.show(
                        this,
                        StaffFeedbackDialogs.Type.SUCCESS,
                        "Appointments refreshed",
                        "Latest hospital appointments " +
                                "have been synced with Firebase."
                    )
                }
            }
    }

    // =========================================
    // MAP FIREBASE DOCUMENT
    // =========================================

    private fun appointmentFromDoc(
        doc: DocumentSnapshot
    ): Appointment {

        val status =
            doc.getString("status")
                .orEmpty()
                .ifBlank { "Pending" }

        val completed =
            status.equals("Completed", true) ||
                    doc.getBoolean("completed") == true ||
                    doc.getBoolean("inventoryUpdated") == true

        return Appointment(
            documentId = doc.id,

            appointmentId =
                doc.getString("appointmentId")
                    ?: doc.id,

            donorName =
                doc.getString("donorName")
                    ?: "Donor",

            donorId =
                doc.getString("donorId")
                    .orEmpty(),

            phone =
                doc.getString("phone")
                    .orEmpty(),

            bloodGroup =
                doc.getString("bloodGroup")
                    ?: "—",

            hospital =
                doc.getString("hospital")
                    .orEmpty(),

            location =
                doc.getString("location")
                    .orEmpty(),

            date =
                doc.getString("donationDate")
                    .orEmpty(),

            time =
                doc.getString("donationTime")
                    .orEmpty(),

            status = status,

            checkedIn =
                doc.getBoolean("checkedIn") == true,

            completed = completed
        )
    }

    // =========================================
    // CORRECT STATUS DISPLAY
    // =========================================

    private fun displayStatus(
        item: Appointment
    ): String {

        // Completed has highest priority.
        if (item.completed) {
            return "Completed"
        }

        if (item.status.equals("Cancelled", true)) {
            return "Cancelled"
        }

        if (item.status.equals("Rejected", true)) {
            return "Rejected"
        }

        if (item.status.equals("Deferred", true)) {
            return "Deferred"
        }

        if (item.status.equals("Eligible", true)) {
            return "Eligible"
        }

        if (
            item.status.equals("Checked In", true) &&
            item.checkedIn
        ) {
            return "Checked In"
        }

        return item.status
    }

    private fun isClosed(item: Appointment): Boolean {

        val status = displayStatus(item)

        return status.equals("Cancelled", true) ||
                status.equals("Rejected", true) ||
                status.equals("Deferred", true)
    }

    // =========================================
    // DATE HELPERS
    // =========================================

    private fun dateOf(text: String): Long? {

        val formats = listOf(
            "dd MMM yyyy",
            "d MMM yyyy",
            "dd MMMM yyyy",
            "yyyy-MM-dd",
            "dd/MM/yyyy"
        )

        for (format in formats) {

            val parser =
                SimpleDateFormat(
                    format,
                    Locale.ENGLISH
                )

            parser.isLenient = false

            val value = runCatching {
                parser.parse(text.trim())
            }.getOrNull() ?: continue

            val day =
                SimpleDateFormat(
                    "yyyyMMdd",
                    Locale.ENGLISH
                ).format(value)

            return day.toLongOrNull()
        }

        return null
    }

    private fun todayKey(): Long {

        return SimpleDateFormat(
            "yyyyMMdd",
            Locale.ENGLISH
        ).format(Date()).toLong()
    }

    // =========================================
    // FILTER AND DISPLAY APPOINTMENTS
    // =========================================

    private fun render() {

        if (!serverLoaded) return

        appointmentContainer.removeAllViews()

        val today = todayKey()

        val filtered = appointments.filter { item ->

            when (filter) {

                "Completed" -> {
                    item.completed
                }

                "Closed" -> {
                    isClosed(item) && !item.completed
                }

                "Upcoming" -> {

                    !item.completed &&
                            !isClosed(item) &&
                            (
                                    dateOf(item.date) == null ||
                                            dateOf(item.date)!! >= today ||
                                            item.checkedIn
                                    )
                }

                else -> {

                    !item.completed &&
                            !isClosed(item) &&
                            dateOf(item.date) == today
                }
            }
        }

        val sorted =
            if (
                filter == "Completed" ||
                filter == "Closed"
            ) {

                filtered.sortedWith(
                    compareByDescending<Appointment> {
                        dateOf(it.date) ?: 0L
                    }.thenByDescending {
                        it.time
                    }
                )

            } else {

                filtered.sortedWith(
                    compareBy<Appointment> {
                        dateOf(it.date) ?: Long.MAX_VALUE
                    }.thenBy {
                        it.time
                    }
                )
            }

        val label =
            if (sorted.size == 1) {
                "appointment"
            } else {
                "appointments"
            }

        summary.text =
            "${sorted.size} $label • $hospital"

        emptyText.visibility =
            if (sorted.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }

        emptyText.text = when (filter) {

            "Today" ->
                "No active appointments scheduled for today"

            "Upcoming" ->
                "No open or upcoming appointments"

            "Completed" ->
                "No completed donations yet"

            else ->
                "No closed appointments"
        }

        sorted.forEach {
            addAppointmentCard(it)
        }
    }

    // =========================================
    // APPOINTMENT CARD UI
    // =========================================

    private fun addAppointmentCard(
        item: Appointment
    ) {

        val card = layoutInflater.inflate(
            R.layout.item_staff_appointment,
            appointmentContainer,
            false
        )

        card.findViewById<TextView>(
            R.id.txtBloodGroup
        ).text = item.bloodGroup

        card.findViewById<TextView>(
            R.id.txtDonorName
        ).text = item.donorName

        card.findViewById<TextView>(
            R.id.txtDonorId
        ).text =
            item.donorId.ifBlank {
                "Donor ID not available"
            }

        card.findViewById<TextView>(
            R.id.txtDateTime
        ).text = listOf(
            item.date,
            item.time
        )
            .filter { it.isNotBlank() }
            .joinToString("  •  ")
            .ifBlank { "Date not provided" }

        card.findViewById<TextView>(
            R.id.txtHospital
        ).text = item.hospital

        val status = displayStatus(item)

        val badge =
            card.findViewById<TextView>(
                R.id.txtStatus
            )

        badge.text = status

        badge.setTextColor(
            when (status.lowercase(Locale.ROOT)) {

                "completed" ->
                    Color.rgb(21, 128, 61)

                "cancelled", "rejected", "deferred" ->
                    Color.rgb(190, 55, 55)

                "eligible" ->
                    Color.rgb(21, 128, 61)

                "checked in" ->
                    Color.rgb(30, 90, 186)

                else ->
                    Color.rgb(185, 105, 17)
            }
        )

        val action =
            card.findViewById<TextView>(
                R.id.txtCardAction
            )

        action.text = when (status) {

            "Completed" ->
                "View completed appointment  ›"

            "Cancelled", "Rejected", "Deferred" ->
                "View closed appointment  ›"

            "Eligible" ->
                "Review & record donation  ›"

            "Checked In" ->
                "Continue donor screening  ›"

            else ->
                "View details & check in donor  ›"
        }

        card.setOnClickListener {
            openDetails(item)
        }

        appointmentContainer.addView(card)
    }

    // =========================================
    // OPEN APPOINTMENT DETAILS
    // =========================================

    private fun openDetails(item: Appointment) {

        if (
            !serverLoaded ||
            hospital != item.hospital
        ) {
            return
        }

        startActivity(
            Intent(
                this,
                StaffAppointmentDetailsActivity::class.java
            ).apply {

                putExtra("DOCUMENT_ID", item.documentId)
                putExtra("APPOINTMENT_ID", item.appointmentId)
                putExtra("DONOR_NAME", item.donorName)
                putExtra("DONOR_ID", item.donorId)
                putExtra("BLOOD_GROUP", item.bloodGroup)
                putExtra("DATE", item.date)
                putExtra("TIME", item.time)
                putExtra("CENTER", item.hospital)
                putExtra("LOCATION", item.location)
                putExtra("PHONE", item.phone)
                putExtra("STATUS", item.status)
                putExtra("CHECKED_IN", item.checkedIn)
            }
        )
    }

    // =========================================
    // FILTER STYLING
    // =========================================

    private fun selectFilter() {

        listOf(
            filterToday,
            filterUpcoming,
            filterCompleted,
            filterCancelled
        ).forEach {

            it.setBackgroundResource(
                R.drawable.bg_staff_filter
            )

            it.setTextColor(
                Color.parseColor("#666666")
            )
        }

        val selected = when (filter) {

            "Upcoming" -> filterUpcoming

            "Completed" -> filterCompleted

            "Closed" -> filterCancelled

            else -> filterToday
        }

        selected.setBackgroundResource(
            R.drawable.bg_staff_filter_selected
        )

        selected.setTextColor(Color.WHITE)
    }

    // =========================================
    // LOADING AND ERROR STATES
    // =========================================

    private fun showLoading() {

        appointmentContainer.removeAllViews()

        emptyText.visibility = View.VISIBLE

        emptyText.text =
            "Loading verified Firebase appointments..."

        summary.text =
            "Syncing appointments..."
    }

    private fun showUnavailable(message: String) {

        serverLoaded = false
        appointments.clear()

        appointmentContainer.removeAllViews()

        emptyText.visibility = View.VISIBLE
        emptyText.text = message

        summary.text =
            "Appointments unavailable"
    }

    private fun fail(
        message: String,
        retryAllowed: Boolean
    ) {

        showUnavailable(message)

        if (
            errorDialogShown ||
            !started
        ) {
            return
        }

        errorDialogShown = true

        StaffFeedbackDialogs.show(
            this,
            StaffFeedbackDialogs.Type.ERROR,
            "Cannot load appointments",
            message,

            positiveText =
                if (retryAllowed) {
                    "Retry"
                } else {
                    "OK"
                },

            negativeText =
                if (retryAllowed) {
                    "Cancel"
                } else {
                    null
                },

            onPositive =
                if (retryAllowed) {
                    { reload(manual = true) }
                } else {
                    null
                }
        )
    }
}
