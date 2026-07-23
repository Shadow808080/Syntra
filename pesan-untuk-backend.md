# Pesan untuk Backend — hasil pengujian dari sisi aplikasi

Diuji langsung ke `http://192.168.1.6:8081` dengan akun `admin@syntra.app`.
Semua yang bisa dikerjakan dari sisi app **sudah dikerjakan**; berikut yang tersisa
dan hanya bisa diselesaikan di backend.

---

## ✅ Sudah beres, terima kasih

- `POST /api/v1/auth/login | /register | /refresh` — berjalan, `refresh_token`
  dikembalikan dan bisa dipakai ulang. App tidak lagi menyimpan key Supabase.
- `has_raised_hand` di `GET /rooms/{id}/participants` — **baru terdeteksi hari ini**
  dan langsung dipakai. Host kini melihat antrean "minta izin bicara" beserta
  tombol Izinkan. Workaround lewat room chat sudah dibuang.
- `meta.sfu_ready: true` — LiveKit aktif, tombol Join hidup.

---

## 🔴 1. Room tidak bisa diakhiri — paling mendesak

```
DELETE /api/v1/rooms/{id}   → 404
POST   /api/v1/rooms/{id}/end → 404
```

Akibatnya di aplikasi:

- **Room hantu menumpuk.** Setiap room yang pernah dibuat tetap tampil di
  `GET /rooms` selamanya. Saat ini masih ada room `"Room baru"` sisa pengujian yang
  tidak bisa dihapus siapa pun.
- **Host keluar, room tetap hidup.** App sudah menampilkan peringatan *"room akan
  ditutup"* saat pemilik keluar, tapi janji itu tidak bisa ditepati — `leave` hanya
  mengeluarkan si host, room tetap ada dan peserta lain tetap di dalam.

**Yang dibutuhkan:** satu endpoint untuk mengakhiri room, hanya boleh oleh host.

```
POST /api/v1/rooms/{id}/end     → 204
```

Efeknya: `is_live = false`, room hilang dari `GET /rooms`, dan idealnya
`GET /rooms/{id}/participants` mengembalikan **404** agar peserta yang masih di
dalam tahu harus keluar. App sudah menangani 404 itu — begitu muncul, peserta
otomatis dikeluarkan dengan pesan *"Room telah berakhir"*.

Tolong juga bersihkan room lama yang `started_at`-nya sudah jauh lewat.

## 🔴 2. Tidak ada izin masuk room

`POST /rooms/{id}/join` selalu langsung berhasil. Tidak ada mekanisme persetujuan
host, dan `visibility` (`public` / `private`) belum berpengaruh pada join.

Ini tidak bisa dipalsukan dari sisi app — kalau app yang "menahan" peserta, siapa
pun yang memanggil endpoint langsung tetap bisa masuk dan bicara.

**Usulan minimal:** untuk room `visibility: "private"`,

```
POST /api/v1/rooms/{id}/join      → 202 { "data": { "status": "pending" } }
GET  /api/v1/rooms/{id}/requests  → daftar yang menunggu (host saja)
POST /api/v1/rooms/{id}/requests/{user_id}/approve → 204
POST /api/v1/rooms/{id}/requests/{user_id}/reject  → 204
```

`sfu_token` baru diterbitkan setelah disetujui. App sudah punya layar tahan
("Menyambungkan… menunggu izin"), jadi tinggal menunggu status `pending`.

## 🟡 3. `has_raised_hand` tidak bisa diturunkan

`POST /rooms/{id}/raise-hand` menaikkan bendera, tetapi tidak ada cara
menurunkannya. Setelah host mengizinkan, bendera itu perlu ikut hilang.

Cukup salah satu:
- reset otomatis saat peran berubah jadi `speaker`, atau
- `DELETE /api/v1/rooms/{id}/raise-hand` untuk membatalkan.

Saat ini app menyembunyikannya secara lokal, tapi perangkat lain masih melihat
tangan terangkat.

## 🟡 4. `POST /rooms` mengembalikan JWT sebagai `host_id`

```json
"host_id": "eyJhbGciOiJFUzI1NiIs…"   ← seluruh access token
"max_participants": 0                 ← GET /rooms mengembalikan 50
```

`GET /rooms` benar (`host_id` berupa UUID), jadi ini khusus jalur create.

**Ini masalah keamanan, bukan sekadar kerapian:** klaim `sub` pada `sfu_token`
LiveKit juga berisi token yang sama. Di LiveKit, `sub` adalah *participant
identity* yang **terlihat oleh semua peserta lain di room**. Artinya siapa pun
yang bergabung bisa membaca JWT host dan memakainya selama token itu berlaku.

Akar masalahnya satu: di jalur create/join yang diteruskan adalah string token
mentah, bukan `userID` hasil verifikasi dari context auth.

## 🟡 5. Hapus pesan belum ada

```
DELETE /api/v1/conversations/{id}/messages → 404
```

App sudah punya aksi "hapus semua pesan" (tahan foto profil di header chat), tapi
untuk sekarang hanya membersihkan tampilan di perangkat itu — dan dikatakan apa
adanya ke pengguna. Kalau ada endpoint hapus (per pesan atau seluruh percakapan),
saya sambungkan.

## 🟡 6. Story orang lain tidak muncul

Ini kemungkinan besar **bukan bug**, tapi mohon dikonfirmasi: `GET /stories` hanya
mengembalikan story dari yang sudah di-follow dengan status `accepted`. Akun
`admin` belum mem-follow siapa pun, jadi baris story hanya berisi miliknya sendiri.

`GET /users/me/following` sudah `200`. Yang belum ada: **endpoint menyetujui
permintaan follow**, sehingga akun privat menghasilkan `pending` yang menggantung
selamanya. Untuk pengujian kami pakai akun publik.

---

## Catatan kecil

`POST /api/v1/auth/register` dengan email yang sudah terdaftar
(`admin@syntra.app`) mengembalikan **201**, bukan `409 conflict`. Mohon dicek
apakah ada user duplikat yang terbentuk saat pengujian.
