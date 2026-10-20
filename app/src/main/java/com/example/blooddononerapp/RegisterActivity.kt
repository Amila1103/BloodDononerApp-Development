
package com.example.blooddononerapp

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var nameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var phoneInput: TextInputEditText
    private lateinit var groupInput: AutoCompleteTextView
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmInput: TextInputEditText
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmLayout: TextInputLayout
    private lateinit var registerButton: MaterialButton

    private val bloodGroups = arrayOf(
        "A+", "A-", "B+", "B-",
        "AB+", "AB-", "O+", "O-"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        nameInput = findViewById(R.id.edtFullName)
        emailInput = findViewById(R.id.edtRegisterEmail)
        phoneInput = findViewById(R.id.edtPhone)
        groupInput = findViewById(R.id.dropBloodGroup)
        passwordInput = findViewById(R.id.edtRegisterPassword)
        confirmInput = findViewById(R.id.edtConfirmPassword)
        passwordLayout = findViewById(R.id.registerPasswordLayout)
        confirmLayout = findViewById(R.id.confirmPasswordLayout)
        registerButton = findViewById(R.id.btnRegister)

        groupInput.setAdapter(
            ArrayAdapter(
                this,
                R.layout.item_blood_group,
                R.id.txtBloodGroupItem,
                bloodGroups
            )
        )

        groupInput.keyListener = null
        groupInput.dropDownHeight =
            (200 * resources.displayMetrics.density).toInt()

        groupInput.setOnClickListener {
            groupInput.showDropDown()
        }

        groupInput.setOnFocusChangeListener { _, focused ->
            if (focused) groupInput.showDropDown()
        }

        registerButton.setOnClickListener {
            register()
        }

        findViewById<TextView>(R.id.txtLogin)
            .setOnClickListener {
                startActivity(
                    Intent(this, LoginActivity::class.java)
                )
                finish()
            }
    }

    private fun register() {

        passwordLayout.error = null
        confirmLayout.error = null

        val name = nameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val phoneDigits = phoneInput.text.toString().trim()
        val group = groupInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val confirmation = confirmInput.text.toString()

        if (name.isBlank()) {
            nameInput.error = "Please enter your full name"
            nameInput.requestFocus()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.error = "Please enter a valid email address"
            emailInput.requestFocus()
            return
        }

        if (!phoneDigits.matches(Regex("[0-9]{9}"))) {
            phoneInput.error =
                "Enter a valid 9-digit number after +94"
            phoneInput.requestFocus()
            return
        }

        if (group !in bloodGroups) {
            toast("Please select your blood group")
            groupInput.showDropDown()
            return
        }

        val passwordError = when {
            password.length < 12 ->
                "Minimum 12 characters required"

            password.none { it.isUpperCase() } ->
                "Add at least one uppercase letter"

            password.none { it.isLowerCase() } ->
                "Add at least one lowercase letter"

            password.none { it.isDigit() } ->
                "Add at least one number"

            password.none { !it.isLetterOrDigit() } ->
                "Add at least one special character"

            else -> null
        }

        if (passwordError != null) {
            passwordLayout.error = passwordError
            passwordInput.requestFocus()
            return
        }

        if (confirmation != password) {
            confirmLayout.error =
                if (confirmation.isEmpty()) {
                    "Please confirm your password"
                } else {
                    "Passwords do not match"
                }

            confirmInput.requestFocus()
            return
        }

        registerButton.isEnabled = false
        registerButton.text = "Registering..."

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->

                val userId = result.user?.uid

                if (userId == null) {
                    resetButton()
                    toast("Unable to create your account")
                } else {
                    saveUserProfile(
                        userId,
                        name,
                        email,
                        "+94$phoneDigits",
                        group
                    )
                }
            }
            .addOnFailureListener { error ->

                if (error is FirebaseAuthUserCollisionException) {

                    recoverOrExplainExistingAccount(
                        name,
                        email,
                        "+94$phoneDigits",
                        group,
                        password
                    )

                } else {
                    resetButton()
                    toast(
                        "Registration failed: " +
                                (error.localizedMessage ?: "Please try again")
                    )
                }
            }
    }

    private fun recoverOrExplainExistingAccount(
        name: String,
        email: String,
        phone: String,
        group: String,
        password: String
    ) {

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->

                val uid = result.user?.uid

                if (uid == null) {
                    resetButton()
                    toast("Please sign in with your existing account")
                    return@addOnSuccessListener
                }

                db.collection("users")
                    .document(uid)
                    .get()
                    .addOnSuccessListener { snapshot ->

                        if (snapshot.exists()) {

                            auth.signOut()
                            resetButton()

                            toast(
                                "This email already has an account. " +
                                        "Please use Login instead."
                            )

                        } else {

                            // Recover a Firebase Auth account
                            // whose Firestore profile is missing.
                            // Do not overwrite existing user documents.

                            saveUserProfile(
                                uid,
                                name,
                                email,
                                phone,
                                group
                            )
                        }
                    }
                    .addOnFailureListener { error ->

                        resetButton()
                        auth.signOut()

                        toast(
                            "Unable to check existing profile: " +
                                    error.localizedMessage
                        )
                    }
            }
            .addOnFailureListener {

                resetButton()

                toast(
                    "Email already registered. Use Login, " +
                            "Forgot Password, or Continue with Google " +
                            "if you used Google before."
                )
            }
    }

    private fun saveUserProfile(
        uid: String,
        name: String,
        email: String,
        phone: String,
        group: String
    ) {

        val data = hashMapOf<String, Any>(

            "uid" to uid,

            "fullName" to name,

            "email" to email,

            "phone" to phone,

            "bloodGroup" to group,

            "role" to "USER",

            // Required by Firestore Security Rules
            "status" to "Active",

            "profileCompleted" to false,

            "availableToDonate" to false,

            "createdAt" to FieldValue.serverTimestamp(),

            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("users")
            .document(uid)
            .set(data)
            .addOnSuccessListener {

                resetButton()

                toast(
                    "Registration successful. Complete your profile."
                )

                val intent = Intent(
                    this,
                    ProfileSetupActivity::class.java
                )

                intent.putExtra(
                    "PHONE_NUMBER",
                    phone
                )

                startActivity(intent)
                finish()
            }
            .addOnFailureListener { error ->

                resetButton()

                auth.signOut()

                toast(
                    "Account created, but profile was not saved: " +
                            "${error.localizedMessage}. " +
                            "Try registering again with the same " +
                            "email and password."
                )
            }
    }

    private fun resetButton() {
        registerButton.isEnabled = true
        registerButton.text = "Register"
    }

    private fun toast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
