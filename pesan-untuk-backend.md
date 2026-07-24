# Pesan untuk Backend — dari tim aplikasi

Diuji langsung ke `http://192.168.1.6:8081` dengan akun `admin@syntra.app`.
Diperbarui **24 Juli 2026** setelah membaca `docs/catatan-untuk-app.md` terbaru dan
menguji ulang. Semua yang bisa dikerjakan dari sisi app **sudah dikerjakan**.

---

## ✅ Sudah beres (terima kasih — sudah disesuaikan di app)

- **Auth** `POST /auth/login | /register | /refresh | /logout` — berjalan.
  `register` dengan email duplikat kini balas **409** (sebelumnya 201).
- **`host_id` bocor sebagai JWT** (dulu poin merah) — **sudah diperbaiki**.
  `POST /rooms` kini mengembalikan `host_id` berupa UUID, dan klaim `sub` pada
  `sfu_token` LiveKit juga UUID, bukan token mentah lagi. Risiko keamanannya hilang.
- **`POST /rooms` auto-join** — respons kini menyertakan blok `join`
  (`sfu_url` + `sfu_token`). App memakainya langsung tanpa panggilan `join` kedua.
- **Event room WebSocket** `room.ended` / `room.participants` /
  `room.speak_request` / `room.role_changed` — dipakai. Polling peserta diturunkan
  jadi jaring pengaman 20 detik saja.
- **`has_raised_hand`** di participants — dipakai untuk antrean "minta izin bicara".
- **`avatar_url`** dikirim langsung — app membacanya lebih dulu, `avatar_media_id`
  jadi cadangan.
- **`GET /stories/{id}/viewers`** dan **`GET /stories/me` (view_count)** — dipakai
  di layar penonton story.
- **`counterpart_last_read_id`** — dipakai untuk centang ✓✓.
- **`GET /notifications` + `/unread-count`** — berfungsi (belum disambungkan ke UI;
  lihat catatan di bawah).

**Koreksi dari pesan sebelumnya:** saya pernah menulis "host keluar, room tetap
hidup". **Itu keliru** — `POST /rooms/{id}/leave` sebagai host memang mengakhiri
room (terverifikasi: setelahnya `GET /rooms` kosong). Peringatan "room akan
ditutup" di app sudah akurat.

---

## 🔴 0. Auth via nomor HP — dibutuhkan (login & daftar)

Kami menambah **opsi login "Email / Nomor HP"** dan **kolom Nomor HP di halaman
daftar**. Sisi UI **sudah siap**, tapi jalur nomor HP masih ditandai *"segera
hadir"* dan **dinonaktifkan**, karena backend belum mendukungnya:

- `POST /auth/login` hanya menerima `{ email, password }` — tak ada cara login
  dengan nomor HP.
- `POST /auth/register` menerima `email/password/username/display_name/date_of_birth`
  — **tak ada field `phone`**. (Kami mengirimnya pun akan diabaikan diam-diam.)
- Skema DB sudah punya `users.phone_e164` (unik, nullable) dan
  `discoverable_by_phone` di `erd.md`, tapi **tak ada endpoint** yang menyetel
  atau memakainya.

Yang kami butuhkan supaya jalur ini bisa diaktifkan tanpa ubah UI lagi:

1. **Daftar dengan nomor HP** — terima `phone` (format E.164, mis. `+62812...`)
   di `POST /auth/register`, simpan ke `phone_e164`. Kalau perlu verifikasi OTP,
   balas `pending_confirmation`-style + endpoint verifikasi.
2. **Login dengan nomor HP** — izinkan `POST /auth/login` menerima **`phone`**
   sebagai ganti `email` (atau field `identifier` yang menerima keduanya). Balasan
   sama persis seperti sekarang.
3. Idealnya `PATCH /users/me` juga menerima `phone` agar pengguna email bisa
   menambah nomornya belakangan (opsional, tahap dua).

Begitu ini ada, kami tinggal melepas gerbang "segera hadir" — tidak perlu
perubahan tata letak.

**Catatan kecil (sudah kami manfaatkan):** `register` kini menerima `display_name`
langsung, jadi app **berhenti** memanggil `PATCH /users/me` terpisah setelah
daftar. Terima kasih.

## 🔴 1. Hapus pesan belum ada

```
DELETE /api/v1/conversations/{id}/messages/{message_id}  → endpoint tidak ditemukan
```

App sudah punya menu tekan-lama pesan dengan **"Hapus untuk semua orang"** dan
**"Hapus untuk saya"**. Yang kedua bekerja lokal; yang pertama menunggu endpoint
ini. Idealnya menandai `is_deleted = true` (body dikosongkan) lalu menyiarkan lewat
WS, agar perangkat lain ikut memperbarui.

## 🔴 2. Persetujuan permintaan follow

Akun privat menghasilkan `follow_status: "pending"`, tetapi tidak ada cara
menyetujuinya. Semua kandidat 404:

