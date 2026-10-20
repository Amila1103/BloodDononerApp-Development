
package com.example.blooddononerapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source

class PrivacySettingsActivity : AppCompatActivity() {

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private val handler =
        Handler(Looper.getMainLooper())

    private lateinit var switchPhone: SwitchMaterial
    private lateinit var switchLocation: SwitchMaterial
    private lateinit var switchAvailable: SwitchMaterial
    private lateinit var switchRequesterContact: SwitchMaterial
    private lateinit var switchApproximateArea: SwitchMaterial

    private var availabilityDescription: TextView? = null

    private data class PrivacyState(
        val showPhone: Boolean = false,
        val shareLocation: Boolean = false,
        val available: Boolean = false,
        val allowContact: Boolean = false,
        val approximateArea: Boolean = false
    )

    private var savedState = PrivacyState()

    private var profileListener: ListenerRegistration? = null
    private var donationListener: ListenerRegistration? = null

    private var active = false
    private var generation = 0

    private var verifiedUid = ""
    private var profileLoaded = false
    private var donationsLoaded = false

    private var saving = false
    private var forcingOff = false
    private var ignoreChanges = false

    private var cooldown = DonationCooldown.Result(
        DonationCooldown.State.UNVERIFIED
    )

    private var completedDocuments:
            List<DocumentSnapshot> = emptyList()

    private val dailyRefresh = object : Runnable {

        override fun run() {

            if (!active) return

            if (donationsLoaded) {

                cooldown = DonationCooldown.calculate(
                    completedDocuments
                )

                applyState()

                enforceCooldown(
                    verifiedUid,
                    generation
                )
            }

            scheduleDailyRefresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_privacy_settings
        )

        switchPhone =
            findViewById(R.id.switchPhone)

        switchLocation =
            findViewById(R.id.switchLocation)

        switchAvailable =
            findViewById(R.id.switchAvailable)

        switchRequesterContact =
            findViewById(R.id.switchRequesterContact)

        switchApproximateArea =
            findViewById(R.id.switchApproximateArea)

        findViewById<TextView>(
            R.id.btnBack
        ).setOnClickListener {
            finish()
        }

        availabilityDescription = findText(
            findViewById(android.R.id.content),
            "Show your profile in donor searches"
        )

        // Show the lock reason when the availability
        // row is tapped while its switch is disabled.
        val availabilityRow =
            switchAvailable.parent as? ViewGroup

        availabilityRow?.setOnClickListener {

            if (!switchAvailable.isEnabled) {
                explainAvailabilityLock()
            }
        }

        bindSwitch(switchPhone, "showPhone")
        bindSwitch(switchLocation, "shareLocation")

        bindSwitch(
            switchAvailable,
            "available"
        )

        bindSwitch(
            switchRequesterContact,
            "allowContact"
        )

        bindSwitch(
            switchApproximateArea,
            "approximateArea"
        )

        applyState()
    }

    override fun onStart() {
        super.onStart()

        active = true
        connectFirebase()
        scheduleDailyRefresh()
    }

    override fun onStop() {

        active = false
        generation++

        handler.removeCallbacks(dailyRefresh)

        profileListener?.remove()
        donationListener?.remove()

        profileListener = null
        donationListener = null

        verifiedUid = ""

        profileLoaded = false
        donationsLoaded = false

        saving = false
        forcingOff = false

        super.onStop()
    }

    private fun current(
        token: Int,
        uid: String
    ): Boolean {

        return active &&
                token == generation &&
                auth.currentUser?.uid == uid &&
                !isFinishing &&
                !isDestroyed
    }

    private fun scheduleDailyRefresh() {

        handler.removeCallbacks(dailyRefresh)

        if (active) {

            handler.postDelayed(
                dailyRefresh,
                DonationCooldown
                    .millisUntilNextSriLankaDay()
            )
        }
    }

    // =========================================
    // CONNECT REALTIME FIREBASE DATA
    // =========================================

