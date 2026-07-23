# Auth — permintaan endpoint untuk Backend

Keputusan arsitektur: **semua kredensial pihak ketiga (Supabase URL, anon key,
service key) berada di backend.** Aplikasi Android hanya bicara ke satu host —
REST API Syntra — dan tidak menyimpan key apa pun.

Konsekuensinya backend perlu **tiga endpoint baru**. Aplikasi **sudah memanggilnya**
(`SyntraClient.loginWith / register / restoreSession`), jadi begitu endpoint ini ada,
login langsung jalan tanpa perubahan di app.

Mengikuti konvensi [`api.md`](api.md): pembungkus `{"data": ...}`, kode error yang
sama, timestamp RFC3339 UTC.

---

## Kenapa perlu

Kondisi sekarang di `api.md` §1: *"Klien login langsung ke Supabase, bukan ke
backend ini."* Itu memaksa app menanam `SUPABASE_URL` + `SUPABASE_ANON_KEY`.

Masalahnya bukan sekadar kerapian:

- **Key ikut terdistribusi.** Setiap APK yang beredar membawa key itu; menggantinya
  berarti merilis ulang aplikasi, bukan sekadar mengubah `.env`.
- **Penyedia identitas jadi terkunci.** Pindah dari Supabase ke apa pun nanti
  menuntut update aplikasi di semua perangkat.
- **Tidak ada satu titik kendali.** Rate limit, audit login, pemblokiran akun, dan
  logging percobaan gagal tidak bisa ditegakkan kalau app menembak Supabase langsung.

Dengan tiga endpoint ini, backend tetap memakai Supabase di belakang layar — hanya
saja app tidak perlu tahu.

---

## 1. `POST /api/v1/auth/register`

Tanpa autentikasi.

```json
{ "email": "budi@syntra.app", "password": "budi123456", "username": "budi" }
```

**201**

```json
{ "data": {
    "access_token": "eyJhbGciOi...",
    "refresh_token": "v1.Mr7...",
    "expires_in": 3600,
    "user": { "id": "4e127292-...", "username": "budi", "display_name": "Budi" }
} }
```

- `username` harus unik — kembalikan `409 conflict` kalau sudah dipakai.
- Kalau proyek Supabase mewajibkan konfirmasi email dan belum ada sesi, kembalikan
  `403` dengan pesan yang bisa langsung ditampilkan, mis. *"Cek email untuk
  konfirmasi, lalu masuk."* Jangan mengembalikan `200` tanpa token — app
  menganggap ada token dan akan gagal di langkah berikutnya.

## 2. `POST /api/v1/auth/login`

Tanpa autentikasi.

```json
{ "email": "budi@syntra.app", "password": "budi123456" }
```

**200** — bentuk balasan **sama persis** dengan register.

`401 unauthorized` kalau kredensial salah. Pesan sebaiknya tidak membedakan
"email tidak ada" dan "kata sandi salah" — itu membocorkan email mana yang terdaftar.

## 3. `POST /api/v1/auth/refresh`

Tanpa autentikasi (refresh token itu sendiri yang jadi bukti).

```json
{ "refresh_token": "v1.Mr7..." }
```

**200**

```json
{ "data": {
    "access_token": "eyJhbGciOi...",
    "refresh_token": "v1.Nz9...",
    "expires_in": 3600
} }
```

`401` kalau sudah kedaluwarsa atau dicabut — app akan menghapus sesi lokal dan
kembali ke layar login.

Kalau backend merotasi refresh token, kirim yang baru di `refresh_token`; app
selalu menyimpan nilai terakhir yang diterima.

## 4. `POST /api/v1/auth/logout` — opsional

Butuh autentikasi. Mencabut refresh token. Balasan `204`.

App saat ini menghapus sesi lokal saja; kalau endpoint ini ada, saya sambungkan.

---

## Yang app lakukan dengan balasannya

| Field | Dipakai untuk |
|---|---|
| `access_token` | header `Authorization: Bearer` di REST **dan** `?token=` di WebSocket |
| `refresh_token` | disimpan; ditukar saat aplikasi dibuka lagi |
| `user.id` | menentukan `fromMe` pada gelembung pesan, peserta room, dsb. |

Alur di app:

```
buka app  →  ada refresh_token tersimpan?
              ya  → POST /auth/refresh   → berhasil? masuk : ke layar login
              tidak → layar login
layar login → POST /auth/login | /auth/register → simpan kedua token → masuk
```

---

## Catatan

- Tiga endpoint ini **tidak boleh** memerlukan `Authorization`, dan harus lolos
  middleware auth yang sekarang menolak semua `/api/v1/*`.
- Rate limit per IP lebih penting di sini daripada di endpoint lain — login adalah
  target brute force. nginx sudah 10 r/s; untuk `/auth/login` sebaiknya lebih ketat.
- Selama endpoint ini belum ada, app menampilkan pesan gagal dari server apa adanya
  (`404 not_found`), dan tidak bisa masuk. Tidak ada jalur lain yang tersisa —
  kredensial Supabase sudah **dihapus** dari aplikasi.
- Alternatif sementara untuk pengujian: jalankan backend dengan
  `AUTH_DEV_BYPASS=true` + `APP_ENV=development`, lalu isi `DEBUG_USER_ID` di
  `ApiConfig` dengan UUID user. App akan mengirim `X-Debug-User` dan melewati login.
