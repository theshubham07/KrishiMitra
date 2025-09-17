package com.example.krishimitra.Fragments

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.navigation.fragment.findNavController
import com.example.krishimitra.ApiInterface
import com.example.krishimitra.R
import com.example.krishimitra.common.CROP_IMAGES_FOLDER_PATH
import com.example.krishimitra.common.CROP_PATH
import com.example.krishimitra.common.uploadImage
import com.example.krishimitra.databinding.FragmentResultBinding
import com.example.krishimitra.models.CropCaptureModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.krishimitra.data.WeatherApp
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ResultFragment : Fragment() {

    private lateinit var binding: FragmentResultBinding
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var auth: FirebaseAuth
    private lateinit var cropCaptureModel: CropCaptureModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("ResultFrag", "ResultFragment onCreate called")

        binding = FragmentResultBinding.inflate(inflater, container, false)
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())

        val args = arguments?.let { ResultFragmentArgs.fromBundle(it) }
        val label = args?.label
        val imageUri = args?.imageUri
        val uri = Uri.parse(imageUri)

        // Initialize cropCaptureModel with docId
        val docId = FirebaseFirestore.getInstance().collection(CROP_PATH).document().id
        cropCaptureModel = CropCaptureModel(docId = docId)

        binding.imageView.setImageURI(Uri.parse(imageUri))
        cropCaptureModel.predictedDiseaseName = label

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser?.uid
        cropCaptureModel.capturedBy = user

        uploadImage(CROP_IMAGES_FOLDER_PATH, uri) { success, imageUrl ->
            if (success) {
                cropCaptureModel.cropImageUrl = imageUrl
                cropCaptureModel.timestamp = getCurrentDate()
                binding.textView.text = label
                binding.backButton.visibility = View.VISIBLE
                binding.loader.visibility = View.GONE

                if (checkLocationPermission()) {
                    getUserLocationAndAddress()
                } else {
                    requestForPermission()
                }
            } else {
                Toast.makeText(requireContext(), "Error uploading image", Toast.LENGTH_SHORT).show()
            }
        }

        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
            // Now show the Bottom Navigation bar manually
            val bottomNavigationView = requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigationView)
            bottomNavigationView.visibility = View.VISIBLE
        }

        return binding.root
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestForPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            101
        )
    }

    private fun getUserLocationAndAddress() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                getAddressFromLocation(location.latitude, location.longitude)
            } else {
                Toast.makeText(
                    requireContext(),
                    "Unable to get current location",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun fetchWeatherData(latitude: Double, longitude: Double) {
        Log.d("Weather", "Fetching weather data for latitude: $latitude, longitude: $longitude")

        val retrofit = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .build()
            .create(ApiInterface::class.java)

        try {
            val ai: ApplicationInfo = requireContext().packageManager
                .getApplicationInfo(requireContext().packageName, PackageManager.GET_META_DATA)
            val apiKey = ai.metaData["weather_api_id"]?.toString()

            if (apiKey.isNullOrEmpty()) {
                Log.e("Weather", "API key not found in metadata")
                return
            }

            val response = retrofit.getWeatherData(
                latitude.toString(),
                longitude.toString(),
                apiKey,
                "metric"
            )

            response.enqueue(object : Callback<WeatherApp> {
                override fun onResponse(call: Call<WeatherApp>, response: Response<WeatherApp>) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()

                        if (responseBody != null) {
                            cropCaptureModel.apply {
                                temperature = responseBody.main.temp
                                coordinates = "${responseBody.coord.lat},${responseBody.coord.lon}"
                                place = responseBody.name
                                pressure = responseBody.main.pressure
                                wind = responseBody.wind.speed
                                humidity = responseBody.main.humidity
                            }
                            GlobalScope.launch(Dispatchers.IO) {
                                saveCropCaptureToFirestore()
                            }
                        } else {
                            Log.e("Weather", "Response body is null")
                        }
                    } else {
                        Log.e("Weather", "API error: ${response.code()} ${response.message()}")
                    }
                }

                override fun onFailure(call: Call<WeatherApp>, t: Throwable) {
                    Log.e("Weather", "API call failed: ${t.message}")
                }
            })
        } catch (e: Exception) {
            Log.e("Weather", "Exception: ${e.message}", e)
        }
    }

    private fun getAddressFromLocation(latitude: Double, longitude: Double) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]
                cropCaptureModel.captureLocation = buildAddressString(address)

                if (cropCaptureModel.predictedDiseaseName != "Can't Identify..") {
                    GlobalScope.launch(Dispatchers.IO) {
                        fetchWeatherData(latitude, longitude)
                    }
                } else {
                    Toast.makeText(context, "It will not be saved on database", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("Geocoding", "Geocoder service not available.")
        }
    }

    private fun buildAddressString(address: Address): String {
        val addressLines = StringBuilder()
        for (i in 0..address.maxAddressLineIndex) {
            addressLines.append(address.getAddressLine(i)).append("\n")
        }
        return addressLines.toString()
    }

    private suspend fun saveCropCaptureToFirestore() {
        FirebaseFirestore.getInstance().collection(CROP_PATH)
            .document(cropCaptureModel.docId)
            .set(cropCaptureModel)
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    Log.d("Firestore", "Capture saved successfully!")
                } else {
                    Toast.makeText(
                        requireContext(),
                        "${it.exception?.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun getCurrentDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    fun File.delete(context: Context): Boolean {
        var selectionArgs = arrayOf(this.absolutePath)
        val contentResolver = context.contentResolver
        var where: String? = null
        var filesUri: Uri? = null
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            filesUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            where = MediaStore.Images.Media._ID + "=?"
            selectionArgs = arrayOf(this.name)
        } else {
            where = MediaStore.MediaColumns.DATA + "=?"
            filesUri = MediaStore.Files.getContentUri("external")
        }

        val deleted = contentResolver.delete(filesUri!!, where, selectionArgs)
        return !this.exists()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (cropCaptureModel.predictedDiseaseName == "Can't Identify..") {
            val picRef = cropCaptureModel.cropImageUrl
            val storageRef = picRef?.let { Firebase.storage.getReferenceFromUrl(it) }
            storageRef?.delete()?.addOnSuccessListener {}?.addOnFailureListener {}
        }

        val args = arguments?.let { ResultFragmentArgs.fromBundle(it) }
        val imageUri = args?.imageUri
        val uri = Uri.parse(imageUri)

        val fileToDelete = uri.path?.let { File(it) }
        fileToDelete?.let {
            if (it.exists()) {
                if (it.delete()) {
                    if (it.exists()) {
                        it.canonicalFile.delete()
                        if (it.exists()) {
                            context?.deleteFile(it.name)
                        }
                    }
                    Log.e("delete", "File Deleted ${uri.path}")
                } else {
                    Log.e("delete", "File not Deleted ${uri.path}")
                }
            }
        }
    }
}
