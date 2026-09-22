package dev.pranav.applock.features.vault

import android.app.Application
import android.content.IntentSender
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pranav.applock.data.vault.VaultRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed class VaultUiEvent {
    data class RequestDeleteOriginal(val intentSender: IntentSender) : VaultUiEvent()
    data class Error(val message: String) : VaultUiEvent()
    data class Info(val message: String) : VaultUiEvent()
}

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VaultRepository(application)

    private val _items = MutableStateFlow<List<VaultRepository.VaultItem>>(emptyList())
    val items: StateFlow<List<VaultRepository.VaultItem>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val eventChannel = Channel<VaultUiEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _items.value = repository.listHidden()
            _isLoading.value = false
        }
    }

    /** [uris]: SAF çoklu-seçim sonucu. Her biri şifreli kopyaya taşınır; başarılı olanların
     * orijinalleri için tek bir toplu "silinsin mi?" isteği tetiklenir (API 30+). */
    fun hideFiles(uris: List<Uri>) {
        viewModelScope.launch {
            _isLoading.value = true
            val hiddenSourceUris = mutableListOf<Uri>()
            for (uri in uris) {
                try {
                    val (name, mime) = queryNameAndMime(uri)
                    repository.hideFile(uri, name, mime)
                    hiddenSourceUris.add(uri)
                } catch (e: Exception) {
                    eventChannel.trySend(
                        VaultUiEvent.Error("Gizlenemedi: ${e.message ?: uri}")
                    )
                }
            }
            _items.value = repository.listHidden()
            _isLoading.value = false

            if (hiddenSourceUris.isNotEmpty()) {
                requestDeleteOriginals(hiddenSourceUris)
            }
        }
    }

    private fun requestDeleteOriginals(uris: List<Uri>) {
        val context = getApplication<Application>()
        // Yalnızca content://media (MediaStore) kaynaklı URI'ler toplu silme isteğine uygun;
        // rastgele belge sağlayıcılarından (DocumentsContract) gelenler için o dosya kendi
        // sağlayıcısında silinmeye çalışılır (çoğu zaman kaynak izin verirse çalışır).
        val mediaUris = uris.filter { it.authority == MediaStore.AUTHORITY }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mediaUris.isNotEmpty()) {
            val pi = MediaStore.createDeleteRequest(context.contentResolver, mediaUris)
            eventChannel.trySend(VaultUiEvent.RequestDeleteOriginal(pi.intentSender))
        } else {
            var failed = 0
            uris.forEach { uri ->
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (e: Exception) {
                    failed++
                }
            }
            if (failed > 0) {
                eventChannel.trySend(
                    VaultUiEvent.Info("Gizlendi, ancak $failed orijinal dosya elle silinmeli (kaynak izin vermedi).")
                )
            }
        }
    }

    fun restore(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.restoreToPublicStorage(id)
                eventChannel.trySend(VaultUiEvent.Info("Geri yüklendi."))
            } catch (e: Exception) {
                eventChannel.trySend(VaultUiEvent.Error("Geri yüklenemedi: ${e.message}"))
            }
            _items.value = repository.listHidden()
            _isLoading.value = false
        }
    }

    fun deletePermanently(id: String) {
        viewModelScope.launch {
            repository.deletePermanently(id)
            _items.value = repository.listHidden()
        }
    }

    suspend fun readDecrypted(id: String): ByteArray = repository.readDecryptedBytes(id)

    private fun queryNameAndMime(uri: Uri): Pair<String, String?> {
        val context = getApplication<Application>()
        var name = uri.lastPathSegment ?: "dosya"
        var mime = context.contentResolver.getType(uri)
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) name = it.getString(nameIdx) ?: name
            }
        }
        return name to mime
    }
}
