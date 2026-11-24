package com.appforge.ide

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class Resp(val code: Int, val body: String) {
    val json: JSONObject
        get() = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
}

object GH {
    private const val API = "https://api.github.com"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    fun call(
        method: String,
        url: String,
        token: String?,
        json: JSONObject? = null,
        form: Map<String, String>? = null,
        accept: String = "application/vnd.github+json"
    ): Resp {
        val b = Request.Builder().url(url)
            .header("Accept", accept)
            .header("User-Agent", "AppForge")
            .header("X-GitHub-Api-Version", "2022-11-28")
        if (!token.isNullOrEmpty()) b.header("Authorization", "Bearer $token")
        val body: RequestBody? = when {
            json != null -> json.toString().toRequestBody("application/json".toMediaType())
            form != null -> FormBody.Builder().also { fb -> form.forEach { (k, v) -> fb.add(k, v) } }.build()
            method == "GET" -> null
            else -> "".toRequestBody(null)
        }
        b.method(method, body)
        client.newCall(b.build()).execute().use { r ->
            return Resp(r.code, r.body?.string() ?: "")
        }
    }

    // ---------- تسجيل الدخول (Device Flow) ----------
    fun deviceCode(clientId: String): JSONObject {
        val r = call(
            "POST", "https://github.com/login/device/code", null,
            form = mapOf("client_id" to clientId, "scope" to "repo workflow"),
            accept = "application/json"
        )
        if (r.code != 200 || !r.json.has("device_code"))
            throw Exception("Client ID غير صحيح أو Device Flow غير مفعّل: " + r.body.take(150))
        return r.json
    }

    fun pollToken(clientId: String, deviceCode: String): Pair<String?, String> {
        val r = call(
            "POST", "https://github.com/login/oauth/access_token", null,
            form = mapOf(
                "client_id" to clientId,
                "device_code" to deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
            ),
            accept = "application/json"
        )
        val j = r.json
        val t = j.optString("access_token")
        return Pair(if (t.isEmpty()) null else t, j.optString("error"))
    }

    fun login(token: String): String? {
        val r = call("GET", "$API/user", token)
        return if (r.code == 200) r.json.optString("login") else null
    }

    // ---------- المستودع والرفع ----------
    fun ensureRepo(token: String, owner: String, name: String, priv: Boolean): String {
        val r = call("GET", "$API/repos/$owner/$name", token)
        if (r.code == 200) return r.json.optString("default_branch", "main")
        val c = call(
            "POST", "$API/user/repos", token,
            JSONObject().put("name", name).put("private", priv)
                .put("auto_init", true).put("description", "Built with AppForge")
        )
        if (c.code !in 200..299) throw Exception("فشل إنشاء المستودع: " + c.body.take(200))
        Thread.sleep(2500)
        return c.json.optString("default_branch", "main")
    }

    fun push(
        token: String, owner: String, repo: String, branch: String,
        files: Map<String, ByteArray>, msg: String, log: (String) -> Unit
    ): String {
        val base = "$API/repos/$owner/$repo/git"
        val ref = call("GET", "$base/ref/heads/$branch", token)
        if (ref.code != 200) throw Exception("تعذر قراءة الفرع: " + ref.body.take(200))
        val parent = ref.json.getJSONObject("object").getString("sha")

        val tree = JSONArray()
        var n = 0
        for ((path, bytes) in files) {
            val payload = JSONObject()
                .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
                .put("encoding", "base64")
            var b = call("POST", "$base/blobs", token, payload)
            if (b.code !in 200..299) {
                Thread.sleep(1500)
                b = call("POST", "$base/blobs", token, payload)
            }
            if (b.code !in 200..299) throw Exception("فشل رفع الملف: $path (" + b.code + ")")
            tree.put(
                JSONObject().put("path", path)
                    .put("mode", if (path == "gradlew") "100755" else "100644")
                    .put("type", "blob").put("sha", b.json.getString("sha"))
            )
            n++
            if (n % 20 == 0) log("   ... $n / ${files.size}")
        }
        val t = call("POST", "$base/trees", token, JSONObject().put("tree", tree))
        if (t.code !in 200..299) throw Exception("فشل إنشاء الشجرة: " + t.body.take(200))
        val c = call(
            "POST", "$base/commits", token,
            JSONObject().put("message", msg).put("tree", t.json.getString("sha"))
                .put("parents", JSONArray().put(parent))
        )
        if (c.code !in 200..299) throw Exception("فشل الـ commit: " + c.body.take(200))
        val sha = c.json.getString("sha")
        val u = call(
            "PATCH", "$base/refs/heads/$branch", token,
            JSONObject().put("sha", sha).put("force", true)
        )
        if (u.code !in 200..299) throw Exception("فشل تحديث الفرع: " + u.body.take(200))
        return sha
    }