    private fun connectFirebase() {

        val user = auth.currentUser ?: run {

            verifiedUid = ""
            profileLoaded = false
            donationsLoaded = false

            applyState()

            feedback(
                "Please sign in to change privacy settings"
            )

            return
        }

        val uid = user.uid
        val token = ++generation

        profileListener?.remove()
        donationListener?.remove()

        verifiedUid = ""

        profileLoaded = false
        donationsLoaded = false

        saving = false
        forcingOff = false

        cooldown = DonationCooldown.Result(
            DonationCooldown.State.UNVERIFIED
        )

        completedDocuments = emptyList()

        applyState()

        profileListener = db.collection("users")
            .document(uid)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { document, error ->

                if (!current(token, uid)) {
                    return@addSnapshotListener
                }

                if (error != null) {

                    verifiedUid = ""
                    profileLoaded = false

                    applyState()

                    feedback(
                        "Cannot load privacy settings: " +
                                (error.localizedMessage
                                    ?: "Firebase error")
                    )

                    return@addSnapshotListener
                }

                if (
                    document == null ||
                    document.metadata.isFromCache ||
                    document.metadata.hasPendingWrites()
                ) {
                    return@addSnapshotListener
                }

                val status =
                    document.getString("status")
                        ?.ifBlank { "Active" }
                        ?: "Active"

                val validUser =
                    document.exists() &&
                            document.getString("role")
                                .equals("USER", true) &&
                            status.equals("Active", true)

                if (!validUser) {

                    verifiedUid = ""
                    profileLoaded = false

                    applyState()
                    return@addSnapshotListener
                }

                verifiedUid = uid
                profileLoaded = true

                val approximate =
                    document.getBoolean(
                        "shareApproximateLocation"
                    ) == true

                val shareLocation =
                    document.getBoolean(
                        "notificationSettings.donorPrivacy.shareLocation"
                    ) ?: approximate

                savedState = PrivacyState(

                    showPhone =
                        document.getBoolean(
                            "notificationSettings.donorPrivacy.showPhone"
                        ) == true,

                    shareLocation = shareLocation,

                    available =
                        document.getBoolean(
                            "availableToDonate"
                        ) == true,

                    allowContact =
                        document.getBoolean(
                            "notificationSettings.donorPrivacy.allowRequesterContact"
                        ) == true,

                    approximateArea = approximate
                )

                if (!saving) {
                    applyState()
                }

                enforceCooldown(uid, token)
            }

        donationListener = db
            .collection("donationRecords")
            .whereEqualTo("userId", uid)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { snapshot, error ->

                if (!current(token, uid)) {
                    return@addSnapshotListener
                }

                if (error != null) {

                    donationsLoaded = false

                    cooldown = DonationCooldown.Result(
                        DonationCooldown.State.UNVERIFIED
                    )

                    applyState()

                    feedback(
                        "Cannot verify donation history"
                    )

                    return@addSnapshotListener
                }

                if (
                    snapshot == null ||
                    snapshot.metadata.isFromCache ||
                    snapshot.metadata.hasPendingWrites()
                ) {
                    return@addSnapshotListener
                }

                completedDocuments =
                    snapshot.documents.toList()

                donationsLoaded = true

                cooldown = DonationCooldown.calculate(
                    completedDocuments
                )

                applyState()
                enforceCooldown(uid, token)
            }
    }

    // =========================================
    // SWITCH LISTENERS
    // =========================================

    private fun bindSwitch(
        control: SwitchMaterial,
        field: String
    ) {

        control.setOnCheckedChangeListener { _, checked ->

            if (ignoreChanges) {
                return@setOnCheckedChangeListener
            }

            if (
                !profileLoaded ||
                verifiedUid.isBlank() ||
                saving ||
                forcingOff
            ) {

                applyState()
                return@setOnCheckedChangeListener
            }

            if (checked == currentValue(field)) {
                return@setOnCheckedChangeListener
            }

            if (
                field == "available" &&
                checked
            ) {

                if (
                    !donationsLoaded ||
                    cooldown.blocksAvailability
                ) {

                    applyState()
                    explainAvailabilityLock()

                } else {

                    verifyBeforeEnabling()
                }

                return@setOnCheckedChangeListener
            }

            // Explicit consent before enabling
            // phone sharing or direct contact.
            if (
                checked &&
                (
                        field == "showPhone" ||
                                field == "allowContact"
                        )
            ) {

                applyState()

                MaterialAlertDialogBuilder(this)
                    .setTitle(
                        if (field == "showPhone") {
                            "Allow Phone Number Sharing?"
                        } else {
                            "Allow Requester Contact?"
                        }
                    )
                    .setMessage(
                        "You are allowing other app " +
                                "users to contact you.\n\n" +
                                "This setting controls the app UI. " +
                                "Firebase security must also " +
                                "protect personal data."
                    )
                    .setNegativeButton(
                        "Keep Off",
                        null
                    )
                    .setPositiveButton(
                        "Allow"
                    ) { _, _ ->

                        saveField(field, true)
                    }
                    .show()

            } else {

                saveField(field, checked)
            }
        }
    }

