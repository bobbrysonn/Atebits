package dev.bobbrysonn.atebits.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.bobbrysonn.atebits.Constants

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                // Without explicit MATCH_PARENT layout params the WebView defaults to
                // wrap_content, and Chromium then resolves CSS percentage heights
                // against content height (0) instead of the viewport — collapsing
                // x.com's `height: 100%` layout into a blank page.
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                // x.com's login is a JS single-page app that requires localStorage;
                // without DOM storage it renders a blank white page.
                settings.domStorageEnabled = true
                settings.userAgentString = Constants.USER_AGENT
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Match query-string/trailing-slash variants of the home URL too
                        if (url != null && url.startsWith(Constants.HOME_URL)) {
                            onLoginSuccess()
                        }
                    }
                }
                loadUrl(Constants.LOGIN_URL)
            }
        }
    )
}
