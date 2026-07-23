# Syntra

Aplikasi chat Android bergaya modern (mirip WhatsApp) yang dibangun sepenuhnya dengan
**Jetpack Compose**. Syntra menampilkan daftar percakapan, story/status ala WhatsApp
(dengan foto & video), layar percakapan, halaman Shorts, serta fitur scan barcode,
pencarian, dan menu — semuanya dalam tema gelap `#121212` dengan font **Raleway**.

> Catatan: seluruh data (chat, pesan, story) masih bersifat **dummy/in-memory** —
> hilang saat aplikasi ditutup. Belum ada backend atau penyimpanan persisten.

---

## Daftar Isi

- [Teknologi](#teknologi)
- [Struktur Proyek](#struktur-proyek)
- [Cara Menjalankan](#cara-menjalankan)
- [Arsitektur & Navigasi](#arsitektur--navigasi)
- [Alur Aplikasi per Layar](#alur-aplikasi-per-layar)
  - [1. Layar Chat (daftar percakapan)](#1-layar-chat-daftar-percakapan)
  - [2. Story / Status Viewer](#2-story--status-viewer)
  - [3. Menambah Story (foto/video)](#3-menambah-story-fotovideo)
  - [4. Layar Percakapan (Chat Detail)](#4-layar-percakapan-chat-detail)
  - [5. Layar Shorts](#5-layar-shorts)
  - [6. Scan Barcode / QR](#6-scan-barcode--qr)
  - [7. Pencarian](#7-pencarian)
  - [8. Menu Titik-Tiga](#8-menu-titik-tiga)
- [Tema, Warna & Font](#tema-warna--font)
- [Model Data](#model-data)
- [Izin & Ketergantungan Runtime](#izin--ketergantungan-runtime)
- [Keterbatasan & Ide Pengembangan](#keterbatasan--ide-pengembangan)

---

## Teknologi

| Bagian | Detail |
|--------|--------|
| Bahasa | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 37 |
| Font | Raleway (variable font, bundel lokal) |
| Ikon | `material-icons-extended` |
| Scanner | Google Code Scanner (`play-services-code-scanner`) |
| Media | `VideoView` (video story), Android Photo Picker |

---

## Struktur Proyek

```
app/src/main/java/com/example/syntra/
├── MainActivity.kt        # Entry point; host tab (Chat/Shorts) + state navigasi bawah
├── ChatScreen.kt          # Layar Chat: header, story row, daftar chat, story viewer, add-story, search, scan, menu
├── ChatDetailScreen.kt    # Layar percakapan (bubble chat + input bar)
├── ShortsScreen.kt        # Layar Shorts (video vertikal ala TikTok/Reels)
├── NexusNav.kt            # Bottom navigation bar bersama (enum NexusTab + NexusBottomBar)
└── ui/theme/
    ├── Color.kt           # Palet warna Syntra (background #121212, aksen biru, dll.)
    ├── Theme.kt           # SyntraTheme: skema warna gelap + Raleway sebagai font default
    └── Type.kt            # Definisi FontFamily Raleway + Typography Material 3

app/src/main/res/
├── font/raleway_variable.ttf     # Font Raleway (variable weight)
├── drawable/story_*.jpg          # Foto placeholder untuk story bawaan
├── values/colors.xml             # syntra_background = #121212
└── values/themes.xml             # Theme.Syntra (windowBackground #121212, status/nav bar transparan)
```

---

## Cara Menjalankan

1. Buka proyek di **Android Studio** (versi terbaru).
2. Jalankan **Sync Gradle** (mengunduh dependency, termasuk scanner & ikon).
3. Pilih perangkat/emulator. Untuk fitur **scan barcode**, gunakan emulator/HP yang
   memiliki **Google Play Services** (mis. image emulator "Google Play").
4. Tekan **Run** ▶.

> Preview Compose tersedia (`@Preview`) di `ChatScreen.kt` & `ShortsScreen.kt`
> untuk melihat UI tanpa menjalankan emulator penuh.

---

## Arsitektur & Navigasi

Aplikasi menggunakan **satu Activity** (`MainActivity`) dan navigasi berbasis **state**
(bukan Navigation Component). Perpindahan antar layar dilakukan dengan menampilkan/menyembunyikan
composable secara kondisional.

```
MainActivity
 └── SyntraTheme
      └── NexusApp                     // menyimpan tab terpilih (NexusTab)
           ├── ChatScreen              // saat tab == CHAT / ROOMS / CALLS
           │    ├── overlay StoryViewer      // saat sebuah story diklik
           │    └── overlay ChatDetailScreen // saat sebuah chat diklik
           └── ShortsScreen            // saat tab == SHORTS
```

- **Bottom Navigation** (`NexusBottomBar`) punya 4 tab: **Chat, Shorts, Rooms, Calls**.
  Chat & Shorts sudah berisi layar; Rooms & Calls sementara menampilkan layar Chat (placeholder).
- **Story Viewer** dan **Chat Detail** ditampilkan sebagai **overlay layar penuh** di atas
  `ChatScreen`, dan ditutup dengan tombol Back perangkat / gestur / tombol close.

---

## Alur Aplikasi per Layar

### 1. Layar Chat (daftar percakapan)

Layar utama saat aplikasi dibuka (`ChatScreen.kt`).

**Susunan dari atas ke bawah:**
- **Header** — ikon **scan** (kiri), judul **"Syntra"**, ikon **search** & **titik-tiga** (kanan).
- **Story row** (`ActiveRow`) — deretan avatar bundar berisi foto. Ring di sekeliling avatar
  berupa **segmen garis** yang jumlahnya = jumlah story yang di-post orang tersebut.
- **Daftar percakapan** (`ConversationRow`) — tiap baris: avatar, nama, cuplikan pesan,
  waktu, badge jumlah pesan belum dibaca, indikator status (online = titik hijau,
  typing = teks miring biru, terkirim = ✓✓).
- **Tombol + mengambang** (kanan bawah) — untuk menambah story.
- **Bottom navigation**.

**Interaksi:**
- Ketuk sebuah **story** → membuka [Story Viewer](#2-story--status-viewer).
- Ketuk sebuah **chat** → membuka [Chat Detail](#4-layar-percakapan-chat-detail).
- Ketuk **+** → membuka [alur tambah story](#3-menambah-story-fotovideo).
- Ketuk **scan / search / titik-tiga** → lihat bagian masing-masing di bawah.

### 2. Story / Status Viewer

Composable `StoryViewer` — pengalaman menonton story layar penuh ala **Status WhatsApp**.

**Fitur & alur:**
- **Progress bar segmen** di atas — satu bar per story milik orang tersebut.
- **Auto-advance:**
  - Story **foto**: berpindah otomatis setelah **5 detik**.
  - Story **video**: **diputar sampai selesai** dulu (progress bar mengikuti posisi
    pemutaran video nyata) baru berpindah.
- **Navigasi manual:** ketuk sisi **kanan** = story berikutnya, sisi **kiri** = sebelumnya.
  Setelah story terakhir orang terakhir → viewer tertutup.
- **Perpindahan otomatis antar-orang** (Lena → Marcus → …), persis WhatsApp.
- **Swipe ke atas untuk menutup** — konten mengalami **transisi besar → kecil**
  (mengecil, memudar, sudut membulat). Jika tarikan melewati ambang → lanjut menyusut lalu
  tertutup; jika belum → memantul kembali (spring). Ada juga animasi **fade + scale-up** saat
  viewer pertama kali dibuka.
- **Tanda "sudah ditonton" (seen):** setelah semua story seseorang selesai ditonton, ring
  berwarna pada avatarnya di daftar **hilang** dan diganti garis abu tipis (seperti WhatsApp/Instagram).
- Tombol **Back** perangkat & tombol **✕** juga menutup viewer.

### 3. Menambah Story (foto/video)

Alur dari tombol **+** di layar Chat:

1. Membuka **Android Photo Picker** (`PickVisualMedia` mode `ImageAndVideo`) — tanpa perlu
   izin runtime.
2. Pengguna memilih **foto atau video**:
   - **Foto** → di-decode menjadi bitmap (`StoryImage.Bitmap`).
   - **Video** → frame pertama diekstrak sebagai thumbnail (`MediaMetadataRetriever`),
     disimpan sebagai `StoryImage.Video(uri, thumbnail)`.
3. Story baru **ditambahkan di depan** story row dengan label **"Your story"** dan bisa
   langsung diklik untuk ditonton (video akan diputar via `VideoView`).

### 4. Layar Percakapan (Chat Detail)

Composable `ChatDetailScreen` — dibuka saat sebuah chat diklik.

**Susunan:**
- **Top bar ringkas** — avatar + nama + status (`online` / `typing…` / `last seen recently`),
  lalu ikon **video call, call, titik-tiga**. Nama panjang otomatis dipotong dengan **ellipsis**
  (mis. `reza ramadhan start` → `reza ramadhan…`).
- **Daftar pesan** — gelembung chat: pesan masuk rata kiri (abu), pesan sendiri rata kanan
  (biru), masing-masing berjam. Ada chip tanggal **"Today"**.
- **Input bar** — kolom teks (placeholder "Message") dengan ikon emoji/lampiran/kamera, dan
  tombol bulat yang berubah **mic ↔ kirim** tergantung ada tidaknya teks.

**Interaksi:**
- Mengetik lalu **kirim** → pesan baru ditambahkan dan daftar **auto-scroll** ke bawah.
- Keyboard mendorong input ke atas (`imePadding`).
- Tombol **Back** perangkat kembali ke daftar chat.

### 5. Layar Shorts

Composable `ShortsScreen` — tampilan video vertikal ala TikTok/Reels (saat ini area video
masih placeholder).

**Elemen:**
- Header "Nexus" + ikon search + avatar.
- Caption: username (`@quantum_flow`), tombol **Follow**, deskripsi, dan baris audio.
- **Action rail** kanan: like (❤️ 24.5k), komentar (💬 842), share, dan thumbnail audio.
- Bottom navigation (tab Shorts aktif).

### 6. Scan Barcode / QR

Ikon **scan** di kiri-atas header Chat.

- Menggunakan **Google Code Scanner** (`GmsBarcodeScanning`) — membuka UI scanner penuh
  dari Google, **tanpa** menangani izin/kamera manual.
- Hasil scan (`barcode.rawValue`) ditampilkan lewat **Toast**.
- Bila gagal (mis. tanpa Play Services) → Toast error.

### 7. Pencarian

Ikon **search** di header Chat.

- Header berubah menjadi **kolom pencarian** dengan **auto-fokus** (keyboard langsung muncul).
- Daftar chat **terfilter secara langsung** berdasarkan **nama** atau **isi pesan**
  (case-insensitive). Story row disembunyikan selama mencari.
- Jika tidak ada hasil → teks **"No conversations found"**.
- Ikon **✕** mengosongkan teks; **panah kembali** atau tombol **Back** perangkat keluar dari
  mode pencarian.

### 8. Menu Titik-Tiga

Ikon **titik-tiga** di header Chat.

- Membuka **DropdownMenu** dengan opsi: **New group**, **Starred messages**, **Settings**.
- Saat ini setiap opsi memunculkan **Toast** (placeholder) — siap disambungkan ke aksi nyata.

---

## Integrasi Backend

App sudah punya lapisan jaringan penuh ke backend Syntra (Go + Supabase + WebSocket)
sesuai [`api.md`](api.md). Semuanya di balik satu saklar.

### Mengaktifkan

Buka `app/src/main/java/com/example/syntra/net/ApiConfig.kt`:

```kotlin
const val ENABLED = true                    // false = pakai data dummy
const val BASE_URL = "http://10.0.2.2:8081" // 10.0.2.2 = host dari emulator
const val WS_URL   = "ws://10.0.2.2:8081"
const val SUPABASE_URL = "https://<project-ref>.supabase.co"
const val SUPABASE_ANON_KEY = "<anon-key>"
// atau, bila backend jalan dengan AUTH_DEV_BYPASS=true:
const val DEBUG_USER_ID = "<user-uuid>"     // lewati Supabase, kirim X-Debug-User
```

Selama `ENABLED = false`, aplikasi berjalan penuh dengan data dummy seperti sebelumnya.

### Yang sudah tersambung

| Fitur app | Backend |
|---|---|
| Login | Supabase `token?grant_type=password` → JWT (atau `X-Debug-User`) |
| Daftar chat | `GET /conversations` |
| Titik hijau online | `presence.query` + `presence.update` (WS) |
| `typing…` | `typing.start` / `typing.stop` / `typing` (WS) |
| Riwayat pesan | `GET /conversations/{id}/messages` |
| Kirim pesan | `message.send` (WS) — optimistik `ref` → diganti saat `ack` |
| Tandai dibaca | `message.read` (WS) |
| Story row | `GET /stories` (sudah terkelompok; `all_viewed` → ring abu) |
| Tonton story | `POST /stories/{id}/view` |
| Post story | media 3 langkah (`upload-url` → PUT → `confirm`) + `POST /stories` |
| Scan QR | `GET /users/{username}` → `POST /conversations` (direct) → buka chat |
| New group | `POST /conversations` (type `group`) |

Reconnect ditangani otomatis: socket tersambung ulang, lalu app **menarik ulang**
riwayat & daftar chat — menutup sifat at-most-once Redis Pub/Sub (api.md §10).

### Voice Rooms — sepenuhnya dari backend

Layar Rooms **tidak memakai data dummy sama sekali**. Daftar room, peserta, peran,
status mute, dan chat semuanya berasal dari backend; bila backend belum
dikonfigurasi, layar menampilkan pesan kosong, bukan contoh palsu.

| Bagian | Sumber |
|---|---|
| Daftar room | `GET /api/v1/rooms` (+ `meta.sfu_ready`) |
| Gabung | `POST /api/v1/rooms/{id}/join` → `sfu_url` + `sfu_token` |
| Suara | **LiveKit** (`VoiceEngine`) — backend tidak mengalirkan audio |
| Peserta | `GET /api/v1/rooms/{id}/participants`, **dipolling 5 detik** |
| Mute | `PATCH /api/v1/rooms/{id}/mute` |
| Angkat tangan | `POST /api/v1/rooms/{id}/raise-hand` |
| Chat room | `room.chat` → `room.message` (WS, **efemeral**, maks 500 karakter) |

Polling dipakai karena backend **tidak menyiarkan event peserta**. Detail dan
jebakannya ada di [`rooms-api.md`](rooms-api.md); sumber kebenaran ada di
[`docs/voice-rooms.md`](https://github.com/ahmadadptr001/backend-syntra/blob/syntra/docs/voice-rooms.md)
milik repo backend.

Butuh izin **RECORD_AUDIO** (diminta saat pertama menyalakan mikrofon).
Kalau `sfu_ready = false`, tombol Join dinonaktifkan.

### Belum tersambung

- **Shorts** dan **Calls** — belum ada endpoint-nya.
- **Follow/unfollow** — endpoint sudah ada di backend
  (`POST`/`DELETE /users/{username}/follow`, `GET /users/me/following`); klien
  sudah punya `follow()/unfollow()` tetapi belum ada UI-nya.

---

## Tema, Warna & Font

- **Tema gelap tetap** (dynamic color dimatikan) agar tampilan konsisten.
- **Background utama `#121212`** diterapkan di semua layer, termasuk `windowBackground`
  di `themes.xml` supaya tidak ada kedip putih saat aplikasi dibuka.
- Palet kunci (`Color.kt`):
  - `NexusBackground` `#121212` — latar utama
  - `NexusSurface` / `NexusSurfaceElevated` — permukaan/kartu & gelembung pesan masuk
  - `NexusAccent` `#3B68F5` / `NexusAccentSoft` — aksen biru (tombol, timestamp, bubble keluar)
  - `NexusRing` — ring story
  - `NexusOnline` — titik status online
- **Font Raleway** dipasang sebagai satu *variable font* dan dijadikan **default seluruh teks**
  melalui `Typography` + `LocalTextStyle` di `SyntraTheme`.

---

## Model Data

Semua data didefinisikan sebagai `data class` in-memory:

- **`Conversation`** — `name`, `message`, `time`, `gradient`, `unread`, `presence`, `sent`.
- **`Presence`** — enum `NONE` / `ONLINE` / `TYPING`.
- **`ActivePerson`** (story) — `name`, `photo: StoryImage`, `posts` (jumlah story).
- **`StoryImage`** (sealed) — `Res` (drawable bawaan), `Bitmap` (foto galeri), `Video` (uri + thumbnail).
- **`Message`** (di Chat Detail) — `text`, `fromMe`, `time`.

Daftar awal (`conversations`, `defaultActivePeople`) berisi contoh statis; story tambahan dan
pesan baru disimpan di `mutableStateList`/`mutableStateOf` selama sesi berjalan.

---

## Izin & Ketergantungan Runtime

- **Tidak butuh izin kamera manual** untuk scan (ditangani Google Code Scanner) maupun untuk
  memilih media (Android Photo Picker).
- **Google Play Services** diperlukan agar scan barcode berfungsi. Modul scanner di-*prefetch*
  lewat `meta-data com.google.mlkit.vision.DEPENDENCIES = barcode_ui` di `AndroidManifest.xml`.
- `android:windowSoftInputMode="adjustResize"` agar input chat & search terdorong keyboard.

---

## Keterbatasan & Ide Pengembangan

Keterbatasan saat ini (karena fokus pada UI/UX):

- Semua data **dummy & tidak persisten** (hilang setelah app ditutup).
- Story tambahan, status "seen", dan pesan baru hanya bertahan **selama sesi**.
- Tab **Calls** masih placeholder; **Shorts** & **Rooms** sudah punya layar tetapi
  isinya statis (area video Shorts masih placeholder, tombol Join/room belum aktif).
- Item menu titik-tiga masih Toast.
- Durasi progress bar video mengikuti durasi pemutaran, tetapi story foto tetap 5 detik.

Ide pengembangan lanjutan:

- **Menyambungkan ke backend Syntra** (bukan Firebase) — backend-nya **sudah ada**:
  Go + Supabase + WebSocket. Kontrak lengkap ada di [`api.md`](api.md); pemetaan
  tiap layar ke endpoint ada di [`app-backend-alignment.md`](app-backend-alignment.md).
  Klien memakai Supabase Auth SDK (JWT), OkHttp/Ktor untuk REST, dan OkHttp
  WebSocket untuk realtime. FCM dipakai hanya untuk push saat app tertutup.
- **Model app sudah disiapkan**: tiap entitas kini punya `id` stabil dan gradien
  avatar diturunkan dari hash id, agar siap untuk alur optimistik `ref → ack`.
- Penyimpanan lokal (Room/DataStore) sebagai cache offline agar daftar chat tampil seketika.
- Navigation Component + multi-module untuk skala lebih besar.
- Implementasi nyata Shorts (ExoPlayer), Rooms, Calls, dan aksi menu.