    private fun currentValue(field: String): Boolean {

        return when (field) {

            "showPhone" ->
                savedState.showPhone

            "shareLocation" ->
                savedState.shareLocation

            "available" ->
                savedState.available

            "allowContact" ->
                savedState.allowContact

            "approximateArea" ->
                savedState.approximateArea

            else -> false
        }
    }

    // =========================================
    // UPDATE ALL SWITCHES
    // =========================================

    private fun applyState() {

        ignoreChanges = true

        switchPhone.isChecked =
            savedState.showPhone

        switchLocation.isChecked =
            savedState.shareLocation

        switchRequesterContact.isChecked =
            savedState.allowContact

        switchApproximateArea.isChecked =
            savedState.approximateArea

        val allowAvailability =
            profileLoaded &&
                    donationsLoaded &&
                    cooldown.canTurnOn

        // Never display a locked donor as available.
        switchAvailable.isChecked =
            savedState.available && allowAvailability

        val otherEnabled =
            profileLoaded &&
                    verifiedUid.isNotBlank() &&
                    !saving &&
                    !forcingOff

        listOf(
            switchPhone,
            switchLocation,
            switchRequesterContact,
            switchApproximateArea
        ).forEach { control ->

            control.isEnabled = otherEnabled

            control.alpha =
                if (otherEnabled) 1f else 0.55f
        }

        val availableEnabled =
            otherEnabled && allowAvailability

        switchAvailable.isEnabled =
            availableEnabled

        switchAvailable.alpha =
            if (availableEnabled) 1f else 0.55f

        ignoreChanges = false

        availabilityDescription?.text = when {

            !profileLoaded ->
                "Verifying your profile..."

            !donationsLoaded ->
                "Checking completed donation history..."

            cooldown.state ==
                    DonationCooldown.State.WAITING ->
                "Locked: ${cooldown.daysRemaining} " +
                        "days remaining • " +
                        DonationCooldown.formatDate(
                            cooldown.nextDate
                        )

            cooldown.state ==
                    DonationCooldown.State.UNVERIFIED ->
                "Locked: donation date needs verification"

            savedState.available ->
                "Available for contact • " +
                        "medical screening required"

            else ->
                "You may enable donor contact availability"
        }
    }

    // =========================================
    // RECHECK DONATIONS BEFORE TURNING ON
    // =========================================

