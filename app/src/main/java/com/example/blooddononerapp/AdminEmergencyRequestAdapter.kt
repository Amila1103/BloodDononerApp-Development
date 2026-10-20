
package com.example.blooddononerapp

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

data class AdminEmergencyRequest(
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

class AdminEmergencyRequestAdapter(
    private val requests: MutableList<AdminEmergencyRequest>,
    private val onRequestClick: (AdminEmergencyRequest) -> Unit
) : RecyclerView.Adapter<AdminEmergencyRequestAdapter.RequestViewHolder>() {

    class RequestViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        val cardRequest: MaterialCardView =
            itemView.findViewById(R.id.cardRequest)

        val txtBloodGroup: TextView =
            itemView.findViewById(R.id.txtItemBloodGroup)

        val txtRequestId: TextView =
            itemView.findViewById(R.id.txtItemRequestId)

        val txtHospital: TextView =
            itemView.findViewById(R.id.txtItemHospital)

        val txtStatusUnits: TextView =
            itemView.findViewById(R.id.txtItemStatusUnits)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RequestViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(
                R.layout.item_admin_emergency_request,
                parent,
                false
            )

        return RequestViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: RequestViewHolder,
        position: Int
    ) {

        val request = requests[position]

        holder.txtBloodGroup.text =
            request.bloodGroup.ifBlank { "--" }

        holder.txtRequestId.text =
            if (request.requestId.isNotBlank()) {
                "REQ-${request.requestId.takeLast(8).uppercase()}"
            } else {
                "REQUEST"
            }

        holder.txtHospital.text =
            request.hospital.ifBlank {
                "Hospital not specified"
            }

        holder.txtStatusUnits.text =
            "${request.status} • ${request.units}"

        when (request.status.lowercase()) {

            "pending" -> {
                holder.txtStatusUnits.setTextColor(
                    Color.parseColor("#EF2637")
                )
                holder.cardRequest.setCardBackgroundColor(
                    Color.parseColor("#FFF1F3")
                )
            }

            "matching" -> {
                holder.txtStatusUnits.setTextColor(
                    Color.parseColor("#2563EB")
                )
                holder.cardRequest.setCardBackgroundColor(
                    Color.WHITE
                )
            }

            "fulfilled" -> {
                holder.txtStatusUnits.setTextColor(
                    Color.parseColor("#15803D")
                )
                holder.cardRequest.setCardBackgroundColor(
                    Color.WHITE
                )
            }

            else -> {
                holder.txtStatusUnits.setTextColor(
                    Color.parseColor("#666666")
                )
                holder.cardRequest.setCardBackgroundColor(
                    Color.WHITE
                )
            }
        }

        holder.cardRequest.setOnClickListener {
            onRequestClick(request)
        }
    }

    override fun getItemCount(): Int {
        return requests.size
    }

    fun updateRequests(
        newRequests: List<AdminEmergencyRequest>
    ) {
        requests.clear()
        requests.addAll(newRequests)
        notifyDataSetChanged()
    }
}
