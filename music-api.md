# Music API — spesifikasi untuk Backend (katalog musik komunitas)

Status: **belum ada di server** (terverifikasi `GET /api/v1/music` → **404**, 26 Juli 2026).
Sisi aplikasi **sudah 100% siap** (`SyntraClient.postMusic / getMusicFeed / searchMusic /
deleteMusic`). Begitu 3 endpoint di bawah ada, fitur "unggah lagu → publik → bisa dicari"
langsung hidup **tanpa perubahan app apa pun**.

Dokumen ini menuliskan kontrak **persis** yang diharapkan app (parser `toCommunityTrack`),
supaya tidak ada tebak-tebakan.

---

## Alur besar

Audio-nya **sudah diunggah** lewat pipeline media tiga-langkah yang ada
(`POST /media/upload-url` → `PUT` byte → `POST /media/{id}/confirm`, `kind: "audio"`).
Jadi saat app memanggil `POST /api/v1/music`, `media_id` yang dikirim **sudah tercatat**
di tabel `media` dan sudah punya URL publik. Endpoint musik hanya perlu:

1. mengubah `media_id` (dan opsional `cover_media_id`) menjadi URL publik,
2. menyimpan satu baris di tabel `music_tracks`,
3. mengembalikannya, dan menyediakannya lewat feed + search.

**Byte audio tidak pernah lewat server Go** — sama seperti media lain.

---

## Amplop respons (sama seperti endpoint lain)

- Sukses: `{ "data": <objek | array> }`
- Error: `{ "error": { "code": "...", "message": "..." } }`

App membuka `data` untuk setiap request; error dilempar sebagai `ApiException(code, message)`.

---

## 1. `POST /api/v1/music` — terbitkan lagu (auth wajib)

Header: `Authorization: Bearer <jwt>`

Request body (yang app kirim):

```json
{
  "media_id": "019f8e70-...",      // audio hasil confirm, kind "audio"
  "title": "Judul lagu",
  "artist": "Nama artis",
  "duration_ms": 187000,
  "visibility": "public",
  "cover_media_id": "019f...jpg"   // OPSIONAL — hanya ada bila file punya cover embedded
}
```

Response `data` (objek track):

```json
{ "data": {
    "id": "019fa0...",
    "title": "Judul lagu",
    "artist": "Nama artis",
    "url": "https://<ref>.supabase.co/storage/v1/object/public/media/audio/...m4a",
    "cover_url": "https://.../object/public/media/image/...jpg",
    "duration_ms": 187000,
    "author_id": "4e127292-...",
    "author_name": "Budi",
    "created_at": "2026-07-26T10:11:12Z"
} }
```

**Field yang WAJIB agar app bisa memutar & menampilkan** (lihat parser di bawah):
`url` (URL audio publik) — **tanpa ini track diabaikan app**. Sisanya melengkapi tampilan.

---

## 2. `GET /api/v1/music?limit=40` — feed katalog publik (rail "Unggahan komunitas")

Response `data` = **array** track (bentuk objek sama seperti di atas), terbaru dulu:

```json
{ "data": [ { "id": "...", "title": "...", "artist": "...", "url": "...",
             "cover_url": "...", "duration_ms": 187000, "author_name": "..." }, ... ] }
```

`limit` = jumlah maksimum (app default 40).

---

## 3. `GET /api/v1/music/search?q=<kata>` — cari berdasarkan judul/artis

Sama seperti feed, tapi difilter `ILIKE '%q%'` pada `title` ATAU `artist`. Response =
array track. Query kosong boleh balas array kosong.

---

## 4. `DELETE /api/v1/music/{id}` — hapus lagu sendiri (pemilik saja)

`204`. Non-pemilik → `403`. Idealnya juga hapus objek storage terkait (audio + cover).

---

## Parser app (SUMBER KEBENARAN bentuk field)

Setiap track objek dibaca app begini (`SyntraClient.toCommunityTrack`):

```kotlin
url         = data["url"]  (fallback: data["audio_url"])   // WAJIB, else track di-skip
id          = data["id"]   (fallback: url)
title       = data["title"] (fallback: "Tanpa judul")
artist      = data["artist"] (fallback: data["author_name"] → "Komunitas")
artworkUrl  = data["cover_url"]   (boleh null)
durationSec = data["duration_ms"] / 1000
```

Jadi minimal cukup kirim `url`. Kirim `id`, `title`, `artist`/`author_name`, `cover_url`,
`duration_ms` untuk tampilan penuh.

---

## Migrasi SQL (usulan)

```sql
CREATE TABLE music_tracks (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_id     uuid NOT NULL REFERENCES media(id),      -- audio (kind 'audio')
    cover_media_id uuid REFERENCES media(id),             -- opsional
    title        text NOT NULL,
    artist       text NOT NULL DEFAULT '',
    duration_ms  integer NOT NULL DEFAULT 0,
    visibility   text NOT NULL DEFAULT 'public',
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX music_tracks_created_idx ON music_tracks (created_at DESC);
-- Untuk pencarian judul/artis (opsional, kalau mau cepat):
CREATE INDEX music_tracks_search_idx ON music_tracks
    USING gin (to_tsvector('simple', title || ' ' || artist));
```

`url` & `cover_url` pada respons di-resolve dari `media_id`/`cover_media_id` ke URL publik
storage (sama cara `avatar_url` di-resolve dari media), **tidak** disimpan di tabel ini.

---

## (Opsional) Event realtime — biar rail hidup tanpa refresh

Seperti `reel.new`: setelah `POST /music` sukses, siarkan ke topik publik

```json
{ "type": "music.new", "data": { "id": "...", "author_id": "..." } }
```

App belum mendengarkannya (bukan penghalang) — bisa disambungkan belakangan persis seperti
`reel.new`.
```
```
