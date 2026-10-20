
package com.example.blooddononerapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class DonationSuccessActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_donation_success)

        findViewById<TextView>(R.id.txtHospital).text =
            intent.getStringExtra("hospital_name")
                .orEmpty()
                .ifBlank { "—" }

        findViewById<TextView>(R.id.txtLocation).text =
            intent.getStringExtra("hospital_location")
                .orEmpty()
                .ifBlank { "—" }

        findViewById<TextView>(R.id.txtBloodGroup).text =
            intent.getStringExtra("blood_group")
                .orEmpty()
                .ifBlank { "—" }

        findViewById<TextView>(R.id.txtDate).text =
            intent.getStringExtra("donation_date")
                .orEmpty()
                .ifBlank { "—" }

        findViewById<TextView>(R.id.txtTime).text =
            intent.getStringExtra("donation_time")
                .orEmpty()
                .ifBlank { "—" }

        // A Pending appointment is not yet confirmed.
        val root =
            findViewById<ViewGroup>(
                android.R.id.content
            )

        findText(
            root,
            "Appointment Confirmed"
        )?.text = "Booking Request Submitted"

        findText(
            root,
            "Thank you for choosing to donate blood. " +
                    "Your decision can help save a life."
        )?.text =
            "Your preferred donation slot was " +
                    "submitted for hospital review."

        findText(
            root,
            "Please arrive at the donation center " +
                    "at your selected time. Final eligibility " +
                    "and donation approval will be confirmed " +
                    "by medical staff."
        )?.text =
            "Status: Pending. Please verify the appointment " +
                    "with your hospital before travelling. " +
                    "Final eligibility is determined by " +
                    "qualified medical staff."

        findViewById<MaterialButton>(
            R.id.btnHome
        ).setOnClickListener {

            val homeIntent = Intent(
                this,
                HomeActivity::class.java
            )

            homeIntent.flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

            startActivity(homeIntent)
            finish()
        }
    }

    private fun findText(
        root: View,
        text: String
    ): TextView? {

        if (
            root is TextView &&
            root.text.toString() == text
        ) {
            return root
        }

        if (root is ViewGroup) {

            for (index in 0 until root.childCount) {

                val match = findText(
                    root.getChildAt(index),
                    text
                )

                if (match != null) {
                    return match
                }
            }
        }

        return null
    }
}
