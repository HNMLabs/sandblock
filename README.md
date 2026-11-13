# 🏜️ SandBlock — Android Kum Fiziği Blok Bulmacası

**SandBlock**, klasik sürükle-bırak blok bulmacasını gerçek zamanlı **Hücresel Otomat (Cellular Automata) Kum Fiziği**, **ASMR ses sentezi** ve **stratejik güçlendiricilerle** birleştiren yerel (Native) bir Android oyunudur.

## 📥 İndir (Android APK)

- **[📲 SandBlock APK İndir (Doğrudan İndirme — v1.1.0)](https://github.com/HNMLabs/sandblock/raw/main/releases/SandBlock-v1.1.0.apk)** *(Android 8.0 / API 26+)*

## ✨ Özellikler

- **Gerçekçi Kum Fiziği (60 FPS):** Oyun alanına bırakılan her blok yüzlerce kum tanesine dağılır, yerçekimiyle boşluklara akar ve 45° doğal kum tepeleri oluşturur.
- **Sürükle-Bırak & Dokun-Döndür Kontrolleri:**
  - Ekranın altındaki 3'lü tepsideki bloklara **dokunarak 90° döndürebilir**, parmağınızla **oyun alanına sürükleyip** bırakabilirsiniz.
  - Parmak altı kaydırma ofseti ve canlı hayalet önizleme (ghost preview) ile tek elle rahat oynanış.
- **Soldan Sağa Köprü, Kombo & Canlı Altın Kazanımı:**
  - Aynı renkteki kum taneleri **sol duvardan sağ duvara** kesintisiz bir şerit oluşturduğunda parlayarak temizlenir ve **oyun oynarken anında `🪙` altın** kazandırır!
  - Üstte kalan kumların çökmesiyle oluşan ardışık temizlemeler **Kombo Çarpanı (`x2`, `x3`, ...)** ve ekstra altın kazandırır.
- **2 Oyun Modu:**
  1. **Klasik Sonsuz Mod:** Skor arttıkça 3 renkten 5 renge çıkan dinamik zorluk ve yüksek skor yarışı.
  2. **Macera Modu (12 El Yapımı Bölüm):** 4 farklı dünyada özel taş engeller, kum hunileri, hamle sınırları, **Çatlak Kumtaşı (`🪨`)** hedefleri ve **3 Yıldız** derecelendirme sistemi.
- **🛒 Oyun İçi Altın Mağazası & 6 Güçlendirici:**
  - Oyun devam ederken sağ üstteki **`🛒 🪙` Mağaza** butonuna dokunarak kazandığınız altınlarla anında alışveriş yapabilirsiniz:
  - **💣 TNT Bomba:** Geniş bir krater açarak kumları ve taş engelleri patlatır.
  - **⚡ Yatay Lazer:** Seçilen satırı boydan boya lazerle keserek üstteki kumları çökertir.
  - **🎨 Renk Silici:** Seçilen renkteki tüm kum tanelerini tek dokunuşla temizler.
  - **🌈 Gökkuşağı Joker Kum:** Tepsideki bir bloğu her renkle eşleşebilen joker kuma dönüştürür.
  - **🔄 Tepsi Yenile:** Alttaki 3 bloğu anında yeni şekil ve renklerle değiştirir.
  - **🌪️ Kum Fırtınası (Acil Kurtarma):** Mağazadan alındığı anda en üstteki tehlikeli kum yığınını süpürür.
  - **📦 Avantaj Paketleri & 👑 Altın Bereketi:** İndirimli 3'lü TNT / Süper Kombo paketleri ve kalıcı altın çarpanı yükseltmesi (`+%25` → `+%100`).
- **4 Görsel Tema:**
  - 🏜️ **Sahra Çölü** • 🌃 **Neon Gece** • 🌊 **Derin Okyanus** • 🍬 **Pastel Rüya**
- **Prosedürel ASMR Ses & Haptik Titreşim Motoru:**
  - Dış ses dosyasına ihtiyaç duymadan gerçek zamanlı PCM ses sentezi ile kum dökülme hışırtısı, ahşap dokunuş tıkırtısı, pentatonik kristal kombo çanları ve titreşim geri bildirimi.

## 🏗️ Proje Mimarisi

```text
app/src/main/java/com/hnmlabs/sandblock/
├── MainActivity.kt                  # Uygulama giriş noktası ve ekran yönlendirmesi
├── model/
│   ├── GameTheme.kt                 # 4 görsel tema paleti ve renk matematiği
│   ├── BlockShape.kt                # Poliomino şekilleri, döndürme ve 3'lü tepsi üretici
│   └── AdventureLevel.kt            # 12 macera bölümü, arazi şablonları ve güçlendiriciler
├── engine/
│   └── SandWorld.kt                 # Hücresel otomat kum fiziği, 8-yönlü BFS köprü tespiti ve piksel tamponu
├── audio/
│   └── AsmrSoundEngine.kt           # Prosedürel PCM AudioTrack ASMR ses ve titreşim motoru
├── data/
│   └── GamePreferences.kt           # Yüksek skor, altın, güçlendirici, yıldız ve ayar kaydı
└── ui/
    ├── stage/SandStageView.kt       # 60 FPS özel Canvas görünümü, parçacık efektleri ve dokunmatik kontrol
    └── screens/
        ├── MainMenuScreen.kt        # Canlı kum arka planlı ana menü ve tema seçici
        ├── AdventureMapScreen.kt    # 4 dünya, 12 bölümlük yıldızlı macera haritası
        └── GameScreen.kt            # Oyun içi üst HUD, güçlendirici çubuğu ve diyaloglar
```

## 🚀 Derleme ve Çalıştırma

### Android Studio ile
1. Projeyi **Android Studio (Ladybug veya üzeri)** ile açın.
2. Gradle senkronizasyonunun tamamlanmasını bekleyin.
3. Bir Android cihaz veya emülatör (API 26 / Android 8.0+) seçip **Run (`▶`)** tuşuna basın.

### Komut Satırı (CLI) ile
```bash
# Birim testlerini çalıştır
./gradlew testDebugUnitTest

# Debug APK oluştur
./gradlew assembleDebug
```
Derlenen APK dosyası `app/build/outputs/apk/debug/app-debug.apk` konumunda üretilir.

## 📄 Lisans
Bu proje [MIT Lisansı](LICENSE) ile lisanslanmıştır.
