package com.appforge.ide

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

val Bg = Color(0xFF0D1117)
val CardC = Color(0xFF161B22)
val Blue = Color(0xFF1F6FEB)
val Line = Color(0xFF30363D)
val Muted = Color(0xFF8B949E)
val Red = Color(0xFFF85149)
val Green = Color(0xFF3FB950)

val typeLabels = linkedMapOf(
    "kotlin" to "Android (Kotlin)",
    "java" to "Android (Java)",
    "flutter" to "Flutter",
    "html" to "HTML"
)

@Composable
fun AppRoot(onPickZip: () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue, onPrimary = Color.White,
            background = Bg, onBackground = Color.White,
            surface = CardC, onSurface = Color.White
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val b = AppState.browser
            val ed = AppState.editing
            val pid = AppState.openProjectId

            BackHandler(enabled = b != null || ed != null || pid != null) {
                if (b != null) {
                    Auth.cancel()
                    AppState.browser = null
                } else if (ed != null) {
                    AppState.editing = null
                } else {
                    AppState.openProjectId = null
                }
            }

            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (b != null) {
                    BrowserScreen(b)
                } else if (ed != null && pid != null) {
                    EditorScreen(pid, ed)
                } else if (pid != null) {
                    ProjectScreen(pid)
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) {
                            when (AppState.tab) {
                                0 -> ProjectsScreen(onPickZip)
                                1 -> LibraryScreen()
                                else -> SettingsScreen()
                            }
                        }
                        BottomBar()
                    }
                }
            }
        }
    }
}

@Composable
fun BottomBar() {
    Row(Modifier.fillMaxWidth().background(CardC)) {
        listOf("المشاريع", "المكتبة", "الإعدادات").forEachIndexed { i, t ->
            val sel = AppState.tab == i
            Column(
                Modifier.weight(1f).clickable { AppState.tab = i }.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.width(40.dp).height(3.dp).background(if (sel) Blue else Color.Transparent))
                Spacer(Modifier.height(8.dp))
                Text(t, color = if (sel) Blue else Muted, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.clip(shape)
            .border(1.dp, if (selected) Blue else Line, shape)
            .background(if (selected) Blue.copy(alpha = 0.25f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, fontSize = 13.sp, color = if (selected) Color.White else Muted)
    }
}

// ---------------- المشاريع ----------------
@Composable
fun ProjectsScreen(onPickZip: () -> Unit) {
    var showNew by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("AppForge IDE", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { showNew = true },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("+ مشروع جديد", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = onPickZip,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("استيراد ZIP", color = Color.White) }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("مشاريعك", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${AppState.projects.size} مشروع", color = Muted)
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(AppState.projects.toList(), key = { it.id }) { p -> ProjectCard(p) }
        }
    }
    if (showNew) NewProjectDialog { showNew = false }
}

@Composable
fun ProjectCard(p: Project) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { AppState.openProjectId = p.id },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardC)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF0EA5E9)),
                contentAlignment = Alignment.Center
            ) {
                Text(p.name.take(1).uppercase(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(typeLabels[p.type] ?: p.type, color = Color(0xFFA371F7), fontSize = 13.sp)
                if (p.status.isNotEmpty()) Text(p.status, color = Muted, fontSize = 13.sp)
            }
            Text("‹", fontSize = 24.sp, color = Muted)
        }
    }
}

@Composable
fun NewProjectDialog(onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("kotlin") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("مشروع جديد") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("اسم المشروع") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("النوع", color = Muted)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Kotlin", type == "kotlin") { type = "kotlin" }
                        Chip("Java", type == "java") { type = "java" }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Flutter", type == "flutter") { type = "flutter" }
                        Chip("HTML", type == "html") { type = "html" }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val p = AppState.createProject(name, type)
                    AppState.openProjectId = p.id
                    onClose()
                }
            }) { Text("إنشاء") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("إلغاء", color = Muted) } }
    )
}

