package com.example.hearalert

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.hearalert.databinding.ActivityMainBinding
import com.github.squti.androidwaverecorder.WaveRecorder
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel

class MainActivity : AppCompatActivity() {

    // Биндинг для упрощенной работы с UI
    private lateinit var binding: ActivityMainBinding

    // Интерпретатор для работы с моделью
    private lateinit var interpreter: Interpreter

    // Флаг записи звука
    private var isRecording = false

    // Последний предсказанный класс, на которого пришло уведомление
    private var lastNotifiedClass: Int? = null

    // Класс для работы с уведомлениями
    private lateinit var notificationHelper: NotificationHelper

    // Хэндлер для непрерывной записи аудио
    private val handler =
        Handler(Looper.getMainLooper())

    // Класс для работы с аудио
    private lateinit var audioHelper: AudioHelper

    // Для проверки разрешений
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

        // Загружаем модель .tflite
        interpreter = loadModel()!!

        // Иницилизируем классы помощники
        notificationHelper = NotificationHelper(this)
        audioHelper = AudioHelper()

        // Запрос разрешения на уведомления
        notificationHelper.requestNotificationPermission()
        // Открытие канала уведомлений
        notificationHelper.createNotificationChannel()

        // Запуск непрерывной записи по нажатию кнопки "Старт"
        binding.buttonStartRecording.setOnClickListener {
            // Проверка на разрешение использование микрофона
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                isRecording = true
                Toast.makeText(this, "Начало записи!", Toast.LENGTH_SHORT).show()
                startSegmentRecording()
            }
        }

        // Остановка непрерывной записи по нажатию кнопки "Стоп"
        binding.buttonStopRecording.setOnClickListener {
            isRecording = false
            Toast.makeText(this, "Запись остановлена!", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Функция для записи сегмента аудио длительностью 6 секунд.
     * После окончания записи выполняется обработка и начинается запись следующего,
     * если isRecording = true.
     */
    private fun startSegmentRecording() {
        if (!isRecording) return

        // Создаем временный файл .wav для его дальнейшей обработки
        val segmentFile = File.createTempFile("segment", ".wav", cacheDir)
        val outputPath = segmentFile.absolutePath

        // Используем библиотеку WaveRecorder для работы с аудио
        val waveRecorder = WaveRecorder(outputPath)
        //waveRecorder.noiseSuppressorActive = true
        // Устанавливаем частоту дискретизации = 22050
        waveRecorder.configureWaveSettings { sampleRate = 22050 }

        // Запускаем запись сегмента
        waveRecorder.startRecording()
        Log.v("PROCESS_SEGMENT", "Запись сегмента началась.")

        // Останавливаем запись через 6 секунд и обрабатываем сегмент
        handler.postDelayed({
            waveRecorder.stopRecording()
            Log.v("PROCESS_SEGMENT", "Сегмент завершён.")
            processSegment(outputPath)
            // Если запись ещё не остановлена, запускаем следующий сегмент
            if (isRecording) {
                startSegmentRecording()
            }
        }, 6000)
    }

    /**
     * Обработка записанного сегмента: извлечение MFCC признаков и предсказание класса.
     */
    private fun processSegment(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e("PROCESS_SEGMENT", "Файл не найден: $filePath")
            return
        }

        // Извлекаем MFCC признаки
        val mfccFeatures = audioHelper.extractMFCC(filePath)
        if (mfccFeatures != null) {
            // Вытаскиваем предсказанный класс и его вероятность
            val (predictedClass, confidence) = predictWithTFLite(mfccFeatures)

            // Если вероятность более 70% и об этом классе еще не уведомляли(для ограничения спама),
            // то отправляем push уведомление
            if (confidence != null && predictedClass != null) {
                if (confidence > 0.7 && lastNotifiedClass != predictedClass && predictedClass in listOf(
                        1,
                        2,
                        3,
                        6,
                        8,
                        9
                    )
                ) {
                    Log.v(
                        "PUSH",
                        "Обнаружен звук класса $predictedClass (уверенность: ${(confidence * 100).toInt()}%)"
                    )
                    notificationHelper.showPushNotification(
                        "Обнаружен важный звук: ",
                        predictedClass
                    )

                    lastNotifiedClass = predictedClass
                } else {
                    Log.v(
                        "PUSH",
                        "Низкая вероятность или уже предсказанный класс"
                    )
                }
            }
        } else {
            Log.w("MFCC", "MFCC для $filePath не были извлечены")
        }

        val deleted = file.delete()
        Log.v("PROCESS_SEGMENT", "Файл ${file.name} удалён: $deleted")
    }

    /**
     * Функция загрузки модели
     * Загружает модель TensorFlow Lite из assets и инициализирует интерпретатор.
     */
    private fun loadModel(): Interpreter? {
        try {
            val modelPath = "sound_classifier_model.tflite"

            // Получаем файловый дескриптор для модели
            val fileDescriptor = assets.openFd(modelPath)

            // Открываем поток для чтения модели
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel

            // Определяем смещение и длину модели в файле
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength

            // Загружаем модель в память с помощью memory-mapped buffer
            val mappedByteBuffer =
                fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            // Возвращаем интерпретатор модели
            return Interpreter(mappedByteBuffer)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("INTERPRETER", "Файл модели поврежден: ${e.message}")
            Toast.makeText(this, "Обратитесь в поддержку.", Toast.LENGTH_SHORT).show()
            return null
        }
    }

    /**
     * Предсказание с использованием модели TFLite
     * Принимает усреднённые MFCC-признаки и возвращает пару (индекс класса, вероятность)
     */
    private fun predictWithTFLite(meanMfccFeatures: FloatArray): Pair<Int?, Float?> {
        try {
            Log.v("DEBUG", "meanMfccFeatures: ${meanMfccFeatures.joinToString(", ")}")

            // Получаем форму входного тензора
            val inputShape = interpreter.getInputTensor(0).shape()

            // Создаём тензор нужной формы и копируем данные MFCC
            val inputData = Array(1) { FloatArray(inputShape[1]) }
            System.arraycopy(meanMfccFeatures, 0, inputData[0], 0, inputShape[1])

            Log.v("DEBUG", "inputData после копирования: ${inputData[0].joinToString(", ")}")

            // Получаем форму выходного тензора
            val outputShape = interpreter.getOutputTensor(0).shape() // Например, [1, 3]
            val outputData = Array(1) { FloatArray(outputShape[1]) }

            // Запускаем модель
            interpreter.run(inputData, outputData)

            Log.v("PREDICTIONS", "Вероятности: ${outputData[0].joinToString(", ")}")

            // Определяем индекс класса с максимальной вероятностью
            val predictedIndex = outputData[0].indices.maxByOrNull { outputData[0][it] } ?: -1
            val confidence = outputData[0][predictedIndex]

            return Pair(predictedIndex, confidence)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("PREDICTIONS", "Ошибка при предсказании: ${e.message}")
            return Pair(null, null)
        }
    }
}