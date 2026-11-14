package com.example.ui.panels

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.CodeSnippet
import com.example.ui.hud.hudGlassPanel
import com.example.ui.theme.JarvisColors
import java.io.ByteArrayInputStream

@Composable
fun CodingDrawer(
    snippet: CodeSnippet?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copiedFeedback by remember { mutableStateOf(false) }
    var showPreview by remember(snippet) { mutableStateOf(false) }

    val code = snippet?.code.orEmpty()
    val lang = snippet?.language?.lowercase().orEmpty().ifBlank { "plaintext" }
    val previewable = lang in setOf("html", "javascript", "js")

    val ext = when (lang) {
        "html" -> "html"
        "javascript", "js" -> "js"
        "kotlin", "kt" -> "kt"
        "python", "py" -> "py"
        "css" -> "css"
        "json" -> "json"
        "sql" -> "sql"
        else -> "txt"
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && code.isNotEmpty()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(code.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisColors.VoidBlack.copy(alpha = 0.55f))
            .navigationBarsPadding()
            .padding(10.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .heightIn(max = 620.dp)
                .hudGlassPanel(strong = true)
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CODING MODULE", style = MaterialTheme.typography.titleMedium, color = JarvisColors.NeonCyan)
                    Text(
                        text = lang.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisColors.MatrixEmerald,
                        modifier = Modifier
                            .border(1.dp, JarvisColors.MatrixEmerald, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                OutlinedButton(
                    onClick = onClose,
                    border = BorderStroke(1.dp, JarvisColors.CyanBorder),
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("close_coding_drawer")
                ) {
                    Text("CLOSE", style = MaterialTheme.typography.labelSmall, color = JarvisColors.FrostWhite)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        if (code.isNotEmpty()) {
                            clipboard.setText(AnnotatedString(code))
                            copiedFeedback = true
                        }
                    },
                    enabled = code.isNotEmpty(),
                    border = BorderStroke(1.dp, JarvisColors.CyanBorder),
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("copy_code_button")
                ) {
                    Text(
                        if (copiedFeedback) "COPIED" else "COPY",
                        style = MaterialTheme.typography.labelLarge,
                        color = JarvisColors.FrostWhite
                    )
                }

                OutlinedButton(
                    onClick = { saveLauncher.launch("maim-snippet.$ext") },
                    enabled = code.isNotEmpty(),
                    border = BorderStroke(1.dp, JarvisColors.CyanBorder),
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("download_code_button")
                ) {
                    Text("SAVE", style = MaterialTheme.typography.labelLarge, color = JarvisColors.FrostWhite)
                }

                if (previewable && code.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showPreview = !showPreview },
                        border = BorderStroke(1.dp, JarvisColors.MatrixEmerald),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = JarvisColors.EmeraldTint),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("run_preview_button")
                    ) {
                        Text(
                            if (showPreview) "HIDE PREVIEW" else "RUN PREVIEW",
                            style = MaterialTheme.typography.labelLarge,
                            color = JarvisColors.MatrixEmerald
                        )
                    }
                }
            }

            val highlighted = remember(code) { highlightCodeSyntax(code.ifBlank { "// Ask MAIM to write any code snippet, script, or HTML demo." }) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 260.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(JarvisColors.CodeSurface)
                    .border(1.dp, JarvisColors.CyanBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = highlighted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (showPreview && previewable) {
                Text(
                    text = "SANDBOXED PREVIEW (NO NETWORK / NO FILE ACCESS)",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisColors.MatrixEmerald
                )
                SandboxedHtmlPreview(
                    code = code,
                    lang = lang,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, JarvisColors.MatrixEmerald, RoundedCornerShape(10.dp))
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SandboxedHtmlPreview(
    code: String,
    lang: String,
    modifier: Modifier = Modifier
) {
    val cspMeta =
        "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:;\">"

    val htmlDoc = remember(code, lang) {
        if (lang == "html") {
            val headMatch = Regex("<head([^>]*)>", RegexOption.IGNORE_CASE).find(code)
            if (headMatch != null) {
                code.replaceRange(headMatch.range, "<head${headMatch.groupValues[1]}>$cspMeta")
            } else {
                "<!DOCTYPE html><html><head>$cspMeta</head><body>$code</body></html>"
            }
        } else {
            val safeJs = code.replace("</script", "<\\/script", ignoreCase = true)
            """
            <!DOCTYPE html><html><head>$cspMeta
            <style>body{font-family:monospace;background:#0a1017;color:#d8fbff;padding:12px;margin:0;}</style>
            </head><body><pre id="out"></pre><script>
            const out = document.getElementById('out');
            const log = (...a) => { out.textContent += a.map(x => typeof x === 'object' ? JSON.stringify(x, null, 2) : String(x)).join(' ') + '\n'; };
            console.log = log; console.error = log;
            try { $safeJs } catch (e) { log('Error:', e.message); }
            </script></body></html>
            """.trimIndent()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.databaseEnabled = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.blockNetworkLoads = true
                settings.blockNetworkImage = true
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val scheme = request?.url?.scheme?.lowercase()
                        return if (scheme == "about" || scheme == "data") {
                            null
                        } else {
                            WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                        }
                    }
                }
                loadDataWithBaseURL(null, htmlDoc, "text/html", "utf-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, htmlDoc, "text/html", "utf-8", null)
        }
    )
}

private fun highlightCodeSyntax(source: String): AnnotatedString {
    val keywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "return", "if", "else", "when", "for", "while",
        "const", "let", "function", "async", "await", "import", "export", "from", "def", "print", "true",
        "false", "null", "try", "catch", "new", "SELECT", "FROM", "WHERE"
    )
    return buildAnnotatedString {
        var i = 0
        while (i < source.length) {
            when {
                source.startsWith("//", i) || source.startsWith("#", i) -> {
                    val end = source.indexOf('\n', i).let { if (it == -1) source.length else it }
                    withStyle(SpanStyle(color = JarvisColors.SyntaxComment)) {
                        append(source.substring(i, end))
                    }
                    i = end
                }
                source[i] == '"' || source[i] == '\'' || source[i] == '`' -> {
                    val quote = source[i]
                    var end = i + 1
                    while (end < source.length && source[end] != quote) {
                        if (source[end] == '\\' && end + 1 < source.length) end += 2 else end++
                    }
                    if (end < source.length) end++
                    withStyle(SpanStyle(color = JarvisColors.SyntaxString)) {
                        append(source.substring(i, end))
                    }
                    i = end
                }
                source[i].isDigit() -> {
                    var end = i + 1
                    while (end < source.length && (source[end].isDigit() || source[end] == '.')) end++
                    withStyle(SpanStyle(color = JarvisColors.SyntaxNumber)) {
                        append(source.substring(i, end))
                    }
                    i = end
                }
                source[i].isLetter() || source[i] == '_' -> {
                    var end = i + 1
                    while (end < source.length && (source[end].isLetterOrDigit() || source[end] == '_')) end++
                    val word = source.substring(i, end)
                    if (word in keywords) {
                        withStyle(SpanStyle(color = JarvisColors.SyntaxKeyword)) { append(word) }
                    } else {
                        withStyle(SpanStyle(color = JarvisColors.FrostWhite)) { append(word) }
                    }
                    i = end
                }
                else -> {
                    withStyle(SpanStyle(color = JarvisColors.MutedCyan)) { append(source[i]) }
                    i++
                }
            }
        }
    }
}
