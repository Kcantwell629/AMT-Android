package com.amtandroid.quotebuilder

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * Thin native wrapper around the Flat Rate Quote Builder web app
 * (app/src/main/assets/index.html — a byte-for-byte copy of the app
 * at https://github.com/Kcantwell629/flat-rate-quote-builder).
 *
 * All pricing logic, the checklist, and the .docx generator live in that
 * HTML/JS file unmodified. This activity only bridges three things the
 * web app does that a plain WebView can't do on its own:
 *   1. sms: / tel: / mailto: links -> hand off to a real system app.
 *   2. Blob-based file downloads (the "Download Quote (.docx)" button)
 *      -> saved into the device's Downloads folder.
 *   3. window.print() (the "Print / Save Quote (PDF)" button)
 *      -> the native Android print dialog (Save as PDF / print to a printer).
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                if (uri.scheme in EXTERNAL_SCHEMES) {
                    return try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                        true
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            "No app available to handle this action.",
                            Toast.LENGTH_SHORT
                        ).show()
                        true
                    }
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(BRIDGE_JS, null)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        webView.loadUrl("file:///android_asset/index.html")
    }

    /** Exposed to the page as `window.AndroidBridge`. */
    inner class AndroidBridge {

        @JavascriptInterface
        fun saveBase64File(base64Data: String, filename: String, mimeType: String) {
            runOnUiThread {
                try {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                    }
                    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    val uri = contentResolver.insert(collection, values)
                    if (uri != null) {
                        contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        Toast.makeText(
                            this@MainActivity,
                            "Saved to Downloads: $filename",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Could not save file.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun requestPrint() {
            runOnUiThread {
                val printManager = getSystemService(PRINT_SERVICE) as PrintManager
                val jobName = "Flat Rate Quote"
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
            }
        }
    }

    companion object {
        private val EXTERNAL_SCHEMES = setOf("sms", "tel", "mailto")

        // Installed once per page load. Intercepts the two browser APIs the
        // web app relies on that a bare WebView doesn't implement:
        // anchor-click blob downloads, and window.print().
        private const val BRIDGE_JS = """
        (function(){
          if(window.__amtBridgeInstalled) return;
          window.__amtBridgeInstalled = true;

          var origClick = HTMLAnchorElement.prototype.click;
          HTMLAnchorElement.prototype.click = function(){
            if(this.href && this.href.indexOf('blob:') === 0 && this.download){
              var filename = this.download;
              fetch(this.href).then(function(r){ return r.blob(); }).then(function(blob){
                var reader = new FileReader();
                reader.onloadend = function(){
                  var base64 = reader.result.split(',')[1];
                  if(window.AndroidBridge){
                    window.AndroidBridge.saveBase64File(base64, filename, blob.type || 'application/octet-stream');
                  }
                };
                reader.readAsDataURL(blob);
              });
              return;
            }
            return origClick.call(this);
          };

          window.print = function(){
            if(window.AndroidBridge){ window.AndroidBridge.requestPrint(); }
          };
        })();
        """
    }
}
