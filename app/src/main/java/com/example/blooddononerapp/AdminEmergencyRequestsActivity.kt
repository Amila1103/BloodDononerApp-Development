
package com.example.blooddononerapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class AdminEmergencyRequestsActivity : AppCompatActivity() {

    private val firestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private var requestListener: ListenerRegistration? = null

    private val allRequests =
        mutableListOf<AdminEmergencyRequest>()

    private lateinit var adapter: AdminEmergencyRequestAdapter

    private lateinit var filterAll: TextView
    private lateinit var filterPending: TextView
    private lateinit var filterMatching: TextView
    private lateinit var filterFulfilled: TextView

    private var currentFilter = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_admin_emergency_requests)

        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        filterAll = findViewById(R.id.filterAll)
        filterPending = findViewById(R.id.filterPending)
        filterMatching = findViewById(R.id.filterMatching)
        filterFulfilled = findViewById(R.id.filterFulfilled)

        adapter = AdminEmergencyRequestAdapter(
            mutableListOf()
        ) { request ->

            val intent = Intent(
                this,
                AdminRequestDetailsActivity::class.java
            )

            intent.putExtra(
                "REQUEST_DOCUMENT_ID",
                request.documentId
            )

            startActivity(intent)
        }

        findViewById<RecyclerView>(
            R.id.recyclerEmergencyRequests
        ).apply {
            layoutManager = LinearLayoutManager(
                this@AdminEmergencyRequestsActivity
            )

            adapter = this@AdminEmergencyRequestsActivity.adapter
        }

        filterAll.setOnClickListener {
            selectFilter("All")
        }

        filterPending.setOnClickListener {
            selectFilter("Pending")
        }

        filterMatching.setOnClickListener {
            selectFilter("Matching")
        }

        filterFulfilled.setOnClickListener {
            selectFilter("Fulfilled")
        }

        updateFilterUI()
    }

    override fun onStart() {
        super.onStart()

        requestListener?.remove()

        requestListener = firestore
            .collection("emergencyRequests")
            .orderBy(
                "createdAt",
                Query.Direction.DESCENDING
            )
            .addSnapshotListener { snapshot, error ->

                if (isFinishing || isDestroyed) {
                    return@addSnapshotListener
                }

                if (error != null) {
                    allRequests.clear()
                    applyFilter()

                    Toast.makeText(
                        this,
                        "Failed to load requests: ${error.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    allRequests.clear()

                    for (document in snapshot.documents) {
                        allRequests.add(
                            readRequest(document)
                        )
                    }

                    applyFilter()
                }
            }
    }

    override fun onStop() {
        requestListener?.remove()
        requestListener = null
        super.onStop()
    }

    private fun readRequest(
        document: DocumentSnapshot
    ): AdminEmergencyRequest {

        return AdminEmergencyRequest(
            documentId = document.id,
            requestId = document.getString("requestId") ?: document.id,
            userId = document.getString("userId") ?: "",
            userEmail = document.getString("userEmail") ?: "",
            bloodGroup = document.getString("bloodGroup") ?: "",
            units = document.get("units")?.toString() ?: "",
            province = document.getString("province") ?: "",
            district = document.getString("district") ?: "",
            area = document.getString("area") ?: "",
            hospital = document.getString("hospital") ?: "",
            requiredDate = document.getString("requiredDate") ?: "",
            requiredBefore = document.getString("requiredBefore") ?: "",
            primaryContact = document.getString("primaryContact") ?: "",
            secondaryContact = document.getString("secondaryContact") ?: "",
            urgency = document.getString("urgency") ?: "",
            additionalInfo = document.getString("additionalInfo") ?: "",
            status = document.getString("status") ?: "Pending"
        )
    }

    private fun selectFilter(filter: String) {
        currentFilter = filter
        updateFilterUI()
        applyFilter()
    }

    private fun applyFilter() {

        val filteredRequests: List<AdminEmergencyRequest> =
            if (currentFilter == "All") {

                allRequests.toList()

            } else {

                allRequests.filter { request ->
                    request.status.equals(
                        currentFilter,
                        ignoreCase = true
                    )
                }
            }

        adapter.updateRequests(filteredRequests)
    }

    private fun updateFilterUI() {

        setFilterStyle(
            filterAll,
            currentFilter == "All"
        )

        setFilterStyle(
            filterPending,
            currentFilter == "Pending"
        )

        setFilterStyle(
            filterMatching,
            currentFilter == "Matching"
        )

        setFilterStyle(
            filterFulfilled,
            currentFilter == "Fulfilled"
        )
    }

    private fun setFilterStyle(
        view: TextView,
        selected: Boolean
    ) {

        view.setBackgroundResource(
            if (selected) {
                R.drawable.bg_staff_filter_selected
            } else {
                R.drawable.bg_staff_filter
            }
        )

        view.setTextColor(
            if (selected) {
                Color.WHITE
            } else {
                Color.parseColor("#666666")
            }
        )
    }
}
