import AVFoundation
import Foundation
import UIKit

/// Procedural ASMR sound synthesizer, seamless pentatonic Zen ambient music loop & haptic controller for iOS.
final class AsmrSoundEngine {
    private let sfxSampleRate: Double = 44100.0
    private let musicSampleRate: Double = 22050.0

    private let engine = AVAudioEngine()
    private let sfxFormat: AVAudioFormat
    private let musicFormat: AVAudioFormat
    private var sfxPlayers: [AVAudioPlayerNode] = []
    private let musicPlayer = AVAudioPlayerNode()
    private var nextPlayerIdx: Int = 0

    private let lightHaptic = UIImpactFeedbackGenerator(style: .light)
    private let mediumHaptic = UIImpactFeedbackGenerator(style: .medium)
    private let heavyHaptic = UIImpactFeedbackGenerator(style: .heavy)
    private let notifyHaptic = UINotificationFeedbackGenerator()

    var soundEnabled: Bool = true
    var hapticsEnabled: Bool = true
    private(set) var musicEnabled: Bool = true
    private var appInForeground: Bool = true
    private var isMusicLoopScheduled: Bool = false

    private lazy var tapBuffer: AVAudioPCMBuffer? = synthWoodTap()
    private lazy var sandPourBuffer: AVAudioPCMBuffer? = synthSandPour()
    private lazy var lavaBuffer: AVAudioPCMBuffer? = synthLavaSizzle()
    private lazy var bombBuffer: AVAudioPCMBuffer? = synthBombExplosion()
    private lazy var laserBuffer: AVAudioPCMBuffer? = synthLaserBeam()
    private lazy var coinBuffer: AVAudioPCMBuffer? = synthCoinClink()
    private lazy var magicBuffer: AVAudioPCMBuffer? = synthMagicSweep()
    private lazy var feverBuffer: AVAudioPCMBuffer? = synthFeverIgnite()
    private lazy var winBuffer: AVAudioPCMBuffer? = synthVictoryFanfare()
    private lazy var loseBuffer: AVAudioPCMBuffer? = synthGameOver()
    private lazy var clearBuffers: [AVAudioPCMBuffer?] = (0..<6).map { synthCrystalClear(comboIdx: $0) }
    private lazy var zenMusicBuffer: AVAudioPCMBuffer? = synthZenMusicCycle()

    init() {
        self.sfxFormat = AVAudioFormat(standardFormatWithSampleRate: sfxSampleRate, channels: 1)!
        self.musicFormat = AVAudioFormat(standardFormatWithSampleRate: musicSampleRate, channels: 1)!
        setupAudioEngine()
        prepareHaptics()
    }

