
package com.example.blooddononerapp

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Locale

class AdminDonationManagementActivity : AppCompatActivity() {

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private var donationListener: ListenerRegistration? = null

    private lateinit var edtSearch: EditText

    private lateinit var recordsContainer: LinearLayout

    private var donationDocuments: List<DocumentSnapshot> =
        emptyList()

    private var loadError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_admin_donation_management
        )

        findViewById<TextView>(
            R.id.btnBack
        ).setOnClickListener {
            finish()
        }

        edtSearch = findViewById(
            R.id.edtSearchDonation
        )

        createRecordsContainer()

        edtSearch.addTextChangedListener {
            renderDonations()
        }

        showMessage("Loading donation records...")
    }

    private fun createRecordsContainer() {

        val firstCard =
            findViewById<MaterialCardView>(
                R.id.cardDonation1
            )

        val parent = firstCard.parent as LinearLayout

        val insertionIndex = parent.indexOfChild(firstCard)

        // Create separate container for Firebase records.
        recordsContainer = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL
        }

        parent.addView(
            recordsContainer,
            insertionIndex,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        )

        // Remove only the three hardcoded sample cards.
        val sampleIds = listOf(
            R.id.cardDonation1,
            R.id.cardDonation2,
            R.id.cardDonation3
        )

        for (id in sampleIds) {

            val card = findViewById<View>(id)

            if (card != null) {
                parent.removeView(card)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        listenToDonations()
    }

    override fun onStop() {
        donationListener?.remove()
        donationListener = null
        super.onStop()
    }

    private fun listenToDonations() {

        donationListener?.remove()

        loadError = null

        donationListener = db
            .collection("donationRecords")
            .addSnapshotListener { snapshot, error ->

                if (isFinishing || isDestroyed) {
                    return@addSnapshotListener
                }

                if (error != null) {

                    donationDocuments = emptyList()
                    loadError = error.localizedMessage

                    renderDonations()

                    Toast.makeText(
                        this,
                        "Failed to load donation records",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addSnapshotListener
                }

                loadError = null

                donationDocuments = snapshot
                    ?.documents
                    ?.sortedByDescending { document ->

                        document.getTimestamp("createdAt")
                            ?.seconds ?: 0L
                    }
                    ?: emptyList()

                renderDonations()
            }
    }

    private fun renderDonations() {

        if (!::recordsContainer.isInitialized) {
            return
        }

        // Clear only the donation record views.
        // Header and Search Bar remain unchanged.
        recordsContainer.removeAllViews()

        if (loadError != null) {

            showMessage(
                "Unable to load donations.\n$loadError"
            )

            return
        }

        val keyword = edtSearch.text
            ?.toString()
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        val filtered = donationDocuments.filter { doc ->

            val searchable = listOf(
                doc.id,
                doc.getString("donationId").orEmpty(),
                doc.getString("donorName").orEmpty(),
                doc.getString("donorId").orEmpty(),
                doc.getString("userEmail").orEmpty(),
                doc.getString("bloodGroup").orEmpty(),
                doc.getString("hospital").orEmpty(),
                doc.getString("status").orEmpty()
            ).joinToString(" ")
                .lowercase(Locale.ROOT)

            searchable.contains(keyword)
        }

        if (filtered.isEmpty()) {

            showMessage(
                if (keyword.isBlank()) {
                    "No donation records found"
                } else {
                    "No matching donations"
                }
            )

            return
        }

        for (document in filtered) {
            addDonationCard(document)
        }
    }

    private fun addDonationCard(
        document: DocumentSnapshot
    ) {

        val donationId = document.getString("donationId")
            ?.takeIf { it.isNotBlank() }
            ?: document.id

        val donorName = document.getString("donorName")
            ?.takeIf { it.isNotBlank() }
            ?: "Unknown Donor"

        val bloodGroup = document.getString("bloodGroup")
            ?.takeIf { it.isNotBlank() }
            ?: "--"

        val hospitalName = document.getString("hospital")
            ?.takeIf { it.isNotBlank() }
            ?: "Hospital not available"

        val status = document.getString("status")
            ?: "Unknown"

        val date = document.getString("recordedDateTime")
            ?.takeIf { it.isNotBlank() }
            ?: document.getTimestamp("createdAt")
                ?.toDate()
                ?.let {
                    SimpleDateFormat(
                        "dd MMM yyyy",
                        Locale.getDefault()
                    ).format(it)
                }
            ?: "Date not available"

        val card = MaterialCardView(this).apply {

            radius = dp(17).toFloat()
            cardElevation = 0f

            strokeWidth = dp(1)
            strokeColor = Color.parseColor("#EAEAEA")

            setCardBackgroundColor(
                Color.WHITE
            )

            isClickable = true
            isFocusable = true

            setOnClickListener {

                val intent = Intent(
                    this@AdminDonationManagementActivity,
                    AdminDonationDetailsActivity::class.java
                )

                intent.putExtra(
                    "DONATION_DOCUMENT_ID",
                    document.id
                )

                startActivity(intent)
            }
        }

        val content = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(17),
                dp(15),
                dp(17),
                dp(15)
            )
        }

        val title = createText(
            "$donationId • $donorName",
            14f,
            "#222222",
            true
        )

        val hospital = createText(
            hospitalName,
            12f,
            "#777777",
            false
        )

        val details = createText(
            "$bloodGroup • $date • $status",
            11f,
            if (status.equals("Completed", true)) {
                "#15803D"
            } else {
                "#EF2637"
            },
            true
        )

        content.addView(title)

        content.addView(
            hospital,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(7)
            }
        )

        content.addView(
            details,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(7)
            }
        )

        card.addView(content)

        recordsContainer.addView(
            card,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        )
    }

    private fun showMessage(message: String) {

        recordsContainer.removeAllViews()

        val textView = createText(
            message,
            13f,
            "#777777",
            false
        ).apply {

            gravity = android.view.Gravity.CENTER

            setPadding(
                dp(12),
                dp(28),
                dp(12),
                dp(28)
            )
        }

        recordsContainer.addView(
            textView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun createText(
        value: String,
        sizeSp: Float,
        color: String,
        bold: Boolean
    ): TextView {

        return TextView(this).apply {

            text = value

            setTextColor(
                Color.parseColor(color)
            )

            setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                sizeSp
            )

            if (bold) {
                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }
        }
    }

    private fun dp(value: Int): Int {

        return (
                value * resources.displayMetrics.density
                ).toInt()
    }
}
