package com.example.blooddononerapp

import android.os.Bundle
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class AdminAddStaffActivity : AppCompatActivity() {

    // =========================================================
    // FIREBASE
    // =========================================================

    private lateinit var firestore: FirebaseFirestore

    private var secondaryFirebaseApp: FirebaseApp? = null
    private var secondaryAuth: FirebaseAuth? = null


    // =========================================================
    // VIEWS
    // =========================================================

    private lateinit var edtFullName: TextInputEditText
    private lateinit var edtEmail: TextInputEditText
    private lateinit var edtPhone: TextInputEditText
    private lateinit var dropdownHospital: AutoCompleteTextView
    private lateinit var edtPassword: TextInputEditText
    private lateinit var edtConfirmPassword: TextInputEditText
    private lateinit var btnCreateStaff: MaterialButton


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_admin_add_staff
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

        edtFullName =
            findViewById(
                R.id.edtFullName
            )

        edtEmail =
            findViewById(
                R.id.edtEmail
            )

        edtPhone =
            findViewById(
                R.id.edtPhone
            )

        dropdownHospital =
            findViewById(
                R.id.dropdownHospital
            )

        edtPassword =
            findViewById(
                R.id.edtPassword
            )

        edtConfirmPassword =
            findViewById(
                R.id.edtConfirmPassword
            )

        btnCreateStaff =
            findViewById(
                R.id.btnCreateStaff
            )


        // =====================================================
        // BACK
        // =====================================================

        btnBack.setOnClickListener {

            finish()
        }


        // =====================================================
        // LOAD HOSPITALS
        // =====================================================

        loadHospitals()


        // =====================================================
        // CREATE STAFF
        // =====================================================

        btnCreateStaff.setOnClickListener {

            validateAndCreateStaff()
        }
    }


    // =========================================================
    // LOAD HOSPITALS FROM FIRESTORE
    // =========================================================

    private fun loadHospitals() {

        firestore
            .collection("hospitals")
            .get()
            .addOnSuccessListener { documents ->

                val hospitalNames =
                    mutableListOf<String>()

                for (document in documents) {

                    val hospitalName =
                        document.getString("name")

                    if (!hospitalName.isNullOrBlank()) {

                        hospitalNames.add(
                            hospitalName
                        )
                    }
                }


                hospitalNames.sort()


                val adapter =
                    ArrayAdapter(
                        this,
                        android.R.layout.simple_dropdown_item_1line,
                        hospitalNames
                    )


                dropdownHospital.setAdapter(
                    adapter
                )
            }

            .addOnFailureListener { exception ->

                Toast.makeText(
                    this,
                    "Failed to load hospitals: ${
                        exception.message ?: "Unknown error"
                    }",
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    // =========================================================
    // VALIDATE
    // =========================================================

    private fun validateAndCreateStaff() {

        val fullName =
            edtFullName.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val email =
            edtEmail.text
                ?.toString()
                ?.trim()
                ?.lowercase()
                .orEmpty()

        val phone =
            edtPhone.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val hospital =
            dropdownHospital.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val password =
            edtPassword.text
                ?.toString()
                .orEmpty()

        val confirmPassword =
            edtConfirmPassword.text
                ?.toString()
                .orEmpty()


        // =====================================================
        // FULL NAME
        // =====================================================

        if (fullName.isBlank()) {

            edtFullName.error =
                "Enter staff full name"

            edtFullName.requestFocus()

            return
        }


        // =====================================================
        // EMAIL
        // =====================================================

        if (email.isBlank()) {

            edtEmail.error =
                "Enter email address"

            edtEmail.requestFocus()

            return
        }


        if (
            !Patterns.EMAIL_ADDRESS
                .matcher(email)
                .matches()
        ) {

            edtEmail.error =
                "Enter a valid email address"

            edtEmail.requestFocus()

            return
        }


        // =====================================================
        // PHONE
        // =====================================================

        if (phone.isBlank()) {

            edtPhone.error =
                "Enter phone number"

            edtPhone.requestFocus()

            return
        }


        // =====================================================
        // HOSPITAL
        // =====================================================

        if (hospital.isBlank()) {

            dropdownHospital.error =
                "Select a hospital"

            dropdownHospital.requestFocus()

            return
        }


        // =====================================================
        // PASSWORD
        // =====================================================

        if (password.length < 8) {

            edtPassword.error =
                "Password must contain at least 8 characters"

            edtPassword.requestFocus()

            return
        }


        if (password != confirmPassword) {

            edtConfirmPassword.error =
                "Passwords do not match"

            edtConfirmPassword.requestFocus()

            return
        }


        // =====================================================
        // START CREATION
        // =====================================================

        createStaffAccount(
            fullName = fullName,
            email = email,
            phone = phone,
            hospital = hospital,
            password = password
        )
    }


    // =========================================================
    // CREATE STAFF USING SECONDARY FIREBASE AUTH
    // =========================================================

    private fun createStaffAccount(
        fullName: String,
        email: String,
        phone: String,
        hospital: String,
        password: String
    ) {

        setLoading(true)


        try {

            // =================================================
            // GET DEFAULT FIREBASE CONFIG
            // =================================================

            val defaultApp =
                FirebaseApp.getInstance()

            val defaultOptions =
                defaultApp.options


            val secondaryOptions =
                FirebaseOptions.Builder()
                    .setApplicationId(
                        defaultOptions.applicationId
                    )
                    .setApiKey(
                        defaultOptions.apiKey
                    )
                    .setProjectId(
                        defaultOptions.projectId
                    )
                    .build()


            // =================================================
            // DELETE OLD SECONDARY INSTANCE IF PRESENT
            // =================================================

            try {

                FirebaseApp
                    .getInstance("SecondaryStaffCreation")
                    .delete()

            } catch (_: Exception) {

                // No old secondary app exists.
            }


            // =================================================
            // CREATE SECONDARY FIREBASE APP
            // =================================================

            secondaryFirebaseApp =
                FirebaseApp.initializeApp(
                    this,
                    secondaryOptions,
                    "SecondaryStaffCreation"
                )


            if (secondaryFirebaseApp == null) {

                setLoading(false)

                Toast.makeText(
                    this,
                    "Unable to initialize staff account service.",
                    Toast.LENGTH_LONG
                ).show()

                return
            }


            secondaryAuth =
                FirebaseAuth.getInstance(
                    secondaryFirebaseApp!!
                )


            // =================================================
            // CREATE FIREBASE AUTH ACCOUNT
            // =================================================

            secondaryAuth!!
                .createUserWithEmailAndPassword(
                    email,
                    password
                )
                .addOnSuccessListener { authResult ->

                    val newStaffUser =
                        authResult.user

                    if (newStaffUser == null) {

                        cleanupSecondaryFirebase()

                        setLoading(false)

                        Toast.makeText(
                            this,
                            "Staff account could not be created.",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addOnSuccessListener
                    }


                    val newStaffUid =
                        newStaffUser.uid


                    // =========================================
                    // FIRESTORE STAFF DOCUMENT
                    // =========================================

                    val staffData =
                        hashMapOf<String, Any>(
                            "uid" to newStaffUid,
                            "fullName" to fullName,
                            "email" to email,
                            "phone" to phone,
                            "hospital" to hospital,
                            "role" to "STAFF",
                            "status" to "Active",
                            "profileCompleted" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )


                    firestore
                        .collection("users")
                        .document(newStaffUid)
                        .set(staffData)
                        .addOnSuccessListener {

                            cleanupSecondaryFirebase()

                            setLoading(false)


                            Toast.makeText(
                                this,
                                "Staff account created successfully",
                                Toast.LENGTH_LONG
                            ).show()


                            finish()
                        }

                        .addOnFailureListener { exception ->

                            /*
                             * Authentication account was created,
                             * but Firestore profile creation failed.
                             *
                             * Try to delete the newly-created Auth user
                             * while the secondary account is still signed in.
                             */

                            newStaffUser
                                .delete()
                                .addOnCompleteListener {

                                    cleanupSecondaryFirebase()

                                    setLoading(false)


                                    Toast.makeText(
                                        this,
                                        "Failed to save staff profile: ${
                                            exception.message
                                                ?: "Unknown error"
                                        }",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                }

                .addOnFailureListener { exception ->

                    cleanupSecondaryFirebase()

                    setLoading(false)


                    Toast.makeText(
                        this,
                        "Failed to create staff account: ${
                            exception.message ?: "Unknown error"
                        }",
                        Toast.LENGTH_LONG
                    ).show()
                }

        } catch (exception: Exception) {

            cleanupSecondaryFirebase()

            setLoading(false)


            Toast.makeText(
                this,
                "Error: ${
                    exception.message ?: "Unknown error"
                }",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    // =========================================================
    // LOADING
    // =========================================================

    private fun setLoading(
        loading: Boolean
    ) {

        btnCreateStaff.isEnabled =
            !loading

        btnCreateStaff.text =
            if (loading) {

                "Creating Staff Account..."

            } else {

                "Create Staff Account"
            }
    }


    // =========================================================
    // CLEAN SECONDARY FIREBASE
    // =========================================================

    private fun cleanupSecondaryFirebase() {

        try {

            secondaryAuth?.signOut()

        } catch (_: Exception) {
        }


        try {

            secondaryFirebaseApp?.delete()

        } catch (_: Exception) {
        }


        secondaryAuth = null
        secondaryFirebaseApp = null
    }


    // =========================================================
    // CLEANUP
    // =========================================================

    override fun onDestroy() {

        cleanupSecondaryFirebase()

        super.onDestroy()
    }
}