package com.example.krishimitra.Fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import com.example.krishimitra.R
import com.example.krishimitra.databinding.FragmentAboutBinding
import com.example.krishimitra.fullscreen_image


private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class About : Fragment() {

    private var param1: String? = null
    private var param2: String? = null
    private lateinit var binding: FragmentAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflating the layout for this fragment
        binding = FragmentAboutBinding.inflate(layoutInflater)

        binding.adarshLinkedinBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://www.linkedin.com/in/theshubham07/")
            startActivity(intent)
        }
        binding.adarshLinkedinLogo.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://www.linkedin.com/in/theshubham07/")
            startActivity(intent)
        }
        binding.adarshPic.setOnClickListener {
            val intent = Intent(context, fullscreen_image::class.java)
            // Passing the image resource ID or URI to the new activity
            intent.putExtra(
                "imageResId",
                "https://firebasestorage.googleapis.com/v0/b/krishi-mitra-07.firebasestorage.app/o/Profile_photos%2Fadarsh.png?alt=media&token=f24172d8-f39f-4551-a514-815fd6a4ff80" )
            startActivity(intent)
        }

        binding.anujLinkedinBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://www.linkedin.com/in/anuj-pratap-singh-1bb85730a/")
            startActivity(intent)
        }
        binding.anujLinkedinLogo.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://www.linkedin.com/in/anuj-pratap-singh-1bb85730a/")
            startActivity(intent)
        }
        binding.anujPic.setOnClickListener {
            val intent = Intent(context, fullscreen_image::class.java)
            // Passing the image resource ID or URI to the new activity
            intent.putExtra(
                "imageResId",
                "https://firebasestorage.googleapis.com/v0/b/krishi-mitra-07.firebasestorage.app/o/Profile_photos%2Fanuj.png?alt=media&token=e1398339-ecaa-4c4a-b5d5-3d6ba2bf40a5")
            startActivity(intent)
        }

        binding.aniketLinkedinBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://www.linkedin.com/in/aniket-shukla-9288bb255/")
            startActivity(intent)
        }
        binding.aniketLinkedinLogo.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://www.linkedin.com/in/aniket-shukla-9288bb255/")
            startActivity(intent)
        }
        binding.aniketPic.setOnClickListener {
            val intent = Intent(context, fullscreen_image::class.java)
            // Passing the image resource ID or URI to the new activity
            intent.putExtra(
                "imageResId",
                "https://firebasestorage.googleapis.com/v0/b/krishi-mitra-07.firebasestorage.app/o/Profile_photos%2Faniket.png?alt=media&token=98b98df4-1b48-4054-aa3d-87c9c520a78a")
            startActivity(intent)
        }

        val cropNames = listOf(
            "🍅 Tomato", "🥔 Potato", "🍇 Grape", "🌽 Corn",
            "🍓 Strawberry", "🍆 Eggplant", "🎃 Sweet Pumpkin", "🍊 Orange", "🍑 Peach",
            "🥬 Lettuce", "🍈 Papaya", "🥒 Bitter Gourd", "🫘 Soybean", "🍎 Apple",
            "🪻 Blueberry", "🍒 Cherry", "🫑 Bell Pepper", "🌿 Pigeon Pea",
            "🍓 Raspberry", "🎃 Squash"
        )


        val cropTable: TableLayout = binding.cropTable
        val columnsPerRow = 2

        for (i in cropNames.indices step columnsPerRow) {
            val tableRow = TableRow(context)

            for (j in 0 until columnsPerRow) {
                val index = i + j
                if (index < cropNames.size) {
                    val cropText = TextView(context).apply {
                        text = cropNames[index]
                        textSize = 16f
                        setPadding(16, 12, 16, 12)
                    }
                    tableRow.addView(cropText)
                }
            }

            cropTable.addView(tableRow)
        }

        return binding.root
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment About.
         */

        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            About().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}