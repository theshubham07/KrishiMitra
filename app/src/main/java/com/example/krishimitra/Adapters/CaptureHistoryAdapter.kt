package com.example.krishimitra.Adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.krishimitra.CropDetailActivity
import com.example.krishimitra.R
import com.example.krishimitra.databinding.RvItemHistoryBinding
import com.example.krishimitra.models.CropCaptureModel

class CaptureHistoryAdapter(
    private val context: Context,
    private var captureList: ArrayList<CropCaptureModel>
) : RecyclerView.Adapter<CaptureHistoryAdapter.MyViewHolder>() {

    inner class MyViewHolder(val binding: RvItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        MyViewHolder(
            RvItemHistoryBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun getItemCount(): Int = captureList.size

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val curr = captureList[position]

        // 1) Load thumbnail & basic info
        holder.binding.cropimage.load(curr.cropImageUrl) { // ✨ corrected field name here
            crossfade(true)
            crossfade(500)
            placeholder(R.drawable.loader)
        }
        holder.binding.cropname.text    = curr.predictedDiseaseName.orEmpty()
        holder.binding.dateandtime.text = curr.timestamp.orEmpty()
        holder.binding.location.text    = curr.captureLocation.orEmpty()

        // 2) On click → start CropDetailActivity
        holder.itemView.setOnClickListener {
            val diseaseName = curr.predictedDiseaseName.orEmpty()

            // Directly pass the diseaseName, no emoji stripping needed
            val docId = diseaseName
                .substringAfter(" ") // Remove emoji and everything before first space
                .replace(Regex("[^A-Za-z0-9]+"), "_") // Replace non-alphanumerics with underscore
                .replace(Regex("_+"), "_") // Collapse multiple underscores into one
                .trim('_') // Remove leading/trailing underscores

            val intent = Intent(context, CropDetailActivity::class.java).apply {
                putExtra("docId", docId)
                putExtra("predictedDiseaseName", curr.predictedDiseaseName)
                putExtra("cropimage", curr.cropImageUrl) // ✨ corrected field name
                putExtra("location", curr.captureLocation)
                putExtra("captured_on", curr.timestamp)
                putExtra("coordinates", curr.coordinates)
                putExtra("temperature", curr.temperature?.toString())
                putExtra("humidity", curr.humidity?.toString())
                putExtra("pressure", curr.pressure?.toString())
                putExtra("windspeed", curr.wind?.toString())
            }
            context.startActivity(intent)
        }
    }

    fun updateProductList(list: ArrayList<CropCaptureModel>) {
        captureList.clear()
        captureList.addAll(list)
        notifyDataSetChanged()
    }
}