```
GET  /api/v1/users/me/follow-requests   → tidak ada
POST /api/v1/users/{username}/follow/approve → belum diuji, kemungkinan tidak ada
```

Dampaknya ke app: `GET /stories` hanya menampilkan story dari following ber-status
`accepted`, jadi permintaan yang menggantung membuat story row kosong. Untuk
pengujian kami pakai akun publik. Butuh: daftar permintaan masuk + approve/reject.

## 🟡 3. Menurunkan tangan (raise-hand)

`POST /rooms/{id}/raise-hand` menaikkan bendera; tidak ada cara menurunkannya
manual. Yang ideal: reset otomatis `has_raised_hand = false` saat peran naik jadi
`speaker` (mohon dikonfirmasi apakah sudah begitu). Kalau belum, sediakan
`DELETE /api/v1/rooms/{id}/raise-hand`.

## 🟡 4. Endpoint hapus/akhiri room eksplisit

Room berakhir saat host `leave`, tapi tidak ada cara **menghapus** room yang
host-nya terlanjur hilang tanpa `leave` (mis. sisa pengujian). Sebuah
`DELETE /api/v1/rooms/{id}` (host only) berguna untuk membersihkannya, dan agar UI
bisa menyediakan tombol "Akhiri & hapus".

## ✅ Ganti profil — `PATCH /users/me` sudah ada, terima kasih

Terverifikasi berfungsi: `PATCH /api/v1/users/me {display_name?, avatar_media_id?}`
mengembalikan profil termasuk `avatar_url`, dan `GET /users/{username}` kini
menyertakan avatar. Sudah disambungkan — ganti nama & foto profil tersimpan di akun.

## 🔴 5. Hapus foto lama dari storage — `DELETE /media/{id}` masih hilang

```
DELETE /api/v1/media/{id}  → endpoint tidak ditemukan
```

Saat pengguna mengganti foto profil, foto lama tetap tertinggal di storage. App
sudah menyimpan `media_id` lama dan **sudah memanggil `deleteMedia(oldId)`** tiap
ganti avatar — sekarang no-op, langsung berfungsi begitu endpoint ini ada
(pemilik saja).

## ✅ Hapus pesan — route sudah ada

`DELETE /api/v1/conversations/{id}/messages/{message_id}` kini menjawab
"sumber daya tidak ditemukan" (bukan "endpoint..."), artinya route sudah
terdaftar. **Edit pesan** (`PATCH .../messages/{id}`) belum ada. Mohon konfirmasi
apakah delete menandai `is_deleted` + menyiarkan lewat WS agar perangkat lawan
bicara ikut memperbarui real-time.

## ✅ Blokir & laporan — sudah ada, terima kasih

Terverifikasi berfungsi (sebelumnya saya kira belum ada):
`POST/DELETE /users/{username}/block` (204), `GET /users/me/blocked`,
`POST /reports {reason, target_type:"user", target_id}` (201). Sudah disambungkan
ke menu titik-3 di layar percakapan.

## 🔴 6. `GET /conversations` tidak mengembalikan identitas lawan bicara

Response nyata sekarang hanya: `id, type, title, unread_count,
last_message_preview, last_message_at, created_at`. **`counterpart_id` yang tercatat
di `api.md` tidak muncul.**

Akibatnya dari layar percakapan app **tidak tahu siapa lawan bicara**, sehingga:
- **Laporkan** (butuh `target_id` UUID) hanya jalan untuk chat yang dibuka lewat
  scan QR (di situ app menyimpan id-nya sendiri), tidak untuk chat dari daftar.
- **Blokir** (butuh username) tidak bisa memanggil backend dari daftar chat sama
  sekali — untuk sekarang app menyembunyikannya lokal.

Mohon kembalikan **`counterpart_id`** dan tambahkan **`counterpart_username`** pada
tiap item `direct` di `GET /conversations`. Dengan itu report & block langsung
berfungsi dari mana saja.

Tambahan kecil: agar konsisten dengan `/reports` (yang menerima UUID),
`POST /users/{id}/block` sebaiknya **juga menerima UUID**, bukan hanya username.

## 🟡 7. Story: jangan hitung pemilik sebagai penonton

Pemilik yang menonton story-nya sendiri sebaiknya **tidak menaikkan `view_count`**
maupun muncul di `GET /stories/{id}/viewers`. App sudah berhenti memanggil
`POST /stories/{id}/view` untuk story sendiri, dan menyaring diri sendiri dari
daftar penonton — tapi kalau server pernah menghitungnya, angkanya tetap salah.
Mohon kecualikan `author_id` dari perhitungan & daftar penonton di sisi server.

## 🟡 8. Balas story (story reply)

Belum ada endpoint (`POST /stories/{id}/reply` → 404). App sudah membuat fitur
"balas story" dengan cara mengirim **pesan direct** ke `author_id`
(`POST /conversations {type:direct}` → `POST .../messages`). Berfungsi, tapi
balasannya tidak terhubung ke story tertentu. Kalau nanti ada
`POST /stories/{id}/reply` (yang menautkan balasan ke story + memicu notifikasi
`story_reply`), app akan beralih ke sana.