// ---------------- صفحة المشروع ----------------
@Composable
fun ProjectScreen(pid: String) {
    val p = AppState.projects.firstOrNull { it.id == pid }
    if (p == null) {
        LaunchedEffect(Unit) { AppState.openProjectId = null }
        return
    }
    val ctx = LocalContext.current
    val tick = AppState.fileTick
    val files = remember(pid, tick) { ProjectStore.files(p) }
    var showAdd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { AppState.openProjectId = null }) { Text("› رجوع") }
            Text(
                p.name, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = { confirmDelete = true }) { Text("حذف", color = Red) }
        }
        Text(typeLabels[p.type] ?: p.type, color = Color(0xFFA371F7), fontSize = 13.sp)
        if (p.repo.isNotEmpty()) Text("GitHub: ${Prefs.login}/${p.repo}", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { Builder.start(ctx, p) },
                enabled = !AppState.building,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) { Text(if (AppState.building) "جاري البناء..." else "بناء APK", fontWeight = FontWeight.Bold) }
            if (p.apk.isNotEmpty() && File(p.apk).exists()) {
                OutlinedButton(
                    onClick = { Builder.install(ctx, File(p.apk)) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp)
                ) { Text("تثبيت", color = Green) }
            }
            if (p.type == "html") {
                OutlinedButton(
                    onClick = {
                        val f = File(ProjectStore.dir(p), "index.html")
                        AppState.browser = BrowserRequest("file://" + f.absolutePath, "معاينة", null)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp)
                ) { Text("معاينة", color = Color.White) }
            }
        }
        if (p.status.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(p.status, color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardC)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                LazyColumn(Modifier.fillMaxWidth().height(150.dp).padding(10.dp)) {
                    if (AppState.logs.isEmpty()) {
                        item { Text("سجل البناء يظهر هنا", color = Muted, fontSize = 12.sp) }
                    }
                    items(AppState.logs.toList()) {
                        Text(it, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("الملفات (${files.size})", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { showAdd = true }) { Text("+ ملف") }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(files) { rel ->
                Row(
                    Modifier.fillMaxWidth().clickable { AppState.editing = rel }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            rel, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    TextButton(onClick = { ProjectStore.delete(p, rel) }) { Text("حذف", color = Red, fontSize = 12.sp) }
                }
                HorizontalDivider(color = Line)
            }
        }
    }

    if (showAdd) {
        var path by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("ملف جديد") },
            text = {
                OutlinedTextField(
                    value = path, onValueChange = { path = it },
                    label = { Text("المسار مثل: lib/page.dart") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val clean = path.trim().trimStart('/')
                    if (clean.isNotEmpty() && !clean.contains("..")) {
                        ProjectStore.write(p, clean, "")
                        showAdd = false
                    }
                }) { Text("إنشاء") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("إلغاء", color = Muted) } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف المشروع؟") },
            text = { Text("راح تنحذف كل ملفات المشروع من الجهاز.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    AppState.deleteProject(p.id)
                }) { Text("حذف", color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } }
        )
    }
}

// ---------------- المحرر ----------------
@Composable
fun EditorScreen(pid: String, rel: String) {
    val p = AppState.projects.firstOrNull { it.id == pid }
    if (p == null) {
        LaunchedEffect(Unit) { AppState.editing = null }
        return
    }
    if (!isTextFile(rel)) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            TextButton(onClick = { AppState.editing = null }) { Text("› رجوع") }
            Text("هذا الملف ما يتعدل من هنا (ملف غير نصي).", color = Muted)
        }
        return
    }
    val text = remember(pid, rel) { mutableStateOf(ProjectStore.read(p, rel)) }
    DisposableEffect(pid, rel) {
        onDispose { ProjectStore.write(p, rel, text.value) }
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { AppState.editing = null }) { Text("› رجوع") }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    rel, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
            }
            Button(onClick = { ProjectStore.write(p, rel, text.value) }, shape = RoundedCornerShape(12.dp)) {
                Text("حفظ")
            }
        }
        Spacer(Modifier.height(8.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = text.value,
                onValueChange = { text.value = it },
                modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color.White)
            )
        }
    }
}

