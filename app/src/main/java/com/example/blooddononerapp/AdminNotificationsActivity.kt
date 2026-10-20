package com.example.blooddononerapp

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class AdminNotificationsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var btnBack: MaterialCardView
    private lateinit var txtMarkAllRead: TextView
    private lateinit var txtEmpty: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerNotifications: RecyclerView

    private lateinit var notificationAdapter: AdminNotificationAdapter

    private val notifications = mutableListOf<AdminNotificationItem>()
    private val readNotificationIds = mutableSetOf<String>()

    private var adminUid: String = ""

    // Number of source queries used to build the notification list.
    private val totalQueries = 4
    private val finishedQueries = AtomicInteger(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_notifications)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        bindViews()
        setupRecyclerView()
        setupClickListeners()

        validateAdminAndLoad()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        txtMarkAllRead = findViewById(R.id.txtMarkAllRead)
        txtEmpty = findViewById(R.id.txtEmpty)
        progressBar = findViewById(R.id.progressBar)
        recyclerNotifications = findViewById(R.id.recyclerNotifications)
    }

    private fun setupRecyclerView() {
        notificationAdapter = AdminNotificationAdapter(
            items = notifications,
            onNotificationClick = { notification ->
                markNotificationAsRead(notification)
            }
        )

        recyclerNotifications.layoutManager = LinearLayoutManager(this)
        recyclerNotifications.adapter = notificationAdapter
    }

    private fun setupClickListeners() {

        btnBack.setOnClickListener {
            finish()
        }

        txtMarkAllRead.setOnClickListener {
            markAllAsRead()
        }
    }

    /**
     * Make sure that:
     * 1. A Firebase user is logged in.
     * 2. The logged-in user has ADMIN role.
     */
    private fun validateAdminAndLoad() {

        val currentUser = auth.currentUser

        if (currentUser == null) {
            Toast.makeText(
                this,
                "Please login again",
                Toast.LENGTH_SHORT
            ).show()

            finish()
            return
        }

        adminUid = currentUser.uid

        showLoading(true)

        db.collection("users")
            .document(adminUid)
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {
                    showLoading(false)

                    Toast.makeText(
                        this,
                        "Admin profile not found",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                    return@addOnSuccessListener
                }

                val role = document.getString("role")
                    ?.trim()
                    ?.uppercase()
                    ?: ""

                if (role != "ADMIN") {
                    showLoading(false)

                    Toast.makeText(
                        this,
                        "Admin access required",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                    return@addOnSuccessListener
                }

                loadReadStates()
            }
            .addOnFailureListener { exception ->

                showLoading(false)

                Toast.makeText(
                    this,
                    exception.message ?: "Failed to verify admin account",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    /**
     * Read notification IDs already marked as read by this admin.
     *
     * Structure:
     *
     * adminNotificationReads/{adminUid}/items/{notificationId}
     */
    private fun loadReadStates() {

        db.collection("adminNotificationReads")
            .document(adminUid)
            .collection("items")
            .get()
            .addOnSuccessListener { snapshot ->

                readNotificationIds.clear()

                for (document in snapshot.documents) {
                    readNotificationIds.add(document.id)
                }

                loadNotifications()
            }
            .addOnFailureListener {

                // Even if read states fail to load, still show notifications.
                readNotificationIds.clear()
                loadNotifications()
            }
    }

    /**
     * Admin notifications are built from existing Firestore collections.
     *
     * 1. emergencyRequests
     * 2. hospitals
     * 3. users
     * 4. donationRecords
     */
    private fun loadNotifications() {

        notifications.clear()
        finishedQueries.set(0)

        showLoading(true)

        loadEmergencyRequestNotifications()
        loadLowStockNotifications()
        loadNewUserNotifications()
        loadDonationNotifications()
    }

    /**
     * NEW EMERGENCY REQUEST notifications
     */
    private fun loadEmergencyRequestNotifications() {

        db.collection("emergencyRequests")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .get()
            .addOnSuccessListener { snapshot ->

                for (document in snapshot.documents) {

                    val requestId =
                        document.getString("requestId")
                            ?: document.id

                    val bloodGroup =
                        document.getString("bloodGroup")
                            ?: "Unknown"

                    val hospital =
                        document.getString("hospital")
                            ?: "Unknown hospital"

                    val area =
                        document.getString("area")
                            ?: ""

                    val createdAt =
                        document.getTimestamp("createdAt")

                    val notificationId =
                        "emergency_${document.id}"

                    val message =
                        if (area.isNotBlank()) {
                            "$bloodGroup blood requested at $hospital, $area."
                        } else {
                            "$bloodGroup blood requested at $hospital."
                        }

                    notifications.add(
                        AdminNotificationItem(
                            notificationId = notificationId,
                            title = "New Emergency Request",
                            message = message,
                            type = AdminNotificationType.EMERGENCY,
                            createdAt = createdAt,
                            isRead = readNotificationIds.contains(notificationId),
                            referenceId = document.id,
                            secondaryId = requestId
                        )
                    )
                }

                queryFinished()
            }
            .addOnFailureListener {
                queryFinished()
            }
    }

    /**
     * LOW BLOOD STOCK notifications
     *
     * For this project:
     * 0 - 2 units = Low Stock
     *
     * One notification is created for each blood group that is <= 2.
     */
    private fun loadLowStockNotifications() {

        db.collection("hospitals")
            .get()
            .addOnSuccessListener { snapshot ->

                for (document in snapshot.documents) {

                    val hospitalName =
                        document.getString("name")
                            ?: document.getString("hospitalName")
                            ?: "Hospital"

                    val updatedAt =
                        document.getTimestamp("updatedAt")
                            ?: document.getTimestamp("createdAt")
                            ?: Timestamp.now()

                    @Suppress("UNCHECKED_CAST")
                    val bloodStock =
                        document.get("bloodStock") as? Map<String, Any>
                            ?: emptyMap()

                    val stockMappings = listOf(
                        "APlus" to "A+",
                        "AMinus" to "A-",
                        "BPlus" to "B+",
                        "BMinus" to "B-",
                        "ABPlus" to "AB+",
                        "ABMinus" to "AB-",
                        "OPlus" to "O+",
                        "OMinus" to "O-"
                    )

                    for ((firestoreKey, displayBloodGroup) in stockMappings) {

                        val stockValue =
                            getNumberValue(
                                bloodStock[firestoreKey]
                            )

                        // Only create alert if stock value exists and is low.
                        if (stockValue != null && stockValue <= 2) {

                            val notificationId =
                                "lowstock_${document.id}_$firestoreKey"

                            notifications.add(
                                AdminNotificationItem(
                                    notificationId = notificationId,
                                    title = "Low Blood Stock",
                                    message =
                                        "$displayBloodGroup stock at $hospitalName is low ($stockValue unit${if (stockValue == 1) "" else "s"}).",
                                    type = AdminNotificationType.LOW_STOCK,
                                    createdAt = updatedAt,
                                    isRead = readNotificationIds.contains(notificationId),
                                    referenceId = document.id
                                )
                            )
                        }
                    }
                }

                queryFinished()
            }
            .addOnFailureListener {
                queryFinished()
            }
    }

    /**
     * NEW USER REGISTERED notifications
     *
     * Only USER accounts are shown.
     */
    private fun loadNewUserNotifications() {

        db.collection("users")
            .whereEqualTo("role", "USER")
            .limit(30)
            .get()
            .addOnSuccessListener { snapshot ->

                for (document in snapshot.documents) {

                    val fullName =
                        document.getString("fullName")
                            ?: "New donor"

                    val bloodGroup =
                        document.getString("bloodGroup")
                            ?: ""

                    val createdAt =
                        document.getTimestamp("createdAt")

                    /*
                     * Some older user documents may not have createdAt.
                     * We skip them instead of showing an incorrect
                     * "new registration" notification.
                     */
                    if (createdAt == null) {
                        continue
                    }

                    val notificationId =
                        "user_${document.id}"

                    val message =
                        if (bloodGroup.isNotBlank()) {
                            "$fullName registered as a new donor ($bloodGroup)."
                        } else {
                            "$fullName registered as a new donor."
                        }

                    notifications.add(
                        AdminNotificationItem(
                            notificationId = notificationId,
                            title = "New User Registered",
                            message = message,
                            type = AdminNotificationType.NEW_USER,
                            createdAt = createdAt,
                            isRead = readNotificationIds.contains(notificationId),
                            referenceId = document.id
                        )
                    )
                }

                queryFinished()
            }
            .addOnFailureListener {
                queryFinished()
            }
    }

    /**
     * DONATION RECORDED notifications
     */
    private fun loadDonationNotifications() {

        db.collection("donationRecords")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .get()
            .addOnSuccessListener { snapshot ->

                for (document in snapshot.documents) {

                    val donorName =
                        document.getString("donorName")
                            ?: "A donor"

                    val bloodGroup =
                        document.getString("bloodGroup")
                            ?: ""

                    val hospital =
                        document.getString("hospital")
                            ?: "hospital"

                    val createdAt =
                        document.getTimestamp("createdAt")
                            ?: document.getTimestamp("completedAt")
                            ?: document.getTimestamp("donatedAt")

                    val notificationId =
                        "donation_${document.id}"

                    val message =
                        when {
                            bloodGroup.isNotBlank() ->
                                "$donorName recorded a $bloodGroup donation at $hospital."

                            else ->
                                "$donorName recorded a donation at $hospital."
                        }

                    notifications.add(
                        AdminNotificationItem(
                            notificationId = notificationId,
                            title = "Donation Recorded",
                            message = message,
                            type = AdminNotificationType.DONATION,
                            createdAt = createdAt,
                            isRead = readNotificationIds.contains(notificationId),
                            referenceId = document.id
                        )
                    )
                }

                queryFinished()
            }
            .addOnFailureListener {
                queryFinished()
            }
    }

    /**
     * Called after every Firestore source query completes.
     */
    private fun queryFinished() {

        val count = finishedQueries.incrementAndGet()

        if (count >= totalQueries) {
            finishLoadingNotifications()
        }
    }

    private fun finishLoadingNotifications() {

        notifications.sortByDescending {
            it.createdAt?.toDate()?.time ?: 0L
        }

        notificationAdapter.notifyDataSetChanged()

        showLoading(false)
        updateEmptyState()
        updateMarkAllReadButton()
    }

    private fun updateEmptyState() {

        if (notifications.isEmpty()) {
            txtEmpty.visibility = View.VISIBLE
            recyclerNotifications.visibility = View.GONE
        } else {
            txtEmpty.visibility = View.GONE
            recyclerNotifications.visibility = View.VISIBLE
        }
    }

    private fun updateMarkAllReadButton() {

        val hasUnread =
            notifications.any {
                !it.isRead
            }

        txtMarkAllRead.isEnabled = hasUnread
        txtMarkAllRead.alpha =
            if (hasUnread) 1f else 0.45f
    }

    /**
     * Mark one notification as read.
     */
    private fun markNotificationAsRead(
        notification: AdminNotificationItem
    ) {

        if (notification.isRead) {
            return
        }

        if (adminUid.isBlank()) {
            return
        }

        val readData = hashMapOf(
            "notificationId" to notification.notificationId,
            "adminUid" to adminUid,
            "read" to true,
            "readAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        db.collection("adminNotificationReads")
            .document(adminUid)
            .collection("items")
            .document(notification.notificationId)
            .set(readData)
            .addOnSuccessListener {

                notification.isRead = true
                readNotificationIds.add(
                    notification.notificationId
                )

                notificationAdapter.notifyDataSetChanged()
                updateMarkAllReadButton()
            }
            .addOnFailureListener { exception ->

                Toast.makeText(
                    this,
                    exception.message
                        ?: "Failed to mark notification as read",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    /**
     * Mark every currently loaded notification as read.
     */
    private fun markAllAsRead() {

        val unreadNotifications =
            notifications.filter {
                !it.isRead
            }

        if (unreadNotifications.isEmpty()) {

            Toast.makeText(
                this,
                "No unread notifications",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (adminUid.isBlank()) {
            return
        }

        txtMarkAllRead.isEnabled = false

        val batch = db.batch()

        for (notification in unreadNotifications) {

            val documentReference =
                db.collection("adminNotificationReads")
                    .document(adminUid)
                    .collection("items")
                    .document(notification.notificationId)

            val readData = hashMapOf(
                "notificationId" to notification.notificationId,
                "adminUid" to adminUid,
                "read" to true,
                "readAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            batch.set(
                documentReference,
                readData
            )
        }

        batch.commit()
            .addOnSuccessListener {

                for (notification in unreadNotifications) {
                    notification.isRead = true

                    readNotificationIds.add(
                        notification.notificationId
                    )
                }

                notificationAdapter.notifyDataSetChanged()
                updateMarkAllReadButton()

                Toast.makeText(
                    this,
                    "All notifications marked as read",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { exception ->

                txtMarkAllRead.isEnabled = true

                Toast.makeText(
                    this,
                    exception.message
                        ?: "Failed to mark notifications as read",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showLoading(show: Boolean) {

        progressBar.visibility =
            if (show) View.VISIBLE
            else View.GONE

        if (show) {
            txtEmpty.visibility = View.GONE
        }
    }

    /**
     * Handles Firestore Number/String values safely.
     */
    private fun getNumberValue(
        value: Any?
    ): Int? {

        return when (value) {

            is Long ->
                value.toInt()

            is Int ->
                value

            is Double ->
                value.toInt()

            is Float ->
                value.toInt()

            is Number ->
                value.toInt()

            is String ->
                value.trim().toIntOrNull()

            else ->
                null
        }
    }
}

/**
 * Notification types used by the adapter
 * to select the correct icon.
 */
enum class AdminNotificationType {
    EMERGENCY,
    LOW_STOCK,
    NEW_USER,
    DONATION
}

/**
 * One notification shown in RecyclerView.
 */
data class AdminNotificationItem(
    val notificationId: String = UUID.randomUUID().toString(),
    val title: String = "",
    val message: String = "",
    val type: AdminNotificationType = AdminNotificationType.EMERGENCY,
    val createdAt: Timestamp? = null,
    var isRead: Boolean = false,
    val referenceId: String = "",
    val secondaryId: String = ""
)

/**
 * RecyclerView Adapter
 */
class AdminNotificationAdapter(
    private val items: List<AdminNotificationItem>,
    private val onNotificationClick: (AdminNotificationItem) -> Unit
) : RecyclerView.Adapter<AdminNotificationAdapter.NotificationViewHolder>() {

    inner class NotificationViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val cardNotification: MaterialCardView =
            itemView.findViewById(R.id.cardNotification)

        val iconBackground: MaterialCardView =
            itemView.findViewById(R.id.iconBackground)

        val imgIcon: android.widget.ImageView =
            itemView.findViewById(R.id.imgIcon)

        val txtTitle: TextView =
            itemView.findViewById(R.id.txtTitle)

        val txtMessage: TextView =
            itemView.findViewById(R.id.txtMessage)

        val txtTime: TextView =
            itemView.findViewById(R.id.txtTime)

        val unreadIndicator: View =
            itemView.findViewById(R.id.unreadIndicator)
    }

    override fun onCreateViewHolder(
        parent: android.view.ViewGroup,
        viewType: Int
    ): NotificationViewHolder {

        val view =
            android.view.LayoutInflater.from(parent.context)
                .inflate(
                    R.layout.item_admin_notification,
                    parent,
                    false
                )

        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: NotificationViewHolder,
        position: Int
    ) {

        val notification =
            items[position]

        holder.txtTitle.text =
            notification.title

        holder.txtMessage.text =
            notification.message

        holder.txtTime.text =
            getRelativeTime(
                notification.createdAt
            )

        when (notification.type) {

            AdminNotificationType.EMERGENCY -> {
                holder.imgIcon.setImageResource(
                    R.drawable.ic_staff_emergency
                )
            }

            AdminNotificationType.LOW_STOCK -> {
                holder.imgIcon.setImageResource(
                    R.drawable.ic_staff_blood_bag
                )
            }

            AdminNotificationType.NEW_USER -> {
                holder.imgIcon.setImageResource(
                    R.drawable.ic_staff_profile
                )
            }

            AdminNotificationType.DONATION -> {
                holder.imgIcon.setImageResource(
                    R.drawable.ic_staff_record_donation
                )
            }
        }

        if (notification.isRead) {

            holder.unreadIndicator.visibility =
                View.INVISIBLE

            holder.cardNotification.setCardBackgroundColor(
                android.graphics.Color.WHITE
            )

            holder.cardNotification.strokeWidth =
                dpToPx(
                    holder.itemView,
                    1
                )

            holder.cardNotification.strokeColor =
                android.graphics.Color.parseColor(
                    "#EEEEEE"
                )

        } else {

            holder.unreadIndicator.visibility =
                View.VISIBLE

            holder.cardNotification.setCardBackgroundColor(
                android.graphics.Color.parseColor(
                    "#FFF1F3"
                )
            )

            holder.cardNotification.strokeWidth = 0
        }

        holder.cardNotification.setOnClickListener {
            onNotificationClick(notification)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    private fun getRelativeTime(
        timestamp: Timestamp?
    ): String {

        if (timestamp == null) {
            return ""
        }

        val now =
            Date().time

        val notificationTime =
            timestamp.toDate().time

        val difference =
            now - notificationTime

        if (difference < 0) {
            return "Just now"
        }

        val seconds =
            difference / 1000

        val minutes =
            seconds / 60

        val hours =
            minutes / 60

        val days =
            hours / 24

        return when {

            seconds < 60 ->
                "Just now"

            minutes < 60 ->
                "$minutes minute${if (minutes == 1L) "" else "s"} ago"

            hours < 24 ->
                "$hours hour${if (hours == 1L) "" else "s"} ago"

            days < 7 ->
                "$days day${if (days == 1L) "" else "s"} ago"

            else -> {
                val formatter =
                    java.text.SimpleDateFormat(
                        "dd MMM yyyy",
                        java.util.Locale.getDefault()
                    )

                formatter.format(
                    timestamp.toDate()
                )
            }
        }
    }

    private fun dpToPx(
        view: View,
        dp: Int
    ): Int {

        val density =
            view.resources.displayMetrics.density

        return (dp * density).toInt()
    }
}