package com.appforge.ide

object Templates {

    fun create(p: Project): Map<String, String> = when (p.type) {
        "kotlin" -> androidProject(p, true, kotlinActivity(p), false)
        "java" -> androidProject(p, false, javaActivity(p), false)
        "flutter" -> flutterProject(p)
        else -> mapOf("index.html" to htmlPage(p))
    }

    // يُستخدم عند البناء: يحوّل ملفات HTML إلى مشروع أندرويد WebView كامل
    fun htmlWrapper(p: Project): Map<String, String> =
        androidProject(p, true, webActivity(p), true)

    private fun androidProject(
        p: Project,
        kotlin: Boolean,
        activity: String,
        internet: Boolean
    ): Map<String, String> {
        val m = linkedMapOf<String, String>()

        m["settings.gradle.kts"] = """
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "${p.name}"
include(":app")
""".trimStart()

        m["build.gradle.kts"] = if (kotlin) """
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
""".trimStart() else """
plugins {
    id("com.android.application") version "8.5.2" apply false
}
""".trimStart()

        m["gradle.properties"] = "org.gradle.jvmargs=-Xmx2048m\nandroid.useAndroidX=true\n"

        val plugins = if (kotlin)
            "    id(\"com.android.application\")\n    id(\"org.jetbrains.kotlin.android\")"
        else
            "    id(\"com.android.application\")"
        val kopts = if (kotlin) "    kotlinOptions { jvmTarget = \"17\" }\n" else ""

        m["app/build.gradle.kts"] = "plugins {\n$plugins\n}\n\n" + """
android {
    namespace = "${p.pkg}"
    compileSdk = 34
    defaultConfig {
        applicationId = "${p.pkg}"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
""".trimStart() + kopts + "}\n"

        val perm = if (internet) "    <uses-permission android:name=\"android.permission.INTERNET\" />\n" else ""
        m["app/src/main/AndroidManifest.xml"] = """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
$perm    <application
        android:label="@string/app_name"
        android:theme="@android:style/Theme.DeviceDefault.Light.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""".trimStart()

        m["app/src/main/res/values/strings.xml"] =
            "<resources>\n    <string name=\"app_name\">${p.name}</string>\n</resources>\n"

        val ext = if (kotlin) "kt" else "java"
        m["app/src/main/java/" + p.pkg.replace('.', '/') + "/MainActivity." + ext] = activity
        return m
    }

    private fun kotlinActivity(p: Project) = """
package ${p.pkg}

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "Hello from ${p.name}"
        tv.textSize = 26f
        tv.gravity = Gravity.CENTER
        setContentView(tv)
    }
}
""".trimStart()

    private fun javaActivity(p: Project) = """
package ${p.pkg};

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Hello from ${p.name}");
        tv.setTextSize(26f);
        tv.setGravity(Gravity.CENTER);
        setContentView(tv);
    }
}
""".trimStart()

    private fun webActivity(p: Project) = """
package ${p.pkg}

import android.app.Activity
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()
        setContentView(web)
        web.loadUrl("file:///android_asset/www/index.html")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
""".trimStart()

    private fun htmlPage(p: Project) = """
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${p.name}</title>
<style>
  body { font-family: sans-serif; background:#0d1117; color:#fff;
         display:flex; align-items:center; justify-content:center;
         height:100vh; margin:0; flex-direction:column; }
  button { padding:12px 24px; font-size:18px; border:0; border-radius:12px;
           background:#1f6feb; color:#fff; }
</style>
</head>
<body>
  <h1>${p.name}</h1>
  <button onclick="document.getElementById('t').innerText = 'اشتغل ✅'">اضغط</button>
  <p id="t"></p>
</body>
</html>
""".trimStart()

    private fun flutterProject(p: Project): Map<String, String> = mapOf(
        "pubspec.yaml" to """
name: klencod_app
description: ${p.name}
publish_to: 'none'
version: 1.0.0+1

environment:
  sdk: '>=3.0.0 <4.0.0'

dependencies:
  flutter:
    sdk: flutter

flutter:
  uses-material-design: true
""".trimStart(),
        "lib/main.dart" to """
import 'package:flutter/material.dart';

void main() => runApp(const MyApp());

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '${p.name}',
      theme: ThemeData(useMaterial3: true, colorSchemeSeed: Colors.blue),
      home: Scaffold(
        appBar: AppBar(title: const Text('${p.name}')),
        body: const Center(
          child: Text('Hello Flutter', style: TextStyle(fontSize: 28)),
        ),
      ),
    );
  }
}
""".trimStart()
    )

    // ملف GitHub Actions حسب نوع المشروع
    fun workflow(type: String): String = if (type == "flutter") """
name: Klencod Build
on:
  push:
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: subosito/flutter-action@v2
        with:
          channel: stable
      - name: Build
        run: |
          if [ ! -d android ]; then
            cp -r lib /tmp/lib_bak
            flutter create . --platforms=android --project-name klencod_app --org com.klencod
            rm -rf lib
            cp -r /tmp/lib_bak lib
          fi
          flutter pub get
          flutter build apk --release
      - uses: actions/upload-artifact@v4
        with:
          name: apk
          path: build/app/outputs/flutter-apk/*.apk
""".trimStart() else """
name: Klencod Build
on:
  push:
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.9'
      - name: Build
        run: |
          if [ -f ./gradlew ]; then chmod +x ./gradlew; ./gradlew assembleDebug --no-daemon; else gradle assembleDebug --no-daemon; fi
      - uses: actions/upload-artifact@v4
        with:
          name: apk
          path: '**/build/outputs/apk/**/*.apk'
""".trimStart()
}
