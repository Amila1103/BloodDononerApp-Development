
package com.example.blooddononerapp

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import java.util.Locale

class DonationHistoryActivity : AppCompatActivity() {

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private lateinit var filterAll: TextView
    private lateinit var filterCompleted: TextView
    private lateinit var filterUpcoming: TextView

    private lateinit var txtTotalDonations: TextView
    private lateinit var txtCenters: TextView
    private lateinit var txtBloodGroup: TextView

    private lateinit var recordsContainer: LinearLayout
    private lateinit var txtEmptyRecords: TextView

    private var profileListener: ListenerRegistration? = null
    private var recordsListener: ListenerRegistration? = null
    private var appointmentsListener: ListenerRegistration? = null

    private var active = false
    private var generation = 0
    private var currentFilter = "ALL"

    private var recordsReady = false
    private var appointmentsReady = false

    private var recordsError: String? = null
    private var appointmentsError: String? = null

    private var registeredBloodGroup = ""

    private val completedRecords =
        mutableListOf<HistoryRecord>()

    private val appointmentRecords =
        mutableListOf<HistoryRecord>()

    private data class HistoryRecord(
        val documentId: String,
        val appointmentId: String,
        val donationId: String,
        val bloodGroup: String,
        val hospital: String,
        val location: String,
        val date: String,
        val time: String,
        val donationType: String,
        val amountMl: Long,
        val status: String,
        val completedDonation: Boolean,
        val createdAtMillis: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_donation_history
        )

        filterAll = findViewById(R.id.filterAll)
        filterCompleted =
            findViewById(R.id.filterCompleted)
        filterUpcoming =
            findViewById(R.id.filterUpcoming)

        txtTotalDonations =
            findViewById(R.id.txtTotalDonations)

        txtCenters =
            findViewById(R.id.txtCenters)

        txtBloodGroup =
            findViewById(R.id.txtBloodGroup)

        recordsContainer =
            findViewById(R.id.recordsContainer)

        txtEmptyRecords =
            findViewById(R.id.txtEmptyRecords)

        findViewById<TextView>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        filterAll.setOnClickListener {
            changeFilter("ALL")
        }

        filterCompleted.setOnClickListener {
            changeFilter("COMPLETED")
        }

        filterUpcoming.setOnClickListener {
            changeFilter("UPCOMING")
        }

        currentFilter =
            savedInstanceState
                ?.getString("historyFilter")
                ?: "ALL"

        changeFilter(currentFilter)

        txtTotalDonations.text = "—"
        txtCenters.text = "—"
        txtBloodGroup.text = "—"

        showLoading()
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        outState.putString(
            "historyFilter",
            currentFilter
        )

        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()

