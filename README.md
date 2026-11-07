# Araç Launcher

Android head unit için sade launcher: büyük saat ve tarih, GPS hız göstergesi, medya kontrolü ve 6 özelleştirilebilir kısayol.

## APK'yı üretme (bilgisayara Android Studio kurmadan)

1. github.com'da ücretsiz hesap aç, yeni bir **boş** repo oluştur (ör. `car-launcher`).
2. Bu klasörün içindekileri repoya yükle (Add file > Upload files). `.github` klasörünün de yüklendiğinden emin ol.
3. Repoda **Actions** sekmesine gir. "Build APK" iş akışı kendiliğinden çalışır (~3-5 dk).
4. Bittiğinde çalıştırmanın içindeki **CarLauncher-APK** dosyasını indir, zip'ten çıkar, `app-debug.apk` elde edersin.

Alternatif: klasörü Android Studio ile aç, Build > Build APK(s).

## Üniteye kurma

APK'yı USB bellekle üniteye aktar, dosya yöneticisinden aç ve kur. Home tuşuna basınca açılan seçim ekranında bu launcher'ı "Bir kez" seçerek önce dene. Beğenirsen "Her zaman" yap.

## İlk kullanım

- **Konum izni**: hız göstergesi için izin ver.
- **Medya kartına dokun**: Bildirim erişimi ekranı açılır, "Araç Launcher"ı aç. Böylece çalan şarkı adı ve oynat/geç tuşları çalışır. İzin vermesen de tuşlar medya tuşu gönderir.
- **Kısayol**: boş kutuya dokun, uygulamayı seç. Dolu kısayola uzun basarak değiştir veya kaldır.
- **Zlink / CarPlay**: kısayollardan birine Zlink'i ata.

## Geri dönüş

Sorun çıkarsa Ayarlar > Uygulamalar > Varsayılan uygulamalar > Ana ekran uygulaması'ndan eski launcher'a dön.