    private func setupAudioEngine() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.ambient, mode: .default, options: [.mixWithOthers])
            try session.setActive(true)
        } catch {
            // Non-fatal in headless / simulator environments
        }

        for _ in 0..<5 {
            let node = AVAudioPlayerNode()
            engine.attach(node)
            engine.connect(node, to: engine.mainMixerNode, format: sfxFormat)
            sfxPlayers.append(node)
        }

        engine.attach(musicPlayer)
        engine.connect(musicPlayer, to: engine.mainMixerNode, format: musicFormat)
        musicPlayer.volume = 0.20

        startEngineIfNeeded()

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAudioInterruption(_:)),
            name: AVAudioSession.interruptionNotification,
            object: nil
        )
    }

    private func prepareHaptics() {
        DispatchQueue.main.async { [weak self] in
            self?.lightHaptic.prepare()
            self?.mediumHaptic.prepare()
            self?.heavyHaptic.prepare()
            self?.notifyHaptic.prepare()
        }
    }

    private func startEngineIfNeeded() {
        guard !engine.isRunning else { return }
        do {
            try engine.start()
        } catch {
            // Non-fatal if audio device is unavailable
        }
    }

    @objc private func handleAudioInterruption(_ notification: Notification) {
        guard let info = notification.userInfo,
              let typeVal = info[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: typeVal) else { return }
        if type == .ended {
            try? AVAudioSession.sharedInstance().setActive(true)
            isMusicLoopScheduled = false
            startEngineIfNeeded()
            syncMusicLoop()
        }
    }

    func updateMusicPreference(_ enabled: Bool) {
        musicEnabled = enabled
        syncMusicLoop()
    }

    func onAppResumed() {
        appInForeground = true
        startEngineIfNeeded()
        syncMusicLoop()
    }

    func onAppPaused() {
        appInForeground = false
        if musicPlayer.isPlaying {
            musicPlayer.pause()
        }
    }

    private func syncMusicLoop() {
        let shouldPlay = musicEnabled && appInForeground
        if shouldPlay {
            startEngineIfNeeded()
            guard engine.isRunning, let loopBuf = zenMusicBuffer else { return }
            if !isMusicLoopScheduled {
                musicPlayer.stop()
                musicPlayer.scheduleBuffer(loopBuf, at: nil, options: .loops, completionHandler: nil)
                isMusicLoopScheduled = true
            }
            if !musicPlayer.isPlaying {
                musicPlayer.play()
            }
        } else {
            if musicPlayer.isPlaying {
                musicPlayer.pause()
            }
        }
    }

    // MARK: - Playback Triggers

    func playTap() {
        triggerImpact(.light, intensity: 0.55)
        playSfx(tapBuffer, volume: 0.55)
    }

    func playCoinPurchase() {
        triggerImpact(.medium, intensity: 0.80)
        playSfx(coinBuffer, volume: 0.85)
    }

    func playFever() {
        triggerNotification(.success)
        playSfx(feverBuffer, volume: 0.90)
    }

    func playLava() {
        triggerImpact(.medium, intensity: 0.85)
        playSfx(lavaBuffer, volume: 0.78)
    }

    func playLaser() {
        triggerImpact(.heavy, intensity: 0.90)
        playSfx(laserBuffer, volume: 0.88)
    }

    func playSandDrop(isRainbow: Bool = false, isLava: Bool = false) {
        triggerImpact(.light, intensity: 0.75)
        if isLava {
            playSfx(lavaBuffer, volume: 0.78)
        } else if isRainbow {
            playSfx(magicBuffer, volume: 0.65)
        } else {
            playSfx(sandPourBuffer, volume: 0.72)
        }
    }

    func playLineClear(combo: Int) {
        let idx = min(clearBuffers.count - 1, max(0, combo - 1))
        playSfx(clearBuffers[idx], volume: 0.85)
        if combo >= 2 {
            triggerNotification(.success)
        } else {
            triggerImpact(.medium, intensity: 0.90)
        }
    }

    func playBomb() {
        triggerImpact(.heavy, intensity: 1.0)
        playSfx(bombBuffer, volume: 0.92)
    }

    func playMagic() {
        triggerImpact(.medium, intensity: 0.85)
        playSfx(magicBuffer, volume: 0.80)
    }

    func playWin() {
        triggerNotification(.success)
        playSfx(winBuffer, volume: 0.85)
    }

    func playGameOver() {
        triggerNotification(.warning)
        playSfx(loseBuffer, volume: 0.72)
    }

    private func triggerImpact(_ style: UIImpactFeedbackGenerator.FeedbackStyle, intensity: CGFloat) {
        guard hapticsEnabled else { return }
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            switch style {
            case .light: self.lightHaptic.impactOccurred(intensity: intensity)
            case .medium: self.mediumHaptic.impactOccurred(intensity: intensity)
            default: self.heavyHaptic.impactOccurred(intensity: intensity)
            }
        }
    }

    private func triggerNotification(_ type: UINotificationFeedbackGenerator.FeedbackType) {
        guard hapticsEnabled else { return }
        DispatchQueue.main.async { [weak self] in
            self?.notifyHaptic.notificationOccurred(type)
        }
    }

    private func playSfx(_ buffer: AVAudioPCMBuffer?, volume: Float) {
        guard soundEnabled, let buffer = buffer else { return }
        startEngineIfNeeded()
        guard engine.isRunning, !sfxPlayers.isEmpty else { return }
        let player = sfxPlayers[nextPlayerIdx % sfxPlayers.count]
        nextPlayerIdx &+= 1
        player.stop()
        player.volume = min(1.0, max(0.0, volume))
        player.scheduleBuffer(buffer, at: nil, options: .interrupts, completionHandler: nil)
        player.play()
    }

    // MARK: - Procedural Waveform Synthesizers

    private func makeBuffer(format: AVAudioFormat, duration: Double, generator: (Int, Double, UnsafeMutablePointer<Float>) -> Void) -> AVAudioPCMBuffer? {
        let frameCount = AVAudioFrameCount(max(1, Int(format.sampleRate * duration)))
        guard let pcm = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frameCount),
              let channel = pcm.floatChannelData?[0] else {
            return nil
        }
        pcm.frameLength = frameCount
        generator(Int(frameCount), format.sampleRate, channel)
        return pcm
    }

    private func synthWoodTap() -> AVAudioPCMBuffer? {
        makeBuffer(format: sfxFormat, duration: 0.045) { count, sr, out in
            for i in 0..<count {
                let t = Double(i) / sr
                let env = exp(-t * 95.0)
                let wave = sin(2.0 * .pi * 540.0 * t) * 0.7 + sin(2.0 * .pi * 880.0 * t) * 0.3
                out[i] = Float(wave * env * 0.45)
            }
        }
    }

    private func synthSandPour() -> AVAudioPCMBuffer? {
        makeBuffer(format: sfxFormat, duration: 0.19) { count, sr, out in
            var rng = SeededRNG(seed: 42)
            var lp = 0.0
            var bp = 0.0
            for i in 0..<count {
                let t = Double(i) / sr
                let attack = min(1.0, t / 0.025)
                let decay = exp(-max(0.0, t - 0.02) * 14.0)
                let white = rng.nextDouble(in: -1.0...1.0)
                lp += 0.14 * (white - lp)
                bp += 0.045 * (lp - bp)
                let shimmer = rng.nextFloat() < 0.02 ? rng.nextDouble(in: -0.40...0.40) : 0.0
                let sample = ((lp - bp) * 1.4 + shimmer) * attack * decay
                out[i] = Float(min(0.95, max(-0.95, sample * 0.55)))
            }
        }
    }

    private func synthLavaSizzle() -> AVAudioPCMBuffer? {
        makeBuffer(format: sfxFormat, duration: 0.26) { count, sr, out in
            var rng = SeededRNG(seed: 88)
            var low = 0.0
            for i in 0..<count {
                let t = Double(i) / sr
                let env = exp(-t * 9.0)
                let white = rng.nextDouble(in: -1.0...1.0)
                low += 0.11 * (white - low)
                let rumble = sin(2.0 * .pi * (120.0 + 35.0 * sin(25.0 * t)) * t) * 0.45
                let hiss = (white - low) * 0.52
                out[i] = Float(min(0.95, max(-0.95, (rumble + hiss) * env * 0.58)))
            }
        }
    }

    private func synthFeverIgnite() -> AVAudioPCMBuffer? {
        let notes: [Double] = [440.0, 554.37, 659.25, 880.0, 1108.73]
        return makeBuffer(format: sfxFormat, duration: 0.48) { count, sr, out in
            for i in 0..<count {
                let t = Double(i) / sr
                var sum = 0.0
                for k in 0..<notes.count {
                    let start = Double(k) * 0.055
                    if t >= start {
                        let dt = t - start
                        sum += sin(2.0 * .pi * notes[k] * dt) * exp(-dt * 6.5) * 0.30
                    }
                }
                out[i] = Float(min(0.95, max(-0.95, sum * 0.65)))
            }
        }
    }

    private func synthCrystalClear(comboIdx: Int) -> AVAudioPCMBuffer? {
        let baseFreqs: [Double] = [523.25, 587.33, 659.25, 783.99, 880.0, 1046.5]
        let root = baseFreqs[min(baseFreqs.count - 1, max(0, comboIdx))]
        let fifth = root * 1.498
        let octave = root * 2.0
        return makeBuffer(format: sfxFormat, duration: 0.36) { count, sr, out in
            var rng = SeededRNG(seed: UInt64(100 + comboIdx))
            var smoothNoise = 0.0
            for i in 0..<count {
                let t = Double(i) / sr
                let env1 = exp(-t * 7.5)
                let env2 = t > 0.05 ? exp(-(t - 0.05) * 8.0) : 0.0
                let env3 = t > 0.10 ? exp(-(t - 0.10) * 9.0) : 0.0

                smoothNoise += 0.14 * (rng.nextDouble(in: -1.0...1.0) - smoothNoise)
                let sandWhoosh = smoothNoise * exp(-t * 15.0) * 0.24

                let chord = sin(2.0 * .pi * root * t) * env1 * 0.45 +
                    sin(2.0 * .pi * fifth * t) * env2 * 0.35 +
                    sin(2.0 * .pi * octave * t) * env3 * 0.30 +
                    sandWhoosh
                out[i] = Float(min(0.95, max(-0.95, chord * 0.60)))
            }
        }
    }

    private func synthBombExplosion() -> AVAudioPCMBuffer? {
        makeBuffer(format: sfxFormat, duration: 0.42) { count, sr, out in
            var rng = SeededRNG(seed: 77)
            var lowNoise = 0.0
            for i in 0..<count {
                let t = Double(i) / sr
                let env = exp(-t * 8.5)
                let subFreq = 115.0 * exp(-t * 4.5) + 38.0
                let sub = sin(2.0 * .pi * subFreq * t) * 0.65
                lowNoise += 0.09 * (rng.nextDouble(in: -1.0...1.0) - lowNoise)
                let sample = (sub + lowNoise * 0.75) * env
                out[i] = Float(min(0.95, max(-0.95, sample * 0.72)))
            }
        }
    }

    private func synthMagicSweep() -> AVAudioPCMBuffer? {
        let notes: [Double] = [523.25, 659.25, 783.99, 1046.5, 1318.5]
        return makeBuffer(format: sfxFormat, duration: 0.32) { count, sr, out in
            let stepDur = 0.32 / Double(notes.count)
            for i in 0..<count {
                let t = Double(i) / sr
                let noteIdx = min(notes.count - 1, max(0, Int((t / 0.32) * Double(notes.count))))
                let localT = t.truncatingRemainder(dividingBy: stepDur)
                let env = exp(-localT * 18.0) * exp(-t * 2.5)
                let s = sin(2.0 * .pi * notes[noteIdx] * t) * 0.6 +
                    sin(2.0 * .pi * notes[noteIdx] * 2.0 * t) * 0.25
                out[i] = Float(min(0.95, max(-0.95, s * env * 0.55)))
            }
        }
    }

    private func synthVictoryFanfare() -> AVAudioPCMBuffer? {
        let notes: [Double] = [523.25, 659.25, 783.99, 1046.5]
        let starts: [Double] = [0.0, 0.10, 0.20, 0.32]
        return makeBuffer(format: sfxFormat, duration: 0.68) { count, sr, out in
            for i in 0..<count {
                let t = Double(i) / sr
                var sum = 0.0
                for k in 0..<notes.count where t >= starts[k] {
                    let dt = t - starts[k]
                    sum += sin(2.0 * .pi * notes[k] * dt) * exp(-dt * 6.0) * 0.35
                }
                out[i] = Float(min(0.95, max(-0.95, sum * 0.65)))
            }
        }
    }

    private func synthGameOver() -> AVAudioPCMBuffer? {
        let notes: [Double] = [440.0, 392.0, 349.23, 293.66]
        let starts: [Double] = [0.0, 0.12, 0.24, 0.36]
        return makeBuffer(format: sfxFormat, duration: 0.65) { count, sr, out in
            for i in 0..<count {
                let t = Double(i) / sr
                var sum = 0.0
                for k in 0..<notes.count where t >= starts[k] {
                    let dt = t - starts[k]
                    sum += sin(2.0 * .pi * notes[k] * dt) * exp(-dt * 6.5) * 0.35
                }
                out[i] = Float(min(0.95, max(-0.95, sum * 0.58)))
            }
        }
    }

    private func synthCoinClink() -> AVAudioPCMBuffer? {
        makeBuffer(format: sfxFormat, duration: 0.24) { count, sr, out in
            for i in 0..<count {
                let t = Double(i) / sr
                let tone1 = sin(2.0 * .pi * 987.77 * t) * exp(-t * 22.0) * 0.55
                let tone2: Double
                if t >= 0.055 {
                    let dt = t - 0.055
                    tone2 = (sin(2.0 * .pi * 1318.51 * dt) * 0.65 + sin(2.0 * .pi * 2637.02 * dt) * 0.25) * exp(-dt * 14.0)
                } else {
                    tone2 = 0.0
                }
                out[i] = Float(min(0.95, max(-0.95, (tone1 + tone2) * 0.60)))
            }
        }
    }

    private func synthLaserBeam() -> AVAudioPCMBuffer? {
        makeBuffer(format: sfxFormat, duration: 0.28) { count, sr, out in
            for i in 0..<count {
                let t = Double(i) / sr
                let env = exp(-t * 9.5)
                let sweepFreq = 1450.0 * exp(-t * 5.2) + 220.0
                let wave = sin(2.0 * .pi * sweepFreq * t) * 0.65 +
                    sin(2.0 * .pi * sweepFreq * 1.5 * t) * 0.30
                out[i] = Float(min(0.95, max(-0.95, wave * env * 0.62)))
            }
        }
    }

    private func synthZenMusicCycle() -> AVAudioPCMBuffer? {
        // Peaceful Zen chord voicings (Hz): Cmaj9 -> Am9 -> Fmaj7 -> G6
        let chords: [[Double]] = [
            [130.81, 196.00, 246.94, 293.66],
            [110.00, 164.81, 220.00, 261.63],
            [174.61, 220.00, 261.63, 329.63],
            [196.00, 246.94, 293.66, 392.00]
        ]
        let bellNotes: [Double] = [523.25, 587.33, 659.25, 783.99, 880.00, 1046.50, 659.25, 783.99]
        let chordDurationSec = 4.8
        let cycleDurationSec = chordDurationSec * Double(chords.count) // 19.2s seamless loop

        return makeBuffer(format: musicFormat, duration: cycleDurationSec) { count, sr, out in
            for i in 0..<count {
                let t = Double(i) / sr
                let posInCycle = t / chordDurationSec
                let chordIdx = Int(posInCycle) % chords.count
                let nextChordIdx = (chordIdx + 1) % chords.count
                let frac = posInCycle - Double(Int(posInCycle))

                let blend: Double
                if frac > 0.75 {
                    let u = (frac - 0.75) / 0.25
                    blend = 0.5 - 0.5 * cos(.pi * u)
                } else {
                    blend = 0.0
                }

                let cA = chords[chordIdx]
                let cB = chords[nextChordIdx]
                // Exact 4 LFO cycles over 19.2s for click-free seamless looping
                let lfo = 0.88 + 0.12 * sin(2.0 * .pi * (4.0 / cycleDurationSec) * t)

                var padA = 0.0
                var padB = 0.0
                for v in 0..<4 {
                    padA += sin(2.0 * .pi * cA[v] * t) * 0.22
                    padB += sin(2.0 * .pi * cB[v] * t) * 0.22
                }
                let pad = (padA * (1.0 - blend) + padB * blend) * lfo

                // 8 evenly-spaced pentatonic wind-chime bells across the 19.2s loop
                let bellInterval = cycleDurationSec / Double(bellNotes.count)
                let bellSlot = min(bellNotes.count - 1, Int(t / bellInterval))
                let bellDt = max(0.0, t - Double(bellSlot) * bellInterval - 0.35)
                let bell = bellDt > 0 ? sin(2.0 * .pi * bellNotes[bellSlot] * bellDt) * exp(-bellDt * 3.6) * 0.15 : 0.0

                // Micro crossfade envelope at loop edges to eliminate phase discontinuity
                let edgeFade: Double
                if t < 0.04 {
                    edgeFade = t / 0.04
                } else if cycleDurationSec - t < 0.04 {
                    edgeFade = (cycleDurationSec - t) / 0.04
                } else {
                    edgeFade = 1.0
                }

                out[i] = Float((pad + bell) * edgeFade * 0.50)
            }
        }
    }
}
