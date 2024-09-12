package com.naulian.modify.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.naulian.modify.PreviewScreen

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Browser(
    url: String,
    onUrlLoad: (String) -> Unit,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = {
            WebView(it).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                val client = object : WebViewClient() {
                    override fun onLoadResource(view: WebView?, url: String?) {
                        super.onLoadResource(view, url)
                        url?.let(onUrlLoad)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onLoading(true)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onLoading(false)
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                }
                webViewClient = client
                loadUrl(url)
            }
        },
        update = {
            it.loadUrl(url)
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebIFrame(
    html: String,
    onUrlLoad: (String) -> Unit,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = {
            WebView(it).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                val client = object : WebViewClient() {
                    override fun onLoadResource(view: WebView?, url: String?) {
                        super.onLoadResource(view, url)
                        url?.let(onUrlLoad)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onLoading(true)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onLoading(false)
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                }
                webViewClient = client
                loadData(html, "text/html", "utf-8")
            }
        },
        update = {
            it.loadData(html, "text/html", "utf-8")
        }
    )
}

@Preview
@Composable
fun PLCIFramePreview() {
    PreviewScreen {
        WebIFrame(
            html = """
                <iframe 
                    width="100%" 
                    height="450" 
                    src="https://www.youtube.com/embed/jgExyMTDrTU" 
                    title="YouTube video player" 
                    frameborder="0" 
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen>
                </iframe>
            """.trimIndent(),
            onUrlLoad = {}, onLoading = {}
        )
    }
}