        active = true
        connectFirebase()
    }

    override fun onStop() {
        active = false
        generation++

        profileListener?.remove()
        recordsListener?.remove()
        appointmentsListener?.remove()

        profileListener = null
        recordsListener = null
        appointmentsListener = null

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
    // CONNECT REALTIME FIREBASE DATA
    // ==========================================

    private fun connectFirebase() {

        val user = auth.currentUser ?: run {

            recordsReady = false
            appointmentsReady = false

            showUnavailable(
                "Please sign in to view donation history."
            )

            return
        }

        val uid = user.uid
        val token = ++generation

        profileListener?.remove()
        recordsListener?.remove()
        appointmentsListener?.remove()

        profileListener = null
        recordsListener = null
        appointmentsListener = null

        completedRecords.clear()
        appointmentRecords.clear()

        registeredBloodGroup = ""

        recordsReady = false
        appointmentsReady = false

        recordsError = null
        appointmentsError = null

        txtTotalDonations.text = "—"
        txtCenters.text = "—"
        txtBloodGroup.text = "—"

        showLoading()

        listenToProfile(uid, token)
        listenToCompleted(uid, token)
        listenToAppointments(uid, token)
    }

    private fun listenToProfile(
        uid: String,
        token: Int
    ) {

        profileListener = db.collection("users")
            .document(uid)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { document, error ->

                if (!isCurrent(token, uid)) {
                    return@addSnapshotListener
                }

                if (error != null) {
                    txtBloodGroup.text = "Unavailable"
                    return@addSnapshotListener
                }

                if (
                    document == null ||
                    document.metadata.isFromCache ||
                    document.metadata.hasPendingWrites()
                ) {
                    return@addSnapshotListener
                }

                registeredBloodGroup =
                    document.getString("bloodGroup")
                        .orEmpty()

                updateStatistics()
            }
    }

    // ==========================================
    // COMPLETED DONATION RECORDS
    // ==========================================

    private fun listenToCompleted(
        uid: String,
        token: Int
    ) {

        recordsListener = db
            .collection("donationRecords")
            .whereEqualTo("userId", uid)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { snapshot, error ->

                if (!isCurrent(token, uid)) {
                    return@addSnapshotListener
                }

                if (error != null) {

                    recordsError =
                        error.localizedMessage
                            ?: "Unable to load completed records"

                    recordsReady = true
                    completedRecords.clear()

                    render()
                    return@addSnapshotListener
                }

                if (
                    snapshot == null ||
                    snapshot.metadata.isFromCache ||
                    snapshot.metadata.hasPendingWrites()
                ) {
                    return@addSnapshotListener
                }

                recordsError = null
                recordsReady = true

                completedRecords.clear()

                for (document in snapshot.documents) {

                    val status =
                        document.getString("status")
                            ?: "Completed"

                    if (!status.equals("Completed", true)) {
                        continue
                    }

                    completedRecords.add(
                        HistoryRecord(
                            documentId = document.id,
                            appointmentId =
                                document.getString(
                                    "appointmentId"
                                ).orEmpty(),
                            donationId =
                                document.getString(
                                    "donationId"
                                ) ?: document.id,
                            bloodGroup =
                                document.getString(
                                    "bloodGroup"
                                ).orEmpty(),
                            hospital =
                                document.getString(
                                    "hospital"
                                ).orEmpty(),
                            location = "",
                            date =
                                document.getString(
                                    "recordedDateTime"
                                ).orEmpty(),
                            time = "",
                            donationType =
                                document.getString(
                                    "donationType"
                                ).orEmpty(),
                            amountMl =
                                document.getLong("amountMl")
                                    ?: 0L,
                            status = "Completed",
                            completedDonation = true,
                            createdAtMillis =
                                timestampMillis(document)
                        )
                    )
                }

                completedRecords.sortByDescending {
                    it.createdAtMillis
                }

                updateStatistics()
                render()
            }
    }

    // ==========================================
    // DONATION APPOINTMENTS
    // ==========================================

    private fun listenToAppointments(
        uid: String,
        token: Int
    ) {

        appointmentsListener = db
            .collection("donationAppointments")
            .whereEqualTo("userId", uid)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { snapshot, error ->

                if (!isCurrent(token, uid)) {
                    return@addSnapshotListener
                }

                if (error != null) {

                    appointmentsError =
                        error.localizedMessage
                            ?: "Unable to load appointments"

                    appointmentsReady = true
                    appointmentRecords.clear()

                    render()
                    return@addSnapshotListener
                }

                if (
                    snapshot == null ||
                    snapshot.metadata.isFromCache ||
                    snapshot.metadata.hasPendingWrites()
                ) {
                    return@addSnapshotListener
                }

                appointmentsError = null
                appointmentsReady = true

                appointmentRecords.clear()

                for (document in snapshot.documents) {

                    val status =
                        document.getString("status")
                            .orEmpty()
                            .ifBlank { "Pending" }

                    val completed =
                        status.equals("Completed", true) ||
                                document.getBoolean("completed") == true ||
                                document.getBoolean(
                                    "inventoryUpdated"
                                ) == true ||
                                !document.getString(
                                    "donationRecordId"
                                ).isNullOrBlank()

                    // A completed appointment must not
                    // duplicate its donationRecords entry.
                    if (completed) {
                        continue
                    }

                    appointmentRecords.add(
                        HistoryRecord(
                            documentId = document.id,
                            appointmentId =
                                document.getString(
                                    "appointmentId"
                                ) ?: document.id,
                            donationId = "",
                            bloodGroup =
                                document.getString(
                                    "bloodGroup"
                                ).orEmpty(),
                            hospital =
                                document.getString(
                                    "hospital"
                                ).orEmpty(),
                            location =
                                document.getString(
                                    "location"
                                ).orEmpty(),
                            date =
                                document.getString(
                                    "donationDate"
                                ).orEmpty(),
                            time =
                                document.getString(
                                    "donationTime"
                                ).orEmpty(),
                            donationType = "",
                            amountMl = 0L,
                            status = status,
                            completedDonation = false,
                            createdAtMillis =
                                timestampMillis(document)
                        )
                    )
                }

                appointmentRecords.sortByDescending {
                    it.createdAtMillis
                }

                render()
            }
    }

    private fun timestampMillis(
        document: DocumentSnapshot
    ): Long {

        return document.getTimestamp("createdAt")
            ?.toDate()
            ?.time ?: 0L
    }

    // ==========================================
    // STATS FROM ACTUAL COMPLETED RECORDS
    // ==========================================

    private fun updateStatistics() {

        txtTotalDonations.text =
            if (recordsReady && recordsError == null) {
                completedRecords.size.toString()
            } else {
                "—"
            }

        txtCenters.text =
            if (recordsReady && recordsError == null) {

                completedRecords
                    .map { it.hospital.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .size
                    .toString()

            } else {
                "—"
            }

        val fallback =
            completedRecords.firstOrNull()
                ?.bloodGroup
                .orEmpty()

        txtBloodGroup.text =
            registeredBloodGroup
                .ifBlank { fallback }
                .ifBlank { "—" }
    }

    // ==========================================
    // FILTERS
    // ==========================================

    private fun changeFilter(value: String) {

        currentFilter = value

        val filters = mapOf(
            "ALL" to filterAll,
            "COMPLETED" to filterCompleted,
            "UPCOMING" to filterUpcoming
        )

        filters.forEach { (key, control) ->

            control.setBackgroundResource(
                if (key == value) {
                    R.drawable.bg_history_filter_selected
                } else {
                    R.drawable.bg_history_filter
                }
            )

            control.setTextColor(
                if (key == value) {
                    Color.WHITE
                } else {
                    Color.parseColor("#666666")
                }
            )

            control.setTypeface(
                null,
                if (key == value) {
                    Typeface.BOLD
                } else {
                    Typeface.NORMAL
                }
            )
        }

        if (::recordsContainer.isInitialized) {
            render()
        }
    }

    private fun isOpenAppointment(
        record: HistoryRecord
    ): Boolean {

        return record.status.equals("Pending", true) ||
                record.status.equals("Checked In", true) ||
                record.status.equals("Eligible", true) ||
                record.status.equals("Verified", true)
    }

    // ==========================================
    // RENDER HISTORY
    // ==========================================

    private fun render() {

        if (!::recordsContainer.isInitialized) {
            return
        }

        recordsContainer.removeAllViews()

        if (!recordsReady || !appointmentsReady) {

            txtEmptyRecords.visibility = View.VISIBLE

            txtEmptyRecords.text =
                "Syncing donation records with Firebase..."

            return
        }

        val displayed = when (currentFilter) {

            "COMPLETED" ->
                completedRecords.toList()

            "UPCOMING" ->
                appointmentRecords.filter {
                    isOpenAppointment(it)
                }

            else ->
                (completedRecords + appointmentRecords)
                    .sortedByDescending {
                        it.createdAtMillis
                    }
        }

        displayed.forEach {
            addRecordCard(it)
        }

        if (displayed.isEmpty()) {

            txtEmptyRecords.visibility = View.VISIBLE

            txtEmptyRecords.text =
                when {

                    currentFilter == "COMPLETED" &&
                            recordsError != null ->
                        "Cannot load completed donations: " +
                                recordsError

                    currentFilter == "UPCOMING" &&
                            appointmentsError != null ->
                        "Cannot load appointments: " +
                                appointmentsError

                    recordsError != null ||
                            appointmentsError != null ->
                        "Some history is unavailable. " +
                                "Check your connection."

                    currentFilter == "COMPLETED" ->
                        "No completed donations yet"

                    currentFilter == "UPCOMING" ->
                        "No active appointments found"

                    else ->
                        "No donation history yet"
                }

        } else {
            txtEmptyRecords.visibility = View.GONE
        }
    }

    // ==========================================
    // DONATION / APPOINTMENT CARDS
    // ==========================================

    private fun addRecordCard(
        record: HistoryRecord
    ) {

        val card = LayoutInflater.from(this)
            .inflate(
                R.layout.item_user_donation_history,
                recordsContainer,
                false
            )

        val status =
            card.findViewById<TextView>(
                R.id.txtItemStatus
            )

        val blood =
            card.findViewById<TextView>(
                R.id.txtItemBloodGroup
            )

        val hospital =
            card.findViewById<TextView>(
                R.id.txtItemHospital
            )

        val location =
            card.findViewById<TextView>(
                R.id.txtItemLocation
            )

        val date =
            card.findViewById<TextView>(
                R.id.txtItemDate
            )

        val info =
            card.findViewById<TextView>(
                R.id.txtItemInfo
            )

        val identifier =
            card.findViewById<TextView>(
                R.id.txtItemId
            )

        val detailsButton =
            card.findViewById<TextView>(
                R.id.btnItemDetails
            )

        blood.text =
            record.bloodGroup.ifBlank { "—" }

        hospital.text =
            record.hospital.ifBlank {
                "Donation Center"
            }

        if (record.completedDonation) {

            status.text = "✓ COMPLETED"

            status.setTextColor(
                Color.parseColor("#15803D")
            )

            status.setBackgroundResource(
                R.drawable.bg_status_completed
            )

            location.text = "Completed blood donation"

            date.text =
                record.date.ifBlank {
                    "Date unavailable"
                }

            info.text =
                "${record.donationType.ifBlank { "Donation" }}" +
                        " • ${record.amountMl} mL"

            identifier.text =
                "Donation ID: ${record.donationId}"

            detailsButton.text = "VIEW DETAILS  ›"

        } else {

            status.text =
                record.status.uppercase(Locale.ROOT)

            val closed =
                record.status.equals("Cancelled", true) ||
                        record.status.equals("Rejected", true) ||
                        record.status.equals("Deferred", true)

            status.setTextColor(
                if (closed) {
                    Color.parseColor("#777777")
                } else {
                    Color.parseColor("#C56B15")
                }
            )

            status.setBackgroundResource(
                if (closed) {
                    R.drawable.bg_history_filter
                } else {
                    R.drawable.bg_status_upcoming
                }
            )

            location.text =
                record.location.ifBlank {
                    "Donation appointment"
                }

            date.text =
                record.date.ifBlank {
                    "Date unavailable"
                }

            info.text =
                "Preferred time: " +
                        record.time.ifBlank { "—" }

            identifier.text =
                "Appointment ID: ${record.appointmentId}"

            detailsButton.text = "VIEW APPOINTMENT  ›"
        }

        detailsButton.setOnClickListener {
            showRecordDetails(record)
        }

        card.setOnClickListener {
            showRecordDetails(record)
        }

        recordsContainer.addView(card)
    }

    // ==========================================
    // PROFESSIONAL DETAILS
    // ==========================================

    private fun showRecordDetails(
        record: HistoryRecord
    ) {

        val heading =
            if (record.completedDonation) {
                "Completed Donation"
            } else {
                "Donation Appointment"
            }

        val details =
            if (record.completedDonation) {

                "Donation ID: ${record.donationId}\n" +
                        "Status: Completed\n" +
                        "Blood group: ${record.bloodGroup}\n" +
                        "Hospital: ${record.hospital}\n" +
                        "Recorded date: ${record.date}\n" +
                        "Donation type: ${record.donationType}\n" +
                        "Amount: ${record.amountMl} mL\n\n" +
                        "This record was created by hospital staff."

            } else {

                "Appointment ID: ${record.appointmentId}\n" +
                        "Status: ${record.status}\n" +
                        "Blood group: ${record.bloodGroup}\n" +
                        "Hospital: ${record.hospital}\n" +
                        "Location: ${record.location}\n" +
                        "Preferred date: ${record.date}\n" +
                        "Preferred time: ${record.time}\n\n" +
                        "A Pending booking is not a confirmed " +
                        "hospital slot. Medical screening " +
                        "is required before donation."
            }

        MaterialAlertDialogBuilder(this)
            .setTitle(heading)
            .setMessage(details)
            .setPositiveButton(
                "Close",
                null
            )
            .show()
    }

    private fun showLoading() {

        recordsContainer.removeAllViews()

        txtEmptyRecords.visibility = View.VISIBLE
        txtEmptyRecords.text = "Loading donation history..."
    }

    private fun showUnavailable(
        message: String
    ) {

        recordsContainer.removeAllViews()

        txtEmptyRecords.visibility = View.VISIBLE
        txtEmptyRecords.text = message

        txtTotalDonations.text = "—"
        txtCenters.text = "—"
    }
}
