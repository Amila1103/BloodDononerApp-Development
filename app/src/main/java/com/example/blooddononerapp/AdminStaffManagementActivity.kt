package com.example.blooddononerapp

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore

class AdminStaffManagementActivity : AppCompatActivity() {

    // =========================================================
    // FIREBASE
    // =========================================================

    private lateinit var firestore: FirebaseFirestore


    // =========================================================
    // VIEWS
    // =========================================================

    private lateinit var recyclerStaff: RecyclerView
    private lateinit var txtStaffCount: TextView
    private lateinit var txtEmptyStaff: TextView


    // =========================================================
    // ADAPTER
    // =========================================================

    private lateinit var staffAdapter: AdminStaffAdapter

    private val staffList =
        mutableListOf<AdminStaffAdapter.StaffMember>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_admin_staff_management
        )


        // =====================================================
        // FIREBASE
        // =====================================================

        firestore =
            FirebaseFirestore.getInstance()


        // =====================================================
        // FIND VIEWS
        // =====================================================

        val btnBack =
            findViewById<TextView>(
                R.id.btnBack
            )

        val btnAddStaff =
            findViewById<MaterialButton>(
                R.id.btnAddStaff
            )

        txtStaffCount =
            findViewById(
                R.id.txtStaffCount
            )

        txtEmptyStaff =
            findViewById(
                R.id.txtEmptyStaff
            )

        recyclerStaff =
            findViewById(
                R.id.recyclerStaff
            )


        // =====================================================
        // RECYCLER VIEW
        // =====================================================

        staffAdapter =
            AdminStaffAdapter(
                mutableListOf()
            ) { staff ->

                showStaffDetails(
                    staff
                )
            }


        recyclerStaff.layoutManager =
            LinearLayoutManager(this)

        recyclerStaff.adapter =
            staffAdapter


        // =====================================================
        // BACK
        // =====================================================

        btnBack.setOnClickListener {

            finish()
        }


        // =====================================================
        // ADD STAFF
        // =====================================================

        btnAddStaff.setOnClickListener {

            val intent =
                Intent(
                    this,
                    AdminAddStaffActivity::class.java
                )

            startActivity(intent)
        }


        // =====================================================
        // LOAD STAFF
        // =====================================================

        loadStaff()
    }


    // =========================================================
    // LOAD STAFF FROM FIRESTORE
    // =========================================================

    private fun loadStaff() {

        firestore
            .collection("users")
            .whereEqualTo(
                "role",
                "STAFF"
            )
            .get()
            .addOnSuccessListener { documents ->

                staffList.clear()


                for (document in documents) {

                    val uid =
                        document.id

                    val fullName =
                        document.getString(
                            "fullName"
                        ) ?: "Staff"

                    val email =
                        document.getString(
                            "email"
                        ) ?: ""

                    val phone =
                        document.getString(
                            "phone"
                        ) ?: ""

                    val hospital =
                        document.getString(
                            "hospital"
                        ) ?: "Not Assigned"

                    val status =
                        document.getString(
                            "status"
                        ) ?: "Active"


                    val staff =
                        AdminStaffAdapter.StaffMember(
                            uid = uid,
                            fullName = fullName,
                            email = email,
                            phone = phone,
                            hospital = hospital,
                            status = status
                        )


                    staffList.add(
                        staff
                    )
                }


                // =============================================
                // UPDATE LIST
                // =============================================

                staffAdapter.updateStaff(
                    staffList
                )


                // =============================================
                // UPDATE COUNT
                // =============================================

                val count =
                    staffList.size

                txtStaffCount.text =
                    if (count == 1) {

                        "1 Staff Account"

                    } else {

                        "$count Staff Accounts"
                    }


                // =============================================
                // EMPTY STATE
                // =============================================

                if (staffList.isEmpty()) {

                    txtEmptyStaff.visibility =
                        android.view.View.VISIBLE

                    recyclerStaff.visibility =
                        android.view.View.GONE

                } else {

                    txtEmptyStaff.visibility =
                        android.view.View.GONE

                    recyclerStaff.visibility =
                        android.view.View.VISIBLE
                }
            }

            .addOnFailureListener { exception ->

                Toast.makeText(
                    this,
                    "Failed to load staff: ${
                        exception.message ?: "Unknown error"
                    }",
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    // =========================================================
    // SHOW STAFF DETAILS
    // =========================================================

    private fun showStaffDetails(
        staff: AdminStaffAdapter.StaffMember
    ) {

        val staffId =
            if (staff.uid.length > 8) {

                "STF-${staff.uid.take(8).uppercase()}"

            } else {

                "STF-${staff.uid.uppercase()}"
            }


        val phoneText =
            if (staff.phone.isBlank()) {

                "Not available"

            } else {

                staff.phone
            }


        AlertDialog.Builder(this)
            .setTitle(
                staff.fullName
            )
            .setMessage(
                """
                Staff ID: $staffId
                
                Hospital:
                ${staff.hospital}
                
                Email:
                ${staff.email}
                
                Phone:
                $phoneText
                
                Status:
                ${staff.status}
                """.trimIndent()
            )
            .setNegativeButton(
                "Close",
                null
            )
            .setPositiveButton(
                if (
                    staff.status.equals(
                        "Active",
                        ignoreCase = true
                    )
                ) {

                    "Suspend"

                } else {

                    "Activate"
                }
            ) { _, _ ->

                val newStatus =
                    if (
                        staff.status.equals(
                            "Active",
                            ignoreCase = true
                        )
                    ) {

                        "Suspended"

                    } else {

                        "Active"
                    }


                confirmStatusChange(
                    staff = staff,
                    newStatus = newStatus
                )
            }
            .show()
    }


    // =========================================================
    // CONFIRM STATUS CHANGE
    // =========================================================

    private fun confirmStatusChange(
        staff: AdminStaffAdapter.StaffMember,
        newStatus: String
    ) {

        val action =
            if (
                newStatus.equals(
                    "Suspended",
                    ignoreCase = true
                )
            ) {

                "suspend"

            } else {

                "activate"
            }


        AlertDialog.Builder(this)
            .setTitle(
                if (newStatus == "Suspended") {
                    "Suspend Staff?"
                } else {
                    "Activate Staff?"
                }
            )
            .setMessage(
                "Are you sure you want to $action ${staff.fullName}?"
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Confirm"
            ) { _, _ ->

                updateStaffStatus(
                    staff.uid,
                    newStatus
                )
            }
            .show()
    }


    // =========================================================
    // UPDATE STAFF STATUS
    // =========================================================

    private fun updateStaffStatus(
        staffId: String,
        newStatus: String
    ) {

        firestore
            .collection("users")
            .document(staffId)
            .update(
                "status",
                newStatus
            )
            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    "Staff status updated to $newStatus",
                    Toast.LENGTH_SHORT
                ).show()


                // Refresh list
                loadStaff()
            }

            .addOnFailureListener { exception ->

                Toast.makeText(
                    this,
                    "Failed to update staff: ${
                        exception.message ?: "Unknown error"
                    }",
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    // =========================================================
    // REFRESH
    // =========================================================

    override fun onResume() {
        super.onResume()

        if (::firestore.isInitialized) {

            loadStaff()
        }
    }
}