package com.android.har

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.android.har.callbacks.PoseOutputListener
import com.android.har.databinding.ActivityCameraBinding
import com.android.har.ml.ObjectDetection
import com.android.har.models.PoseOutput
import com.android.har.utils.PermissionUtils
import com.android.har.utils.Utils
import com.android.har.utils.Utils.FRAME_SIZE
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity(), PoseOutputListener {
    private val TAG = "CameraActivity"

    private val sharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    private lateinit var binding: ActivityCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Check if the app is opening for first time to request for permissions
        PreferenceManager.getDefaultSharedPreferences(this).apply {
            getBoolean("is_initial", true).let {
                if (it) {
                    requestPermissions(
                        PermissionUtils.REQUIRED_PERMISSIONS,
                        PermissionUtils.ALL_PERMISSIONS_REQUEST_CODE
                    )
                    edit().putBoolean("is_initial", false).apply()
                }
            }
        }

        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraExecutor = Executors.newSingleThreadExecutor()

            val preview = Preview.Builder()
                .build().apply {
                    setSurfaceProvider(binding.wrapper.previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(FRAME_SIZE, FRAME_SIZE))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().apply {
                    setAnalyzer(cameraExecutor, PoseAnalyzer(this@CameraActivity))
                }

            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = getCameraSelector(cameraProvider)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (exception: Exception) {
                Log.e(TAG, "startCamera: binding failed", exception)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun getCameraSelector(cameraProvider: ProcessCameraProvider): CameraSelector {
        return if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
            CameraSelector.DEFAULT_BACK_CAMERA
        else
            CameraSelector.DEFAULT_FRONT_CAMERA
    }

    override fun updatePoseResult(
        items: List<PoseOutput>,
        detectionResults: List<ObjectDetection.DetectionResult>
    ) {
        runOnUiThread {
            val result = detectionResults[0]
            val category = result.categoryAsString

            val rect = if (category.equals("person", true)) {
                binding.actionLabel.text = Utils.processList(items, sharedPreferences)
                detectionResults[0].locationAsRectF
            } else {
                binding.actionLabel.text = ""
                RectF()
            }
            binding.wrapper.drawRect(rect)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PermissionUtils.ALL_PERMISSIONS_REQUEST_CODE -> {
                for (i in permissions.indices) {
                    if (grantResults[permissions.indexOf(PermissionUtils.REQUIRED_PERMISSIONS[i])] != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "${permissions[i]} denied.")
                    }
                }
            }
            PermissionUtils.CAMERA_PERMISSION_REQUEST_CODE,
            PermissionUtils.STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "${permissions[0]} denied.")
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        }
    }

    fun finishCameraActivity(view: View) {
        onBackPressed()
    }

    fun openSettings(view: View) {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}