    // ---------- انتظار البناء وتحميل APK ----------
    fun waitRun(
        token: String, owner: String, repo: String, sha: String, log: (String) -> Unit
    ): JSONObject {
        var last = ""
        repeat(400) {
            Thread.sleep(5000)
            val r = call(
                "GET",
                "$API/repos/$owner/$repo/actions/workflows/klencod-build.yml/runs?head_sha=$sha",
                token
            )
            val runs = r.json.optJSONArray("workflow_runs")
            if (r.code == 200 && runs != null && runs.length() > 0) {
                val run = runs.getJSONObject(0)
                val st = run.optString("status")
                if (st != last) {
                    last = st
                    log("   حالة البناء: $st")
                }
                if (st == "completed") return run
            }
        }
        throw Exception("انتهت مهلة الانتظار")
    }

    fun downloadApk(token: String, owner: String, repo: String, runId: Long, out: File) {
        val a = call("GET", "$API/repos/$owner/$repo/actions/runs/$runId/artifacts", token)
        val arr = a.json.optJSONArray("artifacts")
        if (arr == null || arr.length() == 0) throw Exception("البناء نجح لكن ما طلع ملف APK")
        val id = arr.getJSONObject(0).getLong("id")
        val req = Request.Builder()
            .url("$API/repos/$owner/$repo/actions/artifacts/$id/zip")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "AppForge")
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw Exception("فشل تحميل الملف: " + r.code)
            ZipInputStream(r.body!!.byteStream()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (e.name.endsWith(".apk")) {
                        out.outputStream().use { z.copyTo(it) }
                        return
                    }
                    e = z.nextEntry
                }
            }
        }
        throw Exception("ما لقيت APK داخل الملف المحمّل")
    }
}

// ---------- ربط الحساب ----------
object Auth {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun startDevice() {
        val cid = Prefs.clientId.trim()
        if (cid.isEmpty()) {
            AppState.message = "أدخل Client ID أولاً"
            return
        }
        cancel()
        job = scope.launch {
            try {
                AppState.message = "جاري الاتصال بـ GitHub..."
                val d = GH.deviceCode(cid)
                val code = d.getString("user_code")
                val uri = d.getString("verification_uri")
                val dc = d.getString("device_code")
                var interval = d.optInt("interval", 5)
                val expires = d.optInt("expires_in", 900)
                AppState.browser = BrowserRequest("$uri?user_code=$code", "ربط GitHub", code)
                var waited = 0
                while (waited < expires) {
                    delay(interval * 1000L)
                    waited += interval
                    val (t, err) = GH.pollToken(cid, dc)
                    if (t != null) {
                        val l = GH.login(t) ?: ""
                        Prefs.token = t
                        Prefs.login = l
                        AppState.login = l
                        AppState.message = "✅ تم الربط: @$l"
                        AppState.browser = null
                        return@launch
                    }
                    if (err == "slow_down") interval += 5
                    if (err == "expired_token" || err == "access_denied") {
                        AppState.message = "تم الإلغاء أو انتهى الوقت"
                        AppState.browser = null
                        return@launch
                    }
                }
                AppState.message = "انتهى الوقت، حاول مرة ثانية"
                AppState.browser = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppState.message = "❌ " + (e.message ?: "خطأ")
                AppState.browser = null
            }
        }
    }

    fun saveToken(t: String) {
        val token = t.trim()
        scope.launch {
            AppState.message = "جاري التحقق..."
            try {
                val l = GH.login(token)
                if (l == null) {
                    AppState.message = "❌ التوكن غير صالح"
                } else {
                    Prefs.token = token
                    Prefs.login = l
                    AppState.login = l
                    AppState.message = "✅ تم الربط: @$l"
                }
            } catch (e: Exception) {
                AppState.message = "❌ " + (e.message ?: "خطأ بالاتصال")
            }
        }
    }

    fun logout() {
        Prefs.token = ""
        Prefs.login = ""
        AppState.login = ""
        AppState.message = "تم فصل الحساب"
    }
}

