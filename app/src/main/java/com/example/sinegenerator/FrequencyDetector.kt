package com.example.sinegenerator

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * 通过麦克风采集音频，用 FFT 估算主频。
 * 采样率 44100Hz，FFT 窗口 8192 点，频率分辨率约 5.4Hz。
 */
class FrequencyDetector(
    private val onFrequency: (Double) -> Unit
) {
    companion object {
        private const val TAG = "FreqDetector"
        private const val SAMPLE_RATE = 44100
        private const val FFT_SIZE = 8192
        private const val FREQ_MIN = 20.0
        private const val FREQ_MAX = 20_000.0
        // RMS 能量阈值（16-bit PCM，满量程 32767）；低于此值视为静音
        private const val RMS_THRESHOLD = 120.0
    }

    @Volatile
    private var running = false
    private var recordThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    val isRunning: Boolean get() = running

    /** 启动检测；若没有麦克风权限或初始化失败，返回 false */
    fun start(): Boolean {
        if (running) return true

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        Log.d(TAG, "minBuf=$minBuf")
        if (minBuf <= 0) {
            Log.e(TAG, "getMinBufferSize failed: $minBuf")
            return false
        }

        val bufferSize = maxOf(minBuf, FFT_SIZE * 2)
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException creating AudioRecord", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating AudioRecord", e)
            return false
        }

        Log.d(TAG, "AudioRecord state=${record.state} (initialized=${AudioRecord.STATE_INITIALIZED})")
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized, state=${record.state}")
            record.release()
            return false
        }

        audioRecord = record
        running = true
        recordThread = Thread({ detectionLoop(record) }, "FreqDetector").apply { start() }
        Log.d(TAG, "Detection started")
        return true
    }

    fun stop() {
        running = false
        recordThread?.interrupt()
        recordThread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun detectionLoop(record: AudioRecord) {
        val buffer = ShortArray(FFT_SIZE)
        val window = DoubleArray(FFT_SIZE) { i ->
            0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1))
        }

        try {
            record.startRecording()
        } catch (_: IllegalStateException) {
            return
        }

        while (running) {
            var totalRead = 0
            while (totalRead < FFT_SIZE) {
                val read = record.read(buffer, totalRead, FFT_SIZE - totalRead)
                if (read <= 0) break
                totalRead += read
            }
            if (totalRead < FFT_SIZE) continue

            // 转为 double 并加 Hanning 窗
            val real = DoubleArray(FFT_SIZE)
            val imag = DoubleArray(FFT_SIZE)
            var sumSq = 0.0
            for (i in 0 until FFT_SIZE) {
                val v = buffer[i] * window[i]
                real[i] = v
                sumSq += buffer[i] * buffer[i].toDouble()
            }

            // 信号能量不足则跳过（静音）：用 RMS 直接判断
            val rms = sqrt(sumSq / FFT_SIZE)
            if (rms < RMS_THRESHOLD) {
                Log.d(TAG, "silent rms=$rms (threshold=$RMS_THRESHOLD)")
                onFrequency(-1.0)
                continue
            }

            fft(real, imag)

            // 在有效频段内找幅值最大的 bin
            val minBin = (FREQ_MIN * FFT_SIZE / SAMPLE_RATE).toInt().coerceAtLeast(1)
            val maxBin = (FREQ_MAX * FFT_SIZE / SAMPLE_RATE).toInt()
                .coerceAtMost(FFT_SIZE / 2 - 1)

            var maxMag = 0.0
            var maxBinIdx = 0
            for (i in minBin..maxBin) {
                val mag = hypot(real[i], imag[i])
                if (mag > maxMag) {
                    maxMag = mag
                    maxBinIdx = i
                }
            }

            // 峰值附近做抛物线插值，提高频率精度
            val freq = if (maxBinIdx in 1 until maxBin) {
                val m0 = hypot(real[maxBinIdx - 1], imag[maxBinIdx - 1])
                val m1 = maxMag
                val m2 = hypot(real[maxBinIdx + 1], imag[maxBinIdx + 1])
                val denom = (m0 - 2 * m1 + m2)
                val delta = if (denom != 0.0) 0.5 * (m0 - m2) / denom else 0.0
                (maxBinIdx + delta) * SAMPLE_RATE.toDouble() / FFT_SIZE
            } else {
                maxBinIdx * SAMPLE_RATE.toDouble() / FFT_SIZE
            }

            Log.d(TAG, "rms=$rms freq=%.1f Hz".format(freq))
            onFrequency(freq)
        }
    }

    /** 原位 Cooley-Tukey 基 2 FFT（输入实数，虚数初始为 0） */
    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tmpR = real[i]; real[i] = real[j]; real[j] = tmpR
                val tmpI = imag[i]; imag[i] = imag[j]; imag[j] = tmpI
            }
        }

        var size = 2
        while (size <= n) {
            val half = size shr 1
            val ang = -2.0 * PI / size
            val wRe = cos(ang)
            val wIm = kotlin.math.sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until half) {
                    val tRe = curRe * real[i + k + half] - curIm * imag[i + k + half]
                    val tIm = curRe * imag[i + k + half] + curIm * real[i + k + half]
                    real[i + k + half] = real[i + k] - tRe
                    imag[i + k + half] = imag[i + k] - tIm
                    real[i + k] += tRe
                    imag[i + k] += tIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
                i += size
            }
            size = size shl 1
        }
    }
}
