
package com.example.blooddononerapp

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import java.util.Locale

class NotificationSettingsActivity : AppCompatActivity() {

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private lateinit var switches:
            Map<String, SwitchMaterial>

    private var listener: ListenerRegistration? = null

    private var active = false
    private var verifiedUid = ""
    private var generation = 0

    private var loaded = false
    private var saving = false
    private var ignoreChange = false

    private var savedSettings = mapOf(
        "emergency" to true,
        "nearby" to true,
        "appointments" to true,
        "requestUpdates" to true,
        "eligibility" to true
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_notification_settings
        )

        switches = linkedMapOf(
            "emergency" to
                    findViewById(
                        R.id.switchEmergencyNotifications
                    ),

            "nearby" to
                    findViewById(
                        R.id.switchNearbyNotifications
                    ),

            "appointments" to
                    findViewById(
                        R.id.switchAppointmentReminders
                    ),

            "requestUpdates" to
                    findViewById(
                        R.id.switchRequestUpdates
                    ),

            "eligibility" to
                    findViewById(
                        R.id.switchEligibilityReminders
                    )
        )

        findViewById<TextView>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        bindSwitches()
        addInformationLabel()
        applySettings(false)
    }

    override fun onStart() {
        super.onStart()

        active = true
        connectFirebase()
    }

    override fun onStop() {
        active = false
        generation++

        listener?.remove()
        listener = null

        loaded = false
        verifiedUid = ""
        saving = false

        super.onStop()
    }

    private fun current(
        token: Int,
        uid: String
    ): Boolean {

        return active &&
                generation == token &&
                auth.currentUser?.uid == uid &&
                !isFinishing &&
                !isDestroyed
    }

    // ==========================================
    // SWITCH HANDLERS
    // ==========================================

    private fun bindSwitches() {

        switches.forEach { (field, control) ->

            control.setOnCheckedChangeListener {
                    _, checked ->

                if (ignoreChange) {
                    return@setOnCheckedChangeListener
                }

                if (
                    !loaded ||
                    saving ||
                    verifiedUid.isBlank()
                ) {

                    applySettings(
                        loaded && verifiedUid.isNotBlank()
                    )

                    return@setOnCheckedChangeListener
                }

                if (
                    checked == (savedSettings[field] ?: true)
                ) {
                    return@setOnCheckedChangeListener
                }

                saveSetting(field, checked)
            }
        }
    }

    private fun applySettings(enabled: Boolean) {

        ignoreChange = true

        switches.forEach { (field, control) ->

            control.isChecked =
                savedSettings[field] ?: true

            control.isEnabled =
                enabled && !saving

            control.alpha =
                if (control.isEnabled) {
                    1f
                } else {
                    0.55f
                }
        }

        ignoreChange = false
    }

    private fun addInformationLabel() {

        val content =
            findViewById<ViewGroup>(
                android.R.id.content
            )

        val root =
            content.getChildAt(0) as? LinearLayout
                ?: return

        val padding =
            (18 * resources.displayMetrics.density)
                .toInt()

        val message = TextView(this).apply {

            text =
                "These preferences are saved in Firebase. " +
                        "Actual alerts depend on the app's " +
                        "notification delivery features. " +
                        "These switches do not activate " +
                        "push notifications by themselves."

            textSize = 11f

            setTextColor(
                android.graphics.Color.parseColor(
                    "#777777"
                )
            )

            setPadding(
                padding,
                padding,
                padding,
                padding
            )
        }

        root.addView(message)
    }

    // ==========================================
    // FIREBASE REALTIME SETTINGS
    // ==========================================

    private fun connectFirebase() {

        val user = auth.currentUser ?: run {

            loaded = false
            verifiedUid = ""

            applySettings(false)

            showError(
                "Sign-in Required",
                "Please sign in to change notification settings."
            )

            return
        }

        val token = ++generation

        listener?.remove()
        listener = null

        loaded = false
        saving = false
        verifiedUid = ""

        applySettings(false)

        listener = db.collection("users")
            .document(user.uid)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { document, error ->

                if (!current(token, user.uid)) {
                    return@addSnapshotListener
                }

                if (error != null) {

                    loaded = false
                    verifiedUid = ""

                    applySettings(false)

                    showError(
                        "Notification Settings Unavailable",
                        error.localizedMessage
                            ?: "Unable to load Firebase settings."
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

                val authorized =
                    document.exists() &&
                            document.getString("role")
                                .equals("USER", true) &&
                            status.equals("Active", true)

                if (!authorized) {

                    loaded = false
                    verifiedUid = ""

                    applySettings(false)

                    return@addSnapshotListener
                }

                verifiedUid = user.uid
                loaded = true

                val latest = mutableMapOf<String, Boolean>()

                switches.keys.forEach { field ->

                    // Missing settings retain the
                    // original app's ON default.
                    latest[field] =
                        document.getBoolean(
                            "notificationSettings.$field"
                        ) ?: true
                }

                savedSettings = latest

                if (!saving) {
                    applySettings(true)
                }
            }
    }

    // ==========================================
    // SAVE ONLY THE CHANGED FIELD
    // ==========================================

    private fun saveSetting(
        field: String,
        value: Boolean
    ) {

        if (
            field !in switches.keys ||
            saving ||
            !loaded
        ) {
            return
        }

        val user = auth.currentUser

        if (
            user == null ||
            user.uid != verifiedUid
        ) {

            applySettings(false)

            return
        }

        val token = generation

        saving = true
        applySettings(false)

        val reference =
            db.collection("users")
                .document(user.uid)

        db.runTransaction { transaction ->

            val profile =
                transaction.get(reference)

            val status =
                profile.getString("status")
                    ?.ifBlank { "Active" }
                    ?: "Active"

            val authorized =
                profile.exists() &&
                        profile.getString("role")
                            .equals("USER", true) &&
                        status.equals("Active", true)

            if (!authorized) {

                throw IllegalStateException(
                    "Active USER account required."
                )
            }

            // Dot-path update preserves sibling
            // notification settings, including
            // notificationSettings.donorPrivacy.
            transaction.update(
                reference,
                mapOf(
                    "notificationSettings.$field" to value,

                    "notificationSettingsUpdatedAt" to
                            FieldValue.serverTimestamp()
                )
            )

            true
        }
            .addOnSuccessListener {

                if (!current(token, user.uid)) {
                    return@addOnSuccessListener
                }

                saving = false

                savedSettings =
                    savedSettings.toMutableMap().apply {
                        this[field] = value
                    }

                applySettings(true)

                feedback(
                    "${fieldLabel(field)} saved to Firebase"
                )
            }
            .addOnFailureListener { error ->

                if (!current(token, user.uid)) {
                    return@addOnFailureListener
                }

                saving = false

                // Switch returns to its last
                // confirmed server value.
                applySettings(true)

                showError(
                    "Setting Not Saved",
                    error.localizedMessage
                        ?: "Firebase could not save " +
                        "your notification preference."
                )
            }
    }

    // ==========================================
    // FRIENDLY SETTING NAMES
    // ==========================================

    private fun fieldLabel(field: String): String {

        return when (field) {

            "emergency" ->
                "Emergency notifications"

            "nearby" ->
                "Nearby notifications"

            "appointments" ->
                "Appointment reminders"

            "requestUpdates" ->
                "Request updates"

            "eligibility" ->
                "Eligibility reminders"

            else ->
                field.replaceFirstChar {
                    it.titlecase(Locale.ROOT)
                }
        }
    }

    // ==========================================
    // MESSAGES
    // ==========================================

    private fun feedback(message: String) {

        if (!active || isFinishing || isDestroyed) {
            return
        }

        val anchor =
            switches["emergency"] ?: return

        Snackbar.make(
            anchor,
            message,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun showError(
        title: String,
        message: String
    ) {

        if (!active || isFinishing || isDestroyed) {
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
