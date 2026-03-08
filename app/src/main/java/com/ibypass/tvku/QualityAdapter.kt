package com.ibypass.tvku

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class QualityAdapter(
    private var qualityOptions: List<QualityOption>,
    private val onQualitySelected: (QualityOption) -> Unit
) : RecyclerView.Adapter<QualityAdapter.QualityViewHolder>() {

    private var selectedPosition = qualityOptions.indexOfFirst { it.isSelected }

    inner class QualityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val radioButton: RadioButton = itemView.findViewById(R.id.rb_quality)
        val qualityName: TextView = itemView.findViewById(R.id.tv_quality_name)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    updateSelection(position)
                    onQualitySelected(qualityOptions[position])
                }
            }

            radioButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    updateSelection(position)
                    onQualitySelected(qualityOptions[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QualityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quality_option, parent, false)
        return QualityViewHolder(view)
    }

    override fun onBindViewHolder(holder: QualityViewHolder, position: Int) {
        val option = qualityOptions[position]
        holder.qualityName.text = option.name
        holder.radioButton.isChecked = position == selectedPosition
        holder.itemView.isSelected = position == selectedPosition
    }

    override fun getItemCount() = qualityOptions.size

    private fun updateSelection(newPosition: Int) {
        val oldPosition = selectedPosition
        selectedPosition = newPosition

        if (oldPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(oldPosition)
        }
        notifyItemChanged(newPosition)
    }

    fun updateData(newOptions: List<QualityOption>) {
        qualityOptions = newOptions
        selectedPosition = qualityOptions.indexOfFirst { it.isSelected }
        notifyDataSetChanged()
    }
}