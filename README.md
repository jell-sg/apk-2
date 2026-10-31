# Ryuza Official – Creator Hub Android

Project GitHub Actions untuk membangun satu APK dari katalog Ryuza Official dengan modul Android native.

## Fitur yang tersedia di project
- HTML katalog Ryuza yang sudah ada dipertahankan di `www/index.html`.
- Panel Creator Hub di dalam aplikasi.
- Pemilihan folder tujuan melalui Android Storage Access Framework.
- Penggantian file dengan nama yang sama. Jika file lama ada, salinan dibuat di folder `Ryuza-Backup` sebelum penggantian.
- Daftar backup dan pemulihan file.
- Informasi perangkat.
- Deteksi Shizuku dan permintaan izin Shizuku melalui API resmi.

## Build melalui GitHub tanpa Android Studio
1. Ekstrak ZIP ini di komputer.
2. Upload **isi folder** ke root repository GitHub (jangan upload ZIP-nya). Pastikan folder tersembunyi `.github` ikut terunggah.
3. Buka tab **Actions** → **Build Ryuza Creator Hub APK** → **Run workflow**.
4. Setelah sukses, download artifact **Ryuza-Creator-Hub-APK**.

## Catatan akses file Android
Operasi backup/replace menggunakan folder yang dipilih dan diizinkan sendiri oleh pengguna melalui Android folder picker. Android 11–16 membatasi akses ke sebagian folder aplikasi, termasuk beberapa lokasi Android/data/obb; memilih folder tidak menjamin semua lokasi dapat diakses. Shizuku di project ini sudah terintegrasi untuk mendeteksi service dan meminta izin, tetapi akses file di modul saat ini tetap menggunakan izin folder Android (SAF), bukan menjalankan perintah shell dengan hak Shizuku. Jangan menganggap Shizuku otomatis melewati pembatasan Android.

Gunakan hanya untuk file yang kamu miliki dan yang modifikasinya diizinkan. Mengubah aset game dapat melanggar ketentuan game atau menyebabkan akun terkena tindakan.

## Struktur penting
- `www/index.html` – katalog dan UI Creator Hub
- `native/android/.../RyuzaToolsPlugin.java` – plugin native Android
- `scripts/prepare-android.mjs` – memasang plugin native ke project Android saat build
- `.github/workflows/build-apk.yml` – workflow build otomatis
