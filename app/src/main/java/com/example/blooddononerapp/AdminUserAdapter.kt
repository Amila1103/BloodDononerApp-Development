package com.example.blooddononerapp

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AdminUserAdapter(
    private var users: MutableList<AdminUser>,
    private val onUserClick: (AdminUser) -> Unit
) : RecyclerView.Adapter<AdminUserAdapter.UserViewHolder>() {


    // =========================================================
    // USER MODEL
    // =========================================================

    data class AdminUser(

        val uid: String,

        val fullName: String,

        val email: String,

        val phone: String,

        val bloodGroup: String,

        val status: String
    )


    // =========================================================
    // VIEW HOLDER
    // =========================================================

    class UserViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val txtBloodGroup: TextView =
            itemView.findViewById(
                R.id.txtItemBloodGroup
            )

        val txtName: TextView =
            itemView.findViewById(
                R.id.txtItemUserName
            )

        val txtUserId: TextView =
            itemView.findViewById(
                R.id.txtItemUserId
            )

        val txtStatus: TextView =
            itemView.findViewById(
                R.id.txtItemStatus
            )
    }


    // =========================================================
    // CREATE VIEW HOLDER
    // =========================================================

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): UserViewHolder {

        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(
                    R.layout.item_admin_user,
                    parent,
                    false
                )

        return UserViewHolder(
            view
        )
    }


    // =========================================================
    // BIND DATA
    // =========================================================

    override fun onBindViewHolder(
        holder: UserViewHolder,
        position: Int
    ) {

        val user =
            users[position]


        holder.txtBloodGroup.text =
            user.bloodGroup

        holder.txtName.text =
            user.fullName


        // Show a short readable user ID
        val displayId =
            if (user.uid.length > 8) {

                "USR-${user.uid.take(8).uppercase()}"

            } else {

                "USR-${user.uid.uppercase()}"
            }


        holder.txtUserId.text =
            displayId


        holder.txtStatus.text =
            user.status


        // =====================================================
        // STATUS COLOR
        // =====================================================

        if (
            user.status.equals(
                "Active",
                ignoreCase = true
            )
        ) {

            holder.txtStatus.setTextColor(
                Color.parseColor(
                    "#15803D"
                )
            )

        } else {

            holder.txtStatus.setTextColor(
                Color.parseColor(
                    "#DC2626"
                )
            )
        }


        // =====================================================
        // CLICK
        // =====================================================

        holder.itemView.setOnClickListener {

            onUserClick(
                user
            )
        }
    }


    // =========================================================
    // ITEM COUNT
    // =========================================================

    override fun getItemCount(): Int {

        return users.size
    }


    // =========================================================
    // UPDATE LIST
    // =========================================================

    fun updateUsers(
        newUsers: List<AdminUser>
    ) {

        users.clear()

        users.addAll(
            newUsers
        )

        notifyDataSetChanged()
    }
}