    private fun verifyBeforeEnabling() {

        val uid = verifiedUid
        val token = generation

        if (uid.isBlank() || saving) return

        saving = true
        applyState()

        db.collection("donationRecords")
            .whereEqualTo("userId", uid)
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->

                if (!current(token, uid)) {
                    return@addOnSuccessListener
                }

                completedDocuments =
                    snapshot.documents.toList()

                donationsLoaded = true

                cooldown = DonationCooldown.calculate(
                    completedDocuments
                )

                saving = false

                if (cooldown.canTurnOn) {

                    saveField(
                        "available",
                        true
                    )

                } else {

                    applyState()
                    explainAvailabilityLock()
                    enforceCooldown(uid, token)
                }
            }
            .addOnFailureListener { error ->

                if (current(token, uid)) {

                    saving = false
                    applyState()

                    feedback(
                        "Donation history check failed: " +
                                (error.localizedMessage
                                    ?: "Network error")
                    )
                }
            }
    }

    // =========================================
    // AUTO DISABLE WHEN DONOR IS LOCKED
    // =========================================

    private fun enforceCooldown(
        uid: String,
        token: Int
    ) {

        if (
            uid.isBlank() ||
            !current(token, uid) ||
            !profileLoaded ||
            !donationsLoaded ||
            cooldown.canTurnOn ||
            !savedState.available ||
            saving ||
            forcingOff
        ) {
            return
        }

        forcingOff = true
        applyState()

        db.collection("users")
            .document(uid)
            .update("availableToDonate", false)
            .addOnSuccessListener {

                if (current(token, uid)) {

                    forcingOff = false

                    savedState = savedState.copy(
                        available = false
                    )

                    applyState()
                }
            }
            .addOnFailureListener { error ->

                if (current(token, uid)) {

                    forcingOff = false
                    applyState()

                    feedback(
                        "Could not turn off availability: " +
                                (error.localizedMessage
                                    ?: "Permission error")
                    )
                }
            }
    }

    // =========================================
    // FIREBASE PRIVACY SETTINGS SAVE
    // =========================================

    private fun saveField(
        field: String,
        checked: Boolean
    ) {

        val uid = verifiedUid
        val token = generation

        if (
            !profileLoaded ||
            saving ||
            forcingOff ||
            uid.isBlank()
        ) {
            return
        }

        val firebasePath = when (field) {

            "showPhone" ->
                "notificationSettings.donorPrivacy.showPhone"

            "shareLocation" ->
                "notificationSettings.donorPrivacy.shareLocation"

            "available" ->
                "availableToDonate"

            "allowContact" ->
                "notificationSettings.donorPrivacy.allowRequesterContact"

            "approximateArea" ->
                "shareApproximateLocation"

            else -> return
        }

        if (
            field == "available" &&
            checked &&
            (
                    !donationsLoaded ||
                            cooldown.blocksAvailability
                    )
        ) {

            applyState()
            explainAvailabilityLock()
            return
        }

        saving = true
        applyState()

        val reference = db.collection("users")
            .document(uid)

        db.runTransaction { transaction ->

            val profile =
                transaction.get(reference)

            val status =
                profile.getString("status")
                    ?.ifBlank { "Active" }
                    ?: "Active"

            if (
                !profile.exists() ||
                !profile.getString("role")
                    .equals("USER", true) ||
                !status.equals("Active", true)
            ) {

                throw IllegalStateException(
                    "An active USER account is required."
                )
            }

            transaction.update(
                reference,
                mapOf(
                    firebasePath to checked,

                    "updatedAt" to
                            FieldValue.serverTimestamp()
                )
            )

            true
        }
            .addOnSuccessListener {

                if (!current(token, uid)) {
                    return@addOnSuccessListener
                }

                saving = false

                savedState = when (field) {

                    "showPhone" ->
                        savedState.copy(
                            showPhone = checked
                        )

                    "shareLocation" ->
                        savedState.copy(
                            shareLocation = checked
                        )

                    "available" ->
                        savedState.copy(
                            available = checked
                        )

                    "allowContact" ->
                        savedState.copy(
                            allowContact = checked
                        )

                    "approximateArea" ->
                        savedState.copy(
                            approximateArea = checked
                        )

                    else -> savedState
                }

                applyState()

                feedback(
                    "Privacy preference saved to Firebase"
                )
            }
            .addOnFailureListener { error ->

                if (!current(token, uid)) {
                    return@addOnFailureListener
                }

                saving = false
                applyState()

                feedback(
                    "Setting not saved: " +
                            (error.localizedMessage
                                ?: "Firebase error")
                )
            }
    }

    // =========================================
    // MESSAGE HELPERS
    // =========================================

    private fun explainAvailabilityLock() {

        val explanation = when {

            !donationsLoaded ->
                "Completed donation history must " +
                        "be verified online."

            cooldown.state ==
                    DonationCooldown.State.WAITING ->
                "Next possible whole-blood donation: " +
                        DonationCooldown.formatDate(
                            cooldown.nextDate
                        ) +
                        "\n\n${cooldown.daysRemaining} " +
                        "day(s) remaining."

            cooldown.state ==
                    DonationCooldown.State.UNVERIFIED ->
                "A completed donation has no verified " +
                        "date. Please contact hospital staff."

            else ->
                "Your profile or donation history " +
                        "is not verified."
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Donation Interval Lock")
            .setMessage(
                "$explanation\n\n" +
                        "Hospital staff screening is still required."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun findText(
        root: View,
        label: String
    ): TextView? {

        if (
            root is TextView &&
            root.text.toString() == label
        ) {
            return root
        }

        if (root is ViewGroup) {

            for (index in 0 until root.childCount) {

                findText(
                    root.getChildAt(index),
                    label
                )?.let {
                    return it
                }
            }
        }

        return null
    }

    private fun feedback(message: String) {

        if (
            active &&
            !isFinishing &&
            !isDestroyed
        ) {

            Snackbar.make(
                switchAvailable,
                message,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }
}