## 🟡 9. `POST /rooms/{id}/invite` — kontrak body

Route ada (balas `400`, bukan `404`), tapi bentuk body-nya belum terdokumentasi di
`api.md`. Mohon dilengkapi (mis. `{ "user_id": "..." }` atau `{ "username": "..." }`)
supaya app bisa menyambungkan fitur undang ke room `invite_only`.

---

## ✅ Panggilan (audio & video) — sudah disambungkan, terima kasih

Terverifikasi dari `docs/api.md` §Calls dan sudah dipakai app:
`POST /calls {conversation_id, kind}` → `sfu_url`/`sfu_token`, lalu
`.../answer`, `.../decline`, `.../leave`, dan `GET /conversations/{id}/call`.
Event WS `call.incoming` / `call.answered` / `call.ended` dipakai untuk
memunculkan layar dering, mempromosikan ke "berlangsung", dan menutup panggilan.
Media (audio/video) lewat LiveKit persis seperti voice room.

- Untuk **layar panggilan masuk** app memanggil `GET /conversations/{id}/call`
  guna tahu `kind` + `initiator_id`, lalu mengambil nama lawan bicara dari
  `GET /conversations`. Karena itu **poin 6 (counterpart_id/username di
  `/conversations`) makin penting** — tanpa itu nama pemanggil pada layar dering
  bisa kosong untuk chat yang belum pernah dibuka.
- Catatan kecil: `call.incoming` hanya membawa `conversation_id`; kalau payload
  bisa langsung menyertakan `call_id` + `kind` + `initiator`, app tak perlu
  bulat-balik `GET .../call` dulu (bukan penghalang, hanya optimasi).

## 🔴 11. Event realtime yang masih kurang (semua halaman harus live)

Target kami: **semua halaman langsung ikut berubah tanpa refresh**. Event yang ada
(`message.new`, `message.read`, `typing`, `presence.update`, `room.*`, `call.*`,
`conversation.updated`, `notification.new`) sudah dipakai semua. Yang belum ada
dan memaksa app menebak/menyinkronkan sendiri:

1. **`message.deleted`** — hapus pesan sudah ada (`DELETE /messages/{id}`), tapi
   tidak ada siaran WS. Akibatnya perangkat lawan bicara baru tahu pesan dihapus
   setelah membuka ulang chat. Mohon siarkan `{ conversation_id, message_id }`.
2. **`message.reaction`** — reaksi (`PUT /messages/{id}/reaction`) juga tanpa
   event, jadi reaksi tidak muncul realtime.
3. **`reel.new` / `reel.deleted`** — feed Reels tidak punya event sama sekali;
   app terpaksa memuat ulang tiap kali tab Shorts dibuka.
4. **`room.created`** — daftar voice room tidak tahu ada room baru; app sekarang
   menyinkronkan diam-diam tiap 8 detik selama tab Rooms terbuka. Dengan event ini
   polling itu bisa kami hapus.
5. **`story.new`** — story baru tidak muncul sampai daftar chat di-refresh.

## 🟡 10. Event realtime saat profil berubah (`user.updated`)

Ganti foto/nama profil lewat `PATCH /users/me` berhasil dan tersimpan, tapi
**tidak ada event WebSocket** yang memberi tahu perangkat lain. Akibatnya foto
baru baru muncul di perangkat lain saat app dibuka ulang / refresh (app sekarang
menarik `GET /users/me` tiap start & pull-to-refresh untuk itu).

Untuk sinkron **real-time** antar-perangkat milik pengguna yang sama, mohon
siarkan event mis. `user.updated { user_id, display_name, avatar_url }` ke semua
sesi user tersebut setiap `PATCH /users/me`. App akan langsung memperbarui cache
lokal begitu event ini ada.

## ✅ Shorts/Reels — sudah disambungkan, terima kasih

Endpoint `GET /reels`, `POST /reels {media_id, caption}`,
`PUT/DELETE /reels/{id}/like`, `.../save`, `POST /reels/{id}/view`,
`GET/POST /reels/{id}/comments` semuanya dipakai di feed vertikal baru
(swipe atas-bawah, video auto-loop, like/simpan/komentar, unggah via
`media/upload-url` kind `video`). Tombol posting kini benar-benar menerbitkan reel.

---

## Belum disambungkan di app (bukan permintaan — sekadar catatan)

- **Notifikasi in-app** (`GET /notifications`, `/unread-count`, `POST /read`,
  event `notification.new`) sudah berfungsi di server. Rencana: sambungkan ke ikon
  lonceng. Belum dikerjakan.
- **Pesan bermedia** — `POST .../messages` menolak `media_id`
  (`unknown field`). Sementara app mengirim URL media sebagai body teks lalu
  merendernya sebagai foto/suara. Kalau nanti pesan mendukung `media_id` +
  `media_url` + `duration_ms`, app akan beralih ke sana.
