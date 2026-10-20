
package com.example.blooddononerapp

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.text.SimpleDateFormat
import java.util.Locale

class AdminDonationDetailsActivity : AppCompatActivity() {

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private lateinit var txtDonationId: TextView
    private lateinit var txtBloodGroup: TextView
    private lateinit var txtDonor: TextView
    private lateinit var txtDonationType: TextView
    private lateinit var txtAmount: TextView
    private lateinit var txtHospital: TextView
    private lateinit var txtDate: TextView
    private lateinit var txtStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_admin_donation_details
        )

        findViewById<TextView>(
            R.id.btnBack
        ).setOnClickListener {
            finish()
        }

        txtDonationId = findViewById(
            R.id.txtDonationId
        )

        txtBloodGroup = findViewById(
            R.id.txtBloodGroup
        )

        txtDonor = findViewById(
            R.id.txtDonor
        )

        txtDonationType = findViewById(
            R.id.txtDonationType
        )

        txtAmount = findViewById(
            R.id.txtAmount
        )

        txtHospital = findViewById(
            R.id.txtHospital
        )

        txtDate = findViewById(
            R.id.txtDate
        )

        txtStatus = findViewById(
            R.id.txtStatus
        )

        showLoading()

        val documentId = intent.getStringExtra(
            "DONATION_DOCUMENT_ID"
        ).orEmpty()

        if (documentId.isBlank()) {

            Toast.makeText(
                this,
                "Donation ID missing",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        loadDonation(documentId)
    }

    private fun showLoading() {

        txtDonationId.text = "Loading..."
        txtBloodGroup.text = "--"
        txtDonor.text = "Loading..."
        txtDonationType.text = "Loading..."
        txtAmount.text = "Loading..."
        txtHospital.text = "Loading..."
        txtDate.text = "Loading..."
        txtStatus.text = "Loading..."
    }

    private fun loadDonation(documentId: String) {

        db.collection("donationRecords")
            .document(documentId)
            .get(Source.SERVER)
            .addOnSuccessListener { document ->

                if (isFinishing || isDestroyed) {
                    return@addOnSuccessListener
                }

                if (!document.exists()) {

                    Toast.makeText(
                        this,
                        "Donation record not found",
                        Toast.LENGTH_LONG
                    ).show()

                    finish()
                    return@addOnSuccessListener
                }

                txtDonationId.text =
                    document.getString("donationId")
                        ?.takeIf { it.isNotBlank() }
                        ?: document.id

                txtBloodGroup.text =
                    document.getString("bloodGroup")
                        ?: "--"

                txtDonor.text =
                    document.getString("donorName")
                        ?: "Unknown Donor"

                txtDonationType.text =
                    document.getString("donationType")
                        ?: "Not available"

                val amount =
                    document.get("amountMl")
                        ?.toString()
                        ?.trim()
                        .orEmpty()

                txtAmount.text =
                    if (amount.isBlank()) {
                        "Not available"
                    } else if (
                        amount.contains("mL", ignoreCase = true)
                    ) {
                        amount
                    } else {
                        "$amount mL"
                    }

                txtHospital.text =
                    document.getString("hospital")
                        ?: "Not available"

                txtDate.text =
                    document.getString("recordedDateTime")
                        ?.takeIf { it.isNotBlank() }
                        ?: document.getTimestamp("createdAt")
                            ?.toDate()
                            ?.let {
                                SimpleDateFormat(
                                    "dd MMM yyyy, hh:mm a",
                                    Locale.getDefault()
                                ).format(it)
                            }
                                ?: "Not available"

                txtStatus.text =
                    document.getString("status")
                        ?: "Unknown"
            }
            .addOnFailureListener { error ->

                if (!isFinishing && !isDestroyed) {

                    txtDonationId.text = "Unavailable"

                    Toast.makeText(
                        this,
                        "Unable to load donation: ${error.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}
