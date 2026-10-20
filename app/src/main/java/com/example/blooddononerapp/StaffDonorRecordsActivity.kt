
package com.example.blooddononerapp

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import java.util.Locale

class StaffDonorRecordsActivity : AppCompatActivity() {

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private lateinit var recordsContainer: LinearLayout
    private lateinit var txtEmptyRecords: TextView
    private lateinit var txtRecordSummary: TextView
    private lateinit var edtSearch: EditText

    private var staffListener: ListenerRegistration? = null
    private var recordsListener: ListenerRegistration? = null

    private var staffHospital = ""
    private var active = false
    private var ready = false
    private var generation = 0

    private val allRecords =
        mutableListOf<DonorRecord>()

    private data class DonorRecord(
        val documentId: String,
        val donationId: String,
        val donorId: String,
        val donorName: String,
        val bloodGroup: String,
        val donationType: String,
        val amountMl: Long,
        val hospital: String,
        val recordedDateTime: String,
        val status: String,
        val createdAt: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_staff_donor_records
        )

        edtSearch = findViewById(R.id.edtSearchDonor)
        recordsContainer = findViewById(R.id.recordsContainer)
        txtEmptyRecords = findViewById(R.id.txtEmptyRecords)
        txtRecordSummary = findViewById(R.id.txtRecordSummary)

        findViewById<TextView>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        edtSearch.addTextChangedListener {
            if (ready) {
                render()
            }
        }

