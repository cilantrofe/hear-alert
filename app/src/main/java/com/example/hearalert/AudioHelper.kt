package com.example.hearalert

import android.util.Log
import com.jlibrosa.audio.JLibrosa
import kotlin.math.abs

class AudioHelper {
    /**
     * Функция для извлечения MFCC признаков
     */
    fun extractMFCC(filePath: String, threshold: Float = 0.01f): FloatArray? {
        try {
            val jLibrosa = JLibrosa()
            val sampleRate = 22050
            // Читаем аудиофайл. Параметр 4 означает максимальную длительность в сек
            val audioBuffer = jLibrosa.loadAndRead(filePath, sampleRate, 4)

            // Проверка на слишком тихий файл, его нет смысла обрабатывать
            val averageAmplitude = audioBuffer.map { abs(it) }.average().toFloat()
            Log.v("AUDIO_CHECK", "Средняя амплитуда: $averageAmplitude")
            if (averageAmplitude < threshold) {
                Log.v("AUDIO_CHECK", "Слишком тихий файл.")
                return null
            }

            val numMFCC = 40

            // Извлекаем признаки
            val mfccFeatures =
                jLibrosa.generateMFCCFeatures(audioBuffer, sampleRate, numMFCC, 2048, 128, 512)
            // Усредняем их
            val meanMfccFeatures =
                jLibrosa.generateMeanMFCCFeatures(mfccFeatures, numMFCC, 2048)

            return meanMfccFeatures
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("MFCC", "Ошибка при извлечении MFCC: ${e.message}")
            return null
        }
    }
}