// ---------------- المتصفح الداخلي ----------------
@Composable
fun BrowserScreen(req: BrowserRequest) {
    val ctx = LocalContext.current
    LaunchedEffect(req.code) {
        if (req.code != null) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("code", req.code))
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(CardC).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                Auth.cancel()
                AppState.browser = null
            }) { Text("إغلاق", color = Red) }
            Text(req.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (req.code != null) {
                TextButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("code", req.code))
                }) { Text("نسخ الكود") }
            }
        }
        if (req.code != null) {
            Column(
                Modifier.fillMaxWidth().background(Blue.copy(alpha = 0.2f)).padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("الكود: " + req.code, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("سجّل دخولك، ألصق الكود (اننسخ تلقائياً) واضغط Authorize. التطبيق يكمل لحاله.", fontSize = 12.sp, color = Muted)
            }
        }
        AndroidView(
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    webViewClient = WebViewClient()
                    loadUrl(req.url)
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}

// ---------------- المكتبة ----------------
@Composable
fun LibraryScreen() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val apks = remember(tick, AppState.building) {
        Builder.apkDir(ctx).listFiles()?.filter { it.extension == "apk" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("المكتبة", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("ملفات APK التي تم بناؤها", color = Muted)
        Spacer(Modifier.height(12.dp))
        if (apks.isEmpty()) Text("ما كو ملفات بعد. ابنِ مشروع أول.", color = Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(apks) { f ->
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardC)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                            Text("${f.length() / 1024 / 1024} MB", color = Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { Builder.install(ctx, f) }) { Text("تثبيت", color = Green) }
                        TextButton(onClick = { f.delete(); tick++ }) { Text("حذف", color = Red) }
                    }
                }
            }
        }
    }
}

// ---------------- الإعدادات ----------------
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var clientId by remember { mutableStateOf(Prefs.clientId) }
    var token by remember { mutableStateOf("") }
    var priv by remember { mutableStateOf(Prefs.isPrivate) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("الإعدادات", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Card(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardC)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("حساب GitHub", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                if (AppState.login.isNotEmpty()) {
                    Text("متصل: @" + AppState.login, color = Green)
                    TextButton(onClick = { Auth.logout() }) { Text("فصل الحساب", color = Red) }
                } else {
                    Text("غير متصل", color = Muted)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it; Prefs.clientId = it.trim() },
                    label = { Text("Client ID (من OAuth App)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { Auth.startDevice() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("ربط GitHub (متصفح داخلي)", fontWeight = FontWeight.Bold) }
                if (AppState.message.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(AppState.message, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Card(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardC)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("طريقة بديلة: توكن", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("بدون OAuth App: أنشئ توكن (صلاحيات repo و workflow) وألصقه.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        AppState.browser = BrowserRequest(
                            "https://github.com/settings/tokens/new?scopes=repo,workflow&description=AppForge",
                            "إنشاء توكن", null
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("فتح صفحة إنشاء التوكن", color = Color.White) }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = token, onValueChange = { token = it },
                    label = { Text("الصق التوكن هنا") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { if (token.isNotBlank()) { Auth.saveToken(token); token = "" } }) {
                    Text("حفظ التوكن")
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Card(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardC)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("مستودعات خاصة", fontWeight = FontWeight.Bold)
                    Text("المشاريع الجديدة تنرفع كمستودع Private", color = Muted, fontSize = 12.sp)
                }
                Switch(checked = priv, onCheckedChange = { priv = it; Prefs.isPrivate = it })
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "كيف تسوي OAuth App: github.com ← Settings ← Developer settings ← OAuth Apps ← New. " +
                "فعّل Enable Device Flow، وانسخ Client ID هنا.",
            color = Muted, fontSize = 12.sp
        )
    }
}
