package com.example.blooddononerapp

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        // Firebase Authentication
        auth = FirebaseAuth.getInstance()

        // Views
        val email =
            findViewById<TextInputEditText>(R.id.edtForgotEmail)

        val sendButton =
            findViewById<MaterialButton>(R.id.btnSendReset)

        val backButton =
            findViewById<TextView>(R.id.txtBack)

        val backToLogin =
            findViewById<TextView>(R.id.txtBackToLogin)


        // SEND RESET LINK
        sendButton.setOnClickListener {

            val emailText =
                email.text.toString().trim()

            when {

                emailText.isEmpty() -> {

                    email.error =
                        "Please enter your email"

                    email.requestFocus()
                }

                !Patterns.EMAIL_ADDRESS
                    .matcher(emailText)
                    .matches() -> {

                    email.error =
                        "Please enter a valid email"

                    email.requestFocus()
                }

                else -> {

                    // Disable button while Firebase request is running
                    sendButton.isEnabled = false
                    sendButton.text = "Sending..."

                    auth.sendPasswordResetEmail(emailText)
                        .addOnCompleteListener { task ->

                            // Enable button again
                            sendButton.isEnabled = true
                            sendButton.text = "Send Reset Link"

                            if (task.isSuccessful) {

                                Toast.makeText(
                                    this,
                                    "Password reset link sent. Please check your email.",
                                    Toast.LENGTH_LONG
                                ).show()

                                // Return to Login
                                openLogin()

                            } else {

                                Toast.makeText(
                                    this,
                                    task.exception?.localizedMessage
                                        ?: "Unable to send password reset email.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                }
            }
        }


        // TOP BACK
        backButton.setOnClickListener {
            finish()
        }


        // BACK TO LOGIN
        backToLogin.setOnClickListener {
            openLogin()
        }
    }


    private fun openLogin() {

        val intent =
            Intent(
                this,
                LoginActivity::class.java
            )

        startActivity(intent)

        overridePendingTransition(
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )

        finish()
    }
}