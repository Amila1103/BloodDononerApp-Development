package com.example.blooddononerapp

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class AdminUserManagementActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore

    private lateinit var recyclerUsers: RecyclerView
    private lateinit var edtSearchUser: EditText
    private lateinit var txtEmptyUsers: TextView

    private lateinit var userAdapter: AdminUserAdapter

    private val allUsers =
        mutableListOf<AdminUserAdapter.AdminUser>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_admin_user_management
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

        edtSearchUser =
            findViewById(
                R.id.edtSearchUser
            )

        recyclerUsers =
            findViewById(
                R.id.recyclerUsers
            )

        txtEmptyUsers =
            findViewById(
                R.id.txtEmptyUsers
            )


        // =====================================================
        // RECYCLER VIEW
        // =====================================================

        userAdapter =
            AdminUserAdapter(
                mutableListOf()
            ) { user ->

                openUserDetails(
                    user.uid
                )
            }


        recyclerUsers.layoutManager =
            LinearLayoutManager(this)

        recyclerUsers.adapter =
            userAdapter


        // =====================================================
        // BACK
        // =====================================================

        btnBack.setOnClickListener {

            finish()
        }


        // =====================================================
        // SEARCH
        // =====================================================

        edtSearchUser.addTextChangedListener {

            val query =
                it.toString()
                    .trim()
                    .lowercase()

            filterUsers(
                query
            )
        }


        // =====================================================
        // LOAD USERS
        // =====================================================

        loadUsers()
    }


    // =========================================================
    // LOAD FIREBASE USERS
    // =========================================================

    private fun loadUsers() {

        firestore
            .collection("users")
            .whereEqualTo(
                "role",
                "USER"
            )
            .get()
            .addOnSuccessListener { documents ->

                allUsers.clear()


                for (document in documents) {

                    val uid =
                        document.id

                    val fullName =
                        document.getString(
                            "fullName"
                        ) ?: "Unknown User"

                    val email =
                        document.getString(
                            "email"
                        ) ?: ""

                    val phone =
                        document.getString(
                            "phone"
                        ) ?: ""

                    val bloodGroup =
                        document.getString(
                            "bloodGroup"
                        ) ?: "--"

                    val status =
                        document.getString(
                            "status"
                        ) ?: "Active"


                    val user =
                        AdminUserAdapter.AdminUser(
                            uid = uid,
                            fullName = fullName,
                            email = email,
                            phone = phone,
                            bloodGroup = bloodGroup,
                            status = status
                        )


                    allUsers.add(
                        user
                    )
                }


                // =============================================
                // SHOW USERS
                // =============================================

                userAdapter.updateUsers(
                    allUsers
                )

                updateEmptyState(
                    allUsers.isEmpty()
                )
            }

            .addOnFailureListener { exception ->

                Toast.makeText(
                    this,
                    "Failed to load users: ${
                        exception.message ?: "Unknown error"
                    }",
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    // =========================================================
    // SEARCH USERS
    // =========================================================

    private fun filterUsers(
        query: String
    ) {

        if (query.isEmpty()) {

            userAdapter.updateUsers(
                allUsers
            )

            updateEmptyState(
                allUsers.isEmpty()
            )

            return
        }


        val filteredUsers =
            allUsers.filter { user ->

                user.fullName
                    .lowercase()
                    .contains(query) ||

                        user.uid
                            .lowercase()
                            .contains(query) ||

                        user.bloodGroup
                            .lowercase()
                            .contains(query) ||

                        user.email
                            .lowercase()
                            .contains(query)
            }


        userAdapter.updateUsers(
            filteredUsers
        )

        updateEmptyState(
            filteredUsers.isEmpty()
        )
    }


    // =========================================================
    // EMPTY STATE
    // =========================================================

    private fun updateEmptyState(
        isEmpty: Boolean
    ) {

        if (isEmpty) {

            txtEmptyUsers.visibility =
                android.view.View.VISIBLE

            recyclerUsers.visibility =
                android.view.View.GONE

        } else {

            txtEmptyUsers.visibility =
                android.view.View.GONE

            recyclerUsers.visibility =
                android.view.View.VISIBLE
        }
    }


    // =========================================================
    // OPEN USER DETAILS
    // =========================================================

    private fun openUserDetails(
        userId: String
    ) {

        val intent =
            Intent(
                this,
                AdminUserDetailsActivity::class.java
            )

        intent.putExtra(
            "USER_ID",
            userId
        )

        startActivity(
            intent
        )
    }


    // =========================================================
    // REFRESH WHEN RETURNING
    // =========================================================

    override fun onResume() {
        super.onResume()

        if (::firestore.isInitialized) {

            loadUsers()
        }
    }
}