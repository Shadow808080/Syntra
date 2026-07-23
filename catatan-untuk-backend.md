# Catatan untuk Backend

Ditulis dari sisi **aplikasi Android Syntra** untuk tim backend. Isinya: apa yang
sudah dipakai app, kontrak yang app andalkan, dan apa yang masih ditunggu.

Ini pasangan dari [`catatan-untuk-app.md`](catatan-untuk-app.md) (catatan untuk app).
Kontrak endpoint yang mengikat ada di [`api.md`](api.md); pemetaan tiap layar ke
endpoint ada di [`app-backend-alignment.md`](app-backend-alignment.md).

> **Status:** backend sudah menutup **Fase 1** — chat, story, media, presence, dan
> scan QR. App-nya UI-lengkap tetapi masih memakai data dummy in-memory; pekerjaan
> yang tersisa ada di sisi klien (mengganti sumber data). Dokumen ini menegaskan
> *bentuk* data yang app harapkan supaya penyambungan tidak berulang kali gagal.

---

## 1. Yang sudah app pakai dari backend

Semua endpoint di bawah sudah punya konsumen jelas di UI. Tidak ada endpoint
"untuk jaga-jaga" — kalau bentuknya berubah, ada layar yang langsung rusak.

| Layar app | Endpoint / frame | Yang app baca |
|---|---|---|
| Daftar chat | `GET /conversations` | `title`, `unread_count`, `last_message_preview`, `last_message_at`, `counterpart_id` |
| Daftar chat — titik hijau | `presence.query` / `presence.update` (WS) | `online`, `last_seen` |
| Daftar chat — `typing…` | `typing` (WS) | indikator per percakapan |
| Story row | `GET /stories` | grup per orang, `len(stories)`, `all_viewed`, `viewed` |
| Story viewer | `POST /stories/{id}/view` | tandai ditonton (idempoten) |
| Tambah story | media 3 langkah + `POST /stories` | `media_id`, `visibility` |
| Chat detail — riwayat | `GET /conversations/{id}/messages` | cursor `before` = **id pesan** |
| Chat detail — kirim | `message.send` (WS) / `POST .../messages` | `ack` membawa `id` + `created_at` |
| Chat detail — top bar | `presence.query` untuk `counterpart_id` | status lawan bicara |
| Scan QR | `GET /users/{username}` | profil dari username |
| Menu — New group / mulai chat | `POST /conversations` | `direct` idempoten, `group` |

---

## 2. Kontrak yang app andalkan (jangan diubah tanpa kabar)

Ini asumsi yang sudah tertanam di kode app. Kalau salah satu berubah, beri tahu
sebelum rilis.

1. **`GET /conversations` sudah "matang".** `title` untuk `direct` = nama lawan
   bicara; app tidak mencari peserta lain. `counterpart_id` dipakai untuk presence
   dan buka profil. `last_message_type` dipakai saat preview kosong (media).

2. **`POST /conversations` idempoten untuk `direct`.** App memanggilnya setiap kali
   pengguna menekan "kirim pesan pertama" tanpa cek dulu — harus mengembalikan
   percakapan yang sama, bukan membuat duplikat.

3. **Cursor pesan memakai id, bukan waktu.** `before` di `GET .../messages` adalah
   **id pesan** dari `meta.next_before`. App tidak akan mengirim `created_at` sebagai
   cursor.

4. **`ack` membawa `id` + `created_at` final.** App menaruh pesan optimistik lebih
   dulu dengan client id sebagai `ref`, lalu menggantinya saat `ack` tiba — tanpa
   menunggu `message.new`. (Model app sudah menyimpan `id` per entitas untuk ini,
   lihat §4.)

5. **Sinkronisasi ulang lewat `GET .../messages` saat reconnect.** App tidak
   mengandalkan Pub/Sub yang at-most-once; setiap socket terhubung kembali, app
   menarik ulang riwayat percakapan yang terbuka.

6. **Story sudah dikelompokkan & diurutkan server-side.** App tidak
   mengelompokkan/mengurutkan apa pun — `data[]` = avatar, `len(stories)` = segmen
   ring, `all_viewed` = ring abu, `media_kind`/`duration_ms` = perilaku pemutaran.

7. **Media selalu 3 langkah.** `upload-url` → PUT byte ke storage → `confirm`.
   `storage_key` ditentukan server. Byte tidak lewat Go.

