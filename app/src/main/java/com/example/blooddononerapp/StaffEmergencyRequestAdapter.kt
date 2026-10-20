package com.example.blooddononerapp

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

data class StaffEmergencyRequest(

    val documentId: String = "",
    val requestId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val bloodGroup: String = "",
    val units: String = "",
    val province: String = "",
    val district: String = "",
    val area: String = "",
    val hospital: String = "",
    val requiredDate: String = "",
    val requiredBefore: String = "",
    val primaryContact: String = "",
    val secondaryContact: String = "",
    val urgency: String = "",
    val additionalInfo: String = "",
    val status: String = "Pending"
)

class StaffEmergencyRequestAdapter(
    private val requests:
    MutableList<StaffEmergencyRequest>
) : RecyclerView.Adapter<
        StaffEmergencyRequestAdapter.RequestViewHolder
        >() {

    class RequestViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val cardRequest:
                MaterialCardView =
            itemView.findViewById(
                R.id.cardStaffRequest
            )

        val txtBloodGroup:
                TextView =
            itemView.findViewById(
                R.id.txtStaffBloodGroup
            )

        val txtTitle:
                TextView =
            itemView.findViewById(
                R.id.txtStaffRequestTitle
            )

        val txtRequestId:
                TextView =
            itemView.findViewById(
                R.id.txtStaffRequestId
            )

        val txtStatus:
                TextView =
            itemView.findViewById(
                R.id.txtStaffRequestStatus
            )

        val txtHospital:
                TextView =
            itemView.findViewById(
                R.id.txtStaffHospital
            )

        val txtRequester:
                TextView =
            itemView.findViewById(
                R.id.txtStaffRequester
            )

        val txtUnitsUrgency:
                TextView =
            itemView.findViewById(
                R.id.txtStaffUnitsUrgency
            )

        val btnReview:
                MaterialButton =
            itemView.findViewById(
                R.id.btnStaffReviewRequest
            )
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RequestViewHolder {

        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(
                    R.layout.item_staff_emergency_request,
                    parent,
                    false
                )

        return RequestViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: RequestViewHolder,
        position: Int
    ) {

        val request =
            requests[position]

        holder.txtBloodGroup.text =
            request.bloodGroup

        holder.txtTitle.text =
            "${request.bloodGroup} Blood Required"

        holder.txtRequestId.text =
            "REQ-${request.requestId.takeLast(8).uppercase()}"

        holder.txtStatus.text =
            request.status

        holder.txtHospital.text =
            request.hospital

        holder.txtRequester.text =
            "Requester: ${request.userEmail}"

        holder.txtUnitsUrgency.text =
            "${request.units} required  •  ${request.urgency}"

        when (
            request.status.lowercase()
        ) {

            "pending" -> {

                holder.txtStatus.setBackgroundResource(
                    R.drawable.bg_status_pending
                )

                holder.txtStatus.setTextColor(
                    android.graphics.Color.parseColor(
                        "#D97706"
                    )
                )
            }

            "verified" -> {

                holder.txtStatus.setBackgroundResource(
                    R.drawable.bg_status_verified
                )

                holder.txtStatus.setTextColor(
                    android.graphics.Color.parseColor(
                        "#15803D"
                    )
                )
            }

            "matching" -> {

                holder.txtStatus.setBackgroundResource(
                    R.drawable.bg_status_matching
                )

                holder.txtStatus.setTextColor(
                    android.graphics.Color.parseColor(
                        "#2563EB"
                    )
                )
            }

            "fulfilled" -> {

                holder.txtStatus.setBackgroundResource(
                    R.drawable.bg_status_fulfilled
                )

                holder.txtStatus.setTextColor(
                    android.graphics.Color.parseColor(
                        "#15803D"
                    )
                )
            }

            "rejected" -> {

                holder.txtStatus.setBackgroundResource(
                    R.drawable.bg_staff_filter
                )

                holder.txtStatus.setTextColor(
                    android.graphics.Color.parseColor(
                        "#DC2626"
                    )
                )
            }

            else -> {

                holder.txtStatus.setBackgroundResource(
                    R.drawable.bg_staff_filter
                )

                holder.txtStatus.setTextColor(
                    android.graphics.Color.parseColor(
                        "#666666"
                    )
                )
            }
        }

        val openDetails = {

            val intent =
                Intent(
                    holder.itemView.context,
                    StaffRequestDetailsActivity::class.java
                )

            // IMPORTANT:
            // We only pass the Firestore document ID.
            // Details screen reads the real document itself.

            intent.putExtra(
                "REQUEST_DOCUMENT_ID",
                request.documentId
            )

            holder.itemView.context
                .startActivity(intent)
        }

        holder.cardRequest.setOnClickListener {
            openDetails()
        }

        holder.btnReview.setOnClickListener {
            openDetails()
        }
    }

    override fun getItemCount(): Int {
        return requests.size
    }

    fun updateRequests(
        newRequests: List<StaffEmergencyRequest>
    ) {

        requests.clear()
        requests.addAll(newRequests)

        notifyDataSetChanged()
    }
}