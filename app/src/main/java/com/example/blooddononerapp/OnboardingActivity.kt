package com.example.blooddononerapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class OnboardingActivity : AppCompatActivity() {

    private var currentPage = 0

    private lateinit var contentContainer: LinearLayout
    private lateinit var imgOnboarding: ImageView
    private lateinit var txtTitle: TextView
    private lateinit var txtDescription: TextView
    private lateinit var txtSkip: TextView
    private lateinit var btnNext: MaterialButton

    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View


    // Images
    private val images = intArrayOf(
        R.drawable.onboarding_donate,
        R.drawable.onboarding_find,
        R.drawable.onboarding_connect
    )


    // Titles
    private val titles = arrayOf(
        "Donate Blood, Save Lives",
        "Find Blood Donors",
        "Get Connected Easily"
    )


    // Descriptions
    private val descriptions = arrayOf(

        "Give blood and bring hope to someone in need.",

        "Quickly find nearby blood donors when every second matters.",

        "Connect with donors and recipients easily when help is needed."
    )


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_onboarding)


        // Connect XML views

        contentContainer =
            findViewById(R.id.contentContainer)

        imgOnboarding =
            findViewById(R.id.imgOnboarding)

        txtTitle =
            findViewById(R.id.txtTitle)

        txtDescription =
            findViewById(R.id.txtDescription)

        txtSkip =
            findViewById(R.id.txtSkip)

        btnNext =
            findViewById(R.id.btnNext)

        dot1 =
            findViewById(R.id.dot1)

        dot2 =
            findViewById(R.id.dot2)

        dot3 =
            findViewById(R.id.dot3)


        // First page
        updatePage()


        // NEXT
        btnNext.setOnClickListener {

            if (currentPage < 2) {

                animateToNextPage()

            } else {

                openLogin()
            }
        }


        // SKIP
        txtSkip.setOnClickListener {

            openLogin()
        }
    }


    // -------------------------------------------
    // Smooth transition
    // -------------------------------------------

    private fun animateToNextPage() {

        val smooth =
            AccelerateDecelerateInterpolator()


        // Current page slides left
        contentContainer.animate()

            .alpha(0f)

            .translationX(-60f)

            .setDuration(300)

            .setInterpolator(smooth)

            .withEndAction {

                currentPage++

                updatePage()


                // New page starts from right
                contentContainer.translationX = 60f

                contentContainer.alpha = 0f


                // Slide into center
                contentContainer.animate()

                    .alpha(1f)

                    .translationX(0f)

                    .setDuration(400)

                    .setInterpolator(smooth)

                    .start()
            }

            .start()
    }


    // -------------------------------------------
    // Update screen
    // -------------------------------------------

    private fun updatePage() {

        imgOnboarding.setImageResource(
            images[currentPage]
        )

        txtTitle.text =
            titles[currentPage]

        txtDescription.text =
            descriptions[currentPage]

        updateDots()


        // Last page
        if (currentPage == 2) {

            btnNext.text = "Get Started"

            txtSkip.visibility = View.INVISIBLE

        } else {

            btnNext.text = "Next"

            txtSkip.visibility = View.VISIBLE
        }
    }


    // -------------------------------------------
    // Update dots
    // -------------------------------------------

    private fun updateDots() {

        // Reset
        dot1.setBackgroundResource(
            R.drawable.dot_inactive
        )

        dot2.setBackgroundResource(
            R.drawable.dot_inactive
        )

        dot3.setBackgroundResource(
            R.drawable.dot_inactive
        )


        // Active dot
        when (currentPage) {

            0 ->
                dot1.setBackgroundResource(
                    R.drawable.dot_active
                )

            1 ->
                dot2.setBackgroundResource(
                    R.drawable.dot_active
                )

            2 ->
                dot3.setBackgroundResource(
                    R.drawable.dot_active
                )
        }
    }


    // -------------------------------------------
    // Open Login
    // -------------------------------------------

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