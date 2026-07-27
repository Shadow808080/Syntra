package com.example.syntra

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.syntra.ui.theme.NexusTextPrimary
import com.example.syntra.ui.theme.NexusTextSecondary

/**
 * Terms of Service and Privacy Policy.
 *
 * WHY THESE ARE WRITTEN THE WAY THEY ARE. The text below describes what THIS app
 * actually does, taken from the code: which permissions it asks for, what it uploads,
 * what it keeps on the device, and what it deletes. That is the only kind of policy
 * worth shipping — a generic template would claim things Syntra doesn't do and stay
 * silent about things it does (a media cache in app data, a device-local app lock, a
 * "view once" flag that is honoured by the app rather than enforced by the server).
 *
 * IMPORTANT, for whoever ships this: these are drafted to be accurate and readable,
 * not to be legal advice. Before a public release, have them reviewed against the
 * jurisdictions you operate in (for Indonesia: UU PDP No. 27/2022; for EU users:
 * GDPR), and fill in [LEGAL_CONTACT_EMAIL] and [LEGAL_ENTITY] with real details —
 * a privacy policy with no named controller and no working contact address does not
 * satisfy either regime.
 */

/** Replace before release: the legal entity that operates Syntra. */
private const val LEGAL_ENTITY = "Tim Syntra"

/** Replace before release: a mailbox that is actually monitored. */
private const val LEGAL_CONTACT_EMAIL = "privacy@syntra.app"

private const val LAST_UPDATED = "26 Juli 2026"

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

