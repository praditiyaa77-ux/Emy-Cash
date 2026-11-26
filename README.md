# Emy Cash (Android)

Aplikasi yang membaca notifikasi pembayaran masuk (DANA Bisnis, GoPay Merchant, ShopeePay) dan mengirimkannya ke gateway Emy Cash supaya order QRIS otomatis berstatus lunas. Dulu bernama NotifApp.

## Pemakaian
1. Pasang APK, buka Emy Cash, **Pengaturan > Akun Emy Cash**, masuk dengan email dan password akun gateway (daftar di web bila belum punya). Alamat server dan kunci perangkat terisi otomatis.
2. Ketuk **Buka Notification Access** dan aktifkan Emy Cash. Ketuk **Izinkan berjalan di latar belakang**.
3. Header menampilkan status listener, akun, paket aktif, dan pemberitahuan versi baru. Riwayat memuat tiap notifikasi: LUNAS, TIDAK COCOK, GAGAL KIRIM, dan seterusnya.

Mode manual (pemilik/server sendiri): isi **Alamat server** dan **Kunci (Secret)** di bagian Server, lalu **Tes koneksi**. Server bawaan: `https://pay.emypedia.my.id`.

## Build (GitHub Actions)
Push ke GitHub; workflow `Build Emy Cash APK` membuat `EmyCash.apk` (artifact) dan, untuk branch `main`/`master`, memperbarui rilis `latest`. Tautan tetap: `https://github.com/praditiyaa77-ux/Emy-Cash/releases/latest/download/EmyCash.apk`. Pasang tautan itu sebagai `APK_URL` di server, atau unggah APK dengan `deploy/unggah-apk.sh` (proyek server).

**Tanda tangan tetap (disarankan).** Tanpa keystore, tiap build memakai kunci debug yang berbeda sehingga pembaruan di atas versi lama gagal ("aplikasi tidak terpasang"). Jalankan `tools/buat-keystore.sh` sekali, isi secret `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, dan simpan cadangan `emycash.jks`.

Naikkan `versionCode` dan `versionName` di `app/build.gradle.kts` setiap rilis, lalu unggah APK dengan angka yang sama agar app menawarkan pembaruan.

## Perubahan dari NotifApp
- Nama dan paket baru: Emy Cash, `id.emypedia.emycash` (terpasang terpisah dari NotifApp; hapus NotifApp lama dan matikan Notification Access-nya agar tidak mengirim ganda).
- **Tes koneksi memakai `/app/status`** (sebelumnya `/api/state` yang tidak ada di server baru, sumber HTTP 404). Pesan error kini membedakan 401, 402, 404, dan server tidak terjangkau.
- Masuk dengan akun, status langganan dan merchant, heartbeat ke server, pemberitahuan versi baru.
- Alamat tanpa skema dilengkapi otomatis (`https://`, atau `http://` untuk jaringan lokal).
- Privasi: WhatsApp tidak lagi dipantau secara bawaan (saklar di bagian Telegram).

Catatan: perubahan ini ditulis tanpa compiler lokal; bila build gagal, kirim log error dari GitHub Actions.

## v1.2: Premium dan edisi developer
- **Tab Premium** (semua edisi): daftar paket, pilih QRIS merchant (**GoPay, DANA, ShopeePay**), QR tampil di app, status dipantau otomatis, paket aktif saat lunas. Butuh gateway v1.2 (`/app/plans`, `/app/subscribe`, `/app/order/:invoice`).
- **Edisi developer** (`EmyCash-Dev.apk`, package `id.emypedia.emycash.dev`, label "Emy Cash Dev"): tab **Dev** berisi cakupan seluruh PG (pola package, status dipantau, notifikasi terakhir per dompet), uji parser lokal dengan contoh GoPay/DANA/ShopeePay (tidak dikirim ke server), info server pemilik (`/app/dev`, butuh Secret di mode manual), dan info build. Terpasang terpisah dari edisi user: matikan Notification Access salah satunya agar notifikasi tidak terkirim ganda.
- Workflow membangun dua APK: `EmyCash.apk` (rilis `latest`) dan `EmyCash-Dev.apk` (hanya artifact, jangan dibagikan publik).
- Naikkan versi: `versionCode 3`, `versionName 1.2`. Unggah APK user ke server dengan angka sama: `bash deploy/unggah-apk.sh EmyCash.apk https://pay.emypedia.my.id 1.2 3`.
- Catatan: kode Kotlin baru ditulis tanpa compiler lokal. Kalau build gagal, kirim log error dari GitHub Actions.

## v1.3: permintaan bayar dan Secret otomatis
- **Permintaan bayar.** Selama Notification Access aktif, app mengambil order baru dari server (`GET /app/events`, long-poll) dan menampilkan notifikasi berisi nominal, merchant, invoice, dan ref, plus entri "Menunggu bayar" di riwayat. Matikan di Pengaturan > Permintaan bayar. Telegram diatur di dashboard; bila belum aktif di server, app meneruskan lewat Telegram miliknya (Pengaturan > Telegram).
- **Edisi dev: Secret pemilik.** Tab Dev > "Buat atau buat ulang Secret" membuat Secret baru di server dan menyimpannya otomatis di app (kolom Secret di Pengaturan ikut terisi). Ketuk hasilnya untuk menyalin.
- **Edisi dev: Telegram pemilik.** Isi bot token dan chat ID, lalu "Simpan dan aktifkan" dan "Kirim pesan tes". Dipakai untuk order langganan layanan.
- Unduh: https://github.com/praditiyaa77-ux/Emy-Cash/releases/tag/latest
