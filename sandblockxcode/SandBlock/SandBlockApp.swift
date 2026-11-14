import SwiftUI
import UIKit

private enum AppRoute: Equatable {
    case menu
    case adventureMap
    case playMode(mode: GameMode, resumeClassic: Bool, sessionId: UUID)
    case playAdventure(level: AdventureLevel, sessionId: UUID)
}

@main
struct SandBlockApp: App {
    @StateObject private var prefs = GamePreferences()
    private let soundEngine = AsmrSoundEngine()

    var body: some Scene {
        WindowGroup {
            RootContentView(prefs: prefs, soundEngine: soundEngine)
                .preferredColorScheme(.dark)
        }
    }
}

struct RootContentView: View {
    @ObservedObject var prefs: GamePreferences
    let soundEngine: AsmrSoundEngine

    @State private var route: AppRoute = .menu
    @State private var didConfigureAudio: Bool = false

    var body: some View {
        let selectedTheme = prefs.selectedTheme

        Group {
            switch route {
            case .menu:
                MainMenuView(
                    theme: selectedTheme,
                    prefs: prefs,
                    soundEngine: soundEngine,
                    onToggleSound: {
                        let next = !prefs.soundEnabled
                        prefs.soundEnabled = next
                        soundEngine.soundEnabled = next
                        if next { soundEngine.playTap() }
                    },
                    onToggleMusic: {
                        let next = !prefs.musicEnabled
                        prefs.musicEnabled = next
                        soundEngine.updateMusicPreference(next)
                        soundEngine.playTap()
                    },
                    onToggleHaptics: {
                        let next = !prefs.hapticsEnabled
                        prefs.hapticsEnabled = next
                        soundEngine.hapticsEnabled = next
                        soundEngine.playTap()
                    },
                    onSelectTheme: { newTheme in
                        prefs.selectedTheme = newTheme
                        soundEngine.playTap()
                    },
                    onResumeClassic: {
                        soundEngine.playTap()
                        route = .playMode(mode: .classic, resumeClassic: true, sessionId: UUID())
                    },
                    onStartMode: { chosenMode in
                        soundEngine.playTap()
                        route = .playMode(mode: chosenMode, resumeClassic: false, sessionId: UUID())
                    },
                    onOpenAdventure: {
                        soundEngine.playTap()
                        route = .adventureMap
                    }
                )

            case .adventureMap:
                AdventureMapView(
                    theme: selectedTheme,
                    prefs: prefs,
                    onSelectLevel: { level in
                        soundEngine.playTap()
                        route = .playAdventure(level: level, sessionId: UUID())
                    },
                    onBack: {
                        soundEngine.playTap()
                        route = .menu
                    }
                )

            case let .playMode(mode, resumeClassic, sessionId):
                GameView(
                    initialMode: mode,
                    initialLevel: nil,
                    resumeClassic: resumeClassic,
                    selectedTheme: selectedTheme,
                    prefs: prefs,
                    soundEngine: soundEngine,
                    onExitToMenu: {
                        route = .menu
                    },
                    onExitToAdventureMap: {
                        route = .adventureMap
                    }
                )
                .id(sessionId)

            case let .playAdventure(level, sessionId):
                GameView(
                    initialMode: .adventure,
                    initialLevel: level,
                    resumeClassic: false,
                    selectedTheme: selectedTheme,
                    prefs: prefs,
                    soundEngine: soundEngine,
                    onExitToMenu: {
                        route = .menu
                    },
                    onExitToAdventureMap: {
                        route = .adventureMap
                    }
                )
                .id(sessionId)
            }
        }
        .onAppear {
            guard !didConfigureAudio else { return }
            didConfigureAudio = true
            soundEngine.soundEnabled = prefs.soundEnabled
            soundEngine.hapticsEnabled = prefs.hapticsEnabled
            soundEngine.updateMusicPreference(prefs.musicEnabled)
            soundEngine.onAppResumed()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            soundEngine.onAppResumed()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)) { _ in
            soundEngine.onAppPaused()
        }
    }
}