        showLoading(
            "Checking hospital records..."
        )
    }

    override fun onStart() {
        super.onStart()

        active = true
        listenToStaff()
    }

    override fun onStop() {
        active = false
        generation++

        staffListener?.remove()
        staffListener = null

        recordsListener?.remove()
        recordsListener = null

        ready = false

        super.onStop()
    }

    private fun valid(token: Int): Boolean {

        return active &&
                generation == token &&
                !isFinishing &&
                !isDestroyed
    }

    private fun listenToStaff() {

        val user = auth.currentUser ?: run {
            showUnavailable(
                "Please sign in with a staff account."
            )
            return
        }

        val token = ++generation

        staffListener?.remove()
        recordsListener?.remove()

        staffListener = null
        recordsListener = null

        staffHospital = ""
        ready = false

        allRecords.clear()

        showLoading(
            "Verifying staff and hospital..."
        )

        staffListener = db.collection("users")
            .document(user.uid)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { staff, error ->

                if (!valid(token)) {
                    return@addSnapshotListener
                }

                if (error != null) {

                    stopRecords(
                        "Unable to verify staff account."
                    )

                    return@addSnapshotListener
                }

                if (
                    staff == null ||
                    staff.metadata.isFromCache
                ) {
                    return@addSnapshotListener
                }

                val assigned =
                    staff.getString("hospital")
                        .orEmpty()
                        .trim()

                val activeStaff =
                    staff.exists() &&
                            staff.getString("role") == "STAFF" &&
                            staff.getString("status")
                                .equals("Active", true) &&
                            assigned.isNotBlank() &&
                            !assigned.equals("Not Assigned", true)

                if (!activeStaff) {

                    stopRecords(
                        "Active STAFF account and hospital " +
                                "assignment required."
                    )

                    return@addSnapshotListener
                }

                if (
                    staffHospital != assigned ||
                    recordsListener == null
                ) {

                    staffHospital = assigned

                    listenToRecords(
                        token,
                        assigned
                    )
                }
            }
    }

    private fun listenToRecords(
        token: Int,
        hospital: String
    ) {

        recordsListener?.remove()
        recordsListener = null

        ready = false
        allRecords.clear()

        showLoading(
            "Loading donation records for $hospital..."
        )

        recordsListener = db.collection("donationRecords")
            .whereEqualTo("hospital", hospital)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { snapshot, error ->

                if (
                    !valid(token) ||
                    staffHospital != hospital
                ) {
                    return@addSnapshotListener
                }

                if (error != null) {

                    showUnavailable(
                        "Unable to load donation records: " +
                                (error.localizedMessage
                                    ?: "Firebase error")
                    )

                    return@addSnapshotListener
                }

                if (
                    snapshot == null ||
                    snapshot.metadata.isFromCache
                ) {
                    return@addSnapshotListener
                }

                val loadedRecords =
                    snapshot.documents
                        .map(::toRecord)
                        .sortedWith(
                            compareByDescending<DonorRecord> {
                                it.createdAt
                            }.thenByDescending {
                                it.donationId
                            }
                        )

                allRecords.clear()
                allRecords.addAll(loadedRecords)

                ready = true

                render()
            }
    }

    private fun toRecord(
        document: DocumentSnapshot
    ): DonorRecord {

        return DonorRecord(
            documentId = document.id,

            donationId =
                document.getString("donationId")
                    ?: document.id,

            donorId =
                document.getString("donorId").orEmpty(),

            donorName =
                document.getString("donorName").orEmpty(),

            bloodGroup =
                document.getString("bloodGroup").orEmpty(),

            donationType =
                document.getString("donationType").orEmpty(),

            amountMl =
                document.getLong("amountMl")
                    ?: 0L,

            hospital =
                document.getString("hospital").orEmpty(),

            recordedDateTime =
                document.getString("recordedDateTime")
                    .orEmpty(),

            status =
                document.getString("status")
                    ?: "Completed",

            createdAt =
                document.getTimestamp("createdAt")
                    ?.toDate()
                    ?.time ?: 0L
        )
    }

    private fun render() {

        if (!ready) return

        val search = edtSearch.text
            ?.toString()
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        val filtered = if (search.isBlank()) {
            allRecords.toList()
        } else {
            allRecords.filter { record ->

                listOf(
                    record.donorName,
                    record.donorId,
                    record.bloodGroup,
                    record.donationId,
                    record.donationType
                ).any {
                    it.lowercase(Locale.ROOT)
                        .contains(search)
                }
            }
        }

        recordsContainer.removeAllViews()

        txtRecordSummary.text =
            "${filtered.size} of ${allRecords.size} " +
                    "records • $staffHospital"

        if (filtered.isEmpty()) {

            txtEmptyRecords.visibility = View.VISIBLE

            txtEmptyRecords.text =
                if (allRecords.isEmpty()) {
                    "No donation records for $staffHospital"
                } else {
                    "No records match your search"
                }

            return
        }

        txtEmptyRecords.visibility = View.GONE

        filtered.forEach { record ->
            addRecordCard(record)
        }
    }

    private fun addRecordCard(
        record: DonorRecord
    ) {

        val card = LayoutInflater.from(this)
            .inflate(
                R.layout.item_staff_donor_record,
                recordsContainer,
                false
            )

        card.findViewById<TextView>(
            R.id.txtBloodGroup
        ).text =
            record.bloodGroup.ifBlank { "—" }

        card.findViewById<TextView>(
            R.id.txtDonorName
        ).text =
            record.donorName.ifBlank {
                "Unknown Donor"
            }

        card.findViewById<TextView>(
            R.id.txtDonorId
        ).text =
            "Donor ID: " +
                    record.donorId.ifBlank { "—" }

        card.findViewById<TextView>(
            R.id.txtDonationInfo
        ).text =
            "${record.donationType.ifBlank { "Donation" }}" +
                    " • ${record.amountMl} mL"

        card.findViewById<TextView>(
            R.id.txtDateTime
        ).text =
            record.recordedDateTime.ifBlank {
                record.donationId
            }

        val statusText =
            card.findViewById<TextView>(
                R.id.txtStatus
            )

        statusText.text =
            "Status: ${record.status}"

        statusText.setTextColor(
            when (
                record.status.lowercase(Locale.ROOT)
            ) {
                "completed" ->
                    Color.rgb(21, 128, 61)

                "cancelled", "rejected", "failed" ->
                    Color.rgb(220, 38, 38)

                else ->
                    Color.rgb(185, 105, 17)
            }
        )

        card.setOnClickListener {
            showRecordDetails(record)
        }

        recordsContainer.addView(card)
    }

    private fun showRecordDetails(
        record: DonorRecord
    ) {

        StaffFeedbackDialogs.show(
            this,
            StaffFeedbackDialogs.Type.INFO,
            "Donation Record Details",
            "Donation ID: ${record.donationId}\n" +
                    "Donor: ${record.donorName}\n" +
                    "Donor ID: ${record.donorId}\n" +
                    "Blood group: ${record.bloodGroup}\n" +
                    "Donation type: ${record.donationType}\n" +
                    "Amount: ${record.amountMl} mL\n" +
                    "Date: ${record.recordedDateTime}\n" +
                    "Hospital: ${record.hospital}\n" +
                    "Status: ${record.status}",
            positiveText = "Close"
        )
    }

    private fun stopRecords(message: String) {

        recordsListener?.remove()
        recordsListener = null

        staffHospital = ""
        allRecords.clear()
        ready = false

        showUnavailable(message)
    }

    private fun showLoading(message: String) {

        recordsContainer.removeAllViews()

        txtEmptyRecords.visibility = View.VISIBLE
        txtEmptyRecords.text = message

        txtRecordSummary.text =
            "Syncing with Firebase..."
    }

    private fun showUnavailable(message: String) {

        recordsContainer.removeAllViews()

        txtEmptyRecords.visibility = View.VISIBLE
        txtEmptyRecords.text = message

        txtRecordSummary.text =
            "Donation records unavailable"
    }
}
