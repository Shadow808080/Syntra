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

## 🔴 5. Ganti foto profil — butuh 2 endpoint

Fitur ganti foto profil sudah dibuat di app (Settings → Profil, avatar bisa
diketuk → kamera/galeri → unggah). Tapi **dua endpoint hilang**, jadi hasilnya
hanya tersimpan di perangkat:

```
PATCH /api/v1/users/me   (atau PUT)   → endpoint tidak ditemukan
DELETE /api/v1/media/{id}             → endpoint tidak ditemukan
```

Yang dibutuhkan:

1. **`PATCH /api/v1/users/me`** menerima `{ "avatar_media_id": "...", "display_name": "..." }`
   supaya avatar/nama tersimpan di akun dan `GET /users/{username}` mengembalikannya
   (mengembalikan `avatar_url`). Tanpa ini, foto profil tidak pernah terlihat oleh
   pengguna lain.
2. **`DELETE /api/v1/media/{id}`** (pemilik saja) supaya foto lama bisa dihapus dari
   storage saat diganti. Pengguna secara eksplisit meminta agar foto lama dibersihkan.
   App sudah menyimpan `media_id` foto sebelumnya dan **sudah memanggil
   `deleteMedia(oldId)`** setiap avatar diganti — sekarang no-op karena 404, langsung
   berfungsi begitu endpoint-nya ada.

## 🟡 6. `POST /rooms/{id}/invite` — kontrak body

Route ada (balas `400`, bukan `404`), tapi bentuk body-nya belum terdokumentasi di
`api.md`. Mohon dilengkapi (mis. `{ "user_id": "..." }` atau `{ "username": "..." }`)
supaya app bisa menyambungkan fitur undang ke room `invite_only`.

---

## Belum disambungkan di app (bukan permintaan — sekadar catatan)

- **Notifikasi in-app** (`GET /notifications`, `/unread-count`, `POST /read`,
  event `notification.new`) sudah berfungsi di server. Rencana: sambungkan ke ikon
  lonceng. Belum dikerjakan.
- **Shorts/Reels** — belum ada endpoint (`/reels|/shorts|/videos` → 404); tombol
  posting di app menampilkan pesan bahwa server belum siap.
- **Pesan bermedia** — `POST .../messages` menolak `media_id`
  (`unknown field`). Sementara app mengirim URL media sebagai body teks lalu
  merendernya sebagai foto/suara. Kalau nanti pesan mendukung `media_id` +
  `media_url` + `duration_ms`, app akan beralih ke sana.
