
package com.example.blooddononerapp

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.util.Patterns
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private val credentialManager by lazy {
        CredentialManager.create(this)
    }

    private val bloodGroups = arrayOf(
        "A+", "A-", "B+", "B-",
        "AB+", "AB-", "O+", "O-"
    )

    private var googleBusy = false

    private fun screenReady(): Boolean {
        return !isFinishing && !isDestroyed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        val emailInput =
            findViewById<TextInputEditText>(R.id.edtEmail)

        val passwordInput =
            findViewById<TextInputEditText>(R.id.edtPassword)

        val loginButton =
            findViewById<MaterialButton>(R.id.btnLogin)

        val googleButton =
            findViewById<MaterialButton>(R.id.btnGoogle)

        loginButton.setOnClickListener {

            val email =
                emailInput.text.toString().trim()

            val password =
                passwordInput.text.toString()

            if (
                !Patterns.EMAIL_ADDRESS
                    .matcher(email)
                    .matches()
            ) {
                emailInput.error = "Enter a valid email"
                emailInput.requestFocus()
                return@setOnClickListener
            }

            if (password.length < 8) {
                passwordInput.error =
                    "Enter your password (at least 8 characters)"
                passwordInput.requestFocus()
                return@setOnClickListener
            }

            loginButton.isEnabled = false
            loginButton.text = "Logging in..."

            auth.signInWithEmailAndPassword(
                email,
                password
            ).addOnSuccessListener { result ->

                if (!screenReady()) return@addOnSuccessListener

                val user = result.user

                if (user == null) {

                    resetLoginButton(loginButton)
                    toast("Unable to load your account")

                } else {

                    loadUserProfileAndRoute(
                        user.uid,
                        email,
                        loginButton,
                        null
                    )
                }

            }.addOnFailureListener { error ->

                if (!screenReady()) return@addOnFailureListener

                Log.e(
                    "BloodLogin",
                    "Email login failed",
                    error
                )

                resetLoginButton(loginButton)

                toast(
                    "Login failed. Check your email and password."
                )
            }
        }

        googleButton.setOnClickListener {
            startGoogleSignIn(googleButton)
        }

        findViewById<TextView>(
            R.id.txtRegister
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    RegisterActivity::class.java
                )
            )
        }

        findViewById<TextView>(
            R.id.txtForgotPassword
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    ForgotPasswordActivity::class.java
                )
            )
        }
    }

    // =========================================
    // GOOGLE SIGN IN
    // =========================================

    private fun startGoogleSignIn(
        button: MaterialButton
    ) {

        if (googleBusy) return

        googleBusy = true
        button.isEnabled = false
        button.text = "Connecting..."

        CoroutineScope(Dispatchers.Main).launch {

            try {

                val googleOption =
                    GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(
                            getString(
                                R.string.default_web_client_id
                            )
                        )
                        .build()

                val request =
                    GetCredentialRequest.Builder()
                        .addCredentialOption(googleOption)
                        .build()

                val credential =
                    credentialManager.getCredential(
                        context = this@LoginActivity,
                        request = request
                    ).credential

                if (!screenReady()) return@launch

                if (
                    credential !is CustomCredential ||
                    credential.type !=
                    GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {

                    resetGoogleButton(button)

                    toast(
                        "Unsupported Google credential."
                    )

                    return@launch
                }

                val googleToken =
                    GoogleIdTokenCredential
                        .createFrom(credential.data)
                        .idToken

                if (googleToken.isBlank()) {

                    resetGoogleButton(button)

                    toast(
                        "Unable to retrieve Google ID token."
                    )

                    return@launch
                }

                firebaseGoogleLogin(
                    googleToken,
                    button
                )

            } catch (error: GetCredentialException) {

                if (!screenReady()) return@launch

                Log.e(
                    "BloodLogin",
                    "Google credential error",
                    error
                )

                resetGoogleButton(button)

                toast(
                    "Google Sign-In cancelled or unavailable: " +
                            error.localizedMessage
                )

            } catch (error: Exception) {

                if (!screenReady()) return@launch

                Log.e(
                    "BloodLogin",
                    "Google sign-in error",
                    error
                )

                resetGoogleButton(button)

                toast(
                    "Google Sign-In failed: " +
                            error.localizedMessage
                )
            }
        }
    }

    // =========================================
    // FIREBASE GOOGLE AUTHENTICATION
    // =========================================

    private fun firebaseGoogleLogin(
        token: String,
        button: MaterialButton
    ) {

        val credential =
            GoogleAuthProvider.getCredential(
                token,
                null
            )

        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->

                if (!screenReady()) return@addOnSuccessListener

                val user = result.user

                if (user == null) {

                    resetGoogleButton(button)

                    toast(
                        "Unable to load your Google account."
                    )

                    return@addOnSuccessListener
                }

                checkGoogleUserProfile(
                    user,
                    button
                )

            }.addOnFailureListener { error ->

                if (!screenReady()) return@addOnFailureListener

                Log.e(
                    "BloodLogin",
                    "Firebase Google authentication failed",
                    error
                )

                resetGoogleButton(button)

                toast(
                    "Firebase authentication failed: " +
                            error.localizedMessage
                )
            }
    }

    // =========================================
    // CHECK GOOGLE USER FIRESTORE PROFILE
    // =========================================

    private fun checkGoogleUserProfile(
        user: FirebaseUser,
        button: MaterialButton
    ) {

        button.text = "Loading profile..."

        db.collection("users")
            .document(user.uid)
            .get(Source.SERVER)
            .addOnSuccessListener { document ->

                if (!screenReady()) return@addOnSuccessListener

                if (!document.exists()) {

                    showGoogleDetailsDialog(
                        user,
                        null,
                        button
                    )

                    return@addOnSuccessListener
                }

                val role =
                    document.getString("role")
                        ?.trim()
                        ?.uppercase()
                        ?: "USER"

                val status =
                    document.getString("status")
                        ?.trim()
                        ?: ""

                if (!status.equals("Active", true)) {

                    signOutAndExplain(
                        "Your account is unavailable ($status)."
                    )

                    resetGoogleButton(button)

                    return@addOnSuccessListener
                }

                if (role == "USER") {

                    val phone =
                        document.getString("phone")
                            .orEmpty()

                    val bloodGroup =
                        document.getString("bloodGroup")
                            .orEmpty()

                    val phoneValid =
                        phone.matches(
                            Regex("""\+947\d{8}""")
                        )

                    val bloodValid =
                        bloodGroup in bloodGroups

                    if (!phoneValid || !bloodValid) {

                        showGoogleDetailsDialog(
                            user,
                            document,
                            button
                        )

                        return@addOnSuccessListener
                    }
                }

                loadUserProfileAndRoute(
                    user.uid,
                    user.email.orEmpty(),
                    null,
                    button
                )

            }.addOnFailureListener { error ->

                if (!screenReady()) return@addOnFailureListener

                Log.e(
                    "BloodLogin",
                    "Firestore profile check failed",
                    error
                )

                auth.signOut()
                clearLoginSession()
                resetGoogleButton(button)

                toast(
                    "Cannot load Google profile: " +
                            error.localizedMessage
                )
            }
    }

    // =========================================
    // SAFE GOOGLE PROFILE DETAILS DIALOG
    // =========================================

    private fun showGoogleDetailsDialog(
        user: FirebaseUser,
        existing: DocumentSnapshot?,
        googleButton: MaterialButton
    ) {

        if (!screenReady()) return

        googleButton.text = "Complete details..."

        val density = resources.displayMetrics.density
        val padding = (22 * density).toInt()
        val margin = (12 * density).toInt()

        val root = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                padding,
                padding,
                padding,
                padding
            )
        }

        val phoneLabel = TextView(this).apply {
            text = "Phone number (+94)"
            textSize = 15f
        }

        root.addView(
            phoneLabel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val phoneInput = EditText(this).apply {

            hint = "7XXXXXXXX"
            inputType = InputType.TYPE_CLASS_PHONE

            filters = arrayOf(
                InputFilter.LengthFilter(9)
            )

            setSingleLine(true)

            val previous =
                existing?.getString("phone")
                    .orEmpty()
                    .filter { it.isDigit() }

            val previousDigits = when {

                previous.length == 11 &&
                        previous.startsWith("947") ->
                    previous.drop(2)

                previous.length == 10 &&
                        previous.startsWith("07") ->
                    previous.drop(1)

                previous.length == 9 &&
                        previous.startsWith("7") ->
                    previous

                else -> ""
            }

            setText(previousDigits)
        }

        root.addView(
            phoneInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val bloodLabel = TextView(this).apply {

            text = "Blood group"
            textSize = 15f
        }

        root.addView(
            bloodLabel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = margin
            }
        )

        val options =
            arrayOf("Select blood group") + bloodGroups

        val bloodSpinner = Spinner(this).apply {

            adapter = ArrayAdapter(
                this@LoginActivity,
                android.R.layout.simple_spinner_dropdown_item,
                options
            )

            val oldGroup =
                existing?.getString("bloodGroup")
                    .orEmpty()

            val index =
                options.indexOf(oldGroup)

            setSelection(
                if (index >= 0) index else 0
            )
        }

        root.addView(
            bloodSpinner,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val scrollView = ScrollView(this).apply {
            addView(root)
        }

        val dialog =
            AlertDialog.Builder(this)
                .setTitle("Complete Donor Details")
                .setMessage(
                    "Please enter your phone number and " +
                            "blood group to continue with Google."
                )
                .setView(scrollView)
                .setCancelable(false)
                .setNegativeButton("Cancel") { _, _ ->

                    auth.signOut()
                    clearLoginSession()
                    resetGoogleButton(googleButton)
                }
                .setPositiveButton(
                    "Save & Continue",
                    null
                )
                .create()

        dialog.setOnShowListener {

            val saveButton =
                dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
                )

            saveButton.setOnClickListener {

                val digits =
                    phoneInput.text.toString().trim()

                if (
                    !digits.matches(
                        Regex("""7\d{8}""")
                    )
                ) {

                    phoneInput.error =
                        "Enter 9 digits starting with 7"

                    phoneInput.requestFocus()

                    return@setOnClickListener
                }

                val bloodGroup =
                    bloodSpinner.selectedItem
                        ?.toString()
                        .orEmpty()

                if (bloodGroup !in bloodGroups) {

                    toast(
                        "Please select a blood group."
                    )

                    return@setOnClickListener
                }

                saveButton.isEnabled = false

                val phone = "+94$digits"

                saveGoogleProfile(
                    user,
                    existing,
                    phone,
                    bloodGroup,
                    googleButton,
                    dialog,
                    saveButton
                )
            }
        }

        try {

            dialog.show()

        } catch (error: Exception) {

            Log.e(
                "BloodLogin",
                "Unable to display profile details",
                error
            )

            auth.signOut()
            clearLoginSession()
            resetGoogleButton(googleButton)

            toast(
                "Unable to display profile form: " +
                        error.localizedMessage
            )
        }
    }

    // =========================================
    // SAVE GOOGLE USER DETAILS
    // =========================================

    private fun saveGoogleProfile(
        user: FirebaseUser,
        existing: DocumentSnapshot?,
        phone: String,
        bloodGroup: String,
        googleButton: MaterialButton,
        dialog: AlertDialog,
        saveButton: android.widget.Button
    ) {

        val document =
            db.collection("users")
                .document(user.uid)

        val task = if (existing == null) {

            val newUser = hashMapOf<String, Any>(

                "uid" to user.uid,

                "fullName" to (
                        user.displayName
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: "Google User"
                        ),

                "email" to user.email.orEmpty(),

                "phone" to phone,

                "bloodGroup" to bloodGroup,

                "role" to "USER",

                "status" to "Active",

                "profileCompleted" to false,

                "availableToDonate" to false,

                "createdAt" to FieldValue.serverTimestamp(),

                "updatedAt" to FieldValue.serverTimestamp()
            )

            document.set(newUser)

        } else {

            document.update(
                mapOf(
                    "phone" to phone,
                    "bloodGroup" to bloodGroup,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        task.addOnSuccessListener {

            if (!screenReady()) return@addOnSuccessListener

            dialog.dismiss()

            loadUserProfileAndRoute(
                user.uid,
                user.email.orEmpty(),
                null,
                googleButton
            )

        }.addOnFailureListener { error ->

            if (!screenReady()) return@addOnFailureListener

            Log.e(
                "BloodLogin",
                "Google profile save failed",
                error
            )

            saveButton.isEnabled = true

            toast(
                "Could not save donor details: " +
                        error.localizedMessage
            )
        }
    }

    // =========================================
    // FIRESTORE ROLE BASED LOGIN
    // =========================================

    private fun loadUserProfileAndRoute(
        uid: String,
        fallbackEmail: String,
        loginButton: MaterialButton?,
        googleButton: MaterialButton?
    ) {

        db.collection("users")
            .document(uid)
            .get(Source.SERVER)
            .addOnSuccessListener { document ->

                if (!screenReady()) return@addOnSuccessListener

                loginButton?.let {
                    resetLoginButton(it)
                }

                googleButton?.let {
                    resetGoogleButton(it)
                }

                if (!document.exists()) {

                    signOutAndExplain(
                        "User profile not found in Firestore."
                    )

                    return@addOnSuccessListener
                }

                val role =
                    document.getString("role")
                        ?.trim()
                        ?.uppercase()
                        ?: ""

                val status =
                    document.getString("status")
                        ?.trim()
                        ?: ""

                val profileCompleted =
                    document.getBoolean(
                        "profileCompleted"
                    ) == true

                val email =
                    document.getString("email")
                        ?.takeIf { it.isNotBlank() }
                        ?: fallbackEmail

                if (!status.equals("Active", true)) {

                    signOutAndExplain(
                        "Your account is unavailable ($status)."
                    )

                    return@addOnSuccessListener
                }

                when (role) {

                    "ADMIN" -> {

                        saveLoginSession(
                            "ADMIN",
                            email,
                            uid
                        )

                        toast("Admin Login Successful")

                        openActivity(
                            AdminDashboardActivity::class.java
                        )
                    }

                    "STAFF" -> {

                        saveLoginSession(
                            "STAFF",
                            email,
                            uid
                        )

                        toast("Staff Login Successful")

                        openActivity(
                            StaffDashboardActivity::class.java
                        )
                    }

                    "USER" -> {

                        saveLoginSession(
                            "USER",
                            email,
                            uid
                        )

                        if (profileCompleted) {

                            toast("Login Successful")

                            openActivity(
                                HomeActivity::class.java
                            )

                        } else {

                            toast("Please complete your profile")

                            openActivity(
                                ProfileSetupActivity::class.java
                            )
                        }
                    }

                    else -> {

                        signOutAndExplain(
                            "Invalid account role."
                        )
                    }
                }

            }.addOnFailureListener { error ->

                if (!screenReady()) return@addOnFailureListener

                Log.e(
                    "BloodLogin",
                    "Profile routing failed",
                    error
                )

                auth.signOut()
                clearLoginSession()

                loginButton?.let {
                    resetLoginButton(it)
                }

                googleButton?.let {
                    resetGoogleButton(it)
                }

                toast(
                    "Cannot load account profile: " +
                            error.localizedMessage
                )
            }
    }

    // =========================================
    // NAVIGATION
    // =========================================

    private fun openActivity(
        destination: Class<*>
    ) {

        val intent = Intent(this, destination).apply {

            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)

        finish()
    }

    // =========================================
    // LOGIN SESSION
    // =========================================

    private fun saveLoginSession(
        role: String,
        email: String,
        uid: String
    ) {

        getSharedPreferences(
            "login_session",
            MODE_PRIVATE
        ).edit()
            .putBoolean(
                "is_logged_in",
                true
            )
            .putString(
                "role",
                role
            )
            .putString(
                "email",
                email
            )
            .putString(
                "uid",
                uid
            )
            .apply()
    }

    private fun clearLoginSession() {

        getSharedPreferences(
            "login_session",
            MODE_PRIVATE
        ).edit()
            .clear()
            .apply()
    }

    private fun signOutAndExplain(
        message: String
    ) {

        auth.signOut()
        clearLoginSession()

        toast(message)
    }

    // =========================================
    // BUTTON STATES
    // =========================================

    private fun resetLoginButton(
        button: MaterialButton
    ) {

        button.isEnabled = true
        button.text = "Login"
    }

    private fun resetGoogleButton(
        button: MaterialButton
    ) {

        googleBusy = false

        button.isEnabled = true
        button.text = "Continue with Google"
    }

    private fun toast(
        message: String
    ) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
