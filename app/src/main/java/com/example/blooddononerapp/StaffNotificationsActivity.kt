
package com.example.blooddononerapp

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StaffNotificationsActivity : AppCompatActivity() {

    private data class Notice(
        val id: String,
        val title: String,
        val message: String,
        val createdAt: Long?,
        val category: String,
        val referenceId: String
    )

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private lateinit var txtMarkAllRead: TextView
    private lateinit var txtSummary: TextView
    private lateinit var txtEmpty: TextView
    private lateinit var container: LinearLayout

    private var profileListener: ListenerRegistration? = null

    private val sourceListeners =
        mutableListOf<ListenerRegistration>()

    private val sourceItems =
        mutableMapOf<String, List<Notice>>()

    private val sourceErrors =
        mutableSetOf<String>()

    private val readIds =
        mutableSetOf<String>()

    private var displayedItems =
        emptyList<Notice>()

    private var staffUid = ""
    private var staffHospital = ""
    private var sourceGeneration = 0
    private var active = false
    private var savingRead = false

    private val stockFields = linkedMapOf(
        "APlus" to "A+",
        "AMinus" to "A-",
        "BPlus" to "B+",
        "BMinus" to "B-",
        "ABPlus" to "AB+",
        "ABMinus" to "AB-",
        "OPlus" to "O+",
        "OMinus" to "O-"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_staff_notifications
        )

        txtMarkAllRead =
            findViewById(R.id.txtMarkAllRead)

        txtSummary =
            findViewById(R.id.txtNotificationSummary)

        txtEmpty =
            findViewById(R.id.txtEmptyNotifications)

        container =
            findViewById(R.id.containerNotifications)

        findViewById<TextView>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        txtMarkAllRead.setOnClickListener {
            markAllRead()
        }

        showMessageState(
            "Verifying staff account..."
        )
    }

    override fun onStart() {
        super.onStart()

        active = true
        listenToStaffProfile()
    }

    override fun onStop() {
        active = false
        sourceGeneration++

        profileListener?.remove()
        profileListener = null

        clearSourceListeners()

        super.onStop()
    }

    // ============================================
    // VERIFY STAFF ACCOUNT
    // ============================================

    private fun listenToStaffProfile() {

        val user = auth.currentUser ?: run {
            showMessageState(
                "Please sign in with a staff account"
            )
            return
        }

        staffUid = user.uid

        profileListener?.remove()

        profileListener = db
            .collection("users")
            .document(user.uid)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { doc, error ->

                if (!activeScreen()) {
                    return@addSnapshotListener
                }

                if (error != null) {
                    blockAccess(
                        "Staff profile unavailable: " +
                                error.localizedMessage
                    )
                    return@addSnapshotListener
                }

                if (
                    doc == null ||
                    doc.metadata.isFromCache
                ) {
                    return@addSnapshotListener
                }

                val valid =
                    doc.exists() &&
                            doc.getString("role") == "STAFF" &&
                            doc.getString("status") == "Active"

                val assigned =
                    doc.getString("hospital")
                        .orEmpty()
                        .trim()

                if (!valid || assigned.isBlank()) {
                    blockAccess(
                        "Active STAFF account and hospital required"
                    )
                    return@addSnapshotListener
                }

                // Read states are saved in staff's own
                // notificationSettings map.
                val settings =
                    doc.get("notificationSettings")
                            as? Map<*, *>

                val markers =
                    settings?.get("staffReadMarkers")
                            as? Map<*, *>

                readIds.clear()

                markers?.forEach { (key, value) ->
                    if (
                        key is String &&
                        value == true
                    ) {
                        readIds.add(key)
                    }
                }

                if (
                    staffHospital != assigned ||
                    sourceListeners.isEmpty()
                ) {
                    staffHospital = assigned
                    subscribeHospitalSources(assigned)
                } else {
                    render()
                }
            }
    }

    private fun blockAccess(message: String) {
        sourceGeneration++

        clearSourceListeners()
        sourceItems.clear()

        staffHospital = ""
        displayedItems = emptyList()

        container.removeAllViews()
        showMessageState(message)
    }

    private fun clearSourceListeners() {

        sourceListeners.forEach {
            it.remove()
        }

        sourceListeners.clear()
    }

    // ============================================
    // FIREBASE REALTIME NOTIFICATIONS
    // ============================================

    private fun subscribeHospitalSources(
        hospital: String
    ) {

        clearSourceListeners()
        sourceItems.clear()
        sourceErrors.clear()

        val generation = ++sourceGeneration

        showMessageState(
            "Loading notifications for $hospital..."
        )

        // ----------------------------------------
        // EMERGENCY REQUESTS
        // ----------------------------------------

        sourceListeners.add(

            db.collection("emergencyRequests")
                .whereEqualTo("hospital", hospital)
                .addSnapshotListener(
                    MetadataChanges.INCLUDE
                ) { snapshot, error ->

                    if (
                        !validResult(hospital, generation)
                    ) {
                        return@addSnapshotListener
                    }

                    if (error != null) {
                        sourceFailed(
                            "requests",
                            error.localizedMessage
                        )
                        return@addSnapshotListener
                    }

                    if (
                        snapshot == null ||
                        snapshot.metadata.isFromCache
                    ) {
                        return@addSnapshotListener
                    }

                    val items =
                        snapshot.documents.map { doc ->

                            val group =
                                doc.getString("bloodGroup")
                                    .orEmpty()

                            val status =
                                doc.getString("status")
                                    ?: "Pending"

                            val units =
                                doc.get("units")
                                    ?.toString()
                                    .orEmpty()

                            Notice(
                                id = "request_${doc.id}",
                                title = "Emergency Blood Request",
                                message =
                                    "$group • $units • $status",
                                createdAt =
                                    doc.getTimestamp("createdAt")
                                        ?.toDate()
                                        ?.time,
                                category = "request",
                                referenceId = doc.id
                            )
                        }

                    updateSource("requests", items)
                }
        )

        // ----------------------------------------
        // DONATION APPOINTMENTS
        // ----------------------------------------

        sourceListeners.add(

            db.collection("donationAppointments")
                .whereEqualTo("hospital", hospital)
                .addSnapshotListener(
                    MetadataChanges.INCLUDE
                ) { snapshot, error ->

                    if (
                        !validResult(hospital, generation)
                    ) {
                        return@addSnapshotListener
                    }

                    if (error != null) {
                        sourceFailed(
                            "appointments",
                            error.localizedMessage
                        )
                        return@addSnapshotListener
                    }

                    if (
                        snapshot == null ||
                        snapshot.metadata.isFromCache
                    ) {
                        return@addSnapshotListener
                    }

                    val items =
                        snapshot.documents.map { doc ->

                            val donor =
                                doc.getString("donorName")
                                    ?: "Donor"

                            val date =
                                doc.getString("donationDate")
                                    .orEmpty()

                            val status =
                                doc.getString("status")
                                    ?: "Pending"

                            Notice(
                                id = "appointment_${doc.id}",
                                title = "Donation Appointment",
                                message =
                                    "$donor • $date • $status",
                                createdAt =
                                    doc.getTimestamp("createdAt")
                                        ?.toDate()
                                        ?.time,
                                category = "appointment",
                                referenceId = doc.id
                            )
                        }

                    updateSource("appointments", items)
                }
        )

        // ----------------------------------------
        // COMPLETED DONATION RECORDS
        // ----------------------------------------

        sourceListeners.add(

            db.collection("donationRecords")
                .whereEqualTo("hospital", hospital)
                .addSnapshotListener(
                    MetadataChanges.INCLUDE
                ) { snapshot, error ->

                    if (
                        !validResult(hospital, generation)
                    ) {
                        return@addSnapshotListener
                    }

                    if (error != null) {
                        sourceFailed(
                            "donations",
                            error.localizedMessage
                        )
                        return@addSnapshotListener
                    }

                    if (
                        snapshot == null ||
                        snapshot.metadata.isFromCache
                    ) {
                        return@addSnapshotListener
                    }

                    val items =
                        snapshot.documents.map { doc ->

                            val donor =
                                doc.getString("donorName")
                                    ?: "Donor"

                            val group =
                                doc.getString("bloodGroup")
                                    .orEmpty()

                            val status =
                                doc.getString("status")
                                    ?: "Recorded"

                            Notice(
                                id = "donation_${doc.id}",
                                title = "Donation Recorded",
                                message =
                                    "$donor • $group • $status",
                                createdAt =
                                    doc.getTimestamp("createdAt")
                                        ?.toDate()
                                        ?.time,
                                category = "donation",
                                referenceId = doc.id
                            )
                        }

                    updateSource("donations", items)
                }
        )

        // ----------------------------------------
        // LOW STOCK NOTIFICATIONS
        // ----------------------------------------

        sourceListeners.add(

            db.collection("hospitals")
                .whereEqualTo("name", hospital)
                .limit(2)
                .addSnapshotListener(
                    MetadataChanges.INCLUDE
                ) { snapshot, error ->

                    if (
                        !validResult(hospital, generation)
                    ) {
                        return@addSnapshotListener
                    }

                    if (error != null) {
                        sourceFailed(
                            "stock",
                            error.localizedMessage
                        )
                        return@addSnapshotListener
                    }

                    if (
                        snapshot == null ||
                        snapshot.metadata.isFromCache
                    ) {
                        return@addSnapshotListener
                    }

                    if (snapshot.size() != 1) {
                        sourceFailed(
                            "stock",
                            "Hospital inventory unavailable"
                        )
                        return@addSnapshotListener
                    }

                    val doc =
                        snapshot.documents.first()

                    if (
                        !doc.getString("status")
                            .equals("Active", true)
                    ) {
                        updateSource(
                            "stock",
                            emptyList()
                        )
                        return@addSnapshotListener
                    }

                    val stock =
                        doc.get("bloodStock")
                                as? Map<*, *>

                    if (stock == null) {
                        sourceFailed(
                            "stock",
                            "Hospital stock map missing"
                        )
                        return@addSnapshotListener
                    }

                    val items =
                        mutableListOf<Notice>()

                    for ((key, group) in stockFields) {

                        val count =
                            (stock[key] as? Number)
                                ?.toLong()
                                ?: continue

                        if (count in 0L..2L) {

                            items.add(

                                Notice(
                                    id =
                                        "stock_${doc.id}_$key",

                                    title =
                                        if (count == 0L) {
                                            "Out of Stock: $group"
                                        } else {
                                            "Low Blood Stock: $group"
                                        },

                                    message =
                                        "$group has $count available units at $hospital",

                                    createdAt =
                                        doc.getTimestamp("updatedAt")
                                            ?.toDate()
                                            ?.time,

                                    category = "stock",

                                    referenceId = doc.id
                                )
                            )
                        }
                    }

                    updateSource("stock", items)
                }
        )
    }

    private fun sourceFailed(
        source: String,
        message: String?
    ) {
        sourceErrors.add(source)
        sourceItems[source] = emptyList()
        render()
    }

    private fun updateSource(
        source: String,
        items: List<Notice>
    ) {
        sourceErrors.remove(source)
        sourceItems[source] = items
        render()
    }

    // ============================================
    // RENDER NOTIFICATIONS
    // ============================================

    private fun render() {

        if (
            !activeScreen() ||
            staffHospital.isBlank()
        ) {
            return
        }

        displayedItems =
            sourceItems.values.flatten()
                .sortedWith(
                    compareByDescending<Notice> {
                        it.createdAt ?: 0L
                    }.thenBy {
                        it.id
                    }
                )
                .take(100)

        container.removeAllViews()

        for (item in displayedItems) {
            addNoticeCard(item)
        }

        val unread =
            displayedItems.count {
                it.id !in readIds
            }

        val loading =
            sourceItems.size < 4

        val partial =
            sourceErrors.isNotEmpty()

        txtSummary.text = when {

            partial ->
                "${displayedItems.size} notifications • " +
                        "$unread unread • Some data unavailable"

            loading ->
                "Syncing hospital notifications..."

            else ->
                "${displayedItems.size} notifications • " +
                        "$unread unread"
        }

        txtEmpty.visibility =
            if (displayedItems.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }

        txtEmpty.text = when {

            partial ->
                "Some Firebase notifications could not be loaded. " +
                        "Check permissions and connection."

            loading ->
                "Loading notifications..."

            else ->
                "No notifications for $staffHospital"
        }

        txtMarkAllRead.isEnabled =
            unread > 0 && !savingRead

        txtMarkAllRead.alpha =
            if (txtMarkAllRead.isEnabled) {
                1f
            } else {
                0.45f
            }
    }

    // ============================================
    // PROFESSIONAL NOTIFICATION CARDS
    // ============================================

    private fun addNoticeCard(item: Notice) {

        val unread =
            item.id !in readIds

        val card =
            MaterialCardView(this).apply {

                radius = dp(16).toFloat()
                cardElevation = 0f

                strokeWidth = dp(1)

                strokeColor =
                    Color.parseColor(
                        if (unread) {
                            "#F8CBD1"
                        } else {
                            "#EBEBEB"
                        }
                    )

                setCardBackgroundColor(
                    Color.parseColor(
                        if (unread) {
                            "#FFF1F3"
                        } else {
                            "#FFFFFF"
                        }
                    )
                )

                isClickable = true
                isFocusable = true
            }

        val cardParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }

        val row = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            setPadding(
                dp(14),
                dp(16),
                dp(14),
                dp(16)
            )
        }

        val icon = styledText(
            when (item.category) {
                "request" -> "!"
                "appointment" -> "A"
                "donation" -> "+"
                else -> "!"
            },
            18f,
            "#EF2637",
            true
        ).apply {

            gravity = Gravity.CENTER

            background =
                GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(24).toFloat()
                }
        }

        row.addView(
            icon,
            LinearLayout.LayoutParams(
                dp(40),
                dp(40)
            )
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val contentParams =
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dp(12)
            }

        content.addView(
            styledText(
                item.title,
                14f,
                "#222222",
                true
            )
        )

        content.addView(
            styledText(
                item.message,
                12f,
                "#666666"
            ).apply {
                setPadding(0, dp(4), 0, 0)
            }
        )

        content.addView(
            styledText(
                timeLabel(item.createdAt),
                10f,
                "#929292"
            ).apply {
                setPadding(0, dp(7), 0, 0)
            }
        )

        row.addView(
            content,
            contentParams
        )

        if (unread) {
            row.addView(
                styledText(
                    "●",
                    14f,
                    "#EF2637"
                ),
                LinearLayout.LayoutParams(
                    dp(18),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        card.addView(row)

        card.setOnClickListener {

            if (unread) {
                markOneRead(item.id)
            }

            openNotification(item)
        }

        container.addView(
            card,
            cardParams
        )
    }

    private fun styledText(
        text: String,
        size: Float,
        color: String,
        bold: Boolean = false
    ): TextView {

        return TextView(this).apply {

            this.text = text

            setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                size
            )

            setTextColor(
                Color.parseColor(color)
            )

            if (bold) {
                typeface = Typeface.DEFAULT_BOLD
            }
        }
    }

    private fun timeLabel(time: Long?): String {

        if (time == null) {
            return "Hospital notification"
        }

        return SimpleDateFormat(
            "dd MMM yyyy • hh:mm a",
            Locale.getDefault()
        ).format(Date(time))
    }

    // ============================================
    // SAVE READ STATUS TO FIREBASE
    // ============================================

    private fun markOneRead(id: String) {

        if (
            staffUid.isBlank() ||
            id in readIds
        ) {
            return
        }

        readIds.add(id)
        render()

        db.collection("users")
            .document(staffUid)
            .update(
                mapOf(
                    "notificationSettings.staffReadMarkers.$id"
                            to true,

                    "notificationSettingsUpdatedAt"
                            to FieldValue.serverTimestamp()
                )
            )
            .addOnFailureListener { error ->

                readIds.remove(id)

                if (activeScreen()) {

                    render()

                    toast(
                        "Could not save read status: " +
                                error.localizedMessage
                    )
                }
            }
    }

    private fun markAllRead() {

        if (
            savingRead ||
            staffUid.isBlank()
        ) {
            return
        }

        val unread =
            displayedItems.filter {
                it.id !in readIds
            }

        if (unread.isEmpty()) {
            return
        }

        savingRead = true
        render()

        val changes =
            mutableMapOf<String, Any>(
                "notificationSettingsUpdatedAt"
                        to FieldValue.serverTimestamp()
            )

        for (item in unread) {

            changes[
                "notificationSettings.staffReadMarkers.${item.id}"
            ] = true
        }

        db.collection("users")
            .document(staffUid)
            .update(changes)
            .addOnSuccessListener {

                savingRead = false

                if (activeScreen()) {

                    readIds.addAll(
                        unread.map { it.id }
                    )

                    render()

                    toast(
                        "All visible notifications marked as read"
                    )
                }
            }
            .addOnFailureListener { error ->

                savingRead = false

                if (activeScreen()) {

                    render()

                    toast(
                        "Mark all read failed: " +
                                error.localizedMessage
                    )
                }
            }
    }

    // ============================================
    // NOTIFICATION NAVIGATION
    // ============================================

    private fun openNotification(item: Notice) {

        val destination = when (item.category) {

            "request" ->
                StaffRequestDetailsActivity::class.java

            "appointment" ->
                StaffAppointmentDetailsActivity::class.java

            "donation" ->
                StaffDonorRecordsActivity::class.java

            else ->
                StaffBloodInventoryActivity::class.java
        }

        startActivity(
            Intent(this, destination).apply {

                if (item.category == "request") {

                    putExtra(
                        "REQUEST_DOCUMENT_ID",
                        item.referenceId
                    )

                } else if (
                    item.category == "appointment"
                ) {

                    putExtra(
                        "DOCUMENT_ID",
                        item.referenceId
                    )
                }
            }
        )
    }

    // ============================================
    // HELPERS
    // ============================================

    private fun showMessageState(
        message: String
    ) {

        txtSummary.text = message
        txtEmpty.text = message

        txtEmpty.visibility = View.VISIBLE

        container.removeAllViews()

        txtMarkAllRead.isEnabled = false
        txtMarkAllRead.alpha = 0.45f
    }

    private fun validResult(
        hospital: String,
        generation: Int
    ): Boolean {

        return activeScreen() &&
                staffHospital == hospital &&
                generation == sourceGeneration
    }

    private fun activeScreen(): Boolean {

        return active &&
                !isFinishing &&
                !isDestroyed
    }

    private fun dp(value: Int): Int {

        return (
                value * resources.displayMetrics.density +
                        0.5f
                ).toInt()
    }

    private fun toast(message: String) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