@Composable
private fun LegalHeading(text: String) {
    Spacer(Modifier.height(22.dp))
    Text(
        text = text,
        color = NexusTextPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 21.sp,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun LegalBody(text: String) {
    Text(
        text = text,
        color = NexusTextSecondary,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun LegalIntro(text: String) {
    Text(
        text = text,
        color = NexusTextSecondary,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
}

@Composable
private fun LegalMeta(text: String) {
    Text(
        text = text,
        color = NexusTextSecondary.copy(alpha = 0.7f),
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )
    Spacer(Modifier.height(14.dp))
}

// ---------------------------------------------------------------------------
// Terms of Service
// ---------------------------------------------------------------------------

@Composable
fun TermsScreen(onClose: () -> Unit) {
    SettingsSubScreen("Ketentuan Layanan", onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            LegalMeta("Terakhir diperbarui: $LAST_UPDATED")
            LegalIntro(
                "Ketentuan ini mengatur penggunaan aplikasi Syntra (\"Syntra\", \"kami\") " +
                    "yang dioperasikan oleh $LEGAL_ENTITY. Dengan membuat akun atau " +
                    "menggunakan Syntra, kamu menyetujui ketentuan di bawah ini. Jika kamu " +
                    "tidak setuju, mohon berhenti menggunakan aplikasi.",
            )

            LegalHeading("1. Kelayakan dan akun")
            LegalBody(
                "Kamu harus berusia minimal 13 tahun untuk menggunakan Syntra. Jika kamu " +
                    "berusia di bawah 18 tahun, kamu perlu izin orang tua atau wali.\n\n" +
                    "Kamu bertanggung jawab menjaga kerahasiaan akses ke akunmu, termasuk " +
                    "alamat email, kata sandi, dan perangkat yang kamu gunakan. Beri tahu " +
                    "kami jika kamu menduga ada penggunaan akun tanpa izin. Satu orang " +
                    "boleh memiliki lebih dari satu akun, tetapi akun tidak boleh dibuat " +
                    "untuk menyamar sebagai orang lain.",
            )

            LegalHeading("2. Layanan yang kami sediakan")
            LegalBody(
                "Syntra menyediakan: obrolan pribadi dan grup (teks, foto, video, GIF, " +
                    "stiker, pesan suara), story yang hilang otomatis, panggilan suara dan " +
                    "video, voice room publik, video pendek (Shorts), serta pemutar musik " +
                    "yang mengambil pratinjau dari katalog pihak ketiga.\n\n" +
                    "Kami dapat menambah, mengubah, atau menghentikan fitur. Untuk perubahan " +
                    "yang berdampak besar, kami akan berusaha memberi tahu terlebih dahulu " +
                    "di dalam aplikasi.",
            )

            LegalHeading("3. Kontenmu")
            LegalBody(
                "Kamu tetap memiliki hak atas konten yang kamu buat dan kirim. Dengan " +
                    "mengunggah konten, kamu memberi kami lisensi terbatas, non-eksklusif, " +
                    "dan bebas royalti untuk menyimpan, menyalin, dan menampilkannya SEMATA " +
                    "untuk menjalankan layanan — misalnya mengirimkan fotomu ke lawan " +
                    "bicara, atau memutar Shorts-mu untuk penonton yang membukanya. " +
                    "Lisensi ini berakhir ketika kamu menghapus konten tersebut, kecuali " +
                    "salinan yang sudah diterima orang lain atau yang wajib kami simpan " +
                    "oleh hukum.\n\n" +
                    "Kami TIDAK menjual kontenmu, tidak menggunakannya untuk iklan, dan " +
                    "tidak memakainya untuk melatih model kecerdasan buatan.",
            )

            LegalHeading("4. Aturan penggunaan")
            LegalBody(
                "Dilarang menggunakan Syntra untuk:\n\n" +
                    "• Melecehkan, mengancam, membully, atau menguntit orang lain.\n" +
                    "• Konten seksual yang melibatkan anak, dalam bentuk apa pun. Ini kami " +
                    "laporkan ke pihak berwenang tanpa pengecualian.\n" +
                    "• Ujaran kebencian terhadap suku, agama, ras, disabilitas, gender, " +
                    "atau orientasi seksual.\n" +
                    "• Kekerasan nyata, penyiksaan, atau ajakan menyakiti diri sendiri.\n" +
                    "• Penipuan, skema berantai, atau promosi menyesatkan.\n" +
                    "• Menyebarkan konten milik orang lain tanpa hak.\n" +
                    "• Membagikan ulang isi obrolan pribadi, foto \"sekali lihat\", atau " +
                    "story orang lain tanpa izin mereka.\n" +
                    "• Spam, otomatisasi, scraping, atau upaya membebani sistem kami.\n" +
                    "• Merekayasa balik, meretas, atau mengakali batasan teknis aplikasi.",
            )

            LegalHeading("5. Catatan penting soal \"sekali lihat\" dan story")
            LegalBody(
                "Fitur foto \"sekali lihat\" dan story yang hilang otomatis bekerja di sisi " +
                    "aplikasi. Artinya: kami tidak bisa — dan tidak menjanjikan bisa — " +
                    "mencegah penerima memotret layar, merekam layar, atau memotret " +
                    "layarnya dengan perangkat lain.\n\n" +
                    "Perlakukan fitur ini sebagai kesopanan, bukan jaminan keamanan. " +
                    "Jangan kirim sesuatu yang benar-benar tidak boleh tersimpan.",
            )

            LegalHeading("6. Voice room dan panggilan")
            LegalBody(
                "Voice room bersifat publik: siapa pun yang membuka daftar room dapat " +
                    "bergabung dan mendengarkan. Pembuat room dapat mengangkat peserta " +
                    "menjadi pembicara atau mengembalikannya menjadi pendengar, dan dapat " +
                    "menutup room kapan saja.\n\n" +
                    "Chat di dalam room bersifat sementara dan hilang permanen saat room " +
                    "berakhir — kami tidak menyimpannya.\n\n" +
                    "Jangan merekam panggilan atau room tanpa memberi tahu peserta lain. " +
                    "Di beberapa wilayah, merekam tanpa persetujuan adalah pelanggaran hukum.",
            )

            LegalHeading("7. Penegakan")
            LegalBody(
                "Jika ada laporan atau kami menemukan pelanggaran, kami dapat menghapus " +
                    "konten, membatasi fitur, menangguhkan, atau menghapus akun. Untuk " +
                    "pelanggaran berat — terutama yang membahayakan anak — kami dapat " +
                    "bertindak langsung tanpa peringatan dan melaporkannya ke pihak " +
                    "berwenang.\n\n" +
                    "Kamu dapat mengajukan keberatan atas tindakan kami melalui " +
                    "$LEGAL_CONTACT_EMAIL.",
            )

            LegalHeading("8. Layanan pihak ketiga")
            LegalBody(
                "Syntra menggunakan layanan pihak ketiga untuk berfungsi:\n\n" +
                    "• Penyimpanan media dan basis data (Supabase).\n" +
                    "• Server media untuk panggilan dan voice room (LiveKit).\n" +
                    "• Katalog musik untuk pratinjau lagu (Deezer).\n" +
                    "• Pencarian GIF (GIPHY), bila diaktifkan.\n\n" +
                    "Layanan ini memiliki ketentuan dan kebijakan privasinya sendiri.",
            )

            LegalHeading("9. Tanpa jaminan dan batasan tanggung jawab")
            LegalBody(
                "Syntra disediakan \"sebagaimana adanya\". Kami tidak menjamin layanan " +
                    "selalu tersedia, bebas gangguan, atau bebas kesalahan.\n\n" +
                    "Sejauh diizinkan hukum, kami tidak bertanggung jawab atas kerugian " +
                    "tidak langsung, kehilangan data, kehilangan keuntungan, atau kerugian " +
                    "yang timbul dari penggunaan layanan. Ketentuan ini tidak membatasi " +
                    "tanggung jawab yang menurut hukum tidak dapat dibatasi.",
            )

            LegalHeading("10. Mengakhiri penggunaan")
            LegalBody(
                "Kamu dapat berhenti kapan saja dengan keluar dan menghapus akunmu. " +
                    "Menghapus akun akan menghapus profil, obrolan, story, dan Shorts-mu " +
                    "sesuai jadwal pada Kebijakan Privasi.\n\n" +
                    "Kami dapat menutup akun yang melanggar ketentuan ini, atau menghentikan " +
                    "layanan seluruhnya dengan pemberitahuan yang wajar.",
            )

            LegalHeading("11. Perubahan ketentuan")
            LegalBody(
                "Kami dapat memperbarui ketentuan ini. Jika perubahannya material, kami " +
                    "akan memberi tahu di dalam aplikasi sebelum berlaku. Melanjutkan " +
                    "penggunaan setelah perubahan berarti kamu menerimanya.",
            )

            LegalHeading("12. Hukum yang berlaku dan kontak")
            LegalBody(
                "Ketentuan ini tunduk pada hukum Republik Indonesia. Sengketa akan " +
                    "diselesaikan terlebih dahulu secara musyawarah.\n\n" +
                    "Pertanyaan tentang ketentuan ini: $LEGAL_CONTACT_EMAIL",
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Privacy Policy
// ---------------------------------------------------------------------------

@Composable
fun PrivacyPolicyScreen(onClose: () -> Unit) {
    SettingsSubScreen("Kebijakan Privasi", onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            LegalMeta("Terakhir diperbarui: $LAST_UPDATED")
            LegalIntro(
                "Kebijakan ini menjelaskan data apa yang Syntra kumpulkan, mengapa, dan " +
                    "apa yang bisa kamu lakukan terhadapnya. Pengendali data adalah " +
                    "$LEGAL_ENTITY. Untuk pertanyaan atau permintaan apa pun terkait " +
                    "datamu, hubungi $LEGAL_CONTACT_EMAIL.",
            )

            LegalHeading("1. Data yang kamu berikan")
            LegalBody(
                "• Akun: alamat email, nama tampilan, nama pengguna, dan kata sandi " +
                    "(disimpan dalam bentuk hash, tidak pernah sebagai teks biasa).\n" +
                    "• Profil: foto profil, foto latar, dan bio — semuanya opsional.\n" +
                    "• Konten: pesan, foto, video, GIF, stiker, pesan suara, story, " +
                    "Shorts, komentar, dan judul voice room yang kamu buat.\n" +
                    "• Interaksi: siapa yang kamu ikuti, suka, simpan, dan laporkan.",
            )

            LegalHeading("2. Data yang terkumpul otomatis")
            LegalBody(
                "• Data teknis dasar: jenis perangkat, versi Android, dan versi aplikasi, " +
                    "untuk mendiagnosis masalah.\n" +
                    "• Status kehadiran: apakah kamu sedang online dan kapan terakhir " +
                    "aktif. Kamu bisa mematikannya di Pengaturan → Privasi.\n" +
                    "• Tanda terbaca pesan. Bisa dimatikan di Pengaturan → Privasi.\n" +
                    "• Metadata panggilan dan room: siapa memanggil siapa, kapan, dan " +
                    "berapa lama. Kami TIDAK merekam isi panggilan atau voice room.\n" +
                    "• Jumlah tayangan Shorts.\n\n" +
                    "Syntra tidak mengumpulkan lokasi presisi, tidak membaca daftar kontak " +
                    "teleponmu, dan tidak memuat SDK pelacak iklan.",
            )

            LegalHeading("3. Izin perangkat dan alasannya")
            LegalBody(
                "Setiap izin diminta hanya saat fiturnya dipakai, dan kamu boleh menolak:\n\n" +
                    "• Mikrofon — merekam pesan suara, panggilan, dan berbicara di room.\n" +
                    "• Kamera — mengambil foto, video, panggilan video, dan kamera di room.\n" +
                    "• Foto dan media — memilih berkas yang ingin kamu kirim atau unggah. " +
                    "Kami hanya membaca berkas yang kamu pilih sendiri.\n" +
                    "• Notifikasi — memberi tahu pesan dan panggilan masuk.\n" +
                    "• Biometrik — membuka kunci aplikasi. Data sidik jari tidak pernah " +
                    "meninggalkan perangkat dan tidak dapat kami akses.",
            )

            LegalHeading("4. Data yang tersimpan di perangkatmu saja")
            LegalBody(
                "Hal-hal berikut TIDAK dikirim ke server kami:\n\n" +
                    "• PIN kunci aplikasi dan preferensi biometrik.\n" +
                    "• Tema, latar obrolan, dan pengaturan unduh otomatis.\n" +
                    "• Salinan media yang sudah diunduh (agar tidak diunduh ulang), " +
                    "cache pesan, cache feed, dan cache foto profil.\n" +
                    "• Daftar lagu yang kamu sukai dan musik dari penyimpanan perangkat.\n" +
                    "• Catatan foto \"sekali lihat\" yang sudah dibuka.\n\n" +
                    "Semuanya bisa dihapus lewat Pengaturan → Penyimpanan, atau dengan " +
                    "menghapus aplikasi.",
            )

            LegalHeading("5. Siapa yang bisa melihat apa")
            LegalBody(
                "• Pesan pribadi: hanya kamu dan penerima. Pesan disimpan di server agar " +
                    "sampai ke perangkat penerima.\n" +
                    "• Story: orang yang mengikutimu. Kamu bisa melihat siapa saja yang " +
                    "menontonnya.\n" +
                    "• Shorts, komentar, dan profil: publik.\n" +
                    "• Voice room: publik — siapa pun dapat bergabung dan mendengarkan.\n" +
                    "• Chat di dalam room: hanya peserta saat itu, dan hilang permanen " +
                    "saat room ditutup.",
            )

            LegalHeading("6. Enkripsi — apa yang jujur bisa kami katakan")
            LegalBody(
                "Semua koneksi antara aplikasi dan server kami dienkripsi saat transit " +
                    "(HTTPS/TLS). Media panggilan dan voice room dienkripsi oleh SFU.\n\n" +
                    "Namun Syntra BELUM menerapkan enkripsi ujung-ke-ujung untuk pesan. " +
                    "Artinya pesanmu tersimpan terenkripsi di sisi penyimpanan, tetapi " +
                    "secara teknis dapat diakses oleh sistem kami. Kami menyebutkan ini " +
                    "dengan terbuka agar kamu bisa menilai sendiri apa yang pantas dikirim.",
            )

            LegalHeading("7. Berapa lama data disimpan")
            LegalBody(
                "• Pesan: sampai kamu atau penerima menghapusnya, atau akun dihapus.\n" +
                    "• Story: 24 jam, lalu dihapus otomatis.\n" +
                    "• Chat voice room: dihapus saat room berakhir.\n" +
                    "• Shorts dan komentar: sampai kamu menghapusnya.\n" +
                    "• Media yang kamu ganti (mis. foto profil lama): dihapus dari " +
                    "penyimpanan saat diganti.\n" +
                    "• Data akun: dihapus dalam 30 hari setelah permintaan penghapusan " +
                    "akun. Salinan cadangan terhapus dalam 90 hari.",
            )

            LegalHeading("8. Hakmu atas datamu")
            LegalBody(
                "Kamu berhak untuk:\n\n" +
                    "• Mengakses data yang kami simpan tentangmu.\n" +
                    "• Memperbaiki data yang keliru — sebagian besar bisa langsung diubah " +
                    "di Pengaturan → Profil.\n" +
                    "• Menghapus akun dan datamu.\n" +
                    "• Meminta salinan datamu dalam format yang dapat dibaca mesin.\n" +
                    "• Menolak atau membatasi pemrosesan tertentu.\n" +
                    "• Mengajukan keluhan ke otoritas perlindungan data di wilayahmu.\n\n" +
                    "Kirim permintaan ke $LEGAL_CONTACT_EMAIL. Kami menjawab paling lambat " +
                    "30 hari.",
            )

            LegalHeading("9. Dengan siapa data dibagikan")
            LegalBody(
                "Kami tidak menjual data pribadi. Data hanya dibagikan kepada penyedia " +
                    "yang menjalankan layanan ini:\n\n" +
                    "• Supabase — basis data, autentikasi, dan penyimpanan media.\n" +
                    "• LiveKit — server media panggilan dan voice room.\n" +
                    "• Deezer — pencarian dan pratinjau musik. Kata kunci pencarianmu " +
                    "dikirim ke Deezer; identitas akun Syntra-mu tidak.\n" +
                    "• GIPHY — pencarian GIF, bila diaktifkan. Berlaku hal yang sama.\n\n" +
                    "Kami juga dapat membuka data bila diwajibkan hukum, atau untuk " +
                    "mencegah bahaya serius pada seseorang.",
            )

            LegalHeading("10. Transfer lintas negara")
            LegalBody(
                "Server penyedia kami dapat berada di luar Indonesia. Saat data " +
                    "dipindahkan lintas negara, kami mengandalkan perlindungan kontraktual " +
                    "yang disediakan penyedia tersebut.",
            )

            LegalHeading("11. Anak-anak")
            LegalBody(
                "Syntra tidak ditujukan untuk anak di bawah 13 tahun. Jika kami mengetahui " +
                    "ada akun milik anak di bawah usia tersebut, akun itu kami hapus. " +
                    "Orang tua atau wali dapat menghubungi $LEGAL_CONTACT_EMAIL.",
            )

            LegalHeading("12. Keamanan")
            LegalBody(
                "Kami menggunakan enkripsi saat transit, hashing kata sandi, dan token " +
                    "sesi. Tidak ada sistem yang sepenuhnya aman — jika terjadi kebocoran " +
                    "data yang berisiko bagimu, kami akan memberi tahu sesuai kewajiban " +
                    "hukum yang berlaku.",
            )

            LegalHeading("13. Perubahan kebijakan")
            LegalBody(
                "Jika kebijakan ini berubah secara material, kami memberi tahu di dalam " +
                    "aplikasi sebelum perubahan berlaku, dan memperbarui tanggal di atas.",
            )

            LegalHeading("14. Kontak")
            LegalBody(
                "$LEGAL_ENTITY\n$LEGAL_CONTACT_EMAIL\n\n" +
                    "Untuk pertanyaan privasi, permintaan data, atau keluhan.",
            )
        }
    }
}
