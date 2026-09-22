package dev.pranav.applock.features.vault.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.pranav.applock.data.vault.VaultRepository
import dev.pranav.applock.features.vault.VaultUiEvent
import dev.pranav.applock.features.vault.VaultViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: VaultViewModel = viewModel()
    val items by viewModel.items.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scope = rememberCoroutineScope()

    var viewerItem by remember { mutableStateOf<VaultRepository.VaultItem?>(null) }
    var pendingDeleteConfirm by remember { mutableStateOf<VaultRepository.VaultItem?>(null) }

    val deleteOriginalLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { /* Sonuç ne olursa olsun (kabul/iptal) Kasa tarafındaki gizli kopya zaten oluştu. */ }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is VaultUiEvent.RequestDeleteOriginal -> {
                    deleteOriginalLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(event.intentSender).build()
                    )
                }

                is VaultUiEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                is VaultUiEvent.Info -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) viewModel.hideFiles(uris) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gizli Dosyalar") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Gizle") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { pickerLauncher.launch(arrayOf("*/*")) }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading && items.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (items.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "Henüz gizli dosya yok",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        "Sağ alttaki + ile fotoğraf, video veya belge gizleyebilirsin. Orijinali silmen için sistem onayı istenecek.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        VaultGridItem(
                            item = item,
                            onClick = { viewerItem = item },
                            onRestore = { viewModel.restore(item.id) },
                            onDelete = { pendingDeleteConfirm = item }
                        )
                    }
                }
            }
        }
    }

    viewerItem?.let { item ->
        VaultViewerDialog(
            item = item,
            onDismiss = { viewerItem = null },
            loadBytes = { viewModel.readDecrypted(item.id) },
            onOpenWith = { bytes ->
                scope.launch {
                    val tmpDir = File(context.cacheDir, "vault_tmp").apply { mkdirs() }
                    val tmpFile = File(tmpDir, item.displayName)
                    tmpFile.writeBytes(bytes)
                    val uri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", tmpFile
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, item.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Aç"))
                }
            }
        )
    }

    pendingDeleteConfirm?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeleteConfirm = null },
            title = { Text("Kalıcı olarak silinsin mi?") },
            text = { Text("\"${item.displayName}\" Kasa'dan tamamen silinecek. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePermanently(item.id)
                    pendingDeleteConfirm = null
                }) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteConfirm = null }) { Text("İptal") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultGridItem(
    item: VaultRepository.VaultItem,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (item.mimeType.startsWith("image/")) {
                VaultThumbnail(item)
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Geri Yükle") },
                    leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                    onClick = { menuOpen = false; onRestore() }
                )
                DropdownMenuItem(
                    text = { Text("Kalıcı Olarak Sil") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun VaultThumbnail(item: VaultRepository.VaultItem) {
    val viewModel: VaultViewModel = viewModel()
    var bitmap by remember(item.id) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(item.id) {
        try {
            val bytes = viewModel.readDecrypted(item.id)
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            bitmap = bmp?.asImageBitmap()
        } catch (_: Exception) {
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = item.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun VaultViewerDialog(
    item: VaultRepository.VaultItem,
    onDismiss: () -> Unit,
    loadBytes: suspend () -> ByteArray,
    onOpenWith: (ByteArray) -> Unit
) {
    var bytes by remember(item.id) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(item.id) { bytes = loadBytes() }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text(item.displayName, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val b = bytes
                if (b == null) {
                    CircularProgressIndicator()
                } else if (item.mimeType.startsWith("image/")) {
                    val bmp = remember(b) { BitmapFactory.decodeByteArray(b, 0, b.size) }
                    bmp?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = item.displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(48.dp))
                        Text(
                            "Önizleme yok. Açmak için aşağıdaki düğmeyi kullan.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                TextButton(
                    onClick = { bytes?.let(onOpenWith) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = bytes != null
                ) { Text("Başka Uygulamayla Aç") }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Kapat") }
            }
        }
    }
}
