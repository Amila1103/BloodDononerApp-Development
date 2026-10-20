
package com.example.blooddononerapp

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class HelpSupportActivity : AppCompatActivity() {

    private lateinit var searchHelp: EditText
    private lateinit var sectionLearn: LinearLayout
    private lateinit var sectionRequest: LinearLayout
    private lateinit var sectionHelp: LinearLayout
    private lateinit var emptyMessage: TextView

    private data class Topic(
        val card: MaterialCardView,
        val title: String,
        val keywords: String,
        val description: String
    )

    private val topics = mutableListOf<Topic>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help_support)

        searchHelp = findViewById(R.id.searchHelp)
        sectionLearn = findViewById(R.id.sectionLearn)
        sectionRequest = findViewById(R.id.sectionRequest)
        sectionHelp = findViewById(R.id.sectionHelp)

        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        registerTopics()
        addEmptySearchMessage()

        findViewById<MaterialButton>(R.id.btnEmergencyHelp)
            .setOnClickListener { showEmergencyHelp() }

        searchHelp.addTextChangedListener { editable ->
            filterTopics(editable?.toString().orEmpty())
        }

        filterTopics(searchHelp.text.toString())
    }

    private fun registerTopics() {
        register(
            R.id.cardAbout,
            "About Blood Donation",
            "blood donation donate donor basics learn why transfusion",
            """
                Blood donation is the voluntary collection of blood or blood components for use in patient care.

                Donated blood may support treatment in emergencies, surgeries and other healthcare situations.

                Blood donation centers screen donors and collect and handle blood according to their clinical procedures.

                This app can help coordinate donations, but it cannot determine whether somebody is medically eligible to donate.

                For personal medical advice, contact a qualified healthcare professional.
            """.trimIndent()
        )

        register(
            R.id.cardEligibility,
            "Eligibility Criteria",
            "eligible eligibility health medicine medication sickness illness screening defer approved",
            """
                Your eligibility to donate must be assessed by qualified staff at a donation center.

                You may be asked about illness, medication, recent treatment and previous donations. Provide accurate answers.

                The app's health questions are preliminary self-reports, not medical clearance.

                A booking marked Pending, Checked In or Eligible in the app should not replace the center's clinical checks.

                If you are feeling unwell or have concerns, contact the center before visiting.
            """.trimIndent()
        )

        register(
            R.id.cardDonationProcess,
            "Donation Process",
            "appointment booking pending donation process date time hospital slot confirmation",
            """
                1. Open Blood Bank Availability.

                2. Choose a hospital and tap Donate Blood.

                3. Select your registered blood group, preferred future date and time.

                4. Answer the preliminary eligibility questions truthfully.

                5. Submit your booking request.

                6. Check My Requests → Donations for the latest status.

                Important: Pending means your request was saved. It does NOT guarantee a hospital slot or permission to donate. Confirm arrangements with the hospital before travelling.

                7. Hospital staff will complete check-in, clinical screening and the donation record where appropriate.

                8. Completed donations can be viewed in Donation History after staff record them.
            """.trimIndent()
        )

        register(
            R.id.cardBloodGroup,
            "Blood Group Guide",
            "blood group a b ab o positive negative plus minus compatibility",
            """
                The app displays eight common ABO/Rh blood groups:

                A+, A−, B+, B−, AB+, AB−, O+, O−.

                Your registered blood group is used for donor searches and donation appointment details.

                Never guess your blood group or rely on a match in the app to determine transfusion compatibility.

                Hospitals must complete their own compatibility checks before transfusion.

                If your saved blood group is incorrect, request a verified correction through the appropriate healthcare process.
            """.trimIndent()
        )

        register(
            R.id.cardEmergencyRequest,
            "Emergency Blood Requests",
            "emergency urgent request recipient patient units critical contact hospital pending cancel",
            """
                1. Open Emergency Blood Request from Home.

                2. Select the required blood group and exact number of units.

                3. Select the location and active hospital.

                4. Enter a future required date and time, valid contacts, urgency and any relevant notes.

                5. Review the information and submit.

                The request starts with Pending status. You can follow it from My Requests → Blood Requests.

                Cancellation is available only while a request remains Pending. Once hospital staff begin processing it, contact the hospital about changes.

                Submitting through the app does not guarantee blood availability. For an immediate medical emergency, contact a hospital or emergency medical service directly.
            """.trimIndent()
        )

        register(
            R.id.cardFindHelp,
            "Finding Donors & Blood Banks",
            "find search donors available availability area district distance nearby location privacy contact phone bank inventory stock",
            """
                Find Donor:

                Choose a blood group and an area or district, or use your saved profile location.

                Search results use profile area/district information. The app does not calculate exact GPS distances.

                Donors appear according to their saved availability and contact/location consent preferences. Available for contact does NOT mean medically cleared to donate.

                Donors may turn off contact or location sharing in Privacy Settings.

                Blood Bank Availability:

                Browse active hospitals, use My District or All Hospitals and filter by recorded blood stock.

                Recorded stock and donor acceptance should be confirmed directly with the hospital before relying on them.
            """.trimIndent()
        )

        register(
            R.id.cardFaq,
            "Frequently Asked Questions",
            "faq frequently asked questions help status pending complete cancelled mark read notifications",
            """
                Where do I check a blood request?
                Open My Requests → Blood Requests.

                Where do I check my donation appointment?
                Open My Requests → Donations.

                Does Pending mean a donation slot is confirmed?
                No. Confirm arrangements with the hospital.

                Can I cancel a blood request?
                Yes, while its status is Pending. If processing has started, contact the hospital.

                Can I cancel a donation appointment?
                Only while it is Pending and you have not checked in.

                Why can't I see a donor?
                Their availability, location, contact consent or profile details may not meet the current search filters.

                Does Available mean a donor is medically eligible?
                No. Eligibility requires clinical screening.

                Why don't notification settings trigger phone alerts?
                Saving preferences alone does not enable push notification delivery.
            """.trimIndent()
        )

        register(
            R.id.cardPrivacy,
            "Privacy & Safety",
            "privacy safety consent permission phone contact share location data settings firebase",
            """
                Open Profile → Privacy Settings to manage availability, contact and location sharing preferences.

                Direct phone contact requires donor permission in the app. Avoid including private medical or contact information in public request descriptions.

                Privacy controls in the current app also require appropriate Firestore security rules and restricted data storage to prevent unauthorized access. A switch alone cannot guarantee that stored information is private.

                Never share passwords or verification codes with other users.

                Do not use the app instead of urgent professional medical care.
            """.trimIndent()
        )

        register(
            R.id.cardContact,
            "Contact Us",
            "contact support help team technical problem administrator email feedback issue login",
            """
                Need technical help?

                For login, profile, booking, donor search or notification issues, contact the verified application administrator or your project's designated support contact.

                No verified public support email address is configured in this app version. Do not send personal data to an unverified address.

                When reporting a technical problem, describe the screen, what you tapped and the error shown. Never send passwords, verification codes or unnecessary medical details.

                For hospital bookings or blood availability, contact the selected hospital directly.

                This support information does not provide emergency medical assistance.
            """.trimIndent()
        )
    }

    private fun register(
        id: Int,
        title: String,
        keywords: String,
        description: String
    ) {
        val card = findViewById<MaterialCardView>(id)

        card.isClickable = true
        card.isFocusable = true

        card.setOnClickListener {
            showHelpDialog(title, description)
        }

        topics.add(
            Topic(card, title, keywords, description)
        )
    }

    // ==========================================
    // EMPTY SEARCH STATE
    // ==========================================

    private fun addEmptySearchMessage() {

        val parent =
            sectionHelp.parent as? LinearLayout
                ?: return

        val padding =
            (16 * resources.displayMetrics.density)
                .toInt()

        emptyMessage = TextView(this).apply {

            text =
                "No help topics found. Try a different keyword."

            gravity = Gravity.CENTER
            textSize = 13f

            setTextColor(
                Color.parseColor("#777777")
            )

            setPadding(
                padding,
                padding * 2,
                padding,
                padding * 2
            )

            visibility = View.GONE
        }

        parent.addView(
            emptyMessage,
            parent.indexOfChild(sectionHelp) + 1
        )
    }

    // ==========================================
    // SEARCH TOPICS
    // ==========================================

    private fun filterTopics(input: String) {

        val terms =
            input.trim()
                .lowercase(Locale.ROOT)
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

        var matches = 0

        topics.forEach { topic ->

            val searchable = listOf(
                topic.title,
                topic.keywords,
                topic.description
            )
                .joinToString(" ")
                .lowercase(Locale.ROOT)

            val visible = terms.all { word ->
                searchable.contains(word)
            }

            topic.card.visibility =
                if (visible) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            if (visible) {
                matches++
            }
        }

        sectionLearn.visibility =
            if (hasVisibleCards(sectionLearn)) {
                View.VISIBLE
            } else {
                View.GONE
            }

        sectionRequest.visibility =
            if (hasVisibleCards(sectionRequest)) {
                View.VISIBLE
            } else {
                View.GONE
            }

        sectionHelp.visibility =
            if (hasVisibleCards(sectionHelp)) {
                View.VISIBLE
            } else {
                View.GONE
            }

        emptyMessage.visibility =
            if (matches == 0) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun hasVisibleCards(
        section: ViewGroup
    ): Boolean {

        for (i in 0 until section.childCount) {

            if (
                section.getChildAt(i) is MaterialCardView &&
                section.getChildAt(i).visibility == View.VISIBLE
            ) {
                return true
            }
        }

        return false
    }

    // ==========================================
    // HELP DETAILS DIALOG
    // ==========================================

    private fun showHelpDialog(
        title: String,
        content: String
    ) {

        if (isFinishing || isDestroyed) {
            return
        }

        val dialog = Dialog(this)

        dialog.requestWindowFeature(
            Window.FEATURE_NO_TITLE
        )

        dialog.setContentView(
            R.layout.dialog_help_details
        )

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

        dialog.findViewById<TextView>(
            R.id.txtHelpDialogTitle
        ).text = title

        dialog.findViewById<TextView>(
            R.id.txtHelpDialogContent
        ).text = content

        dialog.findViewById<MaterialButton>(
            R.id.btnCloseHelp
        ).setOnClickListener {
            dialog.dismiss()
        }

        // Keep long articles scrollable and
        // the Close button visible.
        val contentView =
            dialog.findViewById<TextView>(
                R.id.txtHelpDialogContent
            )

        val scroll =
            contentView.parent as? ScrollView

        val scrollLayout =
            scroll?.layoutParams
                    as? LinearLayout.LayoutParams

        if (scrollLayout != null) {

            scrollLayout.height = 0
            scrollLayout.weight = 1f

            scroll.layoutParams = scrollLayout
        }

        dialog.show()

        dialog.window?.let { window ->

            window.setLayout(
                (
                        resources.displayMetrics.widthPixels *
                                0.90f
                        ).toInt(),
                (
                        resources.displayMetrics.heightPixels *
                                0.80f
                        ).toInt()
            )

            window.setBackgroundDrawable(
                ColorDrawable(Color.TRANSPARENT)
            )

            window.addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )

            val params = window.attributes

            params.dimAmount = 0.5f
            window.attributes = params
        }
    }

    // ==========================================
    // EMERGENCY SUPPORT
    // ==========================================

    private fun showEmergencyHelp() {

        showHelpDialog(
            "Emergency Help",
            """
                For an immediate medical emergency, contact the treating hospital or an official emergency medical service directly.

                The Blood Donor app can help coordinate a request, but it cannot guarantee a response, suitable blood or clinical treatment.

                If blood is needed urgently:

                1. Contact the treating hospital.
                2. Have the hospital confirm the blood requirements.
                3. Create an Emergency Blood Request if the hospital advises it.
                4. Keep your contact number reachable and follow the hospital's instructions.

                Do not wait for an in-app notification instead of seeking emergency care.
            """.trimIndent()
        )
    }
}
