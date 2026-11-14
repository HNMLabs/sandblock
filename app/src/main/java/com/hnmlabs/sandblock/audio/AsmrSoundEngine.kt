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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural ASMR sound synthesizer, ambient Zen music generator & haptic controller.
 * Generates organic granular sand pouring, soft wooden taps, crystal harmonic combo chimes,
 * volcanic lava sizzles, Fever fanfare, and relaxing pentatonic Zen background pads.
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

    @Volatile
    var musicEnabled: Boolean = true
        private set

    @Volatile
    private var appInForeground: Boolean = false

    @Volatile
    private var released: Boolean = false

    private var musicThread: Thread? = null
    private val musicLock = Any()

    private val tapBuffer: ShortArray by lazy { synthWoodTap() }
    private val sandPourBuffer: ShortArray by lazy { synthSandPour() }
    private val lavaBuffer: ShortArray by lazy { synthLavaSizzle() }
    private val bombBuffer: ShortArray by lazy { synthBombExplosion() }
    private val laserBuffer: ShortArray by lazy { synthLaserBeam() }
    private val coinBuffer: ShortArray by lazy { synthCoinClink() }
    private val magicBuffer: ShortArray by lazy { synthMagicSweep() }
    private val feverBuffer: ShortArray by lazy { synthFeverIgnite() }
    private val winBuffer: ShortArray by lazy { synthVictoryFanfare() }
    private val loseBuffer: ShortArray by lazy { synthGameOver() }
    private val clearBuffers: Array<ShortArray> by lazy {
        Array(6) { comboIdx -> synthCrystalClear(comboIdx) }
    }

    fun updateMusicPreference(enabled: Boolean) {
        musicEnabled = enabled
        syncMusicLoop()
    }

    fun onAppResumed() {
        appInForeground = true
        syncMusicLoop()
    }

    fun onAppPaused() {
        appInForeground = false
        syncMusicLoop()
    }

    private fun syncMusicLoop() {
        synchronized(musicLock) {
            val shouldRun = musicEnabled && appInForeground && !released
            if (shouldRun && (musicThread == null || musicThread?.isAlive != true)) {
                val t = Thread({ streamZenMusicLoop() }, "SandBlock-ZenMusic").apply {
                    isDaemon = true
                    priority = Thread.MIN_PRIORITY + 2
                }
                musicThread = t
                t.start()
            }
        }
    }

    private fun streamZenMusicLoop() {
        val musicRate = 16000
        val chunkSamples = 3200 // 200ms per write
        val buffer = ShortArray(chunkSamples)
        var track: AudioTrack? = null

        // Peaceful Zen chord voicings (Hz): Cmaj9 -> Am9 -> Fmaj7 -> G6
        val chords = arrayOf(
            doubleArrayOf(130.81, 196.00, 246.94, 293.66),
            doubleArrayOf(110.00, 164.81, 220.00, 261.63),
            doubleArrayOf(174.61, 220.00, 261.63, 329.63),
            doubleArrayOf(196.00, 246.94, 293.66, 392.00)
        )
        val bellNotes = doubleArrayOf(523.25, 587.33, 659.25, 783.99, 880.00, 1046.50)

        try {
            val minBuf = AudioTrack.getMinBufferSize(
                musicRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(chunkSamples * 4)

            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(musicRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.setVolume(0.18f)
            track.play()

            var globalSample = 0L
            val chordDurationSec = 4.8
            var bellFreq = bellNotes[0]
            var bellStartSec = 1.2
            val rng = Random(2026)

            while (musicEnabled && appInForeground && !released) {
                for (i in 0 until chunkSamples) {
                    val t = globalSample.toDouble() / musicRate
                    val posInCycle = t / chordDurationSec
                    val chordIdx = (posInCycle.toInt()) % chords.size
                    val nextChordIdx = (chordIdx + 1) % chords.size
                    val frac = posInCycle - posInCycle.toInt()

                    // Smooth crossfade in the final 25% of each chord
                    val blend = if (frac > 0.75) {
                        val u = (frac - 0.75) / 0.25
                        (0.5 - 0.5 * cos(PI * u))
                    } else {
                        0.0
                    }

                    val cA = chords[chordIdx]
                    val cB = chords[nextChordIdx]
                    val lfo = 0.88 + 0.12 * sin(2.0 * PI * 0.22 * t)

                    var padA = 0.0
                    var padB = 0.0
                    for (v in 0 until 4) {
                        padA += sin(2.0 * PI * cA[v] * t) * 0.22
                        padB += sin(2.0 * PI * cB[v] * t) * 0.22
                    }
                    val pad = (padA * (1.0 - blend) + padB * blend) * lfo

                    // Gentle Zen pentatonic wind-chime bell every ~2.4 seconds
                    if (t - bellStartSec > 2.4) {
                        bellStartSec = t
                        bellFreq = bellNotes[rng.nextInt(bellNotes.size)]
                    }
                    val bellDt = (t - bellStartSec).coerceAtLeast(0.0)
                    val bell = sin(2.0 * PI * bellFreq * bellDt) * exp(-bellDt * 3.4) * 0.16

                    val fadeIn = (t / 1.5).coerceAtMost(1.0)
                    val sample = (pad + bell) * fadeIn
                    buffer[i] = (sample * 11500.0).toInt().coerceIn(-32767, 32767).toShort()
                    globalSample++
                }
                track.write(buffer, 0, chunkSamples)
            }
        } catch (_: Exception) {
        } finally {
            try {
                track?.pause()
                track?.flush()
                track?.release()
            } catch (_: Exception) {
            }
        }
    }

    fun playTap() {
        vibrateTick(10L, 60)
        playPcm(tapBuffer, 0.55f)
    }

    fun playCoinPurchase() {
        vibrateTick(18L, 120)
        playPcm(coinBuffer, 0.85f)
    }

    fun playFever() {
        playPcm(feverBuffer, 0.90f)
        if (hapticsEnabled) {
            try {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0L, 28L, 22L, 38L, 22L, 55L),
                        intArrayOf(0, 180, 0, 220, 0, 255),
                        -1
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    fun playLava() {
        vibrateTick(22L, 130)
        playPcm(lavaBuffer, 0.78f)
    }

    fun playLaser() {
        playPcm(laserBuffer, 0.88f)
        if (hapticsEnabled) {
            try {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0L, 20L, 18L, 45L),
                        intArrayOf(0, 200, 0, 240),
                        -1
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    fun playSandDrop(isRainbow: Boolean = false, isLava: Boolean = false) {
        vibrateTick(16L, 95)
        when {
            isLava -> playPcm(lavaBuffer, 0.78f)
            isRainbow -> playPcm(magicBuffer, 0.65f)
            else -> playPcm(sandPourBuffer, 0.72f)
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
        if (!soundEnabled || samples.isEmpty() || released) return
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
            lp += 0.24 * (white - lp)
            bp += 0.08 * (lp - bp)
            val shimmer = if (rng.nextFloat() < 0.03f) rng.nextDouble(-0.45, 0.45) else 0.0
            val sample = ((lp - bp) * 1.4 + shimmer) * attack * decay
            out[i] = (sample * 16500.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthLavaSizzle(): ShortArray {
        val n = (sampleRate * 0.26).toInt()
        val out = ShortArray(n)
        val rng = Random(88)
        var low = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 9.0)
            val white = rng.nextDouble(-1.0, 1.0)
            low += 0.18 * (white - low)
            val rumble = sin(2.0 * PI * (120.0 + 35.0 * sin(25.0 * t)) * t) * 0.45
            val hiss = (white - low) * 0.55
            out[i] = ((rumble + hiss) * env * 18500.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthFeverIgnite(): ShortArray {
        val notes = doubleArrayOf(440.0, 554.37, 659.25, 880.0, 1108.73)
        val n = (sampleRate * 0.48).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            var sum = 0.0
            for (k in notes.indices) {
                val start = k * 0.055
                if (t >= start) {
                    val dt = t - start
                    sum += sin(2.0 * PI * notes[k] * dt) * exp(-dt * 6.5) * 0.30
                }
            }
            out[i] = (sum * 21000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthCrystalClear(comboIdx: Int): ShortArray {
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

    private fun synthCoinClink(): ShortArray {
        val n = (sampleRate * 0.24).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val tone1 = sin(2.0 * PI * 987.77 * t) * exp(-t * 22.0) * 0.55
            val tone2 = if (t >= 0.055) {
                val dt = t - 0.055
                (sin(2.0 * PI * 1318.51 * dt) * 0.65 + sin(2.0 * PI * 2637.02 * dt) * 0.25) * exp(-dt * 14.0)
            } else {
                0.0
            }
            out[i] = ((tone1 + tone2) * 19500.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthLaserBeam(): ShortArray {
        val n = (sampleRate * 0.28).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 9.5)
            val sweepFreq = 1450.0 * exp(-t * 5.2) + 220.0
            val wave = sin(2.0 * PI * sweepFreq * t) * 0.65 +
                sin(2.0 * PI * sweepFreq * 1.5 * t) * 0.30
            out[i] = (wave * env * 20000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    fun release() {
        released = true
        appInForeground = false
        audioExecutor.shutdownNow()
    }
}
