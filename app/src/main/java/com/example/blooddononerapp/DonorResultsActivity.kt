
package com.example.blooddononerapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import java.util.Locale

class DonorResultsActivity : AppCompatActivity() {

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val firestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: DonorAdapter
    private lateinit var resultsCount: TextView
    private lateinit var emptyContainer: LinearLayout
    private lateinit var sortText: TextView

    private val adapterItems = mutableListOf<Donor>()
    private val matches = mutableListOf<Donor>()

    private var donorListener: ListenerRegistration? = null

    private var active = false
    private var generation = 0

    private var bloodGroup = ""
    private var selectedLocation = ""
    private var searchArea = ""
    private var searchDistrict = ""
    private var availableOnly = true
    private var sortMode = "Available Now"

    private val validBloodGroups = setOf(
        "A+", "A-", "B+", "B-",
        "AB+", "AB-", "O+", "O-"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_donor_results
        )

        bloodGroup = intent.getStringExtra("bloodGroup")
            .orEmpty()
            .trim()
            .uppercase(Locale.ROOT)

        selectedLocation = intent.getStringExtra("location")
            .orEmpty()
            .trim()

        availableOnly = intent.getBooleanExtra(
            "availableOnly",
            true
        )

        recycler = findViewById(R.id.recyclerDonors)
        resultsCount = findViewById(R.id.txtResultsCount)
        emptyContainer = findViewById(R.id.noResultsContainer)
        sortText = findViewById(R.id.txtSort)

        recycler.layoutManager =
            LinearLayoutManager(this)

        adapter = DonorAdapter(
            adapterItems,
            onDonorClick = { donor ->
                openDetails(donor)
            },
            onRequestClick = { donor ->
                openDetails(donor)
            }
        )

        recycler.adapter = adapter

        findViewById<Chip>(
            R.id.chipBloodGroup
        ).apply {
            text = bloodGroup.ifBlank {
                "Blood Group"
            }

            setOnCloseIconClickListener {
                openSearch()
            }
        }

        findViewById<Chip>(
            R.id.chipDistance
        ).apply {
            text = selectedLocation.ifBlank {
                "Area / District"
            }

            setOnCloseIconClickListener {
                openSearch()
            }
        }

        findViewById<MaterialButton>(
            R.id.btnFilter
        ).setOnClickListener {
            openSearch()
        }

        findViewById<MaterialButton>(
            R.id.btnChangeFilters
        ).setOnClickListener {
            openSearch()
        }

        findViewById<TextView>(
            R.id.btnBack
        ).setOnClickListener {
            finish()
        }

        sortText.setOnClickListener {
            showSortMenu(it)
        }

        setupNavigation()

