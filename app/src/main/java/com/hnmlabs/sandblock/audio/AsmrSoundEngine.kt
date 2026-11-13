package com.hnmlabs.sandblock.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural ASMR sound synthesizer & haptic feedback controller.
 * Generates organic granular sand pouring, soft wooden taps, crystal harmonic combo chimes,
 * and TNT explosion effects using lightweight PCM buffers with zero external binary dependencies.
 */
class AsmrSoundEngine(context: Context) {

    private val sampleRate = 22050
    private val audioExecutor = Executors.newFixedThreadPool(3)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var soundEnabled: Boolean = true
    var hapticsEnabled: Boolean = true

    private val tapBuffer: ShortArray by lazy { synthWoodTap() }
    private val sandPourBuffer: ShortArray by lazy { synthSandPour() }
    private val bombBuffer: ShortArray by lazy { synthBombExplosion() }
    private val magicBuffer: ShortArray by lazy { synthMagicSweep() }
    private val winBuffer: ShortArray by lazy { synthVictoryFanfare() }
    private val loseBuffer: ShortArray by lazy { synthGameOver() }
    private val clearBuffers: Array<ShortArray> by lazy {
        Array(6) { comboIdx -> synthCrystalClear(comboIdx) }
    }

    fun playTap() {
        vibrateTick(10L, 60)
        playPcm(tapBuffer, 0.55f)
    }

    fun playSandDrop(isRainbow: Boolean = false) {
        vibrateTick(16L, 95)
        if (isRainbow) {
            playPcm(magicBuffer, 0.65f)
        } else {
            playPcm(sandPourBuffer, 0.72f)
        }
    }

