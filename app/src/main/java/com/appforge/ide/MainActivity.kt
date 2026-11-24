package com.appforge.ide

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private val pickZip = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importZip(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppState.init(applicationContext)
        setContent {
            AppRoot {
                pickZip.launch(
                    arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                )
            }
        }
    }

    private fun importZip(uri: Uri) {
        Thread {
            try {
                val p = Importer.import(applicationContext, uri)
                runOnUiThread {
                    Toast.makeText(this, "تم استيراد: " + p.name + " (" + p.type + ")", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "فشل الاستيراد: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}