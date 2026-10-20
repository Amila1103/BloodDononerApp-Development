package com.example.blooddononerapp

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore

class AdminUserDetailsActivity : AppCompatActivity() {

    // =========================================================
    // FIREBASE
    // =========================================================

    private lateinit var firestore: FirebaseFirestore


    // =========================================================
    // VIEWS
    // =========================================================

    private lateinit var txtUserId: TextView
    private lateinit var txtName: TextView
    private lateinit var txtBloodGroup: TextView
    private lateinit var txtEmail: TextView
    private lateinit var txtPhone: TextView
    private lateinit var txtStatus: TextView
    private lateinit var btnAccountAction: MaterialButton


    // =========================================================
    // USER DATA
    // =========================================================

    private var userId: String = ""
    private var userName: String = "User"
    private var currentStatus: String = "Active"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_admin_user_details
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

        txtUserId =
            findViewById(
                R.id.txtUserId
            )

        txtName =
            findViewById(
                R.id.txtUserName
            )

        txtBloodGroup =
            findViewById(
                R.id.txtBloodGroup
            )

        txtEmail =
            findViewById(
                R.id.txtEmail
            )

        txtPhone =
            findViewById(
                R.id.txtPhone
            )

        txtStatus =
            findViewById(
                R.id.txtStatus
            )

        btnAccountAction =
            findViewById(
                R.id.btnAccountAction
            )


        // =====================================================
        // GET FIREBASE USER ID
        // =====================================================

        userId =
            intent.getStringExtra(
                "USER_ID"
            ) ?: ""


        // =====================================================
        // CHECK USER ID
        // =====================================================

        if (userId.isEmpty()) {

            Toast.makeText(
                this,
                "User ID not found",
                Toast.LENGTH_LONG
            ).show()

            finish()

            return
        }


        // =====================================================
        // BACK
        // =====================================================

        btnBack.setOnClickListener {

            finish()
        }


        // =====================================================
        // LOAD USER FROM FIRESTORE
        // =====================================================

        loadUserDetails()


        // =====================================================
        // ACCOUNT ACTION
        // =====================================================

        btnAccountAction.setOnClickListener {

            if (
                currentStatus.equals(
                    "Active",
                    ignoreCase = true
                )
            ) {

                showStatusConfirmation(
                    title = "Suspend User?",
                    message = "Suspend $userName's account?",
                    newStatus = "Suspended"
                )

            } else {

                showStatusConfirmation(
                    title = "Activate User?",
                    message = "Activate $userName's account?",
                    newStatus = "Active"
                )
            }
        }
    }


    // =========================================================
    // LOAD USER DETAILS FROM FIRESTORE
    // =========================================================

    private fun loadUserDetails() {

        // Disable action until user loads
        btnAccountAction.isEnabled = false
        btnAccountAction.text = "Loading..."


        firestore
            .collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {

                    Toast.makeText(
                        this,
                        "User profile not found",
                        Toast.LENGTH_LONG
                    ).show()

                    finish()

                    return@addOnSuccessListener
                }


                // =============================================
                // VERIFY THIS IS A NORMAL USER
                // =============================================

                val role =
                    document.getString(
                        "role"
                    ) ?: "USER"


                if (!role.equals("USER", ignoreCase = true)) {

                    Toast.makeText(
                        this,
                        "This account is not a user account",
                        Toast.LENGTH_LONG
                    ).show()

                    finish()

                    return@addOnSuccessListener
                }


                // =============================================
                // READ FIRESTORE DATA
                // =============================================

                userName =
                    document.getString(
                        "fullName"
                    ) ?: "Unknown User"

                val email =
                    document.getString(
                        "email"
                    ) ?: "Not available"

                val phone =
                    document.getString(
                        "phone"
                    ) ?: "Not available"

                val bloodGroup =
                    document.getString(
                        "bloodGroup"
                    ) ?: "--"

                currentStatus =
                    document.getString(
                        "status"
                    ) ?: "Active"


                // =============================================
                // CREATE DISPLAY USER ID
                // =============================================

                val displayId =
                    if (userId.length > 8) {

                        "USR-${userId.take(8).uppercase()}"

                    } else {

                        "USR-${userId.uppercase()}"
                    }


                // =============================================
                // DISPLAY FIREBASE DATA
                // =============================================

                txtUserId.text =
                    displayId

                txtName.text =
                    userName

                txtEmail.text =
                    email

                txtPhone.text =
                    formatPhoneNumber(
                        phone
                    )

                txtBloodGroup.text =
                    bloodGroup


                // =============================================
                // STATUS
                // =============================================

                btnAccountAction.isEnabled = true

                updateStatusUI()
            }

            .addOnFailureListener { exception ->

                btnAccountAction.isEnabled = false
                btnAccountAction.text = "Unable to load user"

                Toast.makeText(
                    this,
                    "Failed to load user: ${
                        exception.message ?: "Unknown error"
                    }",
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    // =========================================================
    // STATUS CONFIRMATION
    // =========================================================

    private fun showStatusConfirmation(
        title: String,
        message: String,
        newStatus: String
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

                updateUserStatus(
                    newStatus
                )
            }
            .show()
    }


    // =========================================================
    // UPDATE STATUS IN FIRESTORE
    // =========================================================

    private fun updateUserStatus(
        newStatus: String
    ) {

        btnAccountAction.isEnabled = false
        btnAccountAction.text = "Updating..."


        firestore
            .collection("users")
            .document(userId)
            .update(
                "status",
                newStatus
            )
            .addOnSuccessListener {

                currentStatus =
                    newStatus


                updateStatusUI()


                btnAccountAction.isEnabled =
                    true


                Toast.makeText(
                    this,
                    "Account status updated to $newStatus",
                    Toast.LENGTH_SHORT
                ).show()
            }

            .addOnFailureListener { exception ->

                btnAccountAction.isEnabled =
                    true


                updateStatusUI()


                Toast.makeText(
                    this,
                    "Failed to update status: ${
                        exception.message ?: "Unknown error"
                    }",
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    // =========================================================
    // UPDATE STATUS UI
    // =========================================================

    private fun updateStatusUI() {

        txtStatus.text =
            currentStatus


        if (
            currentStatus.equals(
                "Active",
                ignoreCase = true
            )
        ) {

            // ACTIVE
            txtStatus.setTextColor(
                Color.parseColor(
                    "#15803D"
                )
            )


            btnAccountAction.text =
                "Suspend User"


            btnAccountAction.setBackgroundColor(
                Color.parseColor(
                    "#EF2637"
                )
            )

        } else {

            // SUSPENDED
            txtStatus.setTextColor(
                Color.parseColor(
                    "#DC2626"
                )
            )


            btnAccountAction.text =
                "Activate User"


            btnAccountAction.setBackgroundColor(
                Color.parseColor(
                    "#15803D"
                )
            )
        }
    }


    // =========================================================
    // FORMAT PHONE NUMBER
    // =========================================================

    private fun formatPhoneNumber(
        phone: String
    ): String {

        val cleaned =
            phone.replace(
                " ",
                ""
            )


        if (
            cleaned.startsWith("+94") &&
            cleaned.length == 12
        ) {

            val number =
                cleaned.removePrefix(
                    "+94"
                )


            return "+94 ${
                number.substring(0, 2)
            } ${
                number.substring(2, 5)
            } ${
                number.substring(5)
            }"
        }


        return phone
    }
}