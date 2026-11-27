package com.changsheng.carlauncher

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object ChimePlayer {
    fun playParkingChime(isStart: Boolean) {
        thread(start = true) {
            try {
                val sampleRate = 22050
                val notes = if (isStart) doubleArrayOf(880.0, 1318.51, 1760.0) else doubleArrayOf(1318.51, 880.0)
                val noteMs = 130
                val tailMs = 220
                val totalSamples = ((notes.size * noteMs + tailMs) * sampleRate) / 1000
                val pcm = ShortArray(totalSamples)

                for (nIdx in notes.indices) {
                    val freq = notes[nIdx]
                    val startSample = (nIdx * noteMs * sampleRate) / 1000
                    val durSamples = ((noteMs + tailMs) * sampleRate) / 1000
                    for (i in 0 until durSamples) {
                        val pos = startSample + i
                        if (pos >= totalSamples) break
                        val t = i.toDouble() / sampleRate
                        val attack = (t / 0.015).coerceAtMost(1.0)
                        val decay = exp(-t * 8.5)
                        val sample = (sin(2.0 * PI * freq * t) * 0.75 + sin(2.0 * PI * freq * 2.0 * t) * 0.25) * attack * decay
                        val mixed = pcm[pos].toInt() + (sample * 11000).toInt()
                        pcm[pos] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                }

                val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuf, pcm.size * 2))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep((notes.size * noteMs + tailMs + 80).toLong())
                track.stop()
                track.release()
            } catch (_: Exception) {
            }
        }
    }
}
