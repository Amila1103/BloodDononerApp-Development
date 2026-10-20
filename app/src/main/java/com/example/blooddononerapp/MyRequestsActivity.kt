
package com.example.blooddononerapp

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import java.util.Locale

class MyRequestsActivity : AppCompatActivity() {

    private data class BloodRequest(
        val id: String,
        val reference: String,
        val group: String,
        val units: String,
        val hospital: String,
        val area: String,
        val district: String,
        val date: String,
        val before: String,
        val contact: String,
        val urgency: String,
        val status: String,
        val createdAt: Long
    )

    private data class Appointment(
        val id: String,
        val reference: String,
        val group: String,
        val hospital: String,
        val location: String,
        val date: String,
        val time: String,
        val status: String,
        val checkedIn: Boolean,
        val completed: Boolean,
        val createdAt: Long
    )

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var tabAll: TextView
    private lateinit var tabRequests: TextView
    private lateinit var tabDonations: TextView
    private lateinit var requestSection: LinearLayout
    private lateinit var donationSection: LinearLayout
    private lateinit var requestContainer: LinearLayout
    private lateinit var donationContainer: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var noRequests: TextView
    private lateinit var noDonations: TextView

    private val requests = mutableListOf<BloodRequest>()
    private val appointments = mutableListOf<Appointment>()
    private var requestsListener: ListenerRegistration? = null
    private var appointmentsListener: ListenerRegistration? = null
    private var selectedTab = "ALL"
    private var requestsReady = false
    private var appointmentsReady = false
    private var requestsError: String? = null
    private var appointmentsError: String? = null
    private var active = false
    private var generation = 0
    private var savingCancellation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_requests)
        tabAll = findViewById(R.id.btnAll)
        tabRequests = findViewById(R.id.btnBloodRequests)
        tabDonations = findViewById(R.id.btnDonations)
        requestSection = findViewById(R.id.bloodRequestsSection)
        donationSection = findViewById(R.id.donationsSection)
        requestContainer = findViewById(R.id.bloodRequestsContainer)
        donationContainer = findViewById(R.id.donationsContainer)
        emptyState = findViewById(R.id.emptyState)
        noRequests = findViewById(R.id.txtNoBloodRequests)
        noDonations = findViewById(R.id.txtNoDonations)

        selectedTab = savedInstanceState?.getString("tab") ?: "ALL"
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        tabAll.setOnClickListener { changeTab("ALL") }
        tabRequests.setOnClickListener { changeTab("REQUESTS") }
        tabDonations.setOnClickListener { changeTab("DONATIONS") }
        findViewById<MaterialButton>(R.id.btnEmptyRequestBlood).setOnClickListener {
            startActivity(Intent(this, EmergencyRequestActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnEmptyDonate).setOnClickListener {
            startActivity(Intent(this, BloodBankAvailabilityActivity::class.java))
        }
        changeTab(selectedTab)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("tab", selectedTab)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        active = true
        subscribe()
    }

    override fun onStop() {
        active = false
        generation++
        requestsListener?.remove()
        appointmentsListener?.remove()
        requestsListener = null
        appointmentsListener = null
        super.onStop()
    }

    private fun current(token: Int): Boolean =
        active && token == generation && !isFinishing && !isDestroyed

    private fun subscribe() {
        val uid = auth.currentUser?.uid ?: run {
            noRequests.text = "Please sign in again"
            noDonations.text = "Please sign in again"
            requestsError = "Sign-in required"
            appointmentsError = "Sign-in required"
            requestsReady = true
            appointmentsReady = true
            render()
            return
        }
        val token = ++generation
        requestsListener?.remove()
        appointmentsListener?.remove()
        requests.clear()
        appointments.clear()
        requestsError = null
        appointmentsError = null
        requestsReady = false
        appointmentsReady = false
        render()

        requestsListener = db.collection("emergencyRequests")
            .whereEqualTo("userId", uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (!current(token)) return@addSnapshotListener
                if (error != null) {
                    requestsReady = true
                    requestsError = error.localizedMessage ?: "Unable to load emergency requests"
                    requests.clear()
                    render()
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.metadata.isFromCache || snapshot.metadata.hasPendingWrites()) {
                    return@addSnapshotListener
                }
                requestsError = null
                requestsReady = true
                requests.clear()
                requests.addAll(snapshot.documents.map { doc ->
                    val unitText = when (val value = doc.get("units")) {
                        is String -> value
                        is Number -> "${value.toLong()} Units"
                        else -> "—"
                    }
                    BloodRequest(
                        doc.id, doc.getString("requestId") ?: doc.id,
                        doc.getString("bloodGroup").orEmpty(), unitText,
                        doc.getString("hospital").orEmpty(),
                        doc.getString("area").orEmpty(), doc.getString("district").orEmpty(),
                        doc.getString("requiredDate").orEmpty(),
                        doc.getString("requiredBefore").orEmpty(),
                        doc.getString("primaryContact").orEmpty(),
                        doc.getString("urgency").orEmpty(),
                        doc.getString("status") ?: "Pending",
                        doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    )
                }.sortedByDescending { it.createdAt })
                render()
            }

        appointmentsListener = db.collection("donationAppointments")
            .whereEqualTo("userId", uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (!current(token)) return@addSnapshotListener
                if (error != null) {
                    appointmentsReady = true
                    appointmentsError = error.localizedMessage ?: "Unable to load appointments"
                    appointments.clear()
                    render()
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.metadata.isFromCache || snapshot.metadata.hasPendingWrites()) {
                    return@addSnapshotListener
                }
                appointmentsError = null
                appointmentsReady = true
                appointments.clear()
                appointments.addAll(snapshot.documents.map { doc ->
                    val completed = doc.getString("status").equals("Completed", true) ||
                            doc.getBoolean("completed") == true || doc.getBoolean("inventoryUpdated") == true
                    Appointment(
                        doc.id, doc.getString("appointmentId") ?: doc.id,
                        doc.getString("bloodGroup").orEmpty(), doc.getString("hospital").orEmpty(),
                        doc.getString("location").orEmpty(),
                        doc.getString("donationDate").orEmpty(), doc.getString("donationTime").orEmpty(),
                        doc.getString("status") ?: "Pending", doc.getBoolean("checkedIn") == true,
                        completed, doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    )
                }.sortedByDescending { it.createdAt })
                render()
            }
    }

    private fun changeTab(value: String) {
        selectedTab = value
        listOf(tabAll, tabRequests, tabDonations).forEach {
            it.setBackgroundResource(android.R.color.transparent)
            it.setTextColor(Color.parseColor("#777777"))
        }
        val selected = when (value) {
            "REQUESTS" -> tabRequests
            "DONATIONS" -> tabDonations
            else -> tabAll
        }
        selected.setBackgroundResource(R.drawable.bg_tab_selected)
        selected.setTextColor(Color.WHITE)
        render()
    }

    private fun render() {
        if (!::requestContainer.isInitialized) return
        requestContainer.removeAllViews()
        donationContainer.removeAllViews()

        if (!requestsReady || !appointmentsReady) {
            emptyState.visibility = View.GONE
            requestSection.visibility = View.VISIBLE
            donationSection.visibility = View.VISIBLE
            noRequests.visibility = View.VISIBLE
            noDonations.visibility = View.VISIBLE
            noRequests.text = if (requestsReady) "" else "Loading blood requests..."
            noDonations.text = if (appointmentsReady) "" else "Loading appointments..."
            return
        }

        requests.forEach { item -> renderRequest(item) }
        appointments.forEach { item -> renderAppointment(item) }
        noRequests.text = requestsError?.let { "Requests unavailable: $it" } ?: "No blood requests found"
        noDonations.text = appointmentsError?.let { "Appointments unavailable: $it" } ?: "No donation appointments found"
        noRequests.visibility = if (requests.isEmpty()) View.VISIBLE else View.GONE
        noDonations.visibility = if (appointments.isEmpty()) View.VISIBLE else View.GONE

        val bothEmpty = requests.isEmpty() && appointments.isEmpty()
        val hasError = requestsError != null || appointmentsError != null
        emptyState.visibility = if (selectedTab == "ALL" && bothEmpty && !hasError) View.VISIBLE else View.GONE
        requestSection.visibility = when (selectedTab) {
            "DONATIONS" -> View.GONE
            "ALL" -> if (requests.isNotEmpty() || hasError) View.VISIBLE else View.GONE
            else -> View.VISIBLE
        }
        donationSection.visibility = when (selectedTab) {
            "REQUESTS" -> View.GONE
            "ALL" -> if (appointments.isNotEmpty() || hasError) View.VISIBLE else View.GONE
            else -> View.VISIBLE
        }
    }

    private fun renderRequest(item: BloodRequest) {
        val view = LayoutInflater.from(this).inflate(
            R.layout.item_my_blood_request, requestContainer, false
        )
        view.findViewById<TextView>(R.id.txtRequestStatus).apply {
            text = item.status
            styleStatus(this, item.status)
        }
        view.findViewById<TextView>(R.id.txtRequestBloodGroup).text = item.group.ifBlank { "—" }
        view.findViewById<TextView>(R.id.txtRequestHospital).text = item.hospital.ifBlank { "Hospital unavailable" }
        view.findViewById<TextView>(R.id.txtRequestLocation).text =
            listOf(item.area, item.district).filter { it.isNotBlank() }.distinct().joinToString(", ")
        view.findViewById<TextView>(R.id.txtRequestUnits).text = item.units
        view.findViewById<TextView>(R.id.txtRequestRequired).text =
            "${item.date.ifBlank { "—" }} • ${item.before.ifBlank { "—" }}"
        view.findViewById<TextView>(R.id.txtRequestUrgency).text = item.urgency
        view.findViewById<MaterialButton>(R.id.btnBloodRequestDetails).setOnClickListener {
            showRequestDetails(item)
        }
        view.findViewById<MaterialButton>(R.id.btnCancelRequest).apply {
            visibility = if (item.status.equals("Pending", true)) View.VISIBLE else View.GONE
            isEnabled = !savingCancellation
            setOnClickListener { confirmCancelRequest(item) }
        }
        view.setOnClickListener { showRequestDetails(item) }
        requestContainer.addView(view)
    }

    private fun renderAppointment(item: Appointment) {
        val view = LayoutInflater.from(this).inflate(
            R.layout.item_my_donation_appointment, donationContainer, false
        )
        val display = if (item.completed) "Completed" else item.status
        view.findViewById<TextView>(R.id.txtDonationStatus).apply {
            text = display
            styleStatus(this, display)
        }
        view.findViewById<TextView>(R.id.txtDonationBloodGroup).text = item.group.ifBlank { "—" }
        view.findViewById<TextView>(R.id.txtDonationHospital).text = item.hospital.ifBlank { "Hospital unavailable" }
        view.findViewById<TextView>(R.id.txtDonationLocation).text = item.location.ifBlank { "Donation center" }
        view.findViewById<TextView>(R.id.txtDonationDate).text = item.date.ifBlank { "—" }
        view.findViewById<TextView>(R.id.txtDonationTime).text = item.time.ifBlank { "—" }
        view.findViewById<MaterialButton>(R.id.btnDonationDetails).setOnClickListener {
            showAppointmentDetails(item)
        }
        view.findViewById<MaterialButton>(R.id.btnCancelAppointment).apply {
            visibility = if (canCancel(item)) View.VISIBLE else View.GONE
            isEnabled = !savingCancellation
            setOnClickListener { confirmCancelAppointment(item) }
        }
        view.setOnClickListener { showAppointmentDetails(item) }
        donationContainer.addView(view)
    }

    private fun styleStatus(view: TextView, status: String) {
        when (status.trim().uppercase(Locale.ROOT)) {
            "COMPLETED", "FULFILLED" -> {
                view.setTextColor(Color.parseColor("#15803D"))
                view.setBackgroundResource(R.drawable.bg_status_confirmed)
            }
            "CANCELLED", "REJECTED", "DEFERRED" -> {
                view.setTextColor(Color.parseColor("#777777"))
                view.setBackgroundResource(R.drawable.bg_history_filter)
            }
            "CHECKED IN", "ELIGIBLE", "VERIFIED", "MATCHING" -> {
                view.setTextColor(Color.parseColor("#2563EB"))
                view.setBackgroundResource(R.drawable.bg_status_pending)
            }
            else -> {
                view.setTextColor(Color.parseColor("#D97706"))
                view.setBackgroundResource(R.drawable.bg_status_pending)
            }
        }
    }

    private fun showRequestDetails(item: BloodRequest) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_blood_request_details)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.findViewById<TextView>(R.id.txtRequestDetailId).text = "Request #${item.reference}"
        dialog.findViewById<TextView>(R.id.txtRequestDetailStatus).text = item.status.uppercase(Locale.ROOT)
        dialog.findViewById<TextView>(R.id.txtRequestDetailHospital).text = item.hospital
        dialog.findViewById<TextView>(R.id.txtRequestDetailLocation).text =
            listOf(item.area, item.district).filter { it.isNotBlank() }.joinToString(", ")
        dialog.findViewById<TextView>(R.id.txtRequestDetailBloodGroup).text = item.group
        dialog.findViewById<TextView>(R.id.txtRequestDetailUnits).text = item.units
        dialog.findViewById<TextView>(R.id.txtRequestDetailDate).text = item.date
        dialog.findViewById<TextView>(R.id.txtRequestDetailBefore).text = item.before
        dialog.findViewById<TextView>(R.id.txtRequestDetailUrgency).text = item.urgency
        dialog.findViewById<TextView>(R.id.txtRequestDetailContact).text = item.contact
        dialog.findViewById<MaterialButton>(R.id.btnCloseRequestDetails)
            .setOnClickListener { dialog.dismiss() }
        showDetailDialog(dialog)
    }

    private fun showAppointmentDetails(item: Appointment) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_donation_details)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.findViewById<TextView>(R.id.txtDonationAppointmentId).text = "Appointment #${item.reference}"
        dialog.findViewById<TextView>(R.id.txtDonationDetailStatus).text =
            if (item.completed) "COMPLETED" else item.status.uppercase(Locale.ROOT)
        dialog.findViewById<TextView>(R.id.txtDonationDetailHospital).text = item.hospital
        dialog.findViewById<TextView>(R.id.txtDonationDetailLocation).text = item.location
        dialog.findViewById<TextView>(R.id.txtDonationDetailBloodGroup).text = item.group
        dialog.findViewById<TextView>(R.id.txtDonationDetailDate).text = item.date
        dialog.findViewById<TextView>(R.id.txtDonationDetailTime).text = item.time
        dialog.findViewById<MaterialButton>(R.id.btnCloseDonationDetails)
            .setOnClickListener { dialog.dismiss() }
        showDetailDialog(dialog)
    }

    private fun showDetailDialog(dialog: Dialog) {
        dialog.show()
        dialog.window?.let { window ->
            window.setLayout(
                (resources.displayMetrics.widthPixels * 0.92f).toInt(),
                (resources.displayMetrics.heightPixels * 0.86f).toInt()
            )
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val params = window.attributes
            params.dimAmount = 0.45f
            window.attributes = params
        }
    }

    private fun canCancel(item: Appointment): Boolean =
        item.status.equals("Pending", true) && !item.checkedIn && !item.completed

    private fun confirmCancelRequest(item: BloodRequest) {
        if (savingCancellation) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Cancel Emergency Request?")
            .setMessage("Request: ${item.reference}\nHospital: ${item.hospital}\n\n" +
                    "Cancellation is allowed only while the request is still Pending.")
            .setNegativeButton("Keep Request", null)
            .setPositiveButton("Cancel Request") { _, _ -> cancelRequest(item) }.show()
    }

    private fun confirmCancelAppointment(item: Appointment) {
        if (savingCancellation) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Cancel Donation Appointment?")
            .setMessage("Appointment: ${item.reference}\nHospital: ${item.hospital}\n\n" +
                    "Checked-in or completed appointments cannot be cancelled here.")
            .setNegativeButton("Keep Appointment", null)
            .setPositiveButton("Cancel Appointment") { _, _ -> cancelAppointment(item) }.show()
    }

    private fun cancelRequest(item: BloodRequest) {
        val uid = auth.currentUser?.uid ?: return
        if (savingCancellation) return
        savingCancellation = true
        render()
        val ref = db.collection("emergencyRequests").document(item.id)
        db.runTransaction { tx ->
            val document = tx.get(ref)
            if (!document.exists() || document.getString("userId") != uid ||
                !document.getString("status").equals("Pending", true)) {
                throw IllegalStateException("This request is no longer Pending or is not yours.")
            }
            tx.update(ref, mapOf(
                "status" to "Cancelled",
                "cancelledAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            true
        }.addOnSuccessListener {
            if (active) {
                savingCancellation = false
                render()
                message("Request Cancelled", "Your emergency request has been cancelled in Firebase.")
            }
        }.addOnFailureListener { error ->
            if (active) {
                savingCancellation = false
                render()
                message("Cancellation Failed", error.localizedMessage ?: "Unable to cancel the request.")
            }
        }
    }

    private fun cancelAppointment(item: Appointment) {
        val uid = auth.currentUser?.uid ?: return
        if (savingCancellation) return
        savingCancellation = true
        render()
        val ref = db.collection("donationAppointments").document(item.id)
        db.runTransaction { tx ->
            val document: DocumentSnapshot = tx.get(ref)
            val pending = document.getString("status").equals("Pending", true)
            val checkedIn = document.getBoolean("checkedIn") == true
            val completed = document.getBoolean("completed") == true ||
                    document.getBoolean("inventoryUpdated") == true ||
                    !document.getString("donationRecordId").isNullOrBlank()
            if (!document.exists() || document.getString("userId") != uid ||
                !pending || checkedIn || completed) {
                throw IllegalStateException("This appointment is already being processed or is not yours.")
            }
            tx.update(ref, mapOf(
                "status" to "Cancelled",
                "cancelled" to true,
                "cancelledAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            true
        }.addOnSuccessListener {
            if (active) {
                savingCancellation = false
                render()
                message("Appointment Cancelled", "Your donation appointment was cancelled in Firebase.")
            }
        }.addOnFailureListener { error ->
            if (active) {
                savingCancellation = false
                render()
                message("Cancellation Failed", error.localizedMessage ?: "Unable to cancel this appointment.")
            }
        }
    }

    private fun message(title: String, body: String) {
        if (active && !isFinishing && !isDestroyed)
            MaterialAlertDialogBuilder(this).setTitle(title).setMessage(body)
                .setPositiveButton("OK", null).show()
    }
}
