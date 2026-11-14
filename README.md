# 🏜️ SandBlock v2.0 — Android & iOS Kum Fiziği Blok Bulmacası

**SandBlock**, klasik sürükle-bırak ve düşen blok bulmacalarını gerçek zamanlı **60 FPS Hücresel Otomat (Cellular Automata) Kum Fiziği**, **ASMR ses & Zen müzik sentezi**, **Lav & Buz mekanikleri** ve **zengin ilerleme sistemiyle** birleştiren yerel (Native) bir **Android (Kotlin/Compose)** ve **iOS (Swift/SwiftUI/Xcode)** oyunudur.

## 📥 İndir & Platformlar

- **[📲 Android APK İndir (Doğrudan İndirme — v2.0.0)](https://github.com/HNMLabs/sandblock/raw/main/releases/SandBlock-v2.0.0.apk)** *(Android 8.0 / API 26+)*
- **[🍎 iOS / Xcode Projesi (`sandblockxcode/`)](sandblockxcode/README.md)** *(iOS 16.0+ • %100 Native Swift + SwiftUI + CoreGraphics + AVAudioEngine)*

## ✨ v2.0 Yenilikleri ve Özellikler

- **4 Farklı Oyun Modu:**
  1. **♾️ Klasik Sonsuz Mod:** Dinamik renk artışı, **Otomatik Kayıt & Kaldığın Yerden Devam Et** desteği ve yüksek skor yarışı.
  2. **⏱️ Zamana Karşı (Time Rush):** 90 saniyelik heyecan dolu geri sayım! Temizlediğiniz her kum hattı ve kombo sayaca ekstra saniyeler ekler.
  3. **🕹️ Sandtrix Akış Modu:** Yukarıdan gerçek zamanlı düşen kum bloklarını kaydırarak/tuşlarla yönlendirin, döndürün ve hızla indirip kum tanelerine dağıtın!
  4. **🗺️ Macera Modu (6 Dünya, 20 Bölüm, 60 Yıldız):** Özel arazi engelleri, hamle sınırları, **Çatlak Kumtaşı (`🪨`)** ve **2 katmanlı Buz Bloklar (`🧊`)** ile 20 el yapımı bölüm.
- **🌋 Kor Lav & 🧊 Buz Blok Mekanikleri:**
  - **Lav Blokları (`PieceKind.LAVA`):** Dokunduğu kumları, taşları ve buzulları eriterek aşağı süzülür ve ekstra puan kazandırır.
  - **Buz Blokları (`CELL_ICE` → `CELL_THIN_ICE`):** Yanında gerçekleşen ilk hat temizliğinde çatlar, ikinci temizlemede (veya bomba/lazer/lav ile) tamamen parçalanır!
- **📦 Blok Saklama Yuvası (Hold) & 3B Kum Fiziği:**
  - Hem sürükle-bırak hem de Sandtrix modlarında istediğiniz bloğu **`📦 SAKLA`** yuvasına yedekleyip kritik anlarda kullanabilirsiniz.
  - Hızlandırılmış 60 FPS kum akışı, sarsıntı efektleri (Screen Shake) ve kum tepelerinde 3 boyutlu güneş ışığı yansımaları.
- **🔥 Kombo Ateşi (Fever 2X Modu):**
  - Blok yerleştirip hat temizledikçe Ateş barı dolar; tam dolduğunda **10 saniye boyunca tüm puanlar ve kazanılan altınlar 2 katına (`2X`)** çıkar!
- **🎖️ Seviye/XP, Günlük Görevler, Başarımlar & İstatistikler:**
  - Her gün ücretsiz **Günlük Giriş Hediyesi (`+50 🪙`)**, yenilenen **3 Günlük Görev**, **10 Başarım Rozeti**, oyuncu unvan/seviye sistemi ve detaylı ömür boyu istatistik ekranı.
- **🛒 Oyun İçi Altın Mağazası & İkinci Şans (Revive):**
  - **💣 TNT Bomba**, **⚡ Yatay Lazer**, **🎨 Renk Silici**, **🌈 Joker Kum**, **🔄 Tepsi Yenile**, anında **🌪️ Kum Fırtınası**, **🌋 Lav Yağmuru**, avantaj paketleri, kalıcı **👑 Altın Bereketi** çarpanı ve oyun bittiğinde **💖 Altınla İkinci Şans (Revive)**!
- **6 Görsel Tema & Prosedürel Zen Müzik:**
  - 🏜️ **Sahra Çölü** • 🌊 **Derin Okyanus** • 🌃 **Neon Gece** • 🍬 **Pastel Rüya** • 🌋 **Volkanik Magma** • 🌌 **Kuzey Işıkları**
  - Dış ses dosyası gerektirmeyen gerçek zamanlı ASMR kum hışırtısı ve ayrı tuşla açılıp kapatılabilen **Pentatonik Zen Arka Plan Müziği (`🎵`)**.

## 🏗️ Proje Mimarisi

```text
app/src/main/java/com/hnmlabs/sandblock/
├── MainActivity.kt                        # Uygulama giriş noktası, mod yönlendirmesi ve ses yaşam döngüsü
├── model/
│   ├── GameTheme.kt                       # 6 görsel tema paleti, gökkuşağı & lav renk matematiği
│   ├── BlockShape.kt                      # Poliomino şekilleri, döndürme ve özel Lav/Joker/TNT üretici
│   ├── ProgressionModels.kt               # Oyun modları, XP/Rütbe sistemi, günlük görevler ve 10 başarım
│   └── AdventureLevel.kt                  # 6 dünya, 20 macera bölümü, arazi şablonları ve güçlendiriciler
├── engine/
│   └── SandWorld.kt                       # Hücresel otomat kum & lav fiziği, 2-vuruşluk buz, Fever, Hold ve kayıt
├── audio/
│   └── AsmrSoundEngine.kt                 # Prosedürel PCM ASMR ses efektleri, Zen müzik döngüsü ve haptik motoru
├── data/
│   └── GamePreferences.kt                 # Mod rekorları, altın, XP, görevler, başarımlar ve klasik otomatik kayıt
└── ui/
    ├── stage/SandStageView.kt             # 60 FPS özel Canvas görünümü, Fever alevleri, sarsıntı ve kontroller
    ├── components/
    │   ├── ShopDialog.kt                  # Oyun içi ve menü Altın Mağazası
    │   └── QuestsAchievementsDialog.kt    # Günlük Hediye, Görevler, Başarımlar ve İstatistik ekranı
    └── screens/
        ├── MainMenuScreen.kt              # 4 mod seçimi, devam et butonu, rütbe kartı ve tema seçici
        ├── AdventureMapScreen.kt          # 6 dünya, 20 bölümlük yıldızlı macera haritası
        └── GameScreen.kt                  # Oyun içi HUD, Fever & Süre çubukları, güçlendiriciler ve Revive
```

## 🚀 Derleme ve Çalıştırma

```bash
# Birim testlerini çalıştır
./gradlew testDebugUnitTest

# Release APK oluştur
./gradlew assembleRelease
```

## 📄 Lisans
Bu proje [MIT Lisansı](LICENSE) ile lisanslanmıştır.
