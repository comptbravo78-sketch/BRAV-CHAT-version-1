package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ai.MathSymbolicAst
import com.example.ai.PlotSeries

private class MathHeightBridge(private val onMeasuredDp: (Int) -> Unit) {
    @JavascriptInterface
    fun onContentHeight(heightDp: Int) {
        if (heightDp > 0) {
            onMeasuredDp(heightDp)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun LatexFormulaView(
    latex: String,
    modifier: Modifier = Modifier,
    fallbackText: String = "",
    useRichWebRenderer: Boolean = true
) {
    val cleanLatex = latex.trim()
        .removeSurrounding("$$", "$$")
        .removeSurrounding("\\[", "\\]")
        .removeSurrounding("$", "$")
        .removeSurrounding("\\(", "\\)")
        .trim()
    if (cleanLatex.isBlank() && fallbackText.isBlank()) return

    val prettyUnicode = remember(cleanLatex, fallbackText) {
        val base = fallbackText.ifBlank { latexToReadableUnicode(cleanLatex) }
        latexToReadableUnicode(base)
    }
    val inPreview = LocalInspectionMode.current
    val surfaceBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColorHex = remember(onSurfaceColor) {
        String.format("#%06X", 0xFFFFFF and onSurfaceColor.toArgb())
    }
    val accentHex = remember(primaryColor) {
        String.format("#%06X", 0xFFFFFF and primaryColor.toArgb())
    }
    var webHeightDp by remember(cleanLatex) { mutableIntStateOf(56) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = surfaceBg,
        modifier = modifier
            .fillMaxWidth()
            .testTag("latex_formula_view")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (useRichWebRenderer && !inPreview && cleanLatex.isNotBlank()) {
                val htmlDoc = remember(cleanLatex, textColorHex, accentHex) {
                    buildKatexHtml(cleanLatex, textColorHex, accentHex)
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                            setBackgroundColor(Color.TRANSPARENT)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = true
                            addJavascriptInterface(
                                MathHeightBridge { h ->
                                    post { webHeightDp = h.coerceIn(44, 340) }
                                },
                                "BravMathBridge"
                            )
                            webViewClient = WebViewClient()
                            loadDataWithBaseURL("https://bravchat.com/", htmlDoc, "text/html", "utf-8", null)
                        }
                    },
                    update = { webView ->
                        if (webView.tag != htmlDoc) {
                            webView.tag = htmlDoc
                            webView.loadDataWithBaseURL("https://bravchat.com/", htmlDoc, "text/html", "utf-8", null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp, max = 340.dp)
                        .height(webHeightDp.dp)
                )
            } else {
                Text(
                    text = prettyUnicode,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurfaceColor
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun RichMathContentView(
    text: String,
    modifier: Modifier = Modifier,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    accentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    val preparedText = remember(text) { ensureMathDelimiters(text) }
    if (preparedText.isBlank()) return

    val inPreview = LocalInspectionMode.current
    val textColorHex = remember(textColor) {
        String.format("#%06X", 0xFFFFFF and textColor.toArgb())
    }
    val accentHex = remember(accentColor) {
        String.format("#%06X", 0xFFFFFF and accentColor.toArgb())
    }
    val cardBgHex = remember(accentColor) {
        String.format("#%06X18", 0xFFFFFF and accentColor.toArgb())
    }

    if (inPreview) {
        StructuredMathFallbackColumn(
            preparedText = preparedText,
            textColor = textColor,
            accentColor = accentColor,
            modifier = modifier.testTag("rich_math_content_view")
        )
        return
    }

    var webHeightDp by remember(preparedText) {
        val estimatedLines = preparedText.lines().sumOf { 1 + (it.length / 42) }.coerceAtLeast(2)
        mutableIntStateOf((estimatedLines * 24 + 28).coerceIn(56, 980))
    }
    val htmlDoc = remember(preparedText, textColorHex, accentHex, cardBgHex) {
        buildRichMathDocumentHtml(preparedText, textColorHex, accentHex, cardBgHex)
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                addJavascriptInterface(
                    MathHeightBridge { h ->
                        post { webHeightDp = h.coerceIn(48, 2200) }
                    },
                    "BravMathBridge"
                )
                webViewClient = WebViewClient()
                tag = htmlDoc
                loadDataWithBaseURL("https://bravchat.com/", htmlDoc, "text/html", "utf-8", null)
            }
        },
        update = { webView ->
            if (webView.tag != htmlDoc) {
                webView.tag = htmlDoc
                webView.loadDataWithBaseURL("https://bravchat.com/", htmlDoc, "text/html", "utf-8", null)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(webHeightDp.dp)
            .testTag("rich_math_content_view")
    )
}

@Composable
fun StructuredMathFallbackColumn(
    preparedText: String,
    textColor: androidx.compose.ui.graphics.Color,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val lines = remember(preparedText) { preparedText.lines() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) {
                i++
                continue
            }
            if (line.startsWith("$$")) {
                val block = StringBuilder()
                if (line.endsWith("$$") && line.length > 4) {
                    block.append(line.removeSurrounding("$$"))
                } else {
                    block.append(line.removePrefix("$$"))
                    i++
                    while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                        block.append("\n").append(lines[i])
                        i++
                    }
                    if (i < lines.size) {
                        block.append("\n").append(lines[i].trim().removeSuffix("$$"))
                    }
                }
                LatexFormulaView(latex = block.toString().trim(), useRichWebRenderer = false)
            } else {
                val cleanLine = line
                    .removePrefix("### ")
                    .removePrefix("## ")
                    .removePrefix("# ")
                    .replace("**", "")
                val isHeader = line.startsWith("#") ||
                    (line.startsWith("**") && line.endsWith("**")) ||
                    Regex("""^\d+\.\s+[A-ZÉÈÊÀÂ]""").containsMatchIn(cleanLine)
                val readable = renderInlineLatexTokensToReadable(cleanLine)
                Text(
                    text = readable,
                    style = if (isHeader) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                    color = if (isHeader) accentColor else textColor
                )
            }
            i++
        }
    }
}

fun containsMathFormatting(text: String): Boolean {
    if (text.isBlank()) return false
    return text.contains("$") ||
        text.contains("\\(") ||
        text.contains("\\[") ||
        text.contains("\\frac") ||
        text.contains("\\vec") ||
        text.contains("\\sqrt") ||
        text.contains("\\int") ||
        text.contains("\\sum") ||
        text.contains("\\begin{") ||
        text.contains("**") ||
        text.contains("###") ||
        text.contains("## ")
}

fun ensureMathDelimiters(rawText: String): String {
    if (rawText.isBlank()) return ""
    // 1. Normalize \[ ... \] -> $$ ... $$ and \( ... \) -> $ ... $
    var out = rawText
        .replace(Regex("""\\\[([\s\S]*?)\\\]""")) { "$$${it.groupValues[1].trim()}$$" }
        .replace(Regex("""\\\(([\s\S]*?)\\\)""")) { "$${it.groupValues[1].trim()}$" }

    // 2. Wrap bare \begin{env}...\end{env} blocks if not already inside $$...$$
    val splitByDisplay = out.split("$$")
    val rebuiltDisplay = StringBuilder()
    for (idx in splitByDisplay.indices) {
        val segment = splitByDisplay[idx]
        if (idx % 2 == 1) {
            rebuiltDisplay.append("$$").append(segment).append("$$")
        } else {
            val wrappedEnvs = segment.replace(
                Regex("""\\begin\{(pmatrix|bmatrix|vmatrix|matrix|aligned|cases|array)\}[\s\S]*?\\end\{\1\}""")
            ) { m -> "\n$$\n${m.value}\n$$\n" }
            rebuiltDisplay.append(wrappedEnvs)
        }
    }
    out = rebuiltDisplay.toString()

    // 3. Wrap bare inline LaTeX commands outside $...$ / $$...$$ so zero raw code ever leaks
    val mathCmdRegex = Regex(
        """(\\(?:d?frac|sqrt|vec|overrightarrow|overline|hat|int|sum|prod|lim|det)\s*(?:_\{[^}]*\})?(?:\^\{[^}]*\})?(?:\{[^}]*\})*(?:\s*\\,\s*d[A-Za-z])?)"""
    )
    val tokens = out.split("$")
    val finalSb = StringBuilder()
    var i = 0
    while (i < tokens.size) {
        if (i + 1 < tokens.size && tokens[i].isEmpty() && tokens[i + 1].isEmpty()) {
            // Handle $$...$$ boundary cleanly
            finalSb.append("$$")
            i += 2
            if (i < tokens.size) {
                finalSb.append(tokens[i])
                i++
            }
            if (i + 1 < tokens.size && tokens[i].isEmpty() && tokens[i + 1].isEmpty()) {
                finalSb.append("$$")
                i += 2
            }
            continue
        }
        val part = tokens[i]
        if (i % 2 == 1) {
            finalSb.append("$").append(part).append("$")
        } else {
            finalSb.append(mathCmdRegex.replace(part) { m -> "$${m.value}$" })
        }
        i++
    }
    return finalSb.toString().trim()
}

private fun renderInlineLatexTokensToReadable(input: String): String {
    var out = input.replace(Regex("""\$\$([\s\S]*?)\$\$""")) { latexToReadableUnicode(it.groupValues[1]) }
    out = out.replace(Regex("""\$([^$\n]+)\$""")) { latexToReadableUnicode(it.groupValues[1]) }
    if (out.contains("\\")) {
        out = latexToReadableUnicode(out)
    }
    return out
}

fun latexToReadableUnicode(rawLatex: String): String {
    var s = rawLatex.trim()
        .removeSurrounding("$$", "$$")
        .removeSurrounding("\\[", "\\]")
        .removeSurrounding("$", "$")
        .removeSurrounding("\\(", "\\)")

    // Environments: pmatrix, bmatrix, vmatrix, aligned, cases
    s = Regex("""\\begin\{(pmatrix|bmatrix|vmatrix|matrix|aligned|cases)\}([\s\S]*?)\\end\{\1\}""").replace(s) { m ->
        val env = m.groupValues[1]
        val rows = m.groupValues[2].trim()
            .split("""\\""")
            .map { r -> r.trim().split("&").joinToString("  ") { it.trim() } }
            .filter { it.isNotBlank() }
        val inner = rows.joinToString(" ; ")
        when (env) {
            "vmatrix" -> "| $inner |"
            "bmatrix" -> "[ $inner ]"
            "pmatrix" -> "( $inner )"
            "cases" -> "{ $inner }"
            else -> rows.joinToString("\n")
        }
    }

    // \vec{u} or \overrightarrow{AB} -> u⃗ / AB⃗
    s = Regex("""\\(?:vec|overrightarrow)\{([^{}]+)\}""").replace(s) { m ->
        val v = m.groupValues[1].trim()
        if (v.length == 1) "${v}\u20D7" else "$v\u20D7"
    }
    s = Regex("""\\(?:overline|bar)\{([^{}]+)\}""").replace(s) { "${it.groupValues[1].trim()}\u0304" }
    s = Regex("""\\hat\{([^{}]+)\}""").replace(s) { "${it.groupValues[1].trim()}\u0302" }

    // \text{...}, \mathrm{...}, \mathbf{...}, \operatorname{...}
    s = Regex("""\\(?:text|mathrm|mathbf|mathit|operatorname)\{([^{}]*)\}""").replace(s) { it.groupValues[1] }

    // Iterative balanced \frac{num}{den} and \sqrt{x}
    var guard = 0
    while ((s.contains("\\frac") || s.contains("\\dfrac") || s.contains("\\sqrt")) && guard < 12) {
        guard++
        s = replaceBalancedFrac(s)
        s = replaceBalancedSqrt(s)
    }

    s = s
        .replace("\\left", "")
        .replace("\\right", "")
        .replace("\\cdot", "·")
        .replace("\\times", "×")
        .replace("\\div", "÷")
        .replace("\\pm", "±")
        .replace("\\mp", "∓")
        .replace("\\leq", "≤")
        .replace("\\le", "≤")
        .replace("\\geq", "≥")
        .replace("\\ge", "≥")
        .replace("\\neq", "≠")
        .replace("\\ne", "≠")
        .replace("\\equiv", "≡")
        .replace("\\approx", "≈")
        .replace("\\pi", "π")
        .replace("\\alpha", "α")
        .replace("\\beta", "β")
        .replace("\\gamma", "γ")
        .replace("\\delta", "δ")
        .replace("\\epsilon", "ε")
        .replace("\\theta", "θ")
        .replace("\\lambda", "λ")
        .replace("\\mu", "μ")
        .replace("\\sigma", "σ")
        .replace("\\Sigma", "Σ")
        .replace("\\Delta", "Δ")
        .replace("\\Omega", "Ω")
        .replace("\\infty", "∞")
        .replace("\\implies", "⇒")
        .replace("\\iff", "⇔")
        .replace("\\to", "→")
        .replace("\\rightarrow", "→")
        .replace("\\in", "∈")
        .replace("\\notin", "∉")
        .replace("\\subset", "⊂")
        .replace("\\cup", "∪")
        .replace("\\cap", "∩")
        .replace("\\emptyset", "∅")
        .replace("\\forall", "∀")
        .replace("\\exists", "∃")
        .replace("\\partial", "∂")
        .replace("\\nabla", "∇")
        .replace("\\int", "∫")
        .replace("\\sum", "∑")
        .replace("\\prod", "∏")
        .replace("\\lim", "lim")
        .replace("\\sin", "sin")
        .replace("\\cos", "cos")
        .replace("\\tan", "tan")
        .replace("\\ln", "ln")
        .replace("\\log", "log")
        .replace("\\exp", "exp")
        .replace("\\det", "det")
        .replace("\\quad", "  ")
        .replace("\\qquad", "    ")
        .replace("\\,", " ")
        .replace("\\;", " ")
        .replace("\\!", "")
        .replace("\\{", "{")
        .replace("\\}", "}")
        .replace("\\\\", "\n")

    // Superscripts & subscripts to clean Unicode when simple
    s = Regex("""\^\{([^{}]+)\}""").replace(s) { m -> toUnicodeSuperscript(m.groupValues[1]) }
    s = Regex("""\^([0-9+\-n])""").replace(s) { m -> toUnicodeSuperscript(m.groupValues[1]) }
    s = Regex("""_\{([^{}]+)\}""").replace(s) { m -> toUnicodeSubscript(m.groupValues[1]) }
    s = Regex("""_([0-9+\-ijkxyn])""").replace(s) { m -> toUnicodeSubscript(m.groupValues[1]) }

    return s.replace(Regex("""[ \t]{2,}"""), " ").trim()
}

private fun replaceBalancedFrac(src: String): String {
    val cmdIdx = Regex("""\\d?frac\s*\{""").find(src) ?: return src
    val startNum = cmdIdx.range.last + 1
    val numRes = extractBraceGroup(src, startNum - 1) ?: return src
    val afterNum = numRes.second
    val denStart = src.indexOf('{', afterNum)
    if (denStart == -1 || src.substring(afterNum, denStart).isNotBlank()) return src
    val denRes = extractBraceGroup(src, denStart) ?: return src
    val numTxt = numRes.first.trim()
    val denTxt = denRes.first.trim()
    val formattedNum = if (numTxt.any { it in "+- " }) "($numTxt)" else numTxt
    val formattedDen = if (denTxt.any { it in "+- " }) "($denTxt)" else denTxt
    return src.substring(0, cmdIdx.range.first) + "$formattedNum/$formattedDen" + src.substring(denRes.second)
}

private fun replaceBalancedSqrt(src: String): String {
    val cmdIdx = Regex("""\\sqrt(?:\[([^\]]+)\])?\s*\{""").find(src) ?: return src
    val rootIdx = cmdIdx.groupValues[1].trim()
    val braceStart = src.indexOf('{', cmdIdx.range.first)
    if (braceStart == -1) return src
    val innerRes = extractBraceGroup(src, braceStart) ?: return src
    val prefix = if (rootIdx.isNotBlank()) "${toUnicodeSuperscript(rootIdx)}√" else "√"
    return src.substring(0, cmdIdx.range.first) + "$prefix(${innerRes.first.trim()})" + src.substring(innerRes.second)
}

private fun extractBraceGroup(s: String, openIndex: Int): Pair<String, Int>? {
    if (openIndex < 0 || openIndex >= s.length || s[openIndex] != '{') return null
    var depth = 0
    for (i in openIndex until s.length) {
        if (s[i] == '{') depth++
        else if (s[i] == '}') {
            depth--
            if (depth == 0) {
                return s.substring(openIndex + 1, i) to (i + 1)
            }
        }
    }
    return null
}

private fun toUnicodeSuperscript(raw: String): String {
    val map = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', 'n' to 'ⁿ', 'i' to 'ⁱ', '(' to '⁽', ')' to '⁾'
    )
    return if (raw.all { it in map }) raw.map { map.getValue(it) }.joinToString("") else "^($raw)"
}

private fun toUnicodeSubscript(raw: String): String {
    val map = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ', 'x' to 'ₓ', 'n' to 'ₙ'
    )
    return if (raw.all { it in map }) raw.map { map.getValue(it) }.joinToString("") else "_$raw"
}

private val SHARED_MATH_FALLBACK_CSS = """
  .m-frac { display: inline-flex; flex-direction: column; align-items: center; vertical-align: middle; margin: 0 3px; font-size: 0.92em; }
  .m-num { border-bottom: 1.5px solid currentColor; padding: 0 4px 1px 4px; text-align: center; }
  .m-den { padding: 1px 4px 0 4px; text-align: center; }
  .m-sqrt { display: inline-flex; align-items: center; vertical-align: middle; }
  .m-rad { border-top: 1.5px solid currentColor; padding: 1px 3px 0 3px; margin-left: 1px; }
  .m-vec { position: relative; display: inline-block; padding-top: 5px; font-style: italic; }
  .m-vec::before { content: "→"; position: absolute; top: -5px; left: 50%; transform: translateX(-50%); font-size: 0.65em; font-style: normal; }
  .m-mat { display: inline-table; vertical-align: middle; border-collapse: collapse; margin: 2px 4px; padding: 0 6px; border-left: 2px solid currentColor; border-right: 2px solid currentColor; border-radius: 6px; }
  .m-mat.vmat { border-radius: 0; }
  .m-mat td { padding: 2px 7px; text-align: center; }
  .m-block { display: block; text-align: center; margin: 8px 0; padding: 8px 10px; border-radius: 10px; background: rgba(128,128,128,0.08); overflow-x: auto; }
""".trimIndent()

private val SHARED_JS_MATH_FALLBACK = """
function extractGroup(str, idx) {
  if (str[idx] !== '{') return null;
  var d = 0;
  for (var i = idx; i < str.length; i++) {
    if (str[i] === '{') d++;
    else if (str[i] === '}') {
      d--;
      if (d === 0) return [str.slice(idx + 1, i), i + 1];
    }
  }
  return null;
}
function texToVisualHtml(tex) {
  var s = String(tex || '').trim();
  s = s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  s = s.replace(/\\begin\{(pmatrix|bmatrix|vmatrix|matrix|aligned|cases)\}([\s\S]*?)\\end\{\1\}/g, function(_, env, body) {
    var rows = body.trim().split(/\\\\/).map(function(r) {
      return '<tr>' + r.trim().split('&amp;').map(function(c) { return '<td>' + texToVisualHtml(c.trim()) + '</td>'; }).join('') + '</tr>';
    }).join('');
    var cls = env === 'vmatrix' ? 'm-mat vmat' : 'm-mat';
    return '<table class="' + cls + '">' + rows + '</table>';
  });
  s = s.replace(/\\(?:vec|overrightarrow)\{([^{}]+)\}/g, '<span class="m-vec">$1</span>');
  s = s.replace(/\\(?:text|mathrm|mathbf|operatorname)\{([^{}]*)\}/g, '$1');
  for (var pass = 0; pass < 8; pass++) {
    var mFrac = /\\d?frac\s*\{/.exec(s);
    if (mFrac) {
      var g1 = extractGroup(s, mFrac.index + mFrac[0].length - 1);
      if (g1) {
        var nextOpen = s.indexOf('{', g1[1]);
        if (nextOpen !== -1 && s.slice(g1[1], nextOpen).trim() === '') {
          var g2 = extractGroup(s, nextOpen);
          if (g2) {
            s = s.slice(0, mFrac.index) + '<span class="m-frac"><span class="m-num">' + g1[0] + '</span><span class="m-den">' + g2[0] + '</span></span>' + s.slice(g2[1]);
            continue;
          }
        }
      }
    }
    var mSqrt = /\\sqrt\s*\{/.exec(s);
    if (mSqrt) {
      var gs = extractGroup(s, mSqrt.index + mSqrt[0].length - 1);
      if (gs) {
        s = s.slice(0, mSqrt.index) + '<span class="m-sqrt">√<span class="m-rad">' + gs[0] + '</span></span>' + s.slice(gs[1]);
        continue;
      }
    }
    break;
  }
  s = s.replace(/\^\{([^{}]+)\}/g, '<sup>$1</sup>').replace(/\^([0-9n])/g, '<sup>$1</sup>');
  s = s.replace(/_\{([^{}]+)\}/g, '<sub>$1</sub>').replace(/_([0-9ijkxyn])/g, '<sub>$1</sub>');
  s = s.replace(/\\left|\\right/g, '')
       .replace(/\\cdot/g, '·').replace(/\\times/g, '×').replace(/\\div/g, '÷')
       .replace(/\\pm/g, '±').replace(/\\leq?/g, '≤').replace(/\\geq?/g, '≥').replace(/\\neq?/g, '≠')
       .replace(/\\approx/g, '≈').replace(/\\implies/g, '⇒').replace(/\\to|\\rightarrow/g, '→')
       .replace(/\\pi/g, 'π').replace(/\\Delta/g, 'Δ').replace(/\\theta/g, 'θ').replace(/\\lambda/g, 'λ')
       .replace(/\\infty/g, '∞').replace(/\\in/g, '∈').replace(/\\int/g, '∫').replace(/\\sum/g, '∑').replace(/\\prod/g, '∏')
       .replace(/\\lim/g, 'lim').replace(/\\sin/g, 'sin').replace(/\\cos/g, 'cos').replace(/\\tan/g, 'tan')
       .replace(/\\ln/g, 'ln').replace(/\\log/g, 'log').replace(/\\exp/g, 'exp').replace(/\\det/g, 'det')
       .replace(/\\[,;!]/g, ' ').replace(/\\q?quad/g, ' &nbsp; ');
  return s;
}
""".trimIndent()

private fun escapeForJsonScript(text: String): String {
    return org.json.JSONObject.quote(text)
}

private fun buildKatexHtml(latex: String, hexColor: String, accentHex: String): String {
    val quotedLatex = escapeForJsonScript(latex)
    val unicodeFallback = latexToReadableUnicode(latex)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
<script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
<style>
  html, body {
    margin: 0; padding: 2px; background: transparent;
    color: $hexColor; font-family: system-ui, -apple-system, sans-serif;
    overflow-x: auto; overflow-y: hidden;
  }
  #formula { font-size: 1.06rem; min-height: 28px; display: flex; align-items: center; }
  .katex-display { margin: 0.25em 0 !important; }
  .katex { color: $hexColor; }
  $SHARED_MATH_FALLBACK_CSS
</style>
</head>
<body>
<div id="formula">$unicodeFallback</div>
<script>
$SHARED_JS_MATH_FALLBACK
function reportHeight() {
  try {
    var h = Math.ceil(document.body.scrollHeight);
    if (window.BravMathBridge && h > 0) window.BravMathBridge.onContentHeight(h + 8);
  } catch (e) {}
}
window.addEventListener('load', function() {
  var tex = $quotedLatex;
  var el = document.getElementById('formula');
  try {
    if (window.katex) {
      katex.render(tex, el, { throwOnError: false, displayMode: true });
    } else {
      el.innerHTML = texToVisualHtml(tex);
    }
  } catch (e) {
    el.innerHTML = texToVisualHtml(tex);
  }
  reportHeight();
  setTimeout(reportHeight, 120);
});
</script>
</body>
</html>
""".trimIndent()
}

private fun buildRichMathDocumentHtml(
    preparedText: String,
    hexColor: String,
    accentHex: String,
    cardBgHex: String
): String {
    val quotedText = escapeForJsonScript(preparedText)
    val safePreviewFallback = renderInlineLatexTokensToReadable(preparedText)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\n", "<br>")
    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
<script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
<script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/auto-render.min.js"></script>
<style>
  html, body {
    margin: 0; padding: 0; background: transparent;
    color: $hexColor; font-family: system-ui, -apple-system, sans-serif;
    font-size: 14.5px; line-height: 1.52;
    word-wrap: break-word; overflow-x: hidden;
  }
  #content { padding: 2px 0; }
  h3.step-h {
    margin: 10px 0 4px 0; font-size: 14.5px; font-weight: 700;
    color: $accentHex;
  }
  p { margin: 4px 0 6px 0; }
  ul { margin: 4px 0 6px 0; padding-left: 18px; }
  li { margin: 3px 0; }
  .result-box {
    margin: 8px 0; padding: 9px 12px; border-radius: 12px;
    background: $cardBgHex; border-left: 3.5px solid $accentHex;
  }
  .katex-display {
    margin: 8px 0 !important; padding: 8px 10px;
    border-radius: 10px; background: rgba(128,128,128,0.08);
    overflow-x: auto; overflow-y: hidden;
  }
  .katex { color: $hexColor; font-size: 1.04em; }
  $SHARED_MATH_FALLBACK_CSS
</style>
</head>
<body>
<div id="content">$safePreviewFallback</div>
<script>
$SHARED_JS_MATH_FALLBACK
function formatMarkdownPreservingMath(src) {
  var mathSlots = [];
  var tokenized = String(src || '').replace(/\$\$([\s\S]*?)\$\$|\$([^$\n]+)\$/g, function(full) {
    var idx = mathSlots.length;
    mathSlots.push(full);
    return '@@MATH_' + idx + '@@';
  });
  var esc = tokenized.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  var lines = esc.split(/\r?\n/);
  var html = [];
  var inList = false;
  for (var i = 0; i < lines.length; i++) {
    var rawLine = lines[i].trim();
    if (!rawLine) {
      if (inList) { html.push('</ul>'); inList = false; }
      continue;
    }
    rawLine = rawLine.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    if (/^#{1,3}\s+/.test(rawLine)) {
      if (inList) { html.push('</ul>'); inList = false; }
      html.push('<h3 class="step-h">' + rawLine.replace(/^#{1,3}\s+/, '') + '</h3>');
    } else if (/^\*\*?\s*\d+\.\s*/.test(rawLine) || /^(Étape\s+\d+|1\.\s*Énoncé|2\.\s*Données|3\.\s*Méthode|4\.\s*Résolution|5\.\s*Résultat|6\.\s*Interprétation)/i.test(rawLine.replace(/<\/?strong>/g,''))) {
      if (inList) { html.push('</ul>'); inList = false; }
      var plain = rawLine.replace(/<\/?strong>/g, '');
      if (/(Résultat final|Conclusion\s*:)/i.test(plain)) {
        html.push('<div class="result-box"><strong>' + rawLine + '</strong></div>');
      } else {
        html.push('<h3 class="step-h">' + rawLine + '</h3>');
      }
    } else if (/^[-•*]\s+/.test(rawLine)) {
      if (!inList) { html.push('<ul>'); inList = true; }
      html.push('<li>' + rawLine.replace(/^[-•*]\s+/, '') + '</li>');
    } else {
      if (inList) { html.push('</ul>'); inList = false; }
      if (/^(Résultat final|Conclusion)\s*[:]/i.test(rawLine.replace(/<\/?strong>/g, ''))) {
        html.push('<div class="result-box">' + rawLine + '</div>');
      } else {
        html.push('<p>' + rawLine + '</p>');
      }
    }
  }
  if (inList) html.push('</ul>');
  var joined = html.join('\n');
  return joined.replace(/@@MATH_(\d+)@@/g, function(_, idx) { return mathSlots[Number(idx)] || ''; });
}
function renderOfflineMathFallback(root) {
  root.innerHTML = root.innerHTML
    .replace(/\$\$([\s\S]*?)\$\$/g, function(_, tex) { return '<div class="m-block">' + texToVisualHtml(tex) + '</div>'; })
    .replace(/\$([^$\n]+)\$/g, function(_, tex) { return texToVisualHtml(tex); });
}
function reportHeight() {
  try {
    var h = Math.ceil(document.getElementById('content').scrollHeight);
    if (window.BravMathBridge && h > 0) window.BravMathBridge.onContentHeight(h + 10);
  } catch (e) {}
}
window.addEventListener('load', function() {
  var el = document.getElementById('content');
  el.innerHTML = formatMarkdownPreservingMath($quotedText);
  try {
    if (window.renderMathInElement && window.katex) {
      renderMathInElement(el, {
        delimiters: [
          { left: '$$', right: '$$', display: true },
          { left: '$', right: '$', display: false },
          { left: '\\[', right: '\\]', display: true },
          { left: '\\(', right: '\\)', display: false }
        ],
        throwOnError: false
      });
    } else {
      renderOfflineMathFallback(el);
    }
  } catch (e) {
    renderOfflineMathFallback(el);
  }
  reportHeight();
  setTimeout(reportHeight, 150);
  setTimeout(reportHeight, 450);
});
</script>
</body>
</html>
""".trimIndent()
}

@Composable
fun PlotSeriesChart(
    series: PlotSeries,
    modifier: Modifier = Modifier
) {
    if (series.xValues.size < 2 || series.xValues.size != series.yValues.size) return
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outline
    val minX = remember(series) { series.xValues.minOrNull() ?: 0.0 }
    val maxX = remember(series) { (series.xValues.maxOrNull() ?: 1.0).let { if (it <= minX) minX + 1.0 else it } }
    val minY = remember(series) { series.yValues.minOrNull() ?: 0.0 }
    val maxY = remember(series) { (series.yValues.maxOrNull() ?: 1.0).let { if (it <= minY) minY + 1.0 else it } }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier
            .fillMaxWidth()
            .testTag("math_plot_chart")
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(series.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "[${MathSymbolicAst.formatNum(minX)}, ${MathSymbolicAst.formatNum(maxX)}]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val padL = 12f
                    val padR = 12f
                    val padT = 10f
                    val padB = 10f
                    val w = (size.width - padL - padR).coerceAtLeast(1f)
                    val h = (size.height - padT - padB).coerceAtLeast(1f)

                    if (minY <= 0.0 && maxY >= 0.0) {
                        val zeroY = padT + h * (1.0 - (0.0 - minY) / (maxY - minY)).toFloat()
                        drawLine(axisColor.copy(alpha = 0.45f), Offset(padL, zeroY), Offset(padL + w, zeroY), strokeWidth = 1.5f)
                    }
                    if (minX <= 0.0 && maxX >= 0.0) {
                        val zeroX = padL + w * ((0.0 - minX) / (maxX - minX)).toFloat()
                        drawLine(axisColor.copy(alpha = 0.45f), Offset(zeroX, padT), Offset(zeroX, padT + h), strokeWidth = 1.5f)
                    }

                    val path = Path()
                    series.xValues.indices.forEach { i ->
                        val px = padL + w * ((series.xValues[i] - minX) / (maxX - minX)).toFloat()
                        val py = padT + h * (1.0 - (series.yValues[i] - minY) / (maxY - minY)).toFloat()
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    drawPath(path, color = lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

data class LatexSnippet(val label: String, val snippet: String, val replaceFull: Boolean = false)

val universalLatexSnippets = listOf(
    LatexSnippet("Fraction", "\\frac{x^2 - 1}{x - 1}", replaceFull = false),
    LatexSnippet("∫ Définie", "\\int_{0}^{\\pi} \\sin(x) + x^2\\,dx", replaceFull = true),
    LatexSnippet("d/dx", "\\frac{d}{dx}(x^3 \\cdot e^{2x})", replaceFull = true),
    LatexSnippet("Limite", "\\lim_{x \\to 1} \\frac{x^2 - 1}{x - 1}", replaceFull = true),
    LatexSnippet("Σ Somme", "\\sum_{k=1}^{10} k^2", replaceFull = true),
    LatexSnippet("Π Produit", "\\prod_{k=1}^{5} k", replaceFull = true),
    LatexSnippet("Matrice det", "\\det\\begin{pmatrix}4 & 2 \\\\ 1 & 3\\end{pmatrix}", replaceFull = true),
    LatexSnippet("Matrice A⁻¹", "\\begin{pmatrix}4 & 7 \\\\ 2 & 6\\end{pmatrix}^{-1}", replaceFull = true),
    LatexSnippet("EDO 2ᵉ ordre", "y'' - 5y' + 6y = 0", replaceFull = true),
    LatexSnippet("Taylor", "taylor(exp(x), x, 0, 4)", replaceFull = true),
    LatexSnippet("√", "\\sqrt{x}", replaceFull = false),
    LatexSnippet("π", "\\pi", replaceFull = false),
    LatexSnippet("xⁿ", "^{2}", replaceFull = false)
)

@Composable
fun LatexSymbolPalette(
    currentExpression: String,
    onExpressionChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        universalLatexSnippets.forEach { item ->
            AssistChip(
                onClick = {
                    if (!item.replaceFull && currentExpression.isNotBlank()) {
                        onExpressionChange(currentExpression + item.snippet)
                    } else {
                        onExpressionChange(item.snippet)
                    }
                },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
