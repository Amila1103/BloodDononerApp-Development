package com.example.blooddononerapp

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore

class AdminRequestDetailsActivity : AppCompatActivity() {

    private lateinit var firestore:
            FirebaseFirestore

    private lateinit var txtRequestId:
            TextView

    private lateinit var txtBloodGroup:
            TextView

    private lateinit var txtUnits:
            TextView

    private lateinit var txtPatient:
            TextView

    private lateinit var txtHospital:
            TextView

    private lateinit var txtUrgency:
            TextView

    private lateinit var txtStatus:
            TextView

    private lateinit var txtLocation:
            TextView

    private lateinit var txtRequiredDate:
            TextView

    private lateinit var txtRequiredBefore:
            TextView

    private lateinit var txtPrimaryContact:
            TextView

    private lateinit var txtSecondaryContact:
            TextView

    private lateinit var txtAdditionalInfo:
            TextView

    private lateinit var btnStartMatching:
            MaterialButton

    private lateinit var btnMarkFulfilled:
            MaterialButton


    private var requestDocumentId:
            String = ""

    private var currentStatus:
            String = "Pending"


    // =========================================================
    // ON CREATE
    // =========================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_admin_request_details
        )


        // =====================================================
        // FIREBASE
        // =====================================================

        firestore =
            FirebaseFirestore.getInstance()


        // =====================================================
        // FIND VIEWS
        // =====================================================

        val btnBack =
            findViewById<TextView>(
                R.id.btnBack
            )

        txtRequestId =
            findViewById(
                R.id.txtRequestId
            )

        txtBloodGroup =
            findViewById(
                R.id.txtBloodGroup
            )

        txtUnits =
            findViewById(
                R.id.txtUnits
            )

        txtPatient =
            findViewById(
                R.id.txtPatient
            )

        txtHospital =
            findViewById(
                R.id.txtHospital
            )

        txtUrgency =
            findViewById(
                R.id.txtUrgency
            )

        txtStatus =
            findViewById(
                R.id.txtStatus
            )

        txtLocation =
            findViewById(
                R.id.txtLocation
            )

        txtRequiredDate =
            findViewById(
                R.id.txtRequiredDate
            )

        txtRequiredBefore =
            findViewById(
                R.id.txtRequiredBefore
            )

        txtPrimaryContact =
            findViewById(
                R.id.txtPrimaryContact
            )

        txtSecondaryContact =
            findViewById(
                R.id.txtSecondaryContact
            )

        txtAdditionalInfo =
            findViewById(
                R.id.txtAdditionalInfo
            )

        btnStartMatching =
            findViewById(
                R.id.btnStartMatching
            )

        btnMarkFulfilled =
            findViewById(
                R.id.btnMarkFulfilled
            )


        // =====================================================
        // BACK
        // =====================================================

        btnBack.setOnClickListener {

            finish()
        }


        // =====================================================
        // GET FIRESTORE DOCUMENT ID
        // =====================================================

        requestDocumentId =
            intent.getStringExtra(
                "REQUEST_DOCUMENT_ID"
            ) ?: ""


        if (requestDocumentId.isBlank()) {

            Toast.makeText(
                this,
                "Request ID not found",
                Toast.LENGTH_LONG
            ).show()

            finish()

            return
        }


        // =====================================================
        // START MATCHING BUTTON
        // =====================================================

        btnStartMatching.setOnClickListener {

            showStatusConfirmation(
                newStatus = "Matching",
                title = "Start Matching",
                message =
                    "Do you want to start donor matching for this emergency request?"
            )
        }


        // =====================================================
        // MARK FULFILLED BUTTON
        // =====================================================

        btnMarkFulfilled.setOnClickListener {

            showStatusConfirmation(
                newStatus = "Fulfilled",
                title = "Mark as Fulfilled",
                message =
                    "Are you sure this emergency request has been fulfilled?"
            )
        }


        // =====================================================
        // LOAD REQUEST
        // =====================================================

        loadRequestDetails()
    }


    // =========================================================
    // LOAD FIRESTORE REQUEST
    // =========================================================

    private fun loadRequestDetails() {

        firestore
            .collection(
                "emergencyRequests"
            )
            .document(
                requestDocumentId
            )
            .get()

            .addOnSuccessListener { document ->

                if (!document.exists()) {

                    Toast.makeText(
                        this,
                        "Request not found",
                        Toast.LENGTH_LONG
                    ).show()

                    finish()

                    return@addOnSuccessListener
                }


                // =================================================
                // BASIC DETAILS
                // =================================================

                val requestId =
                    document.getString(
                        "requestId"
                    ) ?: document.id

                val bloodGroup =
                    document.getString(
                        "bloodGroup"
                    ) ?: "--"

                val units =
                    document.getString(
                        "units"
                    ) ?: "--"

                val userEmail =
                    document.getString(
                        "userEmail"
                    ) ?: "Not available"

                val hospital =
                    document.getString(
                        "hospital"
                    ) ?: "Not available"

                val urgency =
                    document.getString(
                        "urgency"
                    ) ?: "Not available"

                currentStatus =
                    document.getString(
                        "status"
                    ) ?: "Pending"


                // =================================================
                // LOCATION
                // =================================================

                val province =
                    document.getString(
                        "province"
                    ) ?: ""

                val district =
                    document.getString(
                        "district"
                    ) ?: ""

                val area =
                    document.getString(
                        "area"
                    ) ?: ""

                val location =
                    listOf(
                        area,
                        district,
                        province
                    )
                        .filter {
                            it.isNotBlank()
                        }
                        .joinToString(", ")


                // =================================================
                // OTHER DETAILS
                // =================================================

                val requiredDate =
                    document.getString(
                        "requiredDate"
                    ) ?: "Not available"

                val requiredBefore =
                    document.getString(
                        "requiredBefore"
                    ) ?: "Not available"

                val primaryContact =
                    document.getString(
                        "primaryContact"
                    ) ?: "Not available"

                val secondaryContact =
                    document.getString(
                        "secondaryContact"
                    )

                val additionalInfo =
                    document.getString(
                        "additionalInfo"
                    )


                // =================================================
                // DISPLAY DATA
                // =================================================

                txtRequestId.text =
                    "REQ-${requestId.takeLast(8).uppercase()}"

                txtBloodGroup.text =
                    bloodGroup

                txtUnits.text =
                    units

                txtPatient.text =
                    userEmail

                txtHospital.text =
                    hospital

                txtUrgency.text =
                    urgency

                txtStatus.text =
                    currentStatus

                txtLocation.text =
                    if (
                        location.isBlank()
                    ) {

                        "Not available"

                    } else {

                        location
                    }

                txtRequiredDate.text =
                    requiredDate

                txtRequiredBefore.text =
                    requiredBefore

                txtPrimaryContact.text =
                    primaryContact

                txtSecondaryContact.text =
                    if (
                        secondaryContact.isNullOrBlank()
                    ) {

                        "Not provided"

                    } else {

                        secondaryContact
                    }

                txtAdditionalInfo.text =
                    if (
                        additionalInfo.isNullOrBlank()
                    ) {

                        "No additional information"

                    } else {

                        additionalInfo
                    }


                // =================================================
                // UPDATE BUTTONS BASED ON STATUS
                // =================================================

                updateActionButtons()
            }

            .addOnFailureListener { exception ->

                Toast.makeText(
                    this,
                    "Failed to load request: ${
                        exception.message ?: "Unknown error"
                    }",
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    // =========================================================
    // BUTTON VISIBILITY
    // =========================================================

    private fun updateActionButtons() {

        when (
            currentStatus.lowercase()
        ) {

            // -------------------------------------------------
            // PENDING
            // Only Start Matching available
            // -------------------------------------------------

            "pending" -> {

                btnStartMatching.visibility =
                    View.VISIBLE

                btnMarkFulfilled.visibility =
                    View.GONE
            }


            // -------------------------------------------------
            // MATCHING
            // Only Fulfilled available
            // -------------------------------------------------

            "matching" -> {

                btnStartMatching.visibility =
                    View.GONE

                btnMarkFulfilled.visibility =
                    View.VISIBLE
            }


            // -------------------------------------------------
            // FULFILLED
            // No actions required
            // -------------------------------------------------

            "fulfilled" -> {

                btnStartMatching.visibility =
                    View.GONE

                btnMarkFulfilled.visibility =
                    View.GONE
            }


            // -------------------------------------------------
            // UNKNOWN STATUS
            // -------------------------------------------------

            else -> {

                btnStartMatching.visibility =
                    View.GONE

                btnMarkFulfilled.visibility =
                    View.GONE
            }
        }
    }


    // =========================================================
    // CONFIRM STATUS CHANGE
    // =========================================================

    private fun showStatusConfirmation(
        newStatus: String,
        title: String,
        message: String
    ) {

        AlertDialog.Builder(this)
            .setTitle(
                title
            )
            .setMessage(
                message
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Confirm"
            ) { _, _ ->

                updateRequestStatus(
                    newStatus
                )
            }
            .show()
    }


    // =========================================================
    // UPDATE FIRESTORE STATUS
    // =========================================================

    private fun updateRequestStatus(
        newStatus: String
    ) {

        if (requestDocumentId.isBlank()) {

            return
        }


        // Disable buttons while updating

        btnStartMatching.isEnabled =
            false

        btnMarkFulfilled.isEnabled =
            false


        firestore
            .collection(
                "emergencyRequests"
            )
            .document(
                requestDocumentId
            )
            .update(
                "status",
                newStatus
            )

            .addOnSuccessListener {

                // ---------------------------------------------
                // UPDATE LOCAL STATUS
                // ---------------------------------------------

                currentStatus =
                    newStatus


                // ---------------------------------------------
                // UPDATE SCREEN
                // ---------------------------------------------

                txtStatus.text =
                    newStatus

                updateActionButtons()


                // ---------------------------------------------
                // RE-ENABLE BUTTONS
                // ---------------------------------------------

                btnStartMatching.isEnabled =
                    true

                btnMarkFulfilled.isEnabled =
                    true


                // ---------------------------------------------
                // SUCCESS MESSAGE
                // ---------------------------------------------

                val message =

                    when (newStatus) {

                        "Matching" ->
                            "Request moved to Matching"

                        "Fulfilled" ->
                            "Request marked as Fulfilled"

                        else ->
                            "Request status updated"
                    }


                Toast.makeText(
                    this,
                    message,
                    Toast.LENGTH_SHORT
                ).show()
            }

            .addOnFailureListener { exception ->

                btnStartMatching.isEnabled =
                    true

                btnMarkFulfilled.isEnabled =
                    true


                Toast.makeText(
                    this,
                    "Failed to update status: ${
                        exception.message ?: "Unknown error"
                    }",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}