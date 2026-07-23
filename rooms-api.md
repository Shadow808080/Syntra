# Voice Rooms — catatan integrasi klien

> **Sumber kebenaran ada di backend:**
> [`docs/voice-rooms.md`](https://github.com/ahmadadptr001/backend-syntra/blob/syntra/docs/voice-rooms.md)
> (repo `ahmadadptr001/backend-syntra`, branch `syntra`).
> Dokumen ini hanya mencatat bagaimana app memakainya.

Versi awal berkas ini berisi spesifikasi tebakan sebelum dokumen backend tersedia.
Isinya sudah dibuang — beberapa hal ternyata **berbeda** dari tebakan itu, dan
tiga di antaranya mengubah cara layar dibangun.

---

## Tiga hal yang mengubah implementasi

**1. Audio tidak lewat backend.** Backend hanya menerbitkan token; suaranya lewat
**LiveKit**. `POST /rooms/{id}/join` mengembalikan `sfu_url` + `sfu_token`, dan app
menyambungkannya lewat `VoiceEngine`. Tanpa itu tidak ada suara sama sekali.

**2. Backend tidak menyiarkan event peserta.** Tidak ada `room.state` maupun
`room.participant`. Satu-satunya frame room yang ada adalah chat. Karena itu daftar
peserta **di-polling** tiap 5 detik lewat `GET /rooms/{id}/participants`.

**3. Mute & angkat tangan lewat REST, bukan WebSocket.**
`PATCH /rooms/{id}/mute` dan `POST /rooms/{id}/raise-hand`.

Satu jebakan yang sudah ditangani: **token lama tidak bisa menyalakan mikrofon.**
Token diterbitkan dengan `can_publish` yang berlaku saat itu, jadi setelah peran
naik jadi speaker app memanggil `join` lagi dan menyambung ulang. Polling
mendeteksi perubahan peran ini otomatis.

---

## Peta endpoint → kode app

| Aksi | Backend | Kode |
|---|---|---|
| Daftar room | `GET /rooms` (+ `meta.sfu_ready`) | `SyntraClient.getRooms()` |
| Buat room | `POST /rooms` | `createRoom()` |
| Gabung | `POST /rooms/{id}/join` | `joinRoom()` → `VoiceEngine.connect()` |
| Peserta | `GET /rooms/{id}/participants` | `getRoomParticipants()`, dipolling |
| Ubah peran | `PATCH /rooms/{id}/participants` | `setRoomRole()` |
| Mute | `PATCH /rooms/{id}/mute` | `setRoomMuted()` |
| Angkat tangan | `POST /rooms/{id}/raise-hand` | `raiseHand()` |
| Keluar | `POST /rooms/{id}/leave` | `leaveRoom()` |
| Chat kirim | `room.chat` (WS) | `roomChat()` |
| Chat terima | `room.message` (WS) | `SocketListener.onRoomMessage` |

`meta.sfu_ready = false` → tombol **Join dinonaktifkan**, karena bergabung hanya
akan menghasilkan diam.

---

## Yang sengaja tidak di-cache

Chat room **tidak disimpan** ke Room/DataStore. Ia memang dirancang hilang saat
room berakhir; menyimpannya akan membuat app menampilkan riwayat yang tidak
dimiliki peserta lain.

---

## Batas dari dokumen backend

| Batas | Nilai |
|---|---|
| Panjang chat room | 500 karakter |
| Masa berlaku token SFU | 6 jam |
| Peserta per room | 50 |
| Port UDP LiveKit (self-host) | 7882 harus terbuka |