        showLoading(
            "Preparing donor search..."
        )
    }

    override fun onStart() {
        super.onStart()

        active = true
        startSearch()
    }

    override fun onStop() {
        active = false
        generation++

        donorListener?.remove()
        donorListener = null

        super.onStop()
    }

    private fun isCurrent(token: Int): Boolean {
        return active &&
                token == generation &&
                !isFinishing &&
                !isDestroyed
    }

    // ======================================
    // VERIFY REQUESTER USER
    // ======================================

    private fun startSearch() {

        val currentUid = auth.currentUser?.uid ?: run {

            showError(
                "Please sign in to search for donors."
            )
            return
        }

        if (bloodGroup !in validBloodGroups) {

            showError(
                "Please select a valid blood group."
            )
            return
        }

        val token = ++generation

        donorListener?.remove()
        donorListener = null

        matches.clear()
        adapter.updateDonors(emptyList())

        showLoading(
            "Verifying your USER account..."
        )

        firestore.collection("users")
            .document(currentUid)
            .get(Source.SERVER)
            .addOnSuccessListener { profile ->

                if (!isCurrent(token)) {
                    return@addOnSuccessListener
                }

                if (
                    !profile.exists() ||
                    profile.getString("role") != "USER" ||
                    profile.getString("status") != "Active"
                ) {

                    showError(
                        "An active USER account is required."
                    )
                    return@addOnSuccessListener
                }

                if (
                    selectedLocation.equals(
                        "Current Location",
                        true
                    )
                ) {

                    // Uses the saved profile location.
                    // This is NOT live GPS.

                    searchArea = profile.getString("area")
                        .orEmpty()
                        .trim()

                    searchDistrict =
                        profile.getString("district")
                            .orEmpty()
                            .trim()

                } else {

                    searchArea = selectedLocation
                    searchDistrict = selectedLocation
                }

                if (
                    searchArea.isBlank() &&
                    searchDistrict.isBlank()
                ) {

                    showError(
                        "Please select an area or district."
                    )
                    return@addOnSuccessListener
                }

                listenToDonors(
                    currentUid,
                    token
                )
            }
            .addOnFailureListener { error ->

                if (isCurrent(token)) {

                    showError(
                        "Cannot verify account: " +
                                error.localizedMessage
                    )
                }
            }
    }

    // ======================================
    // FIREBASE DONOR SEARCH
    // ======================================

    private fun listenToDonors(
        currentUid: String,
        token: Int
    ) {

        showLoading(
            "Searching for $bloodGroup donors..."
        )

        donorListener = firestore.collection("users")
            .whereEqualTo(
                "bloodGroup",
                bloodGroup
            )
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { snapshot, error ->

                if (!isCurrent(token)) {
                    return@addSnapshotListener
                }

                if (error != null) {

                    matches.clear()
                    adapter.updateDonors(emptyList())

                    showError(
                        "Firebase search failed: " +
                                error.localizedMessage
                    )
                    return@addSnapshotListener
                }

                if (snapshot == null) {

                    showError(
                        "No response from Firebase."
                    )
                    return@addSnapshotListener
                }

                if (
                    snapshot.metadata.isFromCache ||
                    snapshot.metadata.hasPendingWrites()
                ) {

                    showLoading(
                        "Waiting for verified Firebase results..."
                    )
                    return@addSnapshotListener
                }

                matches.clear()

                for (doc in snapshot.documents) {

                    // Do not show the logged-in
                    // user as their own donor.

                    if (doc.id == currentUid) {
                        continue
                    }

                    if (
                        doc.getString("role") != "USER"
                    ) {
                        continue
                    }

                    if (
                        doc.getString("status") != "Active"
                    ) {
                        continue
                    }

                    if (
                        doc.getBoolean("profileCompleted")
                        != true
                    ) {
                        continue
                    }

                    // FIX 01:
                    // Verify that DOB is valid and
                    // that the donor is at least 18.

                    val dateOfBirth =
                        doc.getString("dateOfBirth")

                    if (
                        !DonorEligibility.isAdult(
                            dateOfBirth
                        )
                    ) {
                        continue
                    }

                    // FIX 02:
                    // Contact consent must be
                    // explicitly enabled by donor.

                    val canContact = doc.getBoolean(
                        "notificationSettings.donorPrivacy.allowRequesterContact"
                    ) == true

                    if (!canContact) {
                        continue
                    }

                    val available = doc.getBoolean(
                        "availableToDonate"
                    ) == true

                    if (
                        availableOnly &&
                        !available
                    ) {
                        continue
                    }

                    // Respect location privacy.

                    val approximate = doc.getBoolean(
                        "shareApproximateLocation"
                    ) == true

                    val shareLocation = doc.getBoolean(
                        "notificationSettings.donorPrivacy.shareLocation"
                    ) ?: approximate

                    if (!shareLocation) {
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

                    if (
                        !locationMatches(
                            area,
                            district
                        )
                    ) {
                        continue
                    }

                    // Only expose permitted fields
                    // through the donor list.

                    matches.add(
                        Donor(
                            uid = doc.id,

                            fullName =
                                doc.getString("fullName")
                                    .orEmpty(),

                            bloodGroup = bloodGroup,

                            area =
                                if (approximate) {
                                    area
                                } else {
                                    ""
                                },

                            district = district,

                            status = "Active",

                            availableToDonate =
                                available,

                            profileCompleted = true,

                            shareApproximateLocation =
                                approximate
                        )
                    )
                }

                renderResults()
            }
    }

    // ======================================
    // MATCH LOCATION
    // ======================================

    private fun locationMatches(
        area: String,
        district: String
    ): Boolean {

        fun same(
            first: String,
            second: String
        ): Boolean {

            return first.isNotBlank() &&
                    second.isNotBlank() &&
                    first.equals(
                        second,
                        ignoreCase = true
                    )
        }

        return same(area, searchArea) ||
                same(district, searchArea) ||
                same(area, searchDistrict) ||
                same(district, searchDistrict)
    }

    // ======================================
    // SORT AND DISPLAY RESULTS
    // ======================================

    private fun renderResults() {

        val sorted = when (sortMode) {

            "Name A-Z" -> {
                matches.sortedBy {
                    it.fullName.lowercase(
                        Locale.ROOT
                    )
                }
            }

            "Area A-Z" -> {
                matches.sortedWith(
                    compareBy<Donor> {
                        it.district.lowercase(
                            Locale.ROOT
                        )
                    }.thenBy {
                        it.fullName.lowercase(
                            Locale.ROOT
                        )
                    }
                )
            }

            else -> {
                matches.sortedWith(
                    compareByDescending<Donor> {
                        it.availableToDonate
                    }.thenBy {
                        it.fullName.lowercase(
                            Locale.ROOT
                        )
                    }
                )
            }
        }

        adapter.updateDonors(sorted)

        val count = sorted.size

        resultsCount.text =
            "$count consenting adult $bloodGroup donor(s) found"

        recycler.visibility =
            if (count > 0) {
                View.VISIBLE
            } else {
                View.GONE
            }

        emptyContainer.visibility =
            if (count == 0) {
                View.VISIBLE
            } else {
                View.GONE
            }

        if (count == 0) {

            updateEmptyText(
                "No eligible donors found",
                "Check blood group and area. " +
                        "Donors must be 18+, complete " +
                        "their profiles, enable Requester " +
                        "Contact and Location sharing, " +
                        "and satisfy the availability filter."
            )
        }
    }

    private fun showSortMenu(anchor: View) {

        val popup = PopupMenu(
            this,
            anchor
        )

        listOf(
            "Available Now",
            "Name A-Z",
            "Area A-Z"
        ).forEach {
            popup.menu.add(it)
        }

        popup.setOnMenuItemClickListener {

            sortMode = it.title.toString()

            sortText.text =
                "Sort: $sortMode ▾"

            renderResults()

            true
        }

        popup.show()
    }

    // ======================================
    // SCREEN STATES
    // ======================================

    private fun showLoading(message: String) {

        resultsCount.text = message

        recycler.visibility = View.GONE
        emptyContainer.visibility = View.VISIBLE

        updateEmptyText(
            "Searching donors",
            "Checking live Firestore profiles..."
        )
    }

    private fun showError(message: String) {

        resultsCount.text =
            "Search unavailable"

        recycler.visibility = View.GONE
        emptyContainer.visibility = View.VISIBLE

        updateEmptyText(
            "Unable to search donors",
            message
        )
    }

    private fun updateEmptyText(
        title: String,
        body: String
    ) {

        (emptyContainer.getChildAt(0) as? TextView)
            ?.text = title

        (emptyContainer.getChildAt(1) as? TextView)
            ?.text = body
    }

    // ======================================
    // DONOR DETAILS
    // ======================================

    private fun openDetails(donor: Donor) {

        startActivity(
            Intent(
                this,
                DonorDetailsActivity::class.java
            ).apply {

                putExtra(
                    "donorUid",
                    donor.uid
                )

                putExtra(
                    "donorName",
                    donor.fullName
                )

                putExtra(
                    "bloodGroup",
                    donor.bloodGroup
                )

                putExtra(
                    "location",
                    listOf(
                        donor.area,
                        donor.district
                    )
                        .filter {
                            it.isNotBlank()
                        }
                        .distinct()
                        .joinToString(", ")
                )

                putExtra(
                    "eligibility",
                    "Hospital screening required"
                )
            }
        )
    }

    private fun openSearch() {

        startActivity(
            Intent(
                this,
                FindDonorActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )

        finish()
    }

    // ======================================
    // BOTTOM NAVIGATION
    // ======================================

    private fun setupNavigation() {

        findViewById<LinearLayout>(
            R.id.navHome
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    HomeActivity::class.java
                ).apply {

                    flags =
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )

            finish()
        }

        findViewById<LinearLayout>(
            R.id.navSearch
        ).setOnClickListener {

            openSearch()
        }

        findViewById<LinearLayout>(
            R.id.navRequests
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    MyRequestsActivity::class.java
                )
            )
        }

        findViewById<LinearLayout>(
            R.id.navNotifications
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    NotificationsActivity::class.java
                )
            )
        }

        findViewById<LinearLayout>(
            R.id.navProfile
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    ProfileActivity::class.java
                )
            )
        }
    }
}
