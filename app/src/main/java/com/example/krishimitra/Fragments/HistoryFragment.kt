package com.example.krishimitra.Fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.example.krishimitra.Adapters.CaptureHistoryAdapter
import com.example.krishimitra.common.CROP_PATH
import com.example.krishimitra.databinding.FragmentHistoryBinding
import com.example.krishimitra.models.CropCaptureModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class HistoryFragment : Fragment() {

    private lateinit var binding: FragmentHistoryBinding
    private lateinit var auth: FirebaseAuth
    var captList: MutableLiveData<ArrayList<CropCaptureModel>> =
        MutableLiveData(arrayListOf<CropCaptureModel>())
    lateinit var adapter: CaptureHistoryAdapter


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentHistoryBinding.inflate(layoutInflater)
        adapter = CaptureHistoryAdapter(requireContext(), arrayListOf<CropCaptureModel>())

        binding.historyRv.adapter = adapter


        captList.observe(viewLifecycleOwner) { list ->
            adapter.updateProductList(list)
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            getDatafromFirestore()
        }



        return binding.root
    }

    private suspend fun getDatafromFirestore() {

        auth = FirebaseAuth.getInstance()
        val curUser = auth.currentUser?.uid

        Firebase.firestore.collection(CROP_PATH).whereEqualTo("capturedBy", curUser)
            .orderBy("timestamp", Query.Direction.DESCENDING).get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val tempList = arrayListOf<CropCaptureModel>()
                task.result?.documents?.mapNotNullTo(tempList) { it.toObject<CropCaptureModel>() }
                captList.value = tempList

                if (tempList.isEmpty()) {
                    binding.historyRv.visibility = View.GONE
                    binding.loader.visibility = View.GONE
                    binding.noCatch.visibility = View.VISIBLE
                    binding.noCatchText.visibility = View.VISIBLE
                } else {
                    binding.historyRv.visibility = View.VISIBLE
                    binding.noCatch.visibility = View.GONE
                    binding.loader.visibility = View.GONE
                    binding.noCatchText.visibility = View.GONE
                }
            }
        }
    }
}

