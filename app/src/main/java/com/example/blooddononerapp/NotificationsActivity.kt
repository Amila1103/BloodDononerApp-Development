
package com.example.blooddononerapp

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationsActivity : AppCompatActivity() {

    private data class Notice(
        val id: String,
        val userId: String,
        val title: String,
        val message: String,
        val type: String,
        val status: String,
        val group: String,
        val hospital: String,
        val area: String,
        val requestId: String,
        val read: Boolean,
        val createdAt: Long
    )

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var all: TextView
    private lateinit var emergency: TextView
    private lateinit var requests: TextView
    private lateinit var donations: TextView
    private lateinit var markAll: TextView
    private lateinit var empty: TextView
    private lateinit var container: LinearLayout
    private val notices = mutableListOf<Notice>()
    private var listener: ListenerRegistration? = null
    private var filter = "ALL"
    private var active = false
    private var version = 0
    private var serverLoaded = false
    private var markingAll = false
    private val markingOne = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        all = findViewById(R.id.filterAll)
        emergency = findViewById(R.id.filterEmergency)
        requests = findViewById(R.id.filterRequests)
        donations = findViewById(R.id.filterDonation)
        markAll = findViewById(R.id.btnMarkAllRead)
        empty = findViewById(R.id.txtEmptyNotifications)
        container = findViewById(R.id.notificationContainer)
        filter = savedInstanceState?.getString("notificationFilter") ?: "ALL"
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        all.setOnClickListener { changeFilter("ALL") }
        emergency.setOnClickListener { changeFilter("EMERGENCY") }
        requests.setOnClickListener { changeFilter("REQUESTS") }
        donations.setOnClickListener { changeFilter("DONATION") }
        markAll.setOnClickListener { markEverythingRead() }
        changeFilter(filter)
        loading("Loading notifications...")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("notificationFilter", filter)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        active = true
        subscribe()
    }

    override fun onStop() {
        active = false
        version++
        listener?.remove()
        listener = null
        markingOne.clear()
        markingAll = false
        super.onStop()
    }

    private fun current(token: Int): Boolean =
        active && token == version && !isFinishing && !isDestroyed

    private fun subscribe() {
        val uid = auth.currentUser?.uid ?: run {
            loading("Please sign in again to view notifications")
            return
        }
        val token = ++version
        listener?.remove()
        serverLoaded = false
        notices.clear()
        loading("Syncing notifications with Firebase...")

        listener = db.collection("notifications")
            .whereEqualTo("userId", uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (!current(token)) return@addSnapshotListener
                if (error != null) {
                    serverLoaded = false
                    notices.clear()
                    loading("Notifications unavailable: ${error.localizedMessage ?: "Connection error"}")
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.metadata.isFromCache || snapshot.metadata.hasPendingWrites()) {
                    return@addSnapshotListener
                }
                notices.clear()
                notices.addAll(snapshot.documents.map { doc ->
                    Notice(
                        doc.id, doc.getString("userId").orEmpty(),
                        doc.getString("title").orEmpty(),
                        doc.getString("message").orEmpty(),
                        doc.getString("type").orEmpty(),
                        doc.getString("status").orEmpty(),
                        doc.getString("bloodGroup").orEmpty(),
                        doc.getString("hospital").orEmpty(),
                        doc.getString("area").orEmpty(),
                        doc.getString("requestId").orEmpty(),
                        doc.getBoolean("read") == true,
                        doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    )
                }.sortedWith(compareByDescending<Notice> { it.createdAt }.thenByDescending { it.id }))
                serverLoaded = true
                render()
            }
    }

    private fun changeFilter(value: String) {
        filter = value
        val tabs = mapOf(
            "ALL" to all, "EMERGENCY" to emergency,
            "REQUESTS" to requests, "DONATION" to donations
        )
        tabs.forEach { (key, view) ->
            view.setBackgroundResource(
                if (key == filter) R.drawable.bg_notification_filter_selected
                else R.drawable.bg_notification_filter
            )
            view.setTextColor(if (key == filter) Color.WHITE else Color.parseColor("#666666"))
        }
        if (::container.isInitialized) render()
    }

    private fun category(type: String): String {
        val normalized = type.uppercase(Locale.ROOT)
        return when {
            normalized.contains("EMERGENCY") -> "EMERGENCY"
            normalized.contains("DONATION") || normalized.contains("APPOINTMENT") ||
                    normalized.contains("ELIGIBILITY") -> "DONATION"
            normalized.contains("REQUEST") -> "REQUESTS"
            else -> "OTHER"
        }
    }

    private fun render() {
        if (!serverLoaded) return
        container.removeAllViews()
        val visible = notices.filter { filter == "ALL" || category(it.type) == filter }
        val unreadCount = notices.count { !it.read }
        markAll.isEnabled = unreadCount > 0 && !markingAll
        markAll.alpha = if (markAll.isEnabled) 1f else 0.45f
        markAll.text = if (markingAll) "Updating..." else "Mark all as read"
        empty.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        empty.text = if (visible.isEmpty()) "No ${filter.lowercase(Locale.ROOT)} notifications" else ""
        visible.forEach(::addCard)
    }

    private fun addCard(item: Notice) {
        val view = LayoutInflater.from(this).inflate(
            R.layout.item_user_notification, container, false
        )
        val card = view.findViewById<MaterialCardView>(R.id.cardNotification)
        val badge = view.findViewById<TextView>(R.id.txtNotificationBloodGroup)
        val type = view.findViewById<TextView>(R.id.txtNotificationType)
        val title = view.findViewById<TextView>(R.id.txtNotificationTitle)
        val body = view.findViewById<TextView>(R.id.txtNotificationMessage)
        val time = view.findViewById<TextView>(R.id.txtNotificationTime)
        val dot = view.findViewById<View>(R.id.dotUnread)

        badge.text = item.group.ifBlank { "•" }
        type.text = when (category(item.type)) {
            "EMERGENCY" -> "EMERGENCY"
            "DONATION" -> "DONATION"
            "REQUESTS" -> "REQUEST UPDATE"
            else -> "NOTIFICATION"
        }
        title.text = item.title.ifBlank {
            when (category(item.type)) {
                "EMERGENCY" -> "Emergency Blood Request"
                "DONATION" -> "Donation Update"
                "REQUESTS" -> "Blood Request Update"
                else -> "Notification"
            }
        }
        body.text = item.message.ifBlank { "You have a new notification." }
        time.text = if (item.createdAt > 0L) {
            SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()).format(Date(item.createdAt))
        } else "Recently"
        dot.visibility = if (item.read) View.GONE else View.VISIBLE
        card.setCardBackgroundColor(Color.parseColor(if (item.read) "#FFFFFF" else "#FFF1F3"))
        card.strokeColor = Color.parseColor(if (item.read) "#ECECEC" else "#F7C8CF")
        card.setOnClickListener {
            showDetails(item)
            if (!item.read) markOneRead(item)
        }
        container.addView(view)
    }

    private fun markOneRead(item: Notice) {
        val uid = auth.currentUser?.uid ?: return
        if (item.read || markingAll || !markingOne.add(item.id)) return
        val token = version
        val ref = db.collection("notifications").document(item.id)
        db.runTransaction { tx ->
            val document = tx.get(ref)
            if (!document.exists() || document.getString("userId") != uid) {
                throw IllegalStateException("This notification is not available for your account.")
            }
            if (document.getBoolean("read") != true) {
                tx.update(ref, mapOf("read" to true, "readAt" to FieldValue.serverTimestamp()))
            }
            true
        }.addOnSuccessListener {
            if (current(token)) {
                markingOne.remove(item.id)
                // The realtime listener updates the card.
            }
        }.addOnFailureListener { error ->
            if (current(token)) {
                markingOne.remove(item.id)
                feedback("Read status not saved: ${error.localizedMessage ?: "Firebase error"}")
            }
        }
    }

    private fun markEverythingRead() {
        if (!serverLoaded || markingAll) return
        val uid = auth.currentUser?.uid ?: return
        val unread = notices.filter { it.userId == uid && !it.read }
        if (unread.isEmpty()) return
        markingAll = true
        render()
        val token = version
        // Keep each Firestore batch well below its 500-write limit.
        val batches = unread.chunked(300)
        fun saveBatch(index: Int) {
            if (!current(token)) return
            if (index >= batches.size) {
                markingAll = false
                render()
                feedback("Notifications marked as read")
                return
            }
            val batch = db.batch()
            batches[index].forEach { item ->
                batch.update(
                    db.collection("notifications").document(item.id),
                    mapOf("read" to true, "readAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().addOnSuccessListener {
                if (current(token)) saveBatch(index + 1)
            }.addOnFailureListener { error ->
                if (current(token)) {
                    markingAll = false
                    render()
                    feedback("Some read updates failed: ${error.localizedMessage ?: "Firebase error"}")
                }
            }
        }
        saveBatch(0)
    }

    private fun showDetails(item: Notice) {
        val details = buildString {
            append(item.message.ifBlank { "You have a notification." })
            if (item.group.isNotBlank()) append("\n\nBlood group: ${item.group}")
            if (item.hospital.isNotBlank()) append("\nHospital: ${item.hospital}")
            if (item.area.isNotBlank()) append("\nArea: ${item.area}")
            if (item.requestId.isNotBlank()) append("\nRequest ID: ${item.requestId}")
            if (item.status.isNotBlank()) append("\nStatus: ${item.status}")
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(item.title.ifBlank { "Notification Details" })
            .setMessage(details)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun loading(text: String) {
        container.removeAllViews()
        empty.visibility = View.VISIBLE
        empty.text = text
        markAll.isEnabled = false
        markAll.alpha = 0.45f
    }

    private fun feedback(text: String) {
        if (active && !isFinishing && !isDestroyed) {
            Snackbar.make(container, text, Snackbar.LENGTH_LONG).show()
        }
    }
}
