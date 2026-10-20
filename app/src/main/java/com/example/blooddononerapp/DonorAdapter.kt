
package com.example.blooddononerapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class DonorAdapter(
    private val donors: MutableList<Donor>,
    private val onDonorClick: (Donor) -> Unit,
    private val onRequestClick: (Donor) -> Unit
) : RecyclerView.Adapter<DonorAdapter.DonorViewHolder>() {

    class DonorViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val txtDonorName: TextView =
            itemView.findViewById(R.id.txtDonorName)

        val txtDonorLocation: TextView =
            itemView.findViewById(R.id.txtDonorLocation)

        val txtAvailability: TextView =
            itemView.findViewById(R.id.txtAvailability)

        val txtLastActive: TextView =
            itemView.findViewById(R.id.txtLastActive)

        val btnCall: MaterialButton =
            itemView.findViewById(R.id.btnCall)

        val btnRequest: MaterialButton =
            itemView.findViewById(R.id.btnRequest)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DonorViewHolder {

        val view = LayoutInflater.from(
            parent.context
        ).inflate(
            R.layout.item_donor_result,
            parent,
            false
        )

        return DonorViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: DonorViewHolder,
        position: Int
    ) {

        val donor = donors[position]

        holder.txtDonorName.text =
            donor.fullName.ifBlank {
                "Blood Donor"
            }

        val location = listOf(
            donor.area,
            donor.district
        )
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")

        holder.txtDonorLocation.text =
            location.ifBlank {
                "Location not shared"
            }

        holder.txtAvailability.text =
            if (donor.availableToDonate) {
                "● Available for Contact"
            } else {
                "● Not Available"
            }

        holder.txtLastActive.text =
            "Screening required"

        holder.btnCall.text = "Contact"

        holder.btnCall.isEnabled =
            donor.availableToDonate

        holder.btnRequest.isEnabled =
            donor.availableToDonate

        holder.btnCall.alpha =
            if (donor.availableToDonate) 1f else 0.45f

        holder.btnRequest.alpha =
            if (donor.availableToDonate) 1f else 0.45f

        holder.itemView.setOnClickListener {
            onDonorClick(donor)
        }

        holder.btnCall.setOnClickListener {
            onDonorClick(donor)
        }

        holder.btnRequest.setOnClickListener {
            if (donor.availableToDonate) {
                onRequestClick(donor)
            }
        }
    }

    override fun getItemCount(): Int {
        return donors.size
    }

    fun updateDonors(newDonors: List<Donor>) {
        donors.clear()
        donors.addAll(newDonors)
        notifyDataSetChanged()
    }
}
