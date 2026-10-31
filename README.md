# Ryuza Official — Android APK

Project ini membungkus website HTML Ryuza Official menjadi aplikasi Android menggunakan Capacitor. File website asli diletakkan di `www/index.html`.

## Build APK lewat GitHub (tanpa Android Studio)

1. Buat repository baru di GitHub.
2. Upload seluruh isi folder/ZIP project ini ke repository (pastikan `package.json`, `capacitor.config.json`, folder `www`, dan `.github` berada di root repository).
3. Pastikan branch bernama `main`.
4. Buka tab **Actions** dan aktifkan Actions jika diminta.
5. Pilih workflow **Build APK Android**, lalu klik **Run workflow**. Build juga berjalan otomatis setiap ada push ke `main`.
6. Setelah selesai, buka hasil workflow yang berstatus hijau.
7. Di bagian **Artifacts**, download `Ryuza-Official-APK`.
8. Ekstrak ZIP artifact dan install `app-debug.apk` di Android.

## Catatan

- APK yang dihasilkan adalah **debug APK** yang bisa dipasang untuk pengujian/pemakaian langsung.
- Untuk distribusi resmi jangka panjang atau Play Store, sebaiknya buat APK/AAB release yang ditandatangani dengan keystore pribadi.
- Website tetap menggunakan layanan online yang memang sudah dipakai oleh HTML, seperti Firebase dan tautan eksternal; koneksi internet diperlukan untuk fitur online.
- `applicationId` saat ini: `com.ryuza.official`. Jangan mengubahnya setelah aplikasi dipublikasikan jika ingin update tetap dikenali sebagai aplikasi yang sama.
