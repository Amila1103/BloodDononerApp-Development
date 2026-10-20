
package com.example.blooddononerapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar

class AdminDashboardActivity : AppCompatActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }

    private var userListener: ListenerRegistration? = null
    private var hospitalListener: ListenerRegistration? = null

    private var userCountText: TextView? = null
    private var staffCountText: TextView? = null
    private var hospitalCountText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        setGreeting(findViewById(R.id.txtAdminGreeting))

        findViewById<MaterialCardView>(R.id.btnAdminNotifications)
            .setOnClickListener {
                openScreen(AdminNotificationsActivity::class.java)
            }

        findViewById<MaterialCardView>(R.id.btnAdminProfile)
            .setOnClickListener {
                openScreen(AdminProfileActivity::class.java)
            }

        findViewById<MaterialCardView>(R.id.cardAdminUsers)
            .setOnClickListener {
                openScreen(AdminUserManagementActivity::class.java)
            }

        findViewById<MaterialCardView>(R.id.cardAdminStaff)
            .setOnClickListener {
                openScreen(AdminStaffManagementActivity::class.java)
            }

        findViewById<MaterialCardView>(R.id.cardAdminHospitals)
            .setOnClickListener {
                openScreen(AdminHospitalManagementActivity::class.java)
            }

        findViewById<MaterialCardView>(R.id.cardAdminRequests)
            .setOnClickListener {
                openScreen(AdminEmergencyRequestsActivity::class.java)
            }

        findViewById<MaterialCardView>(R.id.cardAdminDonations)
            .setOnClickListener {
                openScreen(AdminDonationManagementActivity::class.java)
            }

        findViewById<MaterialCardView>(R.id.cardAdminInventory)
            .setOnClickListener {
                openScreen(AdminInventoryActivity::class.java)
            }

        findViewById<MaterialCardView>(R.id.cardAdminReports)
            .setOnClickListener {
                openScreen(AdminReportsActivity::class.java)
            }

        findViewById<LinearLayout>(R.id.navAdminDashboard)
            .setOnClickListener {
                // Current page
            }

        findViewById<LinearLayout>(R.id.navAdminUsers)
            .setOnClickListener {
                openScreen(AdminUserManagementActivity::class.java)
            }

        findViewById<LinearLayout>(R.id.navAdminHospitals)
            .setOnClickListener {
                openScreen(AdminHospitalManagementActivity::class.java)
            }

        findViewById<LinearLayout>(R.id.navAdminReports)
            .setOnClickListener {
                openScreen(AdminReportsActivity::class.java)
            }

        findViewById<LinearLayout>(R.id.navAdminProfile)
            .setOnClickListener {
                openScreen(AdminProfileActivity::class.java)
            }

        // Find the number labels in the existing XML layout.
        val root = window.decorView

        userCountText = findStatisticCount(root, "Users")
        staffCountText = findStatisticCount(root, "Staff")
        hospitalCountText = findStatisticCount(root, "Hospitals")

        userCountText?.text = "–"
        staffCountText?.text = "–"
        hospitalCountText?.text = "–"
    }

    override fun onStart() {
        super.onStart()

        userListener = db.collection("users")
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    userCountText?.text = "–"
                    staffCountText?.text = "–"
                    return@addSnapshotListener
                }

                val documents = snapshot?.documents.orEmpty()

                userCountText?.text = documents.count {
                    it.getString("role").equals("USER", true)
                }.toString()

                staffCountText?.text = documents.count {
                    it.getString("role").equals("STAFF", true)
                }.toString()
            }

        hospitalListener = db.collection("hospitals")
            .addSnapshotListener { snapshot, error ->

                hospitalCountText?.text =
                    if (error == null)
                        (snapshot?.size() ?: 0).toString()
                    else
                        "–"
            }
    }

    override fun onStop() {
        userListener?.remove()
        hospitalListener?.remove()

        userListener = null
        hospitalListener = null

        super.onStop()
    }

    private fun findStatisticCount(
        view: View,
        label: String
    ): TextView? {

        if (
            view is TextView &&
            view.text.toString() == label
        ) {
            val parent = view.parent as? LinearLayout
            val first = parent?.getChildAt(0) as? TextView

            if (
                parent?.orientation == LinearLayout.VERTICAL &&
                first != null &&
                first.text.toString().matches(Regex("[0-9]+"))
            ) {
                return first
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findStatisticCount(
                    view.getChildAt(i),
                    label
                )?.let { return it }
            }
        }

        return null
    }

    private fun openScreen(activity: Class<*>) {
        startActivity(Intent(this, activity))

        overridePendingTransition(
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
    }

    private fun setGreeting(textView: TextView) {
        val hour = Calendar.getInstance()
            .get(Calendar.HOUR_OF_DAY)

        textView.text = when (hour) {
            in 5..11 -> "Good Morning,"
            in 12..16 -> "Good Afternoon,"
            else -> "Good Evening,"
        }
    }
}