// ---------- البناء ----------
object Builder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun apkDir(ctx: Context): File =
        File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "apks").apply { mkdirs() }

    private fun log(s: String) {
        AppState.logs.add(s)
    }

    fun start(ctx: Context, p: Project) {
        if (AppState.building) return
        val app = ctx.applicationContext
        AppState.building = true
        AppState.logs.clear()
        scope.launch {
            try {
                execute(app, p.id)
            } catch (e: Exception) {
                log("❌ " + (e.message ?: e.toString()))
                AppState.update(p.id) { it.copy(status = "فشل") }
            } finally {
                AppState.building = false
            }
        }
    }

    private fun collect(p: Project): Map<String, ByteArray> {
        val map = linkedMapOf<String, ByteArray>()
        val d = ProjectStore.dir(p)
        for (rel in ProjectStore.files(p)) {
            val path = if (p.type == "html") "app/src/main/assets/www/$rel" else rel
            map[path] = File(d, rel).readBytes()
        }
        if (p.type == "html") {
            Templates.htmlWrapper(p).forEach { (k, v) -> map[k] = v.toByteArray() }
        }
        map[".github/workflows/klencod-build.yml"] = Templates.workflow(p.type).toByteArray()
        return map
    }

    private fun execute(ctx: Context, id: String) {
        var p = AppState.projects.first { it.id == id }
        val token = Prefs.token
        if (token.isEmpty()) throw Exception("اربط حساب GitHub من تبويب الإعدادات أولاً")
        var owner = Prefs.login
        if (owner.isEmpty()) {
            owner = GH.login(token) ?: throw Exception("التوكن غير صالح")
            Prefs.login = owner
            AppState.login = owner
        }
        val repo = p.repo.ifEmpty { slug(p.name) + "-" + (1000..9999).random() }
        log("📦 المستودع: $owner/$repo")
        val branch = GH.ensureRepo(token, owner, repo, Prefs.isPrivate)
        if (p.repo.isEmpty()) {
            AppState.update(id) { it.copy(repo = repo) }
            p = p.copy(repo = repo)
        }
        AppState.update(id) { it.copy(status = "جاري البناء...") }

        val files = collect(p)
        log("⬆️ رفع ${files.size} ملف...")
        val sha = GH.push(token, owner, repo, branch, files, "AppForge build") { log(it) }

        log("⚙️ بدأ البناء على GitHub Actions (من 2 إلى 10 دقائق)")
        val run = GH.waitRun(token, owner, repo, sha) { log(it) }
        if (run.optString("conclusion") != "success") {
            AppState.update(id) { it.copy(status = "فشل البناء") }
            throw Exception("فشل البناء. شوف السجل هنا: " + run.optString("html_url"))
        }

        log("⬇️ تحميل APK...")
        val out = File(apkDir(ctx), slug(p.name) + "-" + (System.currentTimeMillis() / 1000) + ".apk")
        GH.downloadApk(token, owner, repo, run.getLong("id"), out)
        AppState.update(id) { it.copy(status = "✅ تم البناء", apk = out.absolutePath) }
        log("✅ جاهز! اضغط تثبيت")
    }

    fun install(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}

// ---------- استيراد ZIP ----------
object Importer {

    private fun displayName(ctx: Context, uri: Uri): String {
        var name = "Imported"
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: name
        }
        return name.removeSuffix(".zip")
    }

    private fun detect(base: File): String {
        if (File(base, "pubspec.yaml").exists()) return "flutter"
        val gradleFiles = base.walkTopDown().maxDepth(3)
            .filter { it.name == "build.gradle" || it.name == "build.gradle.kts" }.toList()
        if (gradleFiles.isEmpty()) return "html"
        val hasKotlin = gradleFiles.any { it.readText().contains("kotlin") } ||
            base.walkTopDown().any { it.extension == "kt" }
        return if (hasKotlin) "kotlin" else "java"
    }

    fun import(ctx: Context, uri: Uri): Project {
        val name = sanitize(displayName(ctx, uri)).ifEmpty { "Imported" }
        val tmp = File(ctx.cacheDir, "imp_" + System.nanoTime()).also { it.mkdirs() }
        try {
            ctx.contentResolver.openInputStream(uri)!!.use { ins ->
                ZipInputStream(ins).use { z ->
                    var e = z.nextEntry
                    while (e != null) {
                        val out = File(tmp, e.name)
                        if (!out.canonicalPath.startsWith(tmp.canonicalPath + File.separator))
                            throw SecurityException("ملف غير آمن داخل ZIP")
                        if (e.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { z.copyTo(it) }
                        }
                        e = z.nextEntry
                    }
                }
            }
            var base = tmp
            val kids = tmp.listFiles()?.filter { it.name != "__MACOSX" } ?: emptyList()
            if (kids.size == 1 && kids[0].isDirectory) base = kids[0]
            val type = detect(base)
            val p = Project(newId(), name, type, pkgOf(name))
            base.copyRecursively(ProjectStore.dir(p), true)
            AppState.projects.add(0, p)
            AppState.persist()
            return p
        } finally {
            tmp.deleteRecursively()
        }
    }
}