8. **QR berisi username** (`syntra://u/<username>`), bukan UUID.

9. **Auth = JWT Supabase.** App mengirim `Authorization: Bearer <jwt>` (REST & WS)
   atau `?token=` untuk WS. Pengingat dari [`api.md`](api.md) §1: JWT yang tidak
   ikut membuat semua query mengembalikan **nol baris tanpa error** — ini penyebab
   nomor satu "kodenya benar tapi datanya kosong", jadi mohon dijaga di sisi RLS.

---

## 3. Yang app tunggu dari backend (belum ada)

Dari [`api.md`](api.md) §12, diurutkan menurut dampaknya ke UI:

1. **Follow/unfollow — paling mendesak.** Tabelnya ada, endpointnya belum.
   `GET /stories` hanya menampilkan story dari yang diikuti, jadi tanpa cara
   membangun daftar following, story row akan berisi milik sendiri saja. Ini
   memblokir fitur yang sudah jadi di app.

2. **Status terkirim/dibaca per pesan (✓✓).** App sudah menggambar indikatornya
   (`Conversation.sent`), tetapi backend baru punya `unread_count`. Untuk 1:1 cukup
   diturunkan dari `last_read_message_id`; tabel `MESSAGE_RECEIPTS` hanya untuk grup.

3. **Ubah/hapus pesan + kelola anggota grup** setelah dibuat.

4. **Push notification (FCM)** saat app tertutup — belum dirancang di kedua sisi.

5. **Starred messages** — ada di menu app (masih placeholder), belum ada di backend.

---

## 4. Yang sudah app siapkan untuk penyambungan

Supaya backend tahu app tidak akan menghambat:

- **Setiap entitas app kini punya `id` stabil** (`Conversation`, `ActivePerson`
  story, `Message`). Pesan/story yang dibuat lokal memakai **client id**
  (`local-<ts>`) yang akan diganti id server saat `ack`/konfirmasi — sesuai
  alur `ref → ack` di [`api.md`](api.md) §9.
- **Gradien avatar diturunkan dari hash id** dan hanya dipakai sebagai cadangan
  saat `avatar_media_id` kosong — dekorasi murni klien, warnanya stabil per id.
- **Format waktu**: app memformat tampilan; app mengharapkan backend selalu
  mengirim **RFC3339 UTC**.
- **Presence** dikueri sekaligus: app akan menanyakan **seluruh `counterpart_id`
  dalam satu frame** `presence.query`, bukan satu per satu.

---

## 5. Yang sengaja app tahan di sisi klien (backend tak perlu buat)

- **Pencarian** — difilter lokal untuk MVP. Pindahkan ke server hanya kalau
  riwayat sudah tidak muat di memori.
- **Tema gelap, Raleway, navigasi berbasis state, story foto 5 detik** — semuanya
  aturan tampilan, bukan data.

---

## 6. Urutan fase (dari sisi app)

Mengikuti [`app-backend-alignment.md`](app-backend-alignment.md) §7.

- **Fase 1 — ✅ selesai di backend**, app tinggal menyambung: conversations,
  messages, stories, media, presence, users/{username}.
- **Fase 1b — sebelum app benar-benar utuh**: follow/unfollow (blocker story row),
  ✓✓ per pesan, edit/hapus pesan & kelola anggota grup, FCM.
- **Fase 2 — Shorts.** Tab & sebagian UI sudah ada di app (masih statis); backend
  belum. Bisa dikerjakan berbarengan tanpa memblokir.
- **Fase 3 — Rooms & Calls.** Layar **Rooms** sudah dibuat di app (Voice Hub,
  masih data statis, tombol Join/room belum aktif); topik `room:<id>` di WS masih
  ditolak sesuai rencana. Calls masih placeholder.
- **Fase 4 — AI.** Belum ada jejaknya di app maupun backend.

---

## 7. Urutan pemanggilan saat app dibuka (yang akan app ikuti)

Sesuai [`api.md`](api.md) §10, sebagai acuan bersama:

```
1. Supabase SDK: login / pulihkan sesi        → JWT
2. GET  /api/v1/conversations                 → isi layar utama
3. GET  /api/v1/stories                       → isi story row
4. WS   connect                               → tunggu frame "ready"
5. WS   subscribe conversation:<id> (yang terlihat)
6. WS   presence.query dengan seluruh counterpart_id
7. Saat chat dibuka: GET .../messages
8. Saat reconnect: ulangi 5–7
```
