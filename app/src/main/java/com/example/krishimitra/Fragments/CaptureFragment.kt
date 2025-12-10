package com.example.krishimitra.Fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.krishimitra.R
import com.example.krishimitra.databinding.FragmentCaptureBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.flex.FlexDelegate
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CaptureFragment : Fragment() {
    private lateinit var binding: FragmentCaptureBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File

    // Background executor
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // TFLite
    private lateinit var tflite: Interpreter
    private var modelLoaded = false

    // ML Kit Image Labeler
    private lateinit var imageLabeler: ImageLabeler

    private lateinit var cameraControl: CameraControl
    private lateinit var cameraInfo: androidx.camera.core.CameraInfo

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCaptureBinding.inflate(inflater, container, false)

        // Initialize ML Kit Image Labeler
        imageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

        // Permissions
        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(
            requireActivity(), REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
        )

        // Capture
        binding.cameraCaptureButton.setOnClickListener {
            binding.laoder.visibility = View.VISIBLE
            binding.flashlight.visibility = View.GONE
            binding.cameraCaptureButton.visibility = View.GONE
            binding.photoUpload.visibility = View.GONE
            requireActivity().findViewById<BottomNavigationView>(
                R.id.bottomNavigationView
            ).visibility = View.GONE
            takePhoto()
        }

        // Gallery
        binding.photoUpload.setOnClickListener { openGallery() }

        // Torch
        binding.flashlight.setOnClickListener {
            if (cameraInfo.torchState.value == 0) {
                cameraControl.enableTorch(true)
                binding.flashlight.setImageResource(R.drawable.flashlight_on)
            } else {
                cameraControl.enableTorch(false)
                binding.flashlight.setImageResource(R.drawable.flashlight_off)
            }
        }

        outputDirectory = getOutputDirectory()

        // Load model in bg
        if (cameraExecutor.isShutdown) cameraExecutor = Executors.newSingleThreadExecutor()
        cameraExecutor.execute { loadModel() }

        return binding.root
    }

    @Throws(IOException::class)
    private fun loadModelFile(): MappedByteBuffer {
        val afd = requireContext().assets.openFd("model.tflite")
        FileInputStream(afd.fileDescriptor).use { input ->
            val channel = input.channel
            return channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
        }
    }

    private fun loadModel() {
        if (modelLoaded) return
        try {
            val options = Interpreter.Options().apply { addDelegate(FlexDelegate()) }
            tflite = Interpreter(loadModelFile(), options)
            modelLoaded = true
            requireActivity().runOnUiThread {
                binding.laoder.visibility = View.GONE
                Log.d(TAG, "Model loaded successfully")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error loading model: ${e.message}")
            requireActivity().runOnUiThread {
                Toast.makeText(
                    requireContext(),
                    "Error loading model",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val galleryResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                result.data?.data?.let { uri -> classifyImage(uri, isFromGallery = true) }
                    ?: Toast.makeText(requireContext(), "Error loading image", Toast.LENGTH_SHORT)
                        .show()
            }
        }

    private fun openGallery() {
        Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
            galleryResultLauncher.launch(this)
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        capture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: androidx.camera.core.ImageProxy) {
                    try {
                        val bitmap = imageProxy.toBitmap()
                        classifyImage(bitmap, isFromCamera = true)
                    } finally {
                        imageProxy.close()
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}")
                    requireActivity().runOnUiThread {
                        binding.laoder.visibility = View.GONE
                        binding.flashlight.visibility = View.VISIBLE
                        binding.cameraCaptureButton.visibility = View.VISIBLE
                        binding.photoUpload.visibility = View.VISIBLE
                        requireActivity().findViewById<BottomNavigationView>(
                            R.id.bottomNavigationView
                        ).visibility = View.VISIBLE
                        Toast.makeText(
                            requireContext(),
                            "Error capturing photo",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })
    }

    // Convert ImageProxy to Bitmap
    private fun androidx.camera.core.ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // Modified classifyImage with ML Kit Image Labeling
    private fun classifyImage(source: Any, isFromCamera: Boolean = false, isFromGallery: Boolean = false) {
        try {
            val bitmap = when {
                isFromCamera -> source as Bitmap
                isFromGallery -> BitmapFactory.decodeStream(
                    requireContext().contentResolver.openInputStream(source as Uri)
                )
                else -> throw IllegalArgumentException("Invalid source type")
            }
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            // Step 1: Use ML Kit to check for Plant/Crops/Leaves
            imageLabeler.process(inputImage)
                .addOnSuccessListener { labels ->
                    Log.d(TAG, "ML Kit labels: $labels")
                    var isPlantRelated = false
                    for (label in labels) {
                        val text = label.text.lowercase()
                        Log.d(TAG, "ML Kit label: $text")
                        // Check for plant-related labels
                        if (text.contains("plant") || text.contains("leaf") || text.contains("crop") ||
                            text.contains("tree") || text.contains("vegetable") || text.contains("fruit")) {
                            isPlantRelated = true
                            break
                        }
                    }

                    if (isPlantRelated) {
                        // Step 2: Save the image if from camera and proceed with TFLite inference
                        val imageUri = if (isFromCamera) {
                            val photoFile = File(
                                outputDirectory,
                                SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                                    .format(System.currentTimeMillis()) + ".jpg"
                            )
                            photoFile.outputStream().use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                            }
                            Uri.fromFile(photoFile)
                        } else {
                            source as Uri
                        }

                        // Step 3: Proceed with TensorFlow Lite model inference
                        val preprocessedImage = preprocessImage(bitmap)
                        val output = runInference(preprocessedImage)
                        logAllScores(output)
                        val label = interpretOutput(output)
                        navigateToResultScreen(imageUri, label)
                    } else {
                        // Image does not contain plant-related content
                        requireActivity().runOnUiThread {
                            binding.laoder.visibility = View.GONE
                            binding.flashlight.visibility = View.VISIBLE
                            binding.cameraCaptureButton.visibility = View.VISIBLE
                            binding.photoUpload.visibility = View.VISIBLE
                            requireActivity().findViewById<BottomNavigationView>(
                                R.id.bottomNavigationView
                            ).visibility = View.VISIBLE
                            Toast.makeText(
                                requireContext(),
                                "Please capture an image of a plant or crop",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit labeling failed: ${e.message}")
                    requireActivity().runOnUiThread {
                        binding.laoder.visibility = View.GONE
                        binding.flashlight.visibility = View.VISIBLE
                        binding.cameraCaptureButton.visibility = View.VISIBLE
                        binding.photoUpload.visibility = View.VISIBLE
                        requireActivity().findViewById<BottomNavigationView>(
                            R.id.bottomNavigationView
                        ).visibility = View.VISIBLE
                        Toast.makeText(
                            requireContext(),
                            "Error processing image",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Classification error: ${e.message}")
            requireActivity().runOnUiThread {
                binding.laoder.visibility = View.GONE
                binding.flashlight.visibility = View.VISIBLE
                binding.cameraCaptureButton.visibility = View.VISIBLE
                binding.photoUpload.visibility = View.VISIBLE
                requireActivity().findViewById<BottomNavigationView>(
                    R.id.bottomNavigationView
                ).visibility = View.VISIBLE
                Toast.makeText(
                    requireContext(),
                    "Error classifying image",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Subtract mean only, BGR order — **no** scaling!
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val resized =
            Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val buffer =
            ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4)
                .order(ByteOrder.nativeOrder())

        for (y in 0 until 224)
            for (x in 0 until 224) {
                val px = resized.getPixel(x, y)
                val r = (px shr 16 and 0xFF).toFloat()
                val g = (px shr 8 and 0xFF).toFloat()
                val b = (px and 0xFF).toFloat()

                // Caffe‐style preprocess:
                buffer.putFloat(b - 103.939f)
                buffer.putFloat(g - 116.779f)
                buffer.putFloat(r - 123.68f)
            }

        buffer.rewind()
        return buffer
    }

    private fun runInference(input: ByteBuffer): FloatArray {
        val shape = tflite.getOutputTensor(0).shape()
        val size = shape.reduce { acc, v -> acc * v }
        val outBuf = ByteBuffer.allocateDirect(size * 4)
            .order(ByteOrder.nativeOrder())
        tflite.run(input, outBuf)
        outBuf.rewind()
        return FloatArray(size) { i -> outBuf.float }
    }

    private fun logAllScores(output: FloatArray) {
        getLabels().forEachIndexed { i, lbl ->
            Log.d("Prediction", "Label $lbl = ${output[i]*100}")
        }
    }

    private fun interpretOutput(output: FloatArray): String {
        val idx = output.indices.maxByOrNull { output[it] } ?: -1
        return getLabels().getOrElse(idx) { "Unknown" }
    }

    private fun getLabels() = listOf(
        // 🍎 Apple
        "🍎 Apple - Apple Scab",
        "🍎 Apple - Black Rot",
        "🍎 Apple - Cedar Apple Rust",
        "🍎 Apple - Healthy",

        // 🥒 Bitter Gourd
        "🥒 Bitter Gourd - Downy Mildew",
        "🥒 Bitter Gourd - Healthy",
        "🥒 Bitter Gourd - Jassid",
        "🥒 Bitter Gourd - Leaf Spot",
        "🥒 Bitter Gourd - Nitrogen Deficiency",
        "🥒 Bitter Gourd - Nitrogen & Magnesium Deficiency",
        "🥒 Bitter Gourd - Nitrogen & Potassium Deficiency",
        "🥒 Bitter Gourd - Potassium Deficiency",
        "🥒 Bitter Gourd - Potassium & Magnesium Deficiency",

        // 🫐 Blueberry
        "🫐 Blueberry - Healthy",

        // 🍒 Cherry
        "🍒 Cherry - Powdery Mildew",
        "🍒 Cherry - Healthy",

        // 🌽 Corn
        "🌽 Corn - Cercospora Leaf Spot / Gray Leaf Spot",
        "🌽 Corn - Common Rust",
        "🌽 Corn - Northern Leaf Blight",
        "🌽 Corn - Healthy",

        // 🍆 Eggplant
        "🍆 Eggplant - Aphids",
        "🍆 Eggplant - Cercospora Leaf Spot",
        "🍆 Eggplant - Flea Beetles",
        "🍆 Eggplant - Healthy",
        "🍆 Eggplant - Leaf Wilt",
        "🍆 Eggplant - Phytophthora Blight",
        "🍆 Eggplant - Powdery Mildew",
        "🍆 Eggplant - Tobacco Mosaic Virus",

        // 🍇 Grape
        "🍇 Grape - Black Rot",
        "🍇 Grape - Esca (Black Measles)",
        "🍇 Grape - Leaf Blight (Isariopsis Leaf Spot)",
        "🍇 Grape - Healthy",

        // 🥬 Lettuce
        "🥬 Lettuce - Bacterial Infection",
        "🥬 Lettuce - Fungal Infection",
        "🥬 Lettuce - Healthy",

        // 🍊 Orange
        "🍊 Orange - Huanglongbing (Citrus Greening)",

        // 🍈 Papaya
        "🍈 Papaya - Anthracnose Disease",
        "🍈 Papaya - Black Spot Disease",
        "🍈 Papaya - Healthy",
        "🍈 Papaya - Powdery Mildew Disease",
        "🍈 Papaya - Ring Spot Disease",
        "🍈 Papaya - Phytophthora Disease",

        // 🍑 Peach
        "🍑 Peach - Bacterial Spot",
        "🍑 Peach - Healthy",

        // 🫑 Bell Pepper
        "🫑 Bell Pepper - Bacterial Spot",
        "🫑 Bell Pepper - Healthy",

        // 🌿 Pigeon Pea
        "🌿 Pigeon Pea - Healthy",
        "🌿 Pigeon Pea - Leaf Spot",
        "🌿 Pigeon Pea - Leaf Webber",
        "🌿 Pigeon Pea - Sterility Mosaic",

        // 🥔 Potato
        "🥔 Potato - Early Blight",
        "🥔 Potato - Late Blight",
        "🥔 Potato - Healthy",

        // 🍓 Strawberry
        "🍓 Strawberry - Leaf Scorch",
        "🍓 Strawberry - Healthy",

        // 🫘 Soybean
        "🫘 Soybean - Healthy",

        // 🎃 Squash & Sweet Pumpkin
        "🎃 Squash - Powdery Mildew",
        "🎃 Sweet Pumpkin - Downy Mildew Disease",
        "🎃 Sweet Pumpkin - Healthy",
        "🎃 Sweet Pumpkin - Leaf Curl Disease",
        "🎃 Sweet Pumpkin - Mosaic Disease",
        "🎃 Sweet Pumpkin - Red Beetle",

        // 🍅 Tomato
        "🍅 Tomato - Bacterial Spot",
        "🍅 Tomato - Early Blight",
        "🍅 Tomato - Late Blight",
        "🍅 Tomato - Leaf Mold",
        "🍅 Tomato - Septoria Leaf Spot",
        "🍅 Tomato - Spider Mites (Two-Spotted)",
        "🍅 Tomato - Target Spot",
        "🍅 Tomato - Yellow Leaf Curl Virus",
        "🍅 Tomato - Mosaic Virus",
        "🍅 Tomato - Healthy"
    )

    private fun navigateToResultScreen(imageUri: Uri, label: String) {
        val action = CaptureFragmentDirections
            .actionCaptureFragmentToResultFragment(imageUri.toString(), label)
        findNavController().navigate(action)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(
                    view?.findViewById<PreviewView>(R.id.viewFinder)
                        ?.surfaceProvider
                )
            }
            imageCapture = ImageCapture.Builder().build()
            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
            )
            cameraControl = camera.cameraControl
            cameraInfo = camera.cameraInfo
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = context?.externalMediaDirs?.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return mediaDir?.takeIf { it.exists() }
            ?: requireContext().filesDir
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS && allPermissionsGranted()) {
            startCamera()
        } else {
            Toast.makeText(
                requireContext(),
                "Permission not granted",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        imageLabeler.close()
    }

    companion object {
        private const val TAG = "CaptureFragment"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 20
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}