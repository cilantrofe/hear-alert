package com.example.hearalert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.github.squti.androidwaverecorder.WaveRecorder
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel

class AudioForegroundService : Service() {
    private val CHANNEL_ID = "HearAlertAudioChannel"

    // Флаг записи звука
    private var isRecording = false

    // Для работы с wav
    private lateinit var waveRecorder: WaveRecorder

    // Хэндлер для непрерывной записи аудио
    private val handler =
        Handler(Looper.getMainLooper())

    // Интерпретатор для работы с моделью
    private lateinit var interpreter: Interpreter

    // Класс для работы с аудио
    private lateinit var audioHelper: AudioHelper

    // Класс для работы с уведомлениями
    private lateinit var notificationHelper: NotificationHelper

    // Последний предсказанный класс, на которого пришло уведомление
    private var lastNotifiedClass: Int? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Иницилизируем классы помощники
        notificationHelper = NotificationHelper(this)
        audioHelper = AudioHelper()

        // Загружаем модель .tflite
        interpreter = loadModel()!!
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildServiceNotification())
        isRecording = true
        startSegmentRecording()
        return START_STICKY
    }

    override fun onDestroy() {
        isRecording = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
        waveRecorder = WaveRecorder(outputPath)
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
            if (isRecording) startSegmentRecording()
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

            // Вытаскиваем классы, на которые пользователь хочет получать уведомления
            val prefs = getSharedPreferences("hearalert_prefs", MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("class_$predictedClass", false)

            if (confidence != null) {
                Log.v(
                    "PREDICTIONS",
                    "Обнаружен звук класса $predictedClass (уверенность: ${(confidence * 100).toInt()}%)"
                )
            }

            // Если вероятность более 70% и об этом классе еще не уведомляли(для ограничения спама),
            // то отправляем push уведомление
            if (confidence != null && predictedClass != null) {
                if (confidence > 0.7 && lastNotifiedClass != predictedClass && isEnabled) {
                    notificationHelper.showPushNotification("Обнаружен важный звук", predictedClass)
                    lastNotifiedClass = predictedClass
                }
            } else {
                Log.v(
                    "PUSH",
                    "Низкая вероятность или уже предсказанный класс"
                )
            }
        }

        file.delete()
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

    private fun buildServiceNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HearAlert активен")
            .setContentText("Идёт прослушивание звуков")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "HearAlert Сервис", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
