package com.example.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * ArcadeSynth generates nostalgic 16-bit arcade audio effects procedurally using Android's AudioTrack.
 * No asset bulk files needed, highly responsive!
 */
object ArcadeSynth {
    private const val SAMPLE_RATE = 22050

    // Synthesizes dynamic sound effect in a background thread to prevent UI stutter
    private fun playTone(soundGenerator: () -> ShortArray) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val samples = soundGenerator()
                val minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(samples.size * 2, minBufferSize)
                
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()
                // Auto release track after playing completes
                val durationMs = (samples.size * 1000L) / SAMPLE_RATE
                kotlinx.coroutines.delay(durationMs + 100)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Nostalgic 2-tone chime for inserting a coin.
     */
    fun playCoin() {
        playTone {
            val duration = (SAMPLE_RATE * 0.35f).toInt() // 350ms
            val buffer = ShortArray(duration)
            val freq1 = 987.77f // B5
            val freq2 = 1318.51f // E6
            val split = (duration * 0.3f).toInt()
            
            for (i in 0 until duration) {
                val freq = if (i < split) freq1 else freq2
                val envelope = if (i < split) {
                    (i.toFloat() / split) // Attack
                } else {
                    1.0f - (i - split).toFloat() / (duration - split) // Decay
                }
                
                // Add a little square wave grit for that 16-bit FM synth Sega warmth!
                val sine = sin(2.0 * Math.PI * freq * i / SAMPLE_RATE)
                val square = if (sine > 0) 1.0 else -1.0
                val signal = (sine * 0.6 + square * 0.4) * envelope * 24000.0
                buffer[i] = signal.toInt().toShort()
            }
            buffer
        }
    }

    /**
     * High-impact physical punch. Combines low-freq thud with white noise.
     */
    fun playPunch() {
        playTone {
            val duration = (SAMPLE_RATE * 0.15f).toInt() // 150ms
            val buffer = ShortArray(duration)
            
            for (i in 0 until duration) {
                val progress = i.toFloat() / duration
                // Pitch decays rapidly
                val freq = 180f * (1.0f - progress) + 40f
                val sine = sin(2.0 * Math.PI * freq * i / SAMPLE_RATE)
                
                // Add some friction white noise
                val noise = (Math.random() * 2.0 - 1.0)
                
                val env = (1.0f - progress) * (1.0f - progress)
                val signal = (sine * 0.5f + noise * 0.5f) * env * 28000.0
                buffer[i] = signal.toInt().toShort()
            }
            buffer
        }
    }

    /**
     * Hit sound for weapons (slashing, piping, heavy impact).
     */
    fun playWeaponHit() {
        playTone {
            val duration = (SAMPLE_RATE * 0.22f).toInt() // 220ms
            val buffer = ShortArray(duration)
            
            for (i in 0 until duration) {
                val progress = i.toFloat() / duration
                // Rapid sliding pitch
                val freq = 800f * (1.0f - progress) + 120f
                val sine = sin(2.0 * Math.PI * freq * i / SAMPLE_RATE)
                
                // Fast noise modulation
                val noise = if (i % 3 == 0) (Math.random() * 2.0 - 1.0) else 0.0
                
                val env = (1.0f - progress) * (1.0f - progress)
                val signal = (sine * 0.4f + noise * 0.6f) * env * 27000.0
                buffer[i] = signal.toInt().toShort()
            }
            buffer
        }
    }

    /**
     * Soaring sci-fi special sound.
     */
    fun playSpecial() {
        playTone {
            val duration = (SAMPLE_RATE * 0.45f).toInt()
            val buffer = ShortArray(duration)
            
            for (i in 0 until duration) {
                val progress = i.toFloat() / duration
                // Exponential frequency rise
                val freq = 150f + (progress * progress * 1400f)
                val sine1 = sin(2.0 * Math.PI * freq * i / SAMPLE_RATE)
                val sine2 = sin(2.0 * Math.PI * (freq * 1.5f) * i / SAMPLE_RATE)
                
                val env = sin(progress * Math.PI) // Peak at middle
                val signal = (sine1 * 0.5 + sine2 * 0.5) * env * 25000.0
                buffer[i] = signal.toInt().toShort()
            }
            buffer
        }
    }

    /**
     * Springy high jump sound sweep.
     */
    fun playJump() {
        playTone {
            val duration = (SAMPLE_RATE * 0.12f).toInt()
            val buffer = ShortArray(duration)
            
            for (i in 0 until duration) {
                val progress = i.toFloat() / duration
                val freq = 200f + (progress * 700f)
                val sine = sin(2.0 * Math.PI * freq * i / SAMPLE_RATE)
                val env = (1.0f - progress)
                val signal = sine * env * 20000.0
                buffer[i] = signal.toInt().toShort()
            }
            buffer
        }
    }

    /**
     * Deep exploding growl for knocking an enemy down or KO.
     */
    fun playKO() {
        playTone {
            val duration = (SAMPLE_RATE * 0.60f).toInt()
            val buffer = ShortArray(duration)
            
            for (i in 0 until duration) {
                val progress = i.toFloat() / duration
                // Vibrato low thud
                val lfo = sin(2.0 * Math.PI * 18.0 * i / SAMPLE_RATE)
                val freq = 90f * (1.0f - progress) + (lfo * 15f)
                val sine = sin(2.0 * Math.PI * freq * i / SAMPLE_RATE)
                
                val noise = (Math.random() * 2.0 - 1.0)
                val env = (1.0f - progress) * (1.0f - progress)
                
                val signal = (sine * 0.4 + noise * 0.6) * env * 30000.0
                buffer[i] = signal.toInt().toShort()
            }
            buffer
        }
    }

    /**
     * Epic 16-bit style startup theme arpeggio.
     */
    fun playThemeChp() {
        playTone {
            val noteDuration = (SAMPLE_RATE * 0.11f).toInt()
            val scale = floatArrayOf(261.63f, 329.63f, 392.00f, 523.25f, 659.25f, 783.99f, 1046.50f) // C Major arpeggio
            val duration = noteDuration * (scale.size * 2)
            val buffer = ShortArray(duration)
            
            for (i in 0 until duration) {
                val noteIndex = (i / noteDuration) % scale.size
                val freq = scale[noteIndex]
                val envelope = 1.0f - (i % noteDuration).toFloat() / noteDuration
                
                val sine = sin(2.0 * Math.PI * freq * i / SAMPLE_RATE)
                val triangle = (2.0 / Math.PI) * Math.asin(sin(2.0 * Math.PI * freq * i / SAMPLE_RATE))
                
                val signal = (sine * 0.6 + triangle * 0.4) * envelope * 22000.0
                buffer[i] = signal.toInt().toShort()
            }
            buffer
        }
    }
}
