
package com.example.blooddononerapp

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import java.util.Locale

class StaffEmergencyRequestsActivity : AppCompatActivity() {

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private lateinit var recycler: RecyclerView
    private lateinit var summary: TextView
    private lateinit var empty: TextView
    private lateinit var adapter: StaffEmergencyRequestAdapter

    private lateinit var filterAll: TextView
    private lateinit var filterPending: TextView
    private lateinit var filterVerified: TextView
    private lateinit var filterMatching: TextView
    private lateinit var filterFulfilled: TextView
    private lateinit var filterRejected: TextView

    private var requestListener: ListenerRegistration? = null
    private var staffHospital = ""
    private var currentFilter = "All"
    private var hasServerData = false
    private var refreshGeneration = 0

    private val allRequests =
        mutableListOf<StaffEmergencyRequest>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_staff_emergency_requests
        )

        recycler = findViewById(
            R.id.recyclerStaffEmergencyRequests
        )

        summary = findViewById(
            R.id.txtRequestSummary
        )

        empty = findViewById(
            R.id.txtEmptyRequests
        )

        filterAll = findViewById(R.id.filterAll)
        filterPending = findViewById(R.id.filterPending)
        filterVerified = findViewById(R.id.filterVerified)
        filterMatching = findViewById(R.id.filterMatching)
        filterFulfilled = findViewById(R.id.filterFulfilled)
        filterRejected = findViewById(R.id.filterRejected)

        adapter = StaffEmergencyRequestAdapter(
            mutableListOf()
        )

        recycler.layoutManager =
            LinearLayoutManager(this)

        recycler.adapter = adapter

        findViewById<MaterialCardView>(
            R.id.btnBack
        ).setOnClickListener {
            finish()
        }

        findViewById<MaterialCardView>(
            R.id.btnRefresh
        ).setOnClickListener {
            loadStaffProfile()
        }

        val filters = listOf(
            "All" to filterAll,
            "Pending" to filterPending,
            "Verified" to filterVerified,
            "Matching" to filterMatching,
            "Fulfilled" to filterFulfilled,
            "Rejected" to filterRejected
        )

        filters.forEach { (status, view) ->

            view.setOnClickListener {

                currentFilter = status

                renderFilterSelection()
                applyFilter()
            }
        }

        currentFilter =
            savedInstanceState?.getString("filter")
                ?: "All"

        renderFilterSelection()

        showLoading(
            "Checking staff account..."
        )
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        outState.putString(
            "filter",
            currentFilter
        )

        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        loadStaffProfile()
    }

    override fun onStop() {

        refreshGeneration++

        requestListener?.remove()
        requestListener = null

        hasServerData = false

        super.onStop()
    }

    private fun loadStaffProfile() {

        val user = auth.currentUser

        if (user == null) {

            showUnavailable(
                "Please sign in with your staff account"
            )

            return
        }

        val generation = ++refreshGeneration

        requestListener?.remove()
        requestListener = null

        hasServerData = false

        allRequests.clear()
        adapter.updateRequests(emptyList())

        showLoading(
            "Checking staff hospital..."
        )

        db.collection("users")
            .document(user.uid)
            .get(Source.SERVER)
            .addOnSuccessListener { doc ->

                if (!isCurrent(generation)) {
                    return@addOnSuccessListener
                }

                val activeStaff =
                    doc.exists() &&
                            doc.getString("role") == "STAFF" &&
                            doc.getString("status") == "Active"

                val assignedHospital =
                    doc.getString("hospital")
                        ?.trim()
                        .orEmpty()

                if (!activeStaff) {

                    showUnavailable(
                        "An active STAFF account is required"
                    )

                    return@addOnSuccessListener
                }

                if (
                    assignedHospital.isBlank() ||
                    assignedHospital.equals(
                        "Not Assigned",
                        true
                    )
                ) {

                    showUnavailable(
                        "No hospital is assigned to this staff account"
                    )

                    return@addOnSuccessListener
                }

                staffHospital = assignedHospital

                listenToRequests(
                    assignedHospital,
                    generation
                )
            }
            .addOnFailureListener { error ->

                if (isCurrent(generation)) {

                    showUnavailable(
                        "Staff verification failed: " +
                                error.localizedMessage
                    )
                }
            }
    }

    private fun listenToRequests(
        hospital: String,
        generation: Int
    ) {

        showLoading(
            "Loading requests for $hospital..."
        )

        requestListener = db
            .collection("emergencyRequests")
            .whereEqualTo(
                "hospital",
                hospital
            )
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { snapshot, error ->

                if (
                    !isCurrent(generation) ||
                    staffHospital != hospital
                ) {
                    return@addSnapshotListener
                }

                if (error != null) {

                    hasServerData = false

                    allRequests.clear()
                    adapter.updateRequests(emptyList())

                    showUnavailable(
                        "Could not load requests: " +
                                error.localizedMessage
                    )

                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    return@addSnapshotListener
                }

                if (snapshot.metadata.isFromCache) {

                    if (!hasServerData) {
                        showLoading(
                            "Syncing with Firebase..."
                        )
                    }

                    return@addSnapshotListener
                }

                val records = snapshot.documents
                    .sortedWith(
                        compareByDescending<DocumentSnapshot> {
                            it.getTimestamp("createdAt")
                                ?.seconds ?: 0L
                        }.thenByDescending {
                            it.id
                        }
                    )
                    .map(::toRequest)

                allRequests.clear()
                allRequests.addAll(records)

                hasServerData = true

                applyFilter()
            }
    }

    private fun toRequest(
        doc: DocumentSnapshot
    ): StaffEmergencyRequest {

        val units = when (
            val value = doc.get("units")
        ) {

            is Number -> {
                "${value.toLong()} Units"
            }

            is String -> {

                val clean = value.trim()

                if (clean.isBlank()) {
                    "—"
                } else if (
                    clean.contains(
                        "unit",
                        ignoreCase = true
                    )
                ) {
                    clean
                } else {
                    "$clean Units"
                }
            }

            else -> "—"
        }

        return StaffEmergencyRequest(

            documentId = doc.id,

            requestId =
                doc.getString("requestId") ?: doc.id,

            userId =
                doc.getString("userId").orEmpty(),

            userEmail =
                doc.getString("userEmail")
                    ?: "Not available",

            bloodGroup =
                doc.getString("bloodGroup") ?: "—",

            units = units,

            province =
                doc.getString("province").orEmpty(),

            district =
                doc.getString("district").orEmpty(),

            area =
                doc.getString("area").orEmpty(),

            hospital =
                doc.getString("hospital").orEmpty(),

            requiredDate =
                doc.getString("requiredDate")
                    ?: "Not available",

            requiredBefore =
                doc.getString("requiredBefore")
                    ?: "Not available",

            primaryContact =
                doc.getString("primaryContact")
                    ?: "Not available",

            secondaryContact =
                doc.getString("secondaryContact")
                    .orEmpty(),

            urgency =
                doc.getString("urgency")
                    ?: "Not available",

            additionalInfo =
                doc.getString("additionalInfo")
                    .orEmpty(),

            status =
                doc.getString("status") ?: "Pending"
        )
    }

    private fun applyFilter() {

        if (!hasServerData) return

        val filtered = if (currentFilter == "All") {

            allRequests.toList()

        } else {

            allRequests.filter {
                it.status.equals(
                    currentFilter,
                    ignoreCase = true
                )
            }
        }

        adapter.updateRequests(filtered)

        recycler.visibility =
            if (filtered.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }

        empty.visibility =
            if (filtered.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }

        empty.text =
            if (currentFilter == "All") {

                "No emergency requests for $staffHospital"

            } else {

                "No $currentFilter requests for $staffHospital"
            }

        val pending = allRequests.count {

            it.status.lowercase(Locale.ROOT) in setOf(
                "pending",
                "verified",
                "matching"
            )
        }

        summary.text =
            "${allRequests.size} requests • " +
                    "$pending active • $staffHospital"
    }

    private fun renderFilterSelection() {

        val filters = listOf(
            "All" to filterAll,
            "Pending" to filterPending,
            "Verified" to filterVerified,
            "Matching" to filterMatching,
            "Fulfilled" to filterFulfilled,
            "Rejected" to filterRejected
        )

        filters.forEach { (name, view) ->

            if (name == currentFilter) {

                view.setBackgroundResource(
                    R.drawable.bg_staff_filter_selected
                )

                view.setTextColor(
                    Color.WHITE
                )

            } else {

                view.setBackgroundResource(
                    R.drawable.bg_staff_filter
                )

                view.setTextColor(
                    Color.parseColor("#666666")
                )
            }
        }
    }

    private fun showLoading(
        message: String
    ) {

        summary.text = message

        empty.text = "Loading..."
        empty.visibility = View.VISIBLE

        recycler.visibility = View.GONE
    }

    private fun showUnavailable(
        message: String
    ) {

        summary.text = "Requests unavailable"

        empty.text = message
        empty.visibility = View.VISIBLE

        recycler.visibility = View.GONE
    }

    private fun isCurrent(
        generation: Int
    ): Boolean {

        return generation == refreshGeneration &&
                !isFinishing &&
                !isDestroyed
    }
}