    fun playLineClear(combo: Int) {
        val idx = (combo - 1).coerceIn(0, clearBuffers.size - 1)
        playPcm(clearBuffers[idx], 0.85f)
        if (hapticsEnabled) {
            try {
                if (combo >= 2) {
                    vibrator?.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0L, 24L, 35L, 38L),
                            intArrayOf(0, 140, 0, 220),
                            -1
                        )
                    )
                } else {
                    vibrateTick(28L, 150)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun playBomb() {
        playPcm(bombBuffer, 0.9f)
        if (hapticsEnabled) {
            try {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0L, 45L, 25L, 65L),
                        intArrayOf(0, 255, 0, 190),
                        -1
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    fun playMagic() {
        vibrateTick(25L, 140)
        playPcm(magicBuffer, 0.8f)
    }

    fun playWin() {
        vibrateTick(45L, 180)
        playPcm(winBuffer, 0.85f)
    }

    fun playGameOver() {
        vibrateTick(50L, 130)
        playPcm(loseBuffer, 0.7f)
    }

    private fun vibrateTick(durationMs: Long, amplitude: Int) {
        if (!hapticsEnabled) return
        try {
            vibrator?.vibrate(
                VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255))
            )
        } catch (_: Exception) {
        }
    }

    private fun playPcm(samples: ShortArray, volume: Float) {
        if (!soundEnabled || samples.isEmpty()) return
        audioExecutor.execute {
            var track: AudioTrack? = null
            try {
                val byteCount = samples.size * 2
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
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
                    .setBufferSizeInBytes(byteCount)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(samples, 0, samples.size)
                track.setVolume(volume.coerceIn(0f, 1f))
                track.play()
                val sleepMs = (samples.size * 1000L / sampleRate) + 40L
                Thread.sleep(sleepMs)
            } catch (_: Exception) {
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun synthWoodTap(): ShortArray {
        val n = (sampleRate * 0.045).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 95.0)
            val wave = sin(2.0 * PI * 540.0 * t) * 0.7 + sin(2.0 * PI * 880.0 * t) * 0.3
            out[i] = (wave * env * 14000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthSandPour(): ShortArray {
        // Filtered soft granular ASMR rustle
        val n = (sampleRate * 0.19).toInt()
        val out = ShortArray(n)
        val rng = Random(42)
        var lp = 0.0
        var bp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val attack = (t / 0.025).coerceAtMost(1.0)
            val decay = exp(-(t - 0.02).coerceAtLeast(0.0) * 14.0)
            val white = rng.nextDouble(-1.0, 1.0)
            // Soft bandpass + grain crackles
            lp += 0.24 * (white - lp)
            bp += 0.08 * (lp - bp)
            val shimmer = if (rng.nextFloat() < 0.03f) rng.nextDouble(-0.45, 0.45) else 0.0
            val sample = ((lp - bp) * 1.4 + shimmer) * attack * decay
            out[i] = (sample * 16500.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthCrystalClear(comboIdx: Int): ShortArray {
        // Pentatonic scale ascending with combo + soft sand shimmer
        val baseFreqs = doubleArrayOf(523.25, 587.33, 659.25, 783.99, 880.0, 1046.5)
        val root = baseFreqs[comboIdx.coerceIn(0, baseFreqs.size - 1)]
        val fifth = root * 1.498
        val octave = root * 2.0
        val n = (sampleRate * 0.36).toInt()
        val out = ShortArray(n)
        val rng = Random(100 + comboIdx)
        var smoothNoise = 0.0

        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env1 = exp(-t * 7.5)
            val env2 = if (t > 0.05) exp(-(t - 0.05) * 8.0) else 0.0
            val env3 = if (t > 0.10) exp(-(t - 0.10) * 9.0) else 0.0

            smoothNoise += 0.2 * (rng.nextDouble(-1.0, 1.0) - smoothNoise)
            val sandWhoosh = smoothNoise * exp(-t * 15.0) * 0.25

            val chord = sin(2.0 * PI * root * t) * env1 * 0.45 +
                sin(2.0 * PI * fifth * t) * env2 * 0.35 +
                sin(2.0 * PI * octave * t) * env3 * 0.30 +
                sandWhoosh

            out[i] = (chord * 19000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthBombExplosion(): ShortArray {
        val n = (sampleRate * 0.42).toInt()
        val out = ShortArray(n)
        val rng = Random(77)
        var lowNoise = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 8.5)
            val subFreq = 115.0 * exp(-t * 4.5) + 38.0
            val sub = sin(2.0 * PI * subFreq * t) * 0.65
            lowNoise += 0.14 * (rng.nextDouble(-1.0, 1.0) - lowNoise)
            val sample = (sub + lowNoise * 0.75) * env
            out[i] = (sample * 24000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthMagicSweep(): ShortArray {
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.5, 1318.5)
        val n = (sampleRate * 0.32).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val noteIdx = ((t / 0.32) * notes.size).toInt().coerceIn(0, notes.size - 1)
            val localT = (t % (0.32 / notes.size))
            val env = exp(-localT * 18.0) * exp(-t * 2.5)
            val s = sin(2.0 * PI * notes[noteIdx] * t) * 0.6 +
                sin(2.0 * PI * notes[noteIdx] * 2.0 * t) * 0.25
            out[i] = (s * env * 17000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthVictoryFanfare(): ShortArray {
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.5)
        val starts = doubleArrayOf(0.0, 0.10, 0.20, 0.32)
        val n = (sampleRate * 0.68).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            var sum = 0.0
            for (k in notes.indices) {
                if (t >= starts[k]) {
                    val dt = t - starts[k]
                    sum += sin(2.0 * PI * notes[k] * dt) * exp(-dt * 6.0) * 0.35
                }
            }
            out[i] = (sum * 21000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthGameOver(): ShortArray {
        val notes = doubleArrayOf(440.0, 392.0, 349.23, 293.66)
        val starts = doubleArrayOf(0.0, 0.12, 0.24, 0.36)
        val n = (sampleRate * 0.65).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            var sum = 0.0
            for (k in notes.indices) {
                if (t >= starts[k]) {
                    val dt = t - starts[k]
                    sum += sin(2.0 * PI * notes[k] * dt) * exp(-dt * 6.5) * 0.35
                }
            }
            out[i] = (sum * 18000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    fun release() {
        audioExecutor.shutdownNow()
    }
}
