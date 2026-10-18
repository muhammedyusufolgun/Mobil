package com.arvision.studio

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.net.Uri
import android.graphics.Bitmap
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.ar.core.ArCoreApk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private val Blue = Color(0xFF0284C7)
private val Navy = Color(0xFF071625)
private val Pale = Color(0xFFF8FAFC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7,22,37)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary=Blue)) { VisionApp(this) } }
    }
}

private fun checkPassword(password: String): Boolean {
    val digest = MessageDigest.getInstance("SHA-256").digest("arvision-v1|$password".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    // Local prototype gate only. Cloud writes must additionally require Google OAuth authorization.
    return MessageDigest.isEqual(digest.toByteArray(), "edb8ede52dcf3e8d0ec893e0de9d210ade7d7bef0e6b0a72b3bcaeef677fa2dc".toByteArray())
}

@Composable
fun VisionApp(activity: MainActivity) {
    val store = remember { ProjectStore(activity) }
    val scope = rememberCoroutineScope()
    var admin by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var attempts by remember { mutableIntStateOf(0) }
    var lockedUntil by remember { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }
    var published by remember { mutableStateOf(store.published()) }
    var drafts by remember { mutableStateOf(store.drafts()) }
    var customUrl by remember { mutableStateOf(store.customFolder() ?: "") }
    var customApiKey by remember { mutableStateOf(store.driveApiKey()) }
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var selectedFile by remember { mutableStateOf<Uri?>(null) }
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var widthCm by remember { mutableStateOf("20") }
    var refreshKey by remember { mutableIntStateOf(0) }
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val zip = DriveSync.exportDrafts(store)
                activity.contentResolver.openOutputStream(uri)?.use { out -> zip.inputStream().use { it.copyTo(out) } }
                    ?: error("Hedef dosya açılamadı")
            } }.onSuccess { message="Yayın paketi kaydedildi. Drive klasörüne kaydettiyseniz diğer cihazlar indirebilir." }
                .onFailure { message="Paket kaydedilemedi: ${it.message}" }
        }
    }
    val chooseImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedFile = it; capturedImage = null }
    val captureImage = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { image ->
        if (image != null) { capturedImage = image; selectedFile = null }
    }
    var cameraAllowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraAllowed = it }
    LaunchedEffect(Unit) { if (!cameraAllowed) askCamera.launch(Manifest.permission.CAMERA) }

    // Poll only while foreground composable exists. First check never blocks opening the camera.
    LaunchedEffect(refreshKey) {
        delay(700)
        while (true) {
            runCatching {
                val result = DriveSync.check(store) { withContext(Dispatchers.Main) { syncing=true; message="Yeni bilgi yükleniyor…" } }
                withContext(Dispatchers.Main) {
                    if (result.changed) { published=store.published(); message=result.message }
                    else if (result.message != "Güncel" && result.message.contains("anahtar")) message=result.message
                }
            }.onFailure { e -> withContext(Dispatchers.Main) { message="Güncelleme alınamadı; kayıtlı bilgiler kullanılacak" } }
            syncing = false
            delay(90_000)
        }
    }

    Box(Modifier.fillMaxSize().background(if (admin && !preview) Pale else Navy)) {
        if (!cameraAllowed) {
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement=Arrangement.Center, horizontalAlignment=Alignment.CenterHorizontally) {
                Text("Canlı AR Kamera", color=Color.White, style=MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text("Kamera izni gerekli.", color=Color.LightGray)
                Button(onClick={askCamera.launch(Manifest.permission.CAMERA)}) { Text("Kamera İznini Ver") }
            }
        } else if (!admin || preview) {
            val scenes = if (preview) drafts else published
            CameraPanel(activity, scenes)
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().background(Color.White).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Column { Text("AR VISION STUDIO PRO",fontWeight=FontWeight.Bold,color=Blue); Text("Yönetici Paneli",style=MaterialTheme.typography.labelMedium) }
                    TextButton(onClick={admin=false;preview=false;password=""}) { Text("Kullanıcı Modu") }
                }
                LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                Text("Yeni AR tanımı", fontWeight=FontWeight.SemiBold)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Button(onClick={chooseImage.launch("image/*")}) { Text("Dosya Seç") }
                                    OutlinedButton(onClick={captureImage.launch(null)}) { Text("Resim Çek") }
                                }
                                if (selectedFile != null || capturedImage != null) Text("Görsel seçildi ✓", color=Blue)
                                OutlinedTextField(value=title,onValueChange={title=it.take(100)}, label={Text("Tanım adı")}, modifier=Modifier.fillMaxWidth(),singleLine=true)
                                OutlinedTextField(value=text,onValueChange={text=it.take(500)},label={Text("Tanıma sonrası ekranda görülecek yazı")},modifier=Modifier.fillMaxWidth())
                                OutlinedTextField(value=widthCm,onValueChange={widthCm=it.filter { c -> c.isDigit() || c == '.' }.take(8)},label={Text("Basılı görsel genişliği (cm)")},singleLine=true)
                                Button(enabled=(selectedFile != null || capturedImage != null) && title.isNotBlank(), onClick={
                                    scope.launch {
                                        runCatching { withContext(Dispatchers.IO) {
                                            val width = (widthCm.toFloatOrNull() ?: 20f).coerceIn(1f,1000f)/100f
                                            val bitmap = capturedImage
                                            if (bitmap != null) store.addBitmapDraft(bitmap,title,text,width)
                                            else store.addImageDraft(requireNotNull(selectedFile),title,text,width)
                                        } }.onSuccess { drafts=store.drafts(); title=""; text=""; selectedFile=null; capturedImage=null; message="Taslak kaydedildi" }
                                            .onFailure { message="Kayıt başarısız: ${it.message}" }
                                    }
                                }) { Text("Taslak Olarak Kaydet") }
                            }
                        }
                    }
                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                Text("Taslaklar (${drafts.size})",fontWeight=FontWeight.SemiBold)
                                drafts.forEach { s ->
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) { Text(s.title, fontWeight=FontWeight.Medium); Text(s.displayText,style=MaterialTheme.typography.bodySmall,maxLines=1) }
                                        TextButton(onClick={store.deleteDraft(s.id); drafts=store.drafts()}) { Text("Sil") }
                                    }
                                }
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick={preview=true}) { Text("Önizle") }
                                    Button(onClick={
                                        if(drafts.isEmpty()) message="Önce bir tanım ekleyin"
                                        else saveZip.launch(DriveSync.BUNDLE)
                                    }) { Text("Drive'a Yayın Paketi") }
                                }
                                Text("Kaydetme penceresinde Google Drive uygulamanızdaki hedef klasörü seçin. Google Drive dosya sağlayıcısı görünmüyorsa dosyayı indirip elle yükleyin.",style=MaterialTheme.typography.bodySmall,color=Color.Gray)
                            }
                        }
                    }
                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                Text("Sunucu adresi (yalnızca Admin)",fontWeight=FontWeight.SemiBold)
                                Text(if (store.customFolder()==null) "Varsayılan kaynak etkin (adresi gizli)" else "Özel kaynak etkin",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
                                OutlinedTextField(value=customUrl,onValueChange={customUrl=it},label={Text("Kendi Google Drive klasör bağlantınız")},placeholder={Text("https://drive.google.com/drive/folders/...")},modifier=Modifier.fillMaxWidth(),minLines=1,maxLines=3)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Button(onClick={
                                        runCatching {
                                            if(customUrl.isNotBlank()) {
                                                require(customUrl.startsWith("https://drive.google.com/drive/folders/")) { "Drive klasör bağlantısı gerekli" }
                                                require(Regex("/folders/[a-zA-Z0-9_-]{10,}").containsMatchIn(customUrl))
                                            }
                                            store.setCustomFolder(customUrl.trim().ifBlank { null }); published=store.published(); refreshKey++; message="Sunucu ayarı kaydedildi"
                                        }.onFailure { message="Adres geçersiz" }
                                    }) { Text("Kaydet") }
                                    TextButton(onClick={store.setCustomFolder(null);published=store.published();customUrl="";refreshKey++;message="Varsayılan kaynağa dönüldü"}) { Text("Varsayılana Dön") }
                                }
                                Text("Başlangıçta tanımlanan klasör bağlantısı gösterilmez. Yeni adres yalnızca bunu giren Admin'e gösterilir.",style=MaterialTheme.typography.bodySmall,color=Color.Gray)
                                OutlinedTextField(value=customApiKey,onValueChange={customApiKey=it.trim()},label={Text("Drive API anahtarı (isteğe bağlı; yalnızca Admin)")},modifier=Modifier.fillMaxWidth(),visualTransformation=PasswordVisualTransformation(),singleLine=true)
                                OutlinedButton(onClick={store.setDriveApiKey(customApiKey);refreshKey++;message="API ayarı kaydedildi"}) { Text("API Ayarını Kaydet") }
                            }
                        }
                    }
                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                            Text("Yayınlanan içerikler: ${published.size}",fontWeight=FontWeight.Medium)
                            Text("İndirilmiş içerikler internet olmadan da kullanılabilir.", style=MaterialTheme.typography.bodySmall)
                            TextButton(onClick={refreshKey++}) { Text("Güncellemeyi Şimdi Denetle") }
                        } }
                    }
                }
            }
        }
        if (cameraAllowed && (!admin || preview)) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text("AR VISION STUDIO PRO",color=Color.White,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)
                Button(onClick={ if(preview) preview=false else if(admin) preview=false else showLogin=true },contentPadding=PaddingValues(horizontal=12.dp,vertical=2.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF233B55)),shape=RoundedCornerShape(6.dp)) { Text(if(preview) "Editöre Dön" else "admin",style=MaterialTheme.typography.labelSmall) }
            }
            if (syncing) Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top=60.dp),color=Color(0xCC1B4B65),shape=RoundedCornerShape(8.dp)) {
                Text("Yeni bilgi yükleniyor…",Modifier.padding(horizontal=16.dp,vertical=8.dp),color=Color.White)
            }
        }
        if (message.isNotBlank()) {
            Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),color=Color(0xFF1F354E),shape=RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(message,color=Color.White,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={message=""}) { Text("Kapat",color=Color(0xFF93C5FD)) }
                }
            }
        }
    }
    if (showLogin) AlertDialog(
        onDismissRequest={showLogin=false;password=""},
        title={Text("Admin Girişi")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Yönetici işlemleri için şifrenizi girin.")
            OutlinedTextField(value=password,onValueChange={password=it},visualTransformation=PasswordVisualTransformation(),singleLine=true,label={Text("Şifre")})
            if(attempts>0) Text(if(System.currentTimeMillis()<lockedUntil) "Çok fazla deneme; bir süre sonra tekrar deneyin" else "Şifre hatalı",color=Color.Red,style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={TextButton(onClick={
            if(System.currentTimeMillis()>=lockedUntil) {
                if(checkPassword(password)) { admin=true;preview=false;showLogin=false;attempts=0;password="" }
                else { attempts++; if(attempts>=5) { lockedUntil=System.currentTimeMillis()+30_000;attempts=0 };password="" }
            }
        }) { Text("Giriş") }},
        dismissButton={TextButton(onClick={showLogin=false;password=""}) { Text("İptal") }}
    )
}

@Composable
private fun CameraPanel(activity: MainActivity, scenes: List<ArScene>) {
    var displayText by remember { mutableStateOf("") }
    var displayTitle by remember { mutableStateOf("") }
    var matchCenter by remember { mutableStateOf(Pair(0.5f,0.5f)) }
    var error by remember { mutableStateOf("") }
    var arSupported by remember { mutableStateOf(false) }
    var installRequested by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    // No UI gate: camera display attempts immediately; unsupported devices get a camera-only fallback.
    LaunchedEffect(Unit) {
        arSupported = try {
            when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALLED -> true
                else -> { installRequested=true; false }
            }
        } catch (e: Exception) { error="ARCore hazır değil, normal kamera açılıyor"; false }
    }
    Box(Modifier.fillMaxSize()) {
        if (arSupported) {
            val camera = remember { ArCameraView(activity) { msg -> activity.runOnUiThread { error=msg } } }
            LaunchedEffect(camera,scenes) { camera.scenes(scenes) }
            AndroidView(factory={camera},modifier=Modifier.fillMaxSize())
            DisposableEffect(camera,lifecycleOwner) {
                val listener = LifecycleEventObserver { _, event ->
                    when (event) {
                        androidx.lifecycle.Lifecycle.Event.ON_RESUME -> camera.start()
                        androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> camera.stop()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(listener)
                camera.start()
                onDispose { lifecycleOwner.lifecycle.removeObserver(listener); camera.dispose() }
            }
            LaunchedEffect(camera) {
                while (true) {
                    displayText=camera.currentText.get();displayTitle=camera.currentTitle.get();matchCenter=camera.screenCenter.get();delay(120)
                }
            }
        } else {
            AndroidView(modifier=Modifier.fillMaxSize(),factory={context ->
                PreviewView(context).also { pv ->
                    val future=ProcessCameraProvider.getInstance(context)
                    future.addListener({
                        runCatching {
                            val provider=future.get()
                            val preview=Preview.Builder().build().also { it.surfaceProvider=pv.surfaceProvider }
                            provider.unbindAll()
                            provider.bindToLifecycle(activity,CameraSelector.DEFAULT_BACK_CAMERA,preview)
                        }.onFailure { error="Kamera açılamadı: ${it.message}" }
                    }, ContextCompat.getMainExecutor(context))
                }
            })
        }
        // Camera-view overlay tracks the projected reference-image center instead of screen center.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (displayTitle.isNotBlank()) {
                Surface(
                    modifier=Modifier.align(Alignment.TopStart)
                        .offset(x=(maxWidth * matchCenter.first - 95.dp).coerceAtLeast(0.dp),
                                y=(maxHeight * matchCenter.second - 34.dp).coerceAtLeast(0.dp))
                        .widthIn(max=190.dp),
                    color=Color(0xE3075985),shape=RoundedCornerShape(10.dp)
                ) { Column(Modifier.padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(displayTitle,fontWeight=FontWeight.Bold,color=Color.White)
                    if(displayText.isNotBlank()) Text(displayText,color=Color.White)
                } }
            }
        }
        if(error.isNotBlank()) Text(error,Modifier.align(Alignment.BottomCenter).padding(bottom=90.dp, start=20.dp,end=20.dp).background(Color(0xCC142F46),RoundedCornerShape(6.dp)).padding(10.dp),color=Color.White)
        if(!arSupported) Text("AR görüntüleme için desteklenen telefon ve ARCore gerekir",Modifier.align(Alignment.BottomCenter).padding(bottom=20.dp),color=Color.White,style=MaterialTheme.typography.labelSmall)
    }
}
