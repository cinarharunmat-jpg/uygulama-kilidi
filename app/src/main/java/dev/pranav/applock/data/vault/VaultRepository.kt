package dev.pranav.applock.data.vault

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * GİZLİ DOSYALAR (Vault) katmanı.
 *
 * Mantık: dosyanın BAYTLARI, uygulamanın kendi özel `filesDir` klasörüne (diğer uygulamalar ve
 * USB/PC üzerinden erişilemez -- Galeri/Dosyalar uygulamalarının MediaStore'u hiç görmez) AES256-GCM
 * ile şifrelenerek taşınır; anahtar Android Keystore'da tutulur (bu cihazda muhtemelen donanım
 * destekli -- anahtar baytları asla RAM/diske çıkmaz). Orijinal dosya, kullanıcının onayıyla
 * (sistem "silinsin mi?" iletişim kutusu) genel depodan silinir. Böylece dosya ne Galeri'de ne
 * Dosyalarım'da görünür; yalnızca Kasa içinden, PIN girildikten sonra erişilebilir.
 *
 * Bilinçli sınır: dosya İÇERİĞİ şifreli, ama küçük bir dizin (index.json: orijinal ad + tarih +
 * boyut) düz metin tutulur -- yalnızca dosya adı/tarih gibi meta veri, gerçek içerik değil.
 */
class VaultRepository(private val context: Context) {

    private val vaultDir: File by lazy {
        File(context.filesDir, "vault").apply { if (!exists()) mkdirs() }
    }
    private val indexFile: File by lazy { File(vaultDir, "index.json") }
    private val indexMutex = Mutex()

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    data class VaultItem(
        val id: String,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val addedAt: Long
    )

    private fun encryptedFileFor(id: String): EncryptedFile {
        val target = File(vaultDir, "$id.enc")
        return EncryptedFile.Builder(
            context, target, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    private suspend fun readIndex(): MutableList<VaultItem> = indexMutex.withLock { readIndexUnsafe() }

    private fun writeIndexLocked(items: List<VaultItem>) {
        val arr = JSONArray()
        items.forEach { it2 ->
            arr.put(
                JSONObject().apply {
                    put("id", it2.id)
                    put("displayName", it2.displayName)
                    put("mimeType", it2.mimeType)
                    put("sizeBytes", it2.sizeBytes)
                    put("addedAt", it2.addedAt)
                }
            )
        }
        indexFile.writeText(arr.toString())
    }

    suspend fun listHidden(): List<VaultItem> = withContext(Dispatchers.IO) {
        readIndex().sortedByDescending { it.addedAt }
    }

    /** [sourceUri] genelde bir SAF seçici sonucundan (content://) gelir. Orijinali SİLMEZ --
     * onu çağıran taraf (ViewModel), sistem onay penceresiyle ayrıca yapar. */
    suspend fun hideFile(sourceUri: Uri, displayName: String, mimeType: String?): VaultItem =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val resolvedMime = mimeType
                ?: MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(displayName.substringAfterLast('.', ""))
                ?: "application/octet-stream"

            val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Kaynak dosya okunamadı: $sourceUri")

            encryptedFileFor(id).openFileOutput().use { out -> out.write(bytes) }

            val item = VaultItem(
                id = id,
                displayName = displayName,
                mimeType = resolvedMime,
                sizeBytes = bytes.size.toLong(),
                addedAt = System.currentTimeMillis()
            )
            indexMutex.withLock {
                val current = readIndexUnsafe()
                current.add(item)
                writeIndexLocked(current)
            }
            item
        }

    // readIndex() zaten kilit alıyor; kilit içinden tekrar çağırmamak için kilitsiz kopya.
    private fun readIndexUnsafe(): MutableList<VaultItem> {
        if (!indexFile.exists()) return mutableListOf()
        val arr = JSONArray(indexFile.readText())
        val out = mutableListOf<VaultItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                VaultItem(
                    id = o.getString("id"),
                    displayName = o.getString("displayName"),
                    mimeType = o.optString("mimeType", "application/octet-stream"),
                    sizeBytes = o.optLong("sizeBytes", 0L),
                    addedAt = o.optLong("addedAt", 0L)
                )
            )
        }
        return out
    }

    suspend fun readDecryptedBytes(id: String): ByteArray = withContext(Dispatchers.IO) {
        encryptedFileFor(id).openFileInput().use { it.readBytes() }
    }

    /** Şifreli baytları çözüp genel depoya (Pictures/Kasa veya Downloads/Kasa) geri yazar,
     * ardından Kasa'daki şifreli kopyayı ve dizin kaydını siler. */
    suspend fun restoreToPublicStorage(id: String): Uri = withContext(Dispatchers.IO) {
        val items = readIndex()
        val item = items.first { it.id == id }
        val bytes = readDecryptedBytes(id)

        val isImageOrVideo = item.mimeType.startsWith("image/") || item.mimeType.startsWith("video/")
        val collection: Uri
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            if (isImageOrVideo) {
                val relDir = if (item.mimeType.startsWith("image/"))
                    Environment.DIRECTORY_PICTURES + "/Kasa" else Environment.DIRECTORY_MOVIES + "/Kasa"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relDir)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Kasa")
            }
        }
        collection = when {
            item.mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            item.mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
        val outUri = context.contentResolver.insert(collection, values)
            ?: throw IllegalStateException("Geri yükleme için kayıt oluşturulamadı")
        context.contentResolver.openOutputStream(outUri)?.use { it.write(bytes) }
            ?: throw IllegalStateException("Geri yükleme için yazılamadı")

        deletePermanently(id)
        outUri
    }

    suspend fun deletePermanently(id: String) = withContext(Dispatchers.IO) {
        indexMutex.withLock {
            val current = readIndexUnsafe().toMutableList()
            current.removeAll { it.id == id }
            writeIndexLocked(current)
        }
        File(vaultDir, "$id.enc").delete()
    }
}
