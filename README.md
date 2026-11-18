# Disiplin AI (v2)

Aplikasi Android pribadi yang mendisiplinkan hidupmu: membatasi aplikasi, mengunci jam fokus & tidur,
menyusun rencana harian/olahraga/target lewat agent AI (Gemini, free tier), menagih check-in, dan menghukum
(memotong jatah aplikasi) bila gagal.

> **Status:** kode ditulis tanpa bisa dikompilasi atau diuji di perangkat saat pembuatan. Bila build gagal atau ada
> perilaku aneh, kirim pesan error/tangkapan layarnya dan akan diperbaiki.

## Yang baru di v2
- **Model otomatis:** daftar model diambil dari API key-mu, dipilih otomatis, dan bila satu model gagal
  (dimatikan / kuota habis) app otomatis mencoba model berikutnya. Tidak perlu menebak nama model.
- **Onboarding 5 langkah** (API key, izin, profil & jadwal, target pertama, aplikasi dibatasi).
- **Tanda tangan tetap + rilis otomatis** di GitHub, jadi APK bisa di-update tanpa uninstall (lihat bawah).
- **Dashboard baru:** cincin progres, tugas berikutnya, grafik pemakaian 7 hari, tema gelap/terang/sistem.
- **Layar blokir:** hitung mundur sampai bebas, tugas berikutnya, dan target + sisa hari.
- **Tab Progres:** laporan mingguan (statistik, heatmap 5 minggu, aplikasi paling bocor, tinjauan oleh agent),
  target, dan **kebiasaan berulang** yang otomatis muncul sebagai tugas.
- **Kunci imbalan:** aplikasi (mis. game) terkunci sampai 1 tugas "olahraga/belajar/kerja" selesai hari itu.
- **Hari ringan:** 1x per 7 hari, gagal tidak memutus streak/penalti.
- **Perlindungan uninstall** (Device Admin) dan **backup/restore** JSON (API key tidak diekspor).
- **Unit test** untuk logika inti (jam tidur, penalti, jatah).

## Build & rilis lewat GitHub (gratis)

### 1) Sekali saja: buat kunci tanda tangan
Di laptop dengan Java (atau Termux di HP): 
```
keytool -genkeypair -v -keystore disiplin.jks -alias disiplin -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 disiplin.jks > disiplin.b64      # di macOS: base64 -i disiplin.jks
```
**Simpan `disiplin.jks` dan passwordnya baik-baik.** Jika hilang, update tidak bisa lagi dipasang di atas versi lama.

### 2) Simpan sebagai secrets
Repo GitHub > Settings > Secrets and variables > Actions > New repository secret:

| Nama | Isi |
|---|---|
| `KEYSTORE_B64` | isi file `disiplin.b64` |
| `KEYSTORE_PASSWORD` | password keystore |
| `KEY_ALIAS` | `disiplin` |
| `KEY_PASSWORD` | password key (sama dengan keystore bila tidak dibedakan) |

### 3) Jalankan
Push ke `main` atau Actions > Build APK > Run workflow. Hasilnya:
- APK bertanda tangan tetap di **Releases** (`disiplin-ai.apk`), versionCode naik otomatis tiap run.
- Tanpa secrets, workflow tetap membuat APK debug (tidak bisa di-update antar-run; harus uninstall dulu).

### 4) Update di HP
Pasang file APK baru langsung di atas yang lama (data aman karena tanda tangan sama), atau pakai
**Obtainium** (gratis) dengan URL repo-mu agar update terdeteksi otomatis.

> Build sebelumnya yang tak bertanda tangan tetap harus di-uninstall **sekali** sebelum memasang build bertanda tangan tetap.

## Setup pertama di HP
Ikuti wizard onboarding. Ringkasnya:
1. API key gratis dari https://aistudio.google.com/apikey, tekan **Hubungkan & pilih model otomatis**.
2. Izin wajib: **Akses data penggunaan** dan **Layanan aksesibilitas**. Android 13+ untuk APK di luar Play Store:
   bila aksesibilitas abu-abu, buka Info Aplikasi > titik tiga > **Izinkan pengaturan terbatas**.
3. Disarankan: notifikasi, alarm tepat waktu, abaikan optimasi baterai, serta Autostart di Xiaomi/Oppo/Vivo/Samsung.
4. Nyalakan **Mode Strict** dan **Perlindungan uninstall** di Setelan setelah semuanya pas.

## Struktur proyek
```
app/src/main/java/com/disiplinai/
  data/       Models.kt, Store.kt (SharedPreferences+JSON, StateFlow, backup)
  logic/      PolicyEngine, UsageTracker, Discipline, Habits, Perms, Util
  ai/         GeminiClient (daftar model + fallback), Agent (prompt)
  service/    BlockerService (aksesibilitas), Reminders/Receivers, Notifier, AdminReceiver
  ui/         Compose: Theme, Onboarding, Hari Ini, Agent, Aplikasi, Progres, Setelan, BlockActivity
app/src/test/ PolicyEngineTest
.github/workflows/build.yml
```

## Batasan yang jujur
- Tidak ada pemblokir yang kebal. Di luar jam fokus/tidur kamu tetap bisa menonaktifkan aksesibilitas/admin dan
  meng-uninstall; Mode Strict dan Device Admin hanya mempersulit dan menunda.
- Model Gemini dan batas free tier sering berubah; fallback otomatis membantu, tetapi bila seluruh model gagal
  app akan menampilkan pesan yang jelas.
- Pembukaan layar blokir dari latar belakang dapat dibatasi sebagian ROM; minimal kamu tetap dikirim ke Home.
- Alarm tepat waktu di Android 12+ butuh izin khusus; tanpa itu bisa telat beberapa menit.
- Data pemakaian aplikasi di Android hanya tersedia sekitar satu minggu ke belakang.
- Belum ada: widget layar utama, verifikasi olahraga via Health Connect, database Room, penanganan khusus pergantian
  zona waktu. Semuanya bisa jadi tahap berikutnya.
