package com.example.hearalert

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.hearalert.databinding.ActivityMainBinding
import com.github.squti.androidwaverecorder.WaveRecorder
import com.jlibrosa.audio.JLibrosa
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var interpreter: Interpreter


    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Permission denied!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val output = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "recording.wav"
        ).absolutePath

        val waveRecorder = WaveRecorder(output)
        waveRecorder.configureWaveSettings { sampleRate = 22050 }

        binding.buttonStartRecording.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                Toast.makeText(this, "Recording started!", Toast.LENGTH_SHORT).show()
                waveRecorder.startRecording()
            }
        }

        binding.buttonStopRecording.setOnClickListener {
            Toast.makeText(this, "Recording stopped!", Toast.LENGTH_SHORT).show()
            waveRecorder.stopRecording()
        }

        binding.buttonPauseRecording.setOnClickListener {
            Toast.makeText(this, "Paused!", Toast.LENGTH_SHORT).show()
            waveRecorder.pauseRecording()
        }

        val modelPath = "sound_classifier_model.tflite"
        interpreter = loadModel(modelPath)

        binding.buttonPredict.setOnClickListener {

            val mfccFeatures = extractMFCC(output)
            if (mfccFeatures != null) {
                val predictedClass = predictWithTFLite(mfccFeatures)
                Toast.makeText(this, "Predicted class: $predictedClass", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to extract MFCC!", Toast.LENGTH_SHORT).show()
            }
        }

//        binding.buttonSettings.setOnClickListener {
//            val intent = Intent(this, SettingsActivity::class.java)
//            startActivity(intent)
//        }
//
//        notificationsButton.setOnClickListener {
//            // Переход на экран уведомлений
//            val intent = Intent(this, NotificationsActivity::class.java)
//            startActivity(intent)
//        }
//
//        helpButton.setOnClickListener {
//            // Переход на экран помощи и поддержки
//            val intent = Intent(this, HelpActivity::class.java)
//            startActivity(intent)
//        }
//
//        aboutButton.setOnClickListener {
//            // Переход на экран о приложении
//            val intent = Intent(this, AboutActivity::class.java)
//            startActivity(intent)
//        }

    }

    private fun loadModel(modelPath: String): Interpreter {
        val fileDescriptor = assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val mappedByteBuffer =
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        return Interpreter(mappedByteBuffer)
    }

    private fun extractMFCC(filePath: String): FloatArray? {
        try {
            val jLibrosa = JLibrosa()
            val sampleRate = 22050
            val audioBuffer = jLibrosa.loadAndRead(filePath, sampleRate, 4)
            val numMFCC = 40

            val mfccFeatures =
                jLibrosa.generateMFCCFeatures(audioBuffer, sampleRate, numMFCC, 2048, 128, 512)

            val meanMfccFeatures = jLibrosa.generateMeanMFCCFeatures(mfccFeatures, numMFCC, 2048)

//            val mfccFeaturesString = meanMfccFeatures.joinToString(",")
//            val file = File(
//                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
//                "mfcc_features_android.txt"
//            )
//            file.writeText(mfccFeaturesString)

            return meanMfccFeatures
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("MFCC Extraction", "Error during MFCC extraction: \${e.message}")
            return null
        }
    }

    private fun predictWithTFLite(meanmfccFeatures: FloatArray?): Int {
        if (meanmfccFeatures == null) {
            throw IllegalArgumentException("MFCC features cannot be null")
        }

        val inputShape = interpreter.getInputTensor(0).shape()
        val inputData = Array(1) { FloatArray(inputShape[1]) }

        System.arraycopy(meanmfccFeatures, 0, inputData[0], 0, inputShape[1])

        val outputShape = interpreter.getOutputTensor(0).shape() // Например, [1, 3]
        val outputData = Array(1) { FloatArray(outputShape[1]) }

        interpreter.run(inputData, outputData)
        Log.v("PREDICTIONS", "Output probabilities: ${outputData[0].joinToString(", ")}")

        return outputData[0].indices.maxByOrNull { outputData[0][it] } ?: -1
    }
}


