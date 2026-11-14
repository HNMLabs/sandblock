# SandBlock v2.0 — Native iOS (Xcode) Sürümü 🍎🏖️

Android **SandBlock v2.0** oyununun sıfır harici bağımlılıkla (**%100 Native Swift + SwiftUI + CoreGraphics + AVAudioEngine + UIKit Haptics**) geliştirilmiş tam özellikli iOS / iPadOS Xcode projesidir.

---

## ✨ Özellikler (Android v2.0 ile %100 Birebir)

- **4 Oyun Modu:**
  - **♾️ Klasik Sonsuz Mod** — Otomatik kayıt (`SBV2` snapshot) ve kaldığın yerden devam etme desteği.
  - **⏱️ Zamana Karşı (Time Rush)** — 90 saniye başlangıç + her temizlenen kum hattında kombo süre bonusu.
  - **🕹️ Sandtrix Akış** — Gerçek zamanlı yukarıdan düşen kum blokları; dokunmatik kaydırma ve alt arcade yön tuşlarıyla kontrol.
  - **🗺️ Macera Modu** — **6 farklı dünya**, **20 özel bölüm**, **60 yıldız** hedefi.
- **60 FPS Hücresel Otomat Kum & Lav Fiziği (`CADisplayLink` + `CoreGraphics`):**
  - 72×112 piksel kum ızgarası, güneş ışıklı 3D tepe gölgelendirmesi, ekran sarsıntısı, şok halkaları, lazer ışınları ve uçuşan parçacıklar.
  - **🌋 Kor Lav Blokları**, **🌈 Gökkuşağı Joker Kum**, **💣 TNT Bomba**, **🧊 2 Vuruşluk Buz Blokları** ve **Çatlak Kayalar**.
  - **📦 SAKLA (Hold)** yuvası ve **🔥 2X Kombo Ateşi (Fever)** modu.
- **Altın Mağazası, Görevler & İlerleme:**
  - Oyun içi ve menü **🛒 Altın Mağazası** (TNT, Yatay Lazer, Renk Silici, Joker, Tepsi Yenile, Kum Fırtınası, Lav Yağmuru, İndirimli Paketler ve kalıcı **Altın Bereketi** çarpanı).
  - Oyun bittiğinde altınla **💖 İkinci Şans (Revive)** özelliği.
  - Oyuncu XP/Rütbe sistemi, **🎁 Günlük Giriş Hediyesi**, **3 Günlük Görev**, **10 Başarım** ve detaylı İstatistik ekranı.
- **6 Görsel Tema & Prosedürel ASMR Ses Motoru (`AVAudioEngine`):**
  - *Sahara, Okyanus, Siber Neon, Pastel Rüya, Volkanik Kor, Kuzey Işığı* temaları.
  - Harici ses dosyası gerektirmeyen gerçek zamanlı PCM sentezli ASMR kum sesleri, **Pentatonik Zen arka plan müziği (`🎵`)** ve iPhone **Taptic Engine** titreşimleri.

---

## 🚀 Masaüstündeki `sandblockxcode` Klasörüne Aktarma ve Çalıştırma

Mac bilgisayarınızda Terminal'i açıp aşağıdaki tek satırlık komutla dosyaları doğrudan masaüstünüzdeki `sandblockxcode` klasörüne alabilir ve Xcode'da açabilirsiniz:

```bash
mkdir -p ~/Desktop/sandblockxcode && \
git clone https://github.com/HNMLabs/sandblock.git /tmp/sandblock_sync && \
cp -R /tmp/sandblock_sync/sandblockxcode/. ~/Desktop/sandblockxcode/ && \
rm -rf /tmp/sandblock_sync && \
open ~/Desktop/sandblockxcode/SandBlock.xcodeproj
```

> Halihazırda yerel `sandblock` deponuz varsa `git pull` yaptıktan sonra `sandblockxcode/SandBlock.xcodeproj` dosyasını çift tıklayarak veya masaüstündeki klasöre kopyalayarak da açabilirsiniz.

### Xcode İçinde Derleme & Çalıştırma
1. `SandBlock.xcodeproj` açıldığında üst bardan **`SandBlock`** şemasını ve bir **iPhone Simülatörü** (veya USB/Wi-Fi bağlı kendi iPhone'unuzu) seçin.
2. Gerçek cihaza yükleyecekseniz **Signing & Capabilities** sekmesinde kendi Apple hesabınızın (`Team`) seçili olduğundan emin olun (`248V473M84` varsayılan olarak tanımlıdır).
3. **`⌘R` (Run)** tuşuna basarak oyunu başlatın, birim testlerini doğrulamak için **`⌘U` (Test)** kısayolunu kullanın.

---

## 📂 Klasör Yapısı

```text
sandblockxcode/
├── SandBlock.xcodeproj/          # Xcode proje dosyası ve paylaşılan SandBlock şeması
├── SandBlock/
│   ├── SandBlockApp.swift        # @main uygulama giriş noktası ve ekran yönlendiricisi
│   ├── Model/                    # GameTheme, BlockShape, ProgressionModels, AdventureLevel
│   ├── Engine/                   # SandWorld (60FPS kum & lav fiziği, BFS köprü algılama, kayıt)
│   ├── Data/                     # GamePreferences (UserDefaults kalıcı kayıt & ekonomi)
│   ├── Audio/                    # AsmrSoundEngine (AVAudioEngine PCM ses & Zen müzik sentezi)
│   ├── UI/
│   │   ├── Stage/                # SandStageView (CADisplayLink + CoreGraphics oyun sahnesi)
│   │   ├── Components/           # ShopSheetView, QuestsAchievementsSheetView
│   │   └── Screens/              # MainMenuView, AdventureMapView, GameView
│   └── Assets.xcassets/          # 1024x1024 AppIcon ve AccentColor
└── SandBlockTests/
    └── SandWorldTests.swift      # 13 kapsamlı XCTest fizik ve oyun mantığı birim testi
```
