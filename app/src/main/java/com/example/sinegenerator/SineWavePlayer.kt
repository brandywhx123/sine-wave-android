package com.example.sinegenerator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

/**
 * 基于 AudioTrack 的实时正弦波发生器。
 *
 * - 44100Hz、单声道、16-bit PCM，MODE_STREAM 流式写入
 * - 独立渲染线程按 y(t) = A * sin(2πft) 逐采样计算
 * - 采用相位累加：播放过程中改变频率不会产生相位跳变（无爆音）
 * - [frequency] 用 @Volatile 保证 UI 线程修改后渲染线程立即可见
 */
class SineWavePlayer {

    private val sampleRate = 44_100

    /** 输出幅度（0.0 ~ 1.0），留有余量避免削波 */
    private val amplitude = 0.6

    /** 当前频率（Hz），可在播放过程中实时修改 */
    @Volatile
    var frequency: Double = 440.0

    private val started = AtomicBoolean(false)
    private var audioTrack: AudioTrack? = null
    private var workerThread: Thread? = null

    val isPlaying: Boolean
        get() = started.get()

    fun start() {
        if (!started.compareAndSet(false, true)) return

        val minBufferBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).let { if (it > 0) it else FALLBACK_BUFFER_BYTES }

        // 缓冲区约 2 倍最小缓冲，兼顾低延迟与稳定输出
        val bufferSizeBytes = minBufferBytes * 2
        // 每次写入 1/4 缓冲，使播放中改频率在几十毫秒内生效
        val chunkShorts = (bufferSizeBytes / 4 / 2).coerceAtLeast(1024)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSizeBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()

        workerThread = Thread { renderLoop(track, chunkShorts) }.apply {
            name = "sine-wave-renderer"
            start()
        }
    }

    private fun renderLoop(track: AudioTrack, chunkShorts: Int) {
        val chunk = ShortArray(chunkShorts)
        var phase = 0.0
        val phasePerSampleBase = TWO_PI / sampleRate

        while (started.get()) {
            // 每个 chunk 读取一次频率：实时改频率，且不引入相位跳变
            val phaseIncrement = phasePerSampleBase * frequency
            for (i in chunk.indices) {
                chunk[i] = (amplitude * sin(phase) * Short.MAX_VALUE).toInt().toShort()
                phase += phaseIncrement
                if (phase >= TWO_PI) phase -= TWO_PI
            }
            val written = track.write(chunk, 0, chunk.size)
            if (written < 0) break // 写入失败（对象已释放等），退出线程
        }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return

        val track = audioTrack
        // 先暂停以尽快解除渲染线程中阻塞的 write()
        runCatching { track?.pause() }
        workerThread?.interrupt()
        workerThread?.join(800)
        runCatching {
            track?.flush()
            track?.release()
        }

        audioTrack = null
        workerThread = null
    }

    private companion object {
        private const val TWO_PI = 2.0 * PI
        private const val FALLBACK_BUFFER_BYTES = 8192
    }
}
