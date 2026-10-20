
package com.example.blooddononerapp

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class AdminHospitalManagementActivity : AppCompatActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private var hospitalsListener: ListenerRegistration? = null
    private lateinit var listContainer: LinearLayout
    private lateinit var countText: TextView
    private lateinit var searchField: EditText
    private var hospitals: List<Hospital> = emptyList()

    private data class Hospital(
        val documentId: String,
        val hospitalId: String,
        val name: String,
        val district: String,
        val area: String,
        val status: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_hospital_management)

        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<MaterialButton>(R.id.btnAddHospital).setOnClickListener {
            startActivity(
                Intent(this, AdminHospitalDetailsActivity::class.java).apply {
                    putExtra("MODE", "ADD")
                }
            )
        }

        searchField = findViewById(R.id.edtSearchHospital)
        searchField.addTextChangedListener { displayHospitals() }

        // Remove sample cards and create a dynamic list.
        val sample1 = findViewById<MaterialCardView>(R.id.cardHospital1)
        val sample2 = findViewById<MaterialCardView>(R.id.cardHospital2)
        val sample3 = findViewById<MaterialCardView>(R.id.cardHospital3)

        val parent = sample1.parent as LinearLayout
        val insertAt = parent.indexOfChild(sample1)

        parent.removeView(sample1)
        parent.removeView(sample2)
        parent.removeView(sample3)

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        parent.addView(
            listContainer,
            insertAt,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        countText = findRegisteredCentersLabel(window.decorView)
            ?: error("Registered centers summary is missing")

        countText.text = "Loading centers..."
    }

    override fun onStart() {
        super.onStart()

        hospitalsListener = db.collection("hospitals")
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    countText.text = "Centers unavailable"
                    listContainer.removeAllViews()
                    showEmptyState("Unable to load hospitals. Check Firebase permissions.")

                    Toast.makeText(
                        this,
                        error.localizedMessage ?: "Hospital loading failed",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addSnapshotListener
                }

                hospitals = snapshot?.documents.orEmpty()
                    .mapNotNull { doc ->

                        val name = doc.getString("name")
                            ?: doc.getString("hospitalName")
                            ?: return@mapNotNull null

                        Hospital(
                            documentId = doc.id,
                            hospitalId = doc.getString("hospitalId") ?: doc.id,
                            name = name,
                            district = doc.getString("district").orEmpty(),
                            area = doc.getString("area").orEmpty(),
                            status = doc.getString("status")
                                ?: if (doc.getBoolean("acceptingDonors") == false)
                                    "Inactive"
                                else
                                    "Active"
                        )
                    }
                    .sortedBy { it.name.lowercase() }

                countText.text = "${snapshot?.size() ?: 0} Registered Centers"
                displayHospitals()
            }
    }

    override fun onStop() {
        hospitalsListener?.remove()
        hospitalsListener = null
        super.onStop()
    }

    private fun displayHospitals() {
        if (!::listContainer.isInitialized) return

        val query = searchField.text.toString().trim().lowercase()

        val matches = hospitals.filter { hospital ->
            query.isEmpty() || listOf(
                hospital.name,
                hospital.hospitalId,
                hospital.district,
                hospital.area,
                hospital.status
            ).any { it.lowercase().contains(query) }
        }

        listContainer.removeAllViews()

        if (matches.isEmpty()) {
            showEmptyState(
                if (query.isEmpty())
                    "No hospitals yet. Tap Add Hospital / Blood Bank."
                else
                    "No matching hospitals found."
            )
            return
        }

        matches.forEachIndexed { index, hospital ->
            listContainer.addView(
                buildHospitalCard(hospital, index == 0)
            )
        }
    }

    private fun showEmptyState(message: String) {
        listContainer.addView(
            TextView(this).apply {
                text = message
                textSize = 13f
                setTextColor(Color.parseColor("#777777"))
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(30), dp(12), dp(20))
            }
        )
    }

    private fun buildHospitalCard(
        hospital: Hospital,
        highlight: Boolean
    ): MaterialCardView {

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }

            radius = dp(17).toFloat()
            cardElevation = 0f

            setCardBackgroundColor(
                Color.parseColor(
                    if (highlight) "#FFF1F3" else "#FFFFFF"
                )
            )

            if (!highlight) {
                strokeColor = Color.parseColor("#EAEAEA")
                strokeWidth = dp(1)
            }

            isClickable = true
            isFocusable = true

            setOnClickListener {
                startActivity(
                    Intent(
                        this@AdminHospitalManagementActivity,
                        AdminHospitalDetailsActivity::class.java
                    ).apply {
                        putExtra("MODE", "VIEW")
                        putExtra("DOCUMENT_ID", hospital.documentId)
                    }
                )
            }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(17), dp(17), dp(17), dp(17))
        }

        val iconBox = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
            setBackgroundResource(R.drawable.bg_staff_blood_circle)
        }

        iconBox.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_staff_hospital)
                imageTintList = ColorStateList.valueOf(Color.WHITE)

                layoutParams = FrameLayout.LayoutParams(
                    dp(27),
                    dp(27),
                    Gravity.CENTER
                )
            }
        )

        row.addView(iconBox)

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dp(14)
            }
        }

        details.addView(
            TextView(this).apply {
                text = hospital.name
                textSize = 13f
                setTextColor(Color.parseColor("#222222"))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
        )

        details.addView(
            TextView(this).apply {
                text = "${hospital.hospitalId} • ${hospital.district}"
                textSize = 9f
                setTextColor(Color.parseColor("#777777"))
                setPadding(0, dp(4), 0, 0)
            }
        )

        details.addView(
            TextView(this).apply {
                text = hospital.status
                textSize = 9f

                setTextColor(
                    Color.parseColor(
                        if (hospital.status.equals("Active", true))
                            "#15803D"
                        else
                            "#B91C1C"
                    )
                )

                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(5), 0, 0)
            }
        )

        row.addView(details)

        row.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_staff_arrow_right)

                imageTintList = ColorStateList.valueOf(
                    Color.parseColor("#EF2637")
                )

                layoutParams = LinearLayout.LayoutParams(
                    dp(20),
                    dp(20)
                )
            }
        )

        card.addView(row)
        return card
    }

    private fun findRegisteredCentersLabel(view: View): TextView? {
        if (
            view is TextView &&
            view.text.toString().contains("Registered Centers")
        ) {
            return view
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findRegisteredCentersLabel(
                    view.getChildAt(i)
                )?.let { return it }
            }
        }

        return null
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
