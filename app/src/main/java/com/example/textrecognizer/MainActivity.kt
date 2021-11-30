package com.example.textrecognizer

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.textrecognizer.databinding.ActivityMainBinding
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.content.Intent
import android.view.Menu
import android.view.MenuItem

const val PREVIEW_STATE = "PreviewState"
const val IMAGE_STATE = "ImageState"

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recognizer: TextRecognizer
    private val TAG = "Testing"
    private val SAVED_TEXT_TAG = "SavedText"
    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    lateinit var camera: Camera
    var state = ""

    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        if (savedInstanceState != null) {
            binding.scrollView.visibility = View.VISIBLE
            binding.textInImage.text = savedInstanceState.getString(SAVED_TEXT_TAG)
        }

        init()
        setContentView(binding.root)
    }

    private fun init() {

        cameraExecutor = Executors.newSingleThreadExecutor()

        recognizer = TextRecognition.getClient()

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                showToast(
                    getString(R.string.permission_denied_msg)
                )
            }
        }
    }

    /*override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.input) {
            state = when (state) {
                PREVIEW_STATE -> {
                    item.setIcon(R.drawable.ic_photo_camera)
                    IMAGE_STATE
                }
                IMAGE_STATE -> {
                    item.setIcon(R.drawable.ic_image)
                    PREVIEW_STATE
                }
                else -> {
                    item.setIcon(R.drawable.ic_photo_camera)
                    IMAGE_STATE
                }
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }*/

    private fun runTextRecognition(bitmap: Bitmap) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        recognizer
            .process(inputImage)
            .addOnSuccessListener { text ->
                binding.scrollView.visibility = View.VISIBLE
                processTextRecognitionResult(text)
            }.addOnFailureListener { e ->
                e.printStackTrace()
                showToast(e.localizedMessage ?: getString(R.string.error_default_msg))
            }
    }

    private fun processTextRecognitionResult(result: Text) {
        var finalText = ""
        for (block in result.textBlocks) {
            for (line in block.lines) {
                finalText += line.text + " \n"
            }
            finalText += "\n"
        }

        Log.d(TAG, finalText)
        Log.d(TAG, result.text)

        binding.textInImage.text = if (finalText.isNotEmpty()) {
            finalText
        } else {
            getString(R.string.no_text_found)
        }

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview
                )


                binding.apply {
                    // Set up the listener for take photo button
                    cameraCaptureButton.setOnClickListener {
                        if (viewFinder.bitmap != null) {
                            runTextRecognition(viewFinder.bitmap!!)
                        } else {
                            showToast(getString(R.string.camera_error_default_msg))
                        }
                    }

                    camera.apply {
                        if (cameraInfo.hasFlashUnit()) {
                            torchButton.setOnClickListener {
                                cameraControl.enableTorch(cameraInfo.torchState.value == TorchState.OFF)
                            }
                        } else {
                            torchButton.setOnClickListener {
                                showToast(getString(R.string.torch_not_available_msg))
                            }
                        }

                        cameraInfo.torchState.observe(this@MainActivity) { torchState ->
                            if (torchState == TorchState.OFF) {
                                torchButton.setImageResource(R.drawable.ic_flashlight_on)
                            } else {
                                torchButton.setImageResource(R.drawable.ic_flashlight_off)
                            }
                        }

                    }


                    copyToClipboard.setOnClickListener {
                        val textToCopy = textInImage.text
                        if (textToCopy.isNotEmpty() and (textToCopy != getString(R.string.no_text_found))) {
                            copyToClipboard(textToCopy)
                        } else {
                            showToast(getString(R.string.no_text_found))
                        }
                    }

                    share.setOnClickListener {
                        val textToCopy = textInImage.text.toString()
                        if (textToCopy.isNotEmpty() and (textToCopy != getString(R.string.no_text_found))) {
                            shareText(textToCopy)
                        } else {
                            showToast(getString(R.string.no_text_found))
                        }
                    }

                    close.setOnClickListener {
                        scrollView.visibility = View.GONE
                    }

                }

            } catch (exc: Exception) {
                showToast(getString(R.string.error_default_msg))
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))

    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun shareText(text: String) {
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_text_title)))
    }

    private fun copyToClipboard(text: CharSequence) {
        val clipboard =
            ContextCompat.getSystemService(applicationContext, ClipboardManager::class.java)
        val clip = ClipData.newPlainText("label", text)
        clipboard?.setPrimaryClip(clip)
        showToast(getString(R.string.clipboard_text))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val textInImage = (binding.textInImage.text).toString()
        if (textInImage.isNotEmpty()) {
            outState.putString(SAVED_TEXT_TAG, textInImage)
        }
    }

}