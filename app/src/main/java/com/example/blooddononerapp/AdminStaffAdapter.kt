package com.example.blooddononerapp

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AdminStaffAdapter(
    private var staffMembers: MutableList<StaffMember>,
    private val onStaffClick: (StaffMember) -> Unit
) : RecyclerView.Adapter<AdminStaffAdapter.StaffViewHolder>() {


    // =========================================================
    // STAFF MODEL
    // =========================================================

    data class StaffMember(

        val uid: String,

        val fullName: String,

        val email: String,

        val phone: String,

        val hospital: String,

        val status: String
    )


    // =========================================================
    // VIEW HOLDER
    // =========================================================

    class StaffViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val txtStaffName: TextView =
            itemView.findViewById(
                R.id.txtItemStaffName
            )

        val txtStaffInfo: TextView =
            itemView.findViewById(
                R.id.txtItemStaffInfo
            )

        val txtStaffStatus: TextView =
            itemView.findViewById(
                R.id.txtItemStaffStatus
            )
    }


    // =========================================================
    // CREATE VIEW HOLDER
    // =========================================================

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): StaffViewHolder {

        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(
                    R.layout.item_admin_staff,
                    parent,
                    false
                )


        return StaffViewHolder(
            view
        )
    }


    // =========================================================
    // BIND
    // =========================================================

    override fun onBindViewHolder(
        holder: StaffViewHolder,
        position: Int
    ) {

        val staff =
            staffMembers[position]


        holder.txtStaffName.text =
            staff.fullName


        val displayId =
            if (staff.uid.length > 8) {

                "STF-${staff.uid.take(8).uppercase()}"

            } else {

                "STF-${staff.uid.uppercase()}"
            }


        holder.txtStaffInfo.text =
            "$displayId • ${staff.hospital}"


        holder.txtStaffStatus.text =
            staff.status


        // =====================================================
        // STATUS COLOR
        // =====================================================

        if (
            staff.status.equals(
                "Active",
                ignoreCase = true
            )
        ) {

            holder.txtStaffStatus.setTextColor(
                Color.parseColor(
                    "#15803D"
                )
            )

        } else {

            holder.txtStaffStatus.setTextColor(
                Color.parseColor(
                    "#DC2626"
                )
            )
        }


        // =====================================================
        // CLICK
        // =====================================================

        holder.itemView.setOnClickListener {

            onStaffClick(
                staff
            )
        }
    }


    // =========================================================
    // COUNT
    // =========================================================

    override fun getItemCount(): Int {

        return staffMembers.size
    }


    // =========================================================
    // UPDATE LIST
    // =========================================================

    fun updateStaff(
        newStaff: List<StaffMember>
    ) {

        staffMembers.clear()

        staffMembers.addAll(
            newStaff
        )

        notifyDataSetChanged()
    }
}