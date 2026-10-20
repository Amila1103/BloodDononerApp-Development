
package com.example.blooddononerapp

import android.app.AlertDialog
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class AdminHospitalDetailsActivity : AppCompatActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var idField: TextInputEditText
    private lateinit var nameField: TextInputEditText
    private lateinit var districtField: TextInputEditText
    private lateinit var areaField: TextInputEditText
    private lateinit var phoneField: TextInputEditText
    private lateinit var emailField: TextInputEditText
    private lateinit var saveButton: MaterialButton
    private lateinit var statusButton: MaterialButton

    private var documentId: String? = null
    private var currentStatus = "Active"
    private var isAddMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_hospital_details)

        idField = findViewById(R.id.edtHospitalId)
        nameField = findViewById(R.id.edtHospitalName)
        districtField = findViewById(R.id.edtDistrict)
        areaField = findViewById(R.id.edtArea)
        phoneField = findViewById(R.id.edtHospitalPhone)
        emailField = findViewById(R.id.edtHospitalEmail)
        saveButton = findViewById(R.id.btnSaveHospital)

        findViewById<MaterialButton>(R.id.btnHospitalBack)
            .setOnClickListener { finish() }

        isAddMode = intent.getStringExtra("MODE") == "ADD"
        documentId = intent.getStringExtra("DOCUMENT_ID")

        idField.isEnabled = false
        nameField.isEnabled = isAddMode

        statusButton = MaterialButton(this).apply {
            text = "Deactivate Hospital"
            setAllCaps(false)
            visibility = View.GONE
        }

        (saveButton.parent as? LinearLayout)?.let { parent ->
            parent.addView(
                statusButton,
                parent.indexOfChild(saveButton) + 1,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(52)
                ).apply {
                    bottomMargin = dp(22)
                }
            )
        }

        statusButton.setOnClickListener {
            confirmStatusChange()
        }

        saveButton.setOnClickListener {
            saveHospital()
        }

        if (isAddMode) {
            idField.setText("Generated automatically")
            saveButton.text = "Add Hospital"
        } else {
            saveButton.text = "Save Changes"

            val id = documentId

            if (id.isNullOrBlank()) {
                toast("Hospital document was not provided")
                finish()
                return
            }

            loadHospital(id)
        }
    }

    private fun loadHospital(id: String) {
        saveButton.isEnabled = false

        db.collection("hospitals")
            .document(id)
            .get()
            .addOnSuccessListener { doc ->

                if (!doc.exists()) {
                    toast("Hospital not found")
                    finish()
                    return@addOnSuccessListener
                }

                idField.setText(
                    doc.getString("hospitalId") ?: doc.id
                )

                nameField.setText(
                    doc.getString("name")
                        ?: doc.getString("hospitalName")
                        ?: ""
                )

                districtField.setText(
                    doc.getString("district").orEmpty()
                )

                areaField.setText(
                    doc.getString("area").orEmpty()
                )

                phoneField.setText(
                    doc.getString("phone").orEmpty()
                )

                emailField.setText(
                    doc.getString("email").orEmpty()
                )

                currentStatus = doc.getString("status")
                    ?: if (doc.getBoolean("acceptingDonors") == false)
                        "Inactive"
                    else
                        "Active"

                statusButton.text =
                    if (currentStatus.equals("Active", true))
                        "Deactivate Hospital"
                    else
                        "Activate Hospital"

                statusButton.visibility = View.VISIBLE
                saveButton.isEnabled = true
            }
            .addOnFailureListener { error ->
                toast("Unable to load hospital: ${error.localizedMessage}")
                finish()
            }
    }

    private fun saveHospital() {
        val name = nameField.text?.toString()?.trim().orEmpty()
        val district = districtField.text?.toString()?.trim().orEmpty()
        val area = areaField.text?.toString()?.trim().orEmpty()
        val phone = phoneField.text?.toString()?.trim().orEmpty()
        val email = emailField.text?.toString()?.trim().orEmpty()

        if (name.isEmpty()) {
            nameField.error = "Enter hospital name"
            return
        }

        if (district.isEmpty()) {
            districtField.error = "Enter district"
            return
        }

        if (area.isEmpty()) {
            areaField.error = "Enter area"
            return
        }

        if (phone.isEmpty()) {
            phoneField.error = "Enter contact number"
            return
        }

        if (
            email.isNotEmpty() &&
            !Patterns.EMAIL_ADDRESS.matcher(email).matches()
        ) {
            emailField.error = "Enter a valid email"
            return
        }

        saveButton.isEnabled = false
        saveButton.text = "Saving..."

        if (isAddMode) {
            val doc = db.collection("hospitals").document()

            val data = hashMapOf<String, Any>(
                "hospitalId" to "HOS-${doc.id.take(8).uppercase()}",
                "name" to name,
                "district" to district,
                "area" to area,
                "phone" to phone,
                "email" to email,
                "status" to "Active",
                "acceptingDonors" to true,
                "bloodStock" to mapOf(
                    "APlus" to 0,
                    "AMinus" to 0,
                    "BPlus" to 0,
                    "BMinus" to 0,
                    "ABPlus" to 0,
                    "ABMinus" to 0,
                    "OPlus" to 0,
                    "OMinus" to 0
                ),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            doc.set(data)
                .addOnSuccessListener {
                    toast("Hospital added successfully")
                    finish()
                }
                .addOnFailureListener { error ->
                    restoreButton(
                        "Add Hospital",
                        error.localizedMessage
                    )
                }

        } else {
            val id = documentId ?: run {
                restoreButton("Save Changes", "Hospital ID missing")
                return
            }

            db.collection("hospitals")
                .document(id)
                .update(
                    mapOf(
                        "district" to district,
                        "area" to area,
                        "phone" to phone,
                        "email" to email,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .addOnSuccessListener {
                    toast("Hospital updated successfully")
                    finish()
                }
                .addOnFailureListener { error ->
                    restoreButton(
                        "Save Changes",
                        error.localizedMessage
                    )
                }
        }
    }

    private fun confirmStatusChange() {
        val id = documentId ?: return
        val shouldActivate = !currentStatus.equals("Active", true)
        val action = if (shouldActivate) "Activate" else "Deactivate"

        AlertDialog.Builder(this)
            .setTitle("$action Hospital?")
            .setMessage("Are you sure you want to $action this hospital?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm") { _, _ ->

                statusButton.isEnabled = false

                val newStatus =
                    if (shouldActivate) "Active" else "Inactive"

                db.collection("hospitals")
                    .document(id)
                    .update(
                        mapOf(
                            "status" to newStatus,
                            "acceptingDonors" to shouldActivate,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .addOnSuccessListener {
                        currentStatus = newStatus

                        statusButton.text =
                            if (shouldActivate)
                                "Deactivate Hospital"
                            else
                                "Activate Hospital"

                        statusButton.isEnabled = true
                        toast("Hospital is now $newStatus")
                    }
                    .addOnFailureListener { error ->
                        statusButton.isEnabled = true
                        toast(
                            "Status update failed: ${error.localizedMessage}"
                        )
                    }
            }
            .show()
    }

    private fun restoreButton(label: String, message: String?) {
        saveButton.isEnabled = true
        saveButton.text = label
        toast("Save failed: ${message ?: "Unknown error"}")
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
