package com.example.ai

import kotlin.math.*

data class PlotSeries(
    val title: String,
    val xLabel: String = "x",
    val yLabel: String = "f(x)",
    val xValues: List<Double>,
    val yValues: List<Double>
)

data class MathComputationResult(
    val topic: LatexMathTopic,
    val rawInput: String,
    val operationLabel: String,
    val primaryResult: String,
    val unicodeFormula: String,
    val latexFormula: String,
    val steps: List<String>,
    val relatedFormulas: List<Pair<String, String>> = emptyList(),
    val verificationSource: MathVerificationSource = MathVerificationSource.COMPUTED_LOCAL,
    val plotSeries: PlotSeries? = null,
    val isSuccess: Boolean = true,
    val errorMessage: String = ""
)

object MathComputationEngine {

    fun compute(rawInput: String, topic: LatexMathTopic = LatexMathTopic.ANALYSE): MathComputationResult {
        val cleaned = rawInput.trim()
            .removeSurrounding("$$", "$$")
            .removeSurrounding("$", "$")
            .removeSuffix(";")
            .trim()
        if (cleaned.isBlank()) {
            return errorResult(topic, rawInput, "Entrez une expression ou formule LaTeX.")
        }

        return try {
            // 1. LaTeX command dispatchers (\int, \frac{d}{dx}, \lim, \sum, \prod, \det\begin{pmatrix}...)
            tryDispatchLatexCommand(cleaned, topic)?.let { return it }

            // 2. ODE detection: y'' + ay' + by = 0 or y' + ay = b or ode(...)
            if (isDifferentialEquation(cleaned)) {
                return handleOde(cleaned, topic)
            }

            // 3. Explicit function calls: solve(...), factor(...), taylor(...), det(...), mean(...), etc.
            val topCall = parseTopFunctionCall(cleaned)
            if (topCall != null) {
                dispatchFunctionCall(topCall.first, topCall.second, cleaned, topic)
            } else {
                handleDirectExpressionOrEquation(cleaned, topic)
            }
        } catch (e: Exception) {
            errorResult(topic, rawInput, e.localizedMessage ?: "Expression LaTeX non reconnue")
        }
    }

    private fun tryDispatchLatexCommand(cleaned: String, topic: LatexMathTopic): MathComputationResult? {
        // \frac{d}{dx}(...) or \frac{d^2}{dx^2}(...)
        val diffRegex = Regex("""^\\(?:d)?frac\{d(?:\^\{?(\d+)\}?)?\}\{d([A-Za-z_][A-Za-z0-9_]*)(?:\^\{\d+\}|\^\d+)?\}\s*(.+)$""", RegexOption.DOT_MATCHES_ALL)
        diffRegex.matchEntire(cleaned)?.let { m ->
            val orderStr = m.groupValues[1].ifBlank { "1" }
            val v = m.groupValues[2]
            val body = unwrapLatexParens(m.groupValues[3])
            return handleDifferentiate(listOf(body, v, orderStr), cleaned, topic)
        }

        // \int_{a}^{b} f \, dx  OR  \int f \, dx
        if (cleaned.startsWith("\\int")) {
            val defRegex = Regex("""^\\int_\{?([^}\s]+)\}?\^\{?([^}\s]+)\}?\s*(.+?)\s*(?:\\[,;!]|\s)*d([A-Za-z])$""", RegexOption.DOT_MATCHES_ALL)
            defRegex.matchEntire(cleaned)?.let { m ->
                val aStr = m.groupValues[1]
                val bStr = m.groupValues[2]
                val body = unwrapLatexParens(m.groupValues[3])
                val v = m.groupValues[4]
                return handleIntegrate(listOf(body, v, aStr, bStr), cleaned, topic)
            }
            val indefRegex = Regex("""^\\int\s*(.+?)\s*(?:\\[,;!]|\s)*d([A-Za-z])$""", RegexOption.DOT_MATCHES_ALL)
            indefRegex.matchEntire(cleaned)?.let { m ->
                val body = unwrapLatexParens(m.groupValues[1])
                val v = m.groupValues[2]
                return handleIntegrate(listOf(body, v), cleaned, topic)
            }
        }

        // \lim_{x \to target} expr
        val limRegex = Regex("""^\\lim_\{([A-Za-z_][A-Za-z0-9_]*)\s*\\to\s*([^}]+)\}\s*(.+)$""", RegexOption.DOT_MATCHES_ALL)
        limRegex.matchEntire(cleaned)?.let { m ->
            val v = m.groupValues[1].trim()
            val target = m.groupValues[2].trim()
            val body = unwrapLatexParens(m.groupValues[3])
            return handleLimit(listOf(body, v, target), cleaned, topic)
        }

        // \sum_{k=a}^{b} expr  and  \prod_{k=a}^{b} expr
        val sumRegex = Regex("""^\\(sum|prod)_\{([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([^}]+)\}\^\{([^}]+)\}\s*(.+)$""", RegexOption.DOT_MATCHES_ALL)
        sumRegex.matchEntire(cleaned)?.let { m ->
            val isProd = m.groupValues[1] == "prod"
            val v = m.groupValues[2].trim()
            val startIdx = MathSymbolicAst.evaluate(MathSymbolicAst.parseExpr(m.groupValues[3])).roundToInt()
            val endIdx = MathSymbolicAst.evaluate(MathSymbolicAst.parseExpr(m.groupValues[4])).roundToInt()
            val body = unwrapLatexParens(m.groupValues[5])
            return handleFiniteSeries(isProd, body, v, startIdx, endIdx, cleaned, topic)
        }

        // \begin{pmatrix}...\end{pmatrix}^{-1} or \det\begin{pmatrix}...
        if (cleaned.contains("\\begin{pmatrix}") || cleaned.contains("\\begin{bmatrix}")) {
            val mat = parseMatrix(cleaned)
            if (mat != null) {
                val op = when {
                    cleaned.contains("\\det", true) -> "det"
                    cleaned.contains("^{-1}") -> "inv"
                    cleaned.contains("^T") || cleaned.contains("^{T}") -> "transpose"
                    cleaned.contains("\\operatorname{tr}") || cleaned.contains("\\trace") -> "trace"
                    cleaned.contains("\\operatorname{spec}") || cleaned.contains("eig") -> "eig"
                    else -> "det"
                }
                val latexMatArg = matrixToLiteralString(mat)
                return handleMatrixUnary(op, listOf(latexMatArg), cleaned, topic)
            }
        }

        return null
    }

    private fun unwrapLatexParens(s: String): String {
        var t = s.trim()
        if (t.startsWith("\\left(") && t.endsWith("\\right)")) {
            t = t.removePrefix("\\left(").removeSuffix("\\right)").trim()
        }
        return t
    }

    private fun parseTopFunctionCall(input: String): Pair<String, List<String>>? {
        val trimmed = input.trim()
        val match = Regex("""^([A-Za-z_][A-Za-z0-9_:-]*)\s*([(\[])(.*)([)\]])$""", RegexOption.DOT_MATCHES_ALL)
            .matchEntire(trimmed) ?: return null
        val fnName = match.groupValues[1]
        val openBr = match.groupValues[2]
        val closeBr = match.groupValues[4]
        if ((openBr == "(" && closeBr != ")") || (openBr == "[" && closeBr != "]")) return null
        val inner = match.groupValues[3]
        // Ensure parentheses inside inner didn't close and reopen at top level like (x+1)+(x+2)
        if (fnName.lowercase() in setOf("sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh", "exp", "ln", "log", "log10", "log2", "sqrt", "abs")) {
            return null
        }
        return fnName to splitTopLevelArgs(inner)
    }

    private fun splitTopLevelArgs(src: String): List<String> {
        val args = mutableListOf<String>()
        var depth = 0
        var inQuote = false
        var quoteChar = '\''
        val cur = StringBuilder()
        for (c in src) {
            when {
                inQuote -> {
                    cur.append(c)
                    if (c == quoteChar) inQuote = false
                }
                c == '\'' || c == '"' -> {
                    inQuote = true
                    quoteChar = c
                    cur.append(c)
                }
                c == '(' || c == '[' || c == '{' -> {
                    depth++
                    cur.append(c)
                }
                c == ')' || c == ']' || c == '}' -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    cur.append(c)
                }
                c == ',' && depth == 0 -> {
                    args.add(cur.toString().trim())
                    cur.clear()
                }
                else -> cur.append(c)
            }
        }
        val tail = cur.toString().trim()
        if (tail.isNotEmpty() || args.isNotEmpty()) args.add(tail)
        return args
    }

    private fun dispatchFunctionCall(
        rawFn: String,
        rawArgs: List<String>,
        rawInput: String,
        topic: LatexMathTopic
    ): MathComputationResult {
        val fn = rawFn.substringAfterLast(":-").substringAfterLast(".").lowercase()
        return when (fn) {
            "d", "diff", "derive" -> handleDifferentiate(rawArgs, rawInput, topic)
            "integrate", "int", "quad" -> handleIntegrate(rawArgs, rawInput, topic)
            "solve" -> handleSolveCommand(rawArgs, rawInput, topic)
            "roots", "polyroot" -> handleRoots(rawArgs, rawInput, topic)
            "factor" -> handleFactor(rawArgs, rawInput, topic)
            "expand" -> handleExpand(rawArgs, rawInput, topic)
            "simplify", "ratsimp", "fullsimplify", "radcan" -> handleSimplify(rawArgs, rawInput, topic)
            "limit", "lim" -> handleLimit(rawArgs, rawInput, topic)
            "taylor", "series" -> handleTaylor(rawArgs, rawInput, topic)
            "ode", "dsolve" -> handleOde(rawArgs.firstOrNull().orEmpty(), topic)
            "det", "determinant" -> handleMatrixUnary("det", rawArgs, rawInput, topic)
            "inv", "inverse", "invert" -> handleMatrixUnary("inv", rawArgs, rawInput, topic)
            "eig", "eigenvalues", "spec", "eigen" -> handleMatrixUnary("eig", rawArgs, rawInput, topic)
            "transpose", "t" -> handleMatrixUnary("transpose", rawArgs, rawInput, topic)
            "trace", "tr" -> handleMatrixUnary("trace", rawArgs, rawInput, topic)
            "mean", "avg" -> handleStats("mean", rawArgs, rawInput, topic)
            "median" -> handleStats("median", rawArgs, rawInput, topic)
            "sd", "std", "stdev" -> handleStats("sd", rawArgs, rawInput, topic)
            "var", "variance" -> handleStats("var", rawArgs, rawInput, topic)
            "sum" -> handleStats("sum", rawArgs, rawInput, topic)
            "prod" -> handleStats("prod", rawArgs, rawInput, topic)
            "min" -> handleStats("min", rawArgs, rawInput, topic)
            "max" -> handleStats("max", rawArgs, rawInput, topic)
            "linspace" -> handleLinspace(rawArgs, rawInput, topic)
            "seq" -> handleSeq(rawArgs, rawInput, topic)
            "n", "evalf", "float" -> handleNumericEval(rawArgs.firstOrNull().orEmpty(), rawInput, topic)
            else -> handleDirectExpressionOrEquation(rawInput, topic)
        }
    }

    private fun handleDifferentiate(args: List<String>, rawInput: String, topic: LatexMathTopic): MathComputationResult {
        val exprStr = stripQuotes(args.getOrNull(0).orEmpty())
        val (varName, order) = parseVarAndOrder(args.getOrNull(1), args.getOrNull(2))
        val parsed = MathSymbolicAst.parseExpr(exprStr)
        var current = MathSymbolicAst.simplify(parsed)
        val steps = mutableListOf("Fonction initiale : f($varName) = ${MathSymbolicAst.toPrettyString(current)}")
        for (step in 1..order) {
            current = MathSymbolicAst.differentiate(current, varName)
            steps.add("Dérivée d'ordre $step : ${MathSymbolicAst.toPrettyString(current)}")
        }
        val pretty = MathSymbolicAst.toPrettyString(current)
        val latex = "\\frac{d${if (order > 1) "^{$order}" else ""}}{d$varName${if (order > 1) "^{$order}" else ""}}\\left(${MathSymbolicAst.toLatex(parsed)}\\right) = ${MathSymbolicAst.toLatex(current)}"
        val plot = samplePlot(current, varName, -5.0, 5.0, "Dérivée f'($varName)")
        return MathComputationResult(
            topic = LatexMathTopic.ANALYSE,
            rawInput = rawInput,
            operationLabel = "Dérivation symbolique LaTeX",
            primaryResult = pretty,
            unicodeFormula = "d/d$varName (${MathSymbolicAst.toPrettyString(parsed)}) = $pretty",
            latexFormula = latex,
            steps = steps,
            relatedFormulas = listOf(
                "Expression LaTeX" to "\\frac{d}{d$varName}\\left(${MathSymbolicAst.toLatex(parsed)}\\right)",
                "Résultat LaTeX" to MathSymbolicAst.toLatex(current)
            ),
            plotSeries = plot
        )
    }

    private fun handleIntegrate(args: List<String>, rawInput: String, topic: LatexMathTopic): MathComputationResult {
        val exprStr = stripQuotes(args.getOrNull(0).orEmpty())
        val parsed = MathSymbolicAst.parseExpr(exprStr)

        var varName = "x"
        var lowerExpr: String? = null
        var upperExpr: String? = null

        val arg1 = args.getOrNull(1)?.trim().orEmpty()
        when {
            arg1.startsWith("{") && arg1.endsWith("}") -> {
                val inner = splitTopLevelArgs(arg1.removePrefix("{").removeSuffix("}"))
                varName = inner.getOrNull(0)?.ifBlank { "x" } ?: "x"
                lowerExpr = inner.getOrNull(1)
                upperExpr = inner.getOrNull(2)
            }
            arg1.contains("=") && arg1.contains("..") -> {
                varName = arg1.substringBefore("=").trim().ifBlank { "x" }
                val range = arg1.substringAfter("=")
                lowerExpr = range.substringBefore("..").trim()
                upperExpr = range.substringAfter("..").trim()
            }
            args.size >= 4 -> {
                varName = stripQuotes(arg1).ifBlank { "x" }
                lowerExpr = args[2]
                upperExpr = args[3]
            }
            arg1.isNotBlank() -> {
                varName = stripQuotes(arg1)
            }
        }

        val symbolicAntideriv = MathSymbolicAst.integrate(parsed, varName)
        if (lowerExpr != null && upperExpr != null) {
            val a = MathSymbolicAst.evaluate(MathSymbolicAst.parseExpr(lowerExpr))
            val b = MathSymbolicAst.evaluate(MathSymbolicAst.parseExpr(upperExpr))
            val numVal = if (symbolicAntideriv != null) {
                MathSymbolicAst.evaluate(symbolicAntideriv, mapOf(varName to b)) -
                    MathSymbolicAst.evaluate(symbolicAntideriv, mapOf(varName to a))
            } else {
                MathSymbolicAst.definiteIntegralSimpson(parsed, varName, a, b)
            }
            val formattedVal = MathSymbolicAst.formatNum(numVal)
            val steps = mutableListOf(
                "Intégrande : f($varName) = ${MathSymbolicAst.toPrettyString(parsed)}",
                "Intervalle : [${MathSymbolicAst.formatNum(a)}, ${MathSymbolicAst.formatNum(b)}]"
            )
            if (symbolicAntideriv != null) {
                steps.add("Primitive : F($varName) = ${MathSymbolicAst.toPrettyString(symbolicAntideriv)}")
                steps.add("F(${MathSymbolicAst.formatNum(b)}) - F(${MathSymbolicAst.formatNum(a)}) = $formattedVal")
            } else {
                steps.add("Quadrature numérique de Simpson (N=2000) : $formattedVal")
            }
            val latex = "\\int_{${MathSymbolicAst.formatNum(a)}}^{${MathSymbolicAst.formatNum(b)}} ${MathSymbolicAst.toLatex(parsed)}\\,d$varName = $formattedVal"
            val plot = samplePlot(parsed, varName, min(a, b), max(a, b), "Intégrande f($varName)")
            return MathComputationResult(
                topic = LatexMathTopic.ANALYSE,
                rawInput = rawInput,
                operationLabel = "Intégrale définie LaTeX",
                primaryResult = formattedVal,
                unicodeFormula = "∫[${MathSymbolicAst.formatNum(a)}..${MathSymbolicAst.formatNum(b)}] (${MathSymbolicAst.toPrettyString(parsed)}) d$varName = $formattedVal",
                latexFormula = latex,
                steps = steps,
                relatedFormulas = listOf(
                    "Forme LaTeX" to "\\int_{$a}^{$b} ${MathSymbolicAst.toLatex(parsed)}\\,d$varName",
                    "Primitive" to (symbolicAntideriv?.let { MathSymbolicAst.toLatex(it) } ?: "\\text{Méthode numérique de Simpson}")
                ),
                plotSeries = plot
            )
        } else {
            val resultStr = if (symbolicAntideriv != null) {
                "${MathSymbolicAst.toPrettyString(symbolicAntideriv)} + C"
            } else {
                "Primitive non élémentaire — précisez des bornes [a, b] ou utilisez l'Assistant IA."
            }
            val latex = if (symbolicAntideriv != null) {
                "\\int ${MathSymbolicAst.toLatex(parsed)}\\,d$varName = ${MathSymbolicAst.toLatex(symbolicAntideriv)} + C"
            } else {
                "\\int ${MathSymbolicAst.toLatex(parsed)}\\,d$varName"
            }
            return MathComputationResult(
                topic = LatexMathTopic.ANALYSE,
                rawInput = rawInput,
                operationLabel = "Intégration symbolique LaTeX",
                primaryResult = resultStr,
                unicodeFormula = "∫ (${MathSymbolicAst.toPrettyString(parsed)}) d$varName = $resultStr",
                latexFormula = latex,
                steps = listOf(
                    "Intégrande : ${MathSymbolicAst.toPrettyString(parsed)}",
                    "Primitive trouvée : $resultStr"
                ),
                relatedFormulas = listOf("LaTeX" to latex),
                plotSeries = symbolicAntideriv?.let { samplePlot(it, varName, -5.0, 5.0, "Primitive F($varName)") }
                    ?: samplePlot(parsed, varName, -5.0, 5.0, "Intégrande f($varName)")
            )
        }
    }

    private fun handleFiniteSeries(
        isProd: Boolean,
        body: String,
        varName: String,
        startIdx: Int,
        endIdx: Int,
        rawInput: String,
        topic: LatexMathTopic
    ): MathComputationResult {
        if (endIdx < startIdx || endIdx - startIdx > 10_000) {
            return errorResult(topic, rawInput, "Bornes de série invalides (max 10 000 termes).")
        }
        val parsed = MathSymbolicAst.parseExpr(body)
        var acc = if (isProd) 1.0 else 0.0
        val terms = mutableListOf<Double>()
        for (k in startIdx..endIdx) {
            val v = MathSymbolicAst.evaluate(parsed, mapOf(varName to k.toDouble()))
            terms.add(v)
            acc = if (isProd) acc * v else acc + v
        }
        val formatted = MathSymbolicAst.formatNum(acc)
        val cmd = if (isProd) "\\prod" else "\\sum"
        val latex = "${cmd}_{$varName=$startIdx}^{$endIdx} ${MathSymbolicAst.toLatex(parsed)} = $formatted"
        val plot = PlotSeries(
            title = if (isProd) "Termes du produit" else "Termes de la somme",
            xLabel = varName,
            yLabel = "u_$varName",
            xValues = (startIdx..endIdx).take(50).map { it.toDouble() },
            yValues = terms.take(50)
        )
        return MathComputationResult(
            topic = LatexMathTopic.STATISTIQUES,
            rawInput = rawInput,
            operationLabel = if (isProd) "Produit fini LaTeX" else "Somme finie LaTeX",
            primaryResult = formatted,
            unicodeFormula = "${if (isProd) "∏" else "∑"}($varName=$startIdx..$endIdx) ${MathSymbolicAst.toPrettyString(parsed)} = $formatted",
            latexFormula = latex,
            steps = listOf(
                "Nombre de termes : ${endIdx - startIdx + 1}",
                "Résultat exact : $formatted"
            ),
            relatedFormulas = listOf("Notation LaTeX" to latex),
            plotSeries = plot
        )
    }

    private fun handleTaylor(args: List<String>, rawInput: String, topic: LatexMathTopic): MathComputationResult {
        val exprStr = stripQuotes(args.getOrNull(0).orEmpty())
        val varName = args.getOrNull(1)?.trim()?.ifBlank { "x" } ?: "x"
        val center = args.getOrNull(2)?.let { MathSymbolicAst.evaluate(MathSymbolicAst.parseExpr(it)) } ?: 0.0
        val order = (args.getOrNull(3)?.toDoubleOrNull()?.roundToInt() ?: 4).coerceIn(1, 8)
        val parsed = MathSymbolicAst.parseExpr(exprStr)

        var currentDeriv = MathSymbolicAst.simplify(parsed)
        val termStrings = mutableListOf<String>()
        val latexTerms = mutableListOf<String>()
        val steps = mutableListOf("Développement de Taylor autour de $varName = ${MathSymbolicAst.formatNum(center)} à l'ordre $order")
        var fact = 1.0
        for (n in 0..order) {
            if (n > 0) {
                currentDeriv = MathSymbolicAst.differentiate(currentDeriv, varName)
                fact *= n
            }
            val valAt = MathSymbolicAst.evaluate(currentDeriv, mapOf(varName to center))
            val coeff = valAt / fact
            if (abs(coeff) > 1e-11) {
                val cStr = MathSymbolicAst.formatNum(coeff)
                val baseStr = if (abs(center) < 1e-11) varName else "($varName - ${MathSymbolicAst.formatNum(center)})"
                when (n) {
                    0 -> { termStrings.add(cStr); latexTerms.add(cStr) }
                    1 -> { termStrings.add("$cStr·$baseStr"); latexTerms.add("$cStr $baseStr") }
                    else -> { termStrings.add("$cStr·$baseStr^$n"); latexTerms.add("$cStr $baseStr^{$n}") }
                }
            }
            steps.add("f^($n)(${MathSymbolicAst.formatNum(center)}) / $n! = ${MathSymbolicAst.formatNum(coeff)}")
        }
        val polyPretty = termStrings.joinToString(" + ").replace("+ -", "- ").ifBlank { "0" }
        val polyLatex = latexTerms.joinToString(" + ").replace("+ -", "- ").ifBlank { "0" }
        return MathComputationResult(
            topic = LatexMathTopic.ANALYSE,
            rawInput = rawInput,
            operationLabel = "Série de Taylor (ordre $order)",
            primaryResult = polyPretty,
            unicodeFormula = "${MathSymbolicAst.toPrettyString(parsed)} ≈ $polyPretty",
            latexFormula = "${MathSymbolicAst.toLatex(parsed)} = $polyLatex + \\mathcal{O}\\left($varName^{${order + 1}}\\right)",
            steps = steps,
            relatedFormulas = listOf("Polynôme de Taylor" to polyLatex),
            plotSeries = samplePlot(parsed, varName, center - 3.0, center + 3.0, "Fonction f($varName)")
        )
    }

    private fun isDifferentialEquation(cleaned: String): Boolean {
        val lower = cleaned.lowercase()
        return lower.startsWith("ode(") || lower.startsWith("dsolve(") ||
            (cleaned.contains("=") && (cleaned.contains("y'") || cleaned.contains("y''") || cleaned.contains("\\frac{dy}")))
    }

    private fun handleOde(rawEq: String, topic: LatexMathTopic): MathComputationResult {
        val clean = rawEq.replace("==", "=").replace(" ", "")
        // Support 1st-order: ay' + by = c  | 2nd-order: ay'' + by' + cy = 0
        val secondOrder = clean.contains("y''")
        return if (!secondOrder) {
            val lhs = clean.substringBefore("=")
            val rhs = clean.substringAfter("=", "0").toDoubleOrNull() ?: 0.0
            val a = extractTermCoeff(lhs, "y'") ?: 1.0
            val b = extractTermCoeff(lhs.replace("y'", ""), "y") ?: 0.0
            if (abs(a) < 1e-12) return errorResult(LatexMathTopic.EQUATIONS_DIFF, rawEq, "Coefficient de y' nul.")
            val rate = -b / a
            val eqConst = if (abs(b) > 1e-12) rhs / b else 0.0
            val rStr = MathSymbolicAst.formatNum(rate)
            val solPretty = if (abs(eqConst) < 1e-12) "y(x) = C·e^(${rStr}x)" else "y(x) = C·e^(${rStr}x) + ${MathSymbolicAst.formatNum(eqConst)}"
            val solLatex = if (abs(eqConst) < 1e-12) "y(x) = C e^{${rStr}x}" else "y(x) = C e^{${rStr}x} + ${MathSymbolicAst.formatNum(eqConst)}"
            val plotExpr = Expr.Add(Expr.Call("exp", Expr.Mul(Expr.Num(rate), Expr.Sym("x"))), Expr.Num(eqConst))
            MathComputationResult(
                topic = LatexMathTopic.EQUATIONS_DIFF,
                rawInput = rawEq,
                operationLabel = "Équation différentielle d'ordre 1",
                primaryResult = solPretty,
                unicodeFormula = solPretty,
                latexFormula = solLatex,
                steps = listOf(
                    "Forme standard : y' + (${MathSymbolicAst.formatNum(b / a)})y = ${MathSymbolicAst.formatNum(rhs / a)}",
                    "Équation caractéristique : r + (${MathSymbolicAst.formatNum(b / a)}) = 0 ⇒ r = $rStr",
                    "Solution générale : $solPretty"
                ),
                relatedFormulas = listOf("Solution LaTeX (C=1)" to solLatex),
                plotSeries = samplePlot(plotExpr, "x", 0.0, 5.0, "Solution y(x) pour C=1")
            )
        } else {
            val lhs = clean.substringBefore("=")
            val a = extractTermCoeff(lhs, "y''") ?: 1.0
            val withoutY2 = lhs.replace("y''", "")
            val b = extractTermCoeff(withoutY2, "y'") ?: 0.0
            val c = extractTermCoeff(withoutY2.replace("y'", ""), "y") ?: 0.0
            val disc = b * b - 4.0 * a * c
            val (solPretty, solLatex, steps) = when {
                disc > 1e-11 -> {
                    val r1 = (-b + sqrt(disc)) / (2.0 * a)
                    val r2 = (-b - sqrt(disc)) / (2.0 * a)
                    val f1 = MathSymbolicAst.formatNum(r1)
                    val f2 = MathSymbolicAst.formatNum(r2)
                    Triple(
                        "y(x) = C₁·e^(${f1}x) + C₂·e^(${f2}x)",
                        "y(x) = C_1 e^{${f1}x} + C_2 e^{${f2}x}",
                        listOf("Polynôme caractéristique : ${MathSymbolicAst.formatNum(a)}r² + ${MathSymbolicAst.formatNum(b)}r + ${MathSymbolicAst.formatNum(c)} = 0", "Δ = ${MathSymbolicAst.formatNum(disc)} > 0 ⇒ r₁ = $f1, r₂ = $f2")
                    )
                }
                abs(disc) <= 1e-11 -> {
                    val r0 = MathSymbolicAst.formatNum(-b / (2.0 * a))
                    Triple(
                        "y(x) = (C₁ + C₂·x)·e^(${r0}x)",
                        "y(x) = (C_1 + C_2 x) e^{${r0}x}",
                        listOf("Δ = 0 ⇒ racine double r₀ = $r0")
                    )
                }
                else -> {
                    val alpha = MathSymbolicAst.formatNum(-b / (2.0 * a))
                    val beta = MathSymbolicAst.formatNum(sqrt(-disc) / (2.0 * a))
                    Triple(
                        "y(x) = e^(${alpha}x)·(C₁·cos(${beta}x) + C₂·sin(${beta}x))",
                        "y(x) = e^{${alpha}x}\\left(C_1 \\cos(${beta}x) + C_2 \\sin(${beta}x)\\right)",
                        listOf("Δ = ${MathSymbolicAst.formatNum(disc)} < 0 ⇒ r = $alpha ± ${beta}i")
                    )
                }
            }
            MathComputationResult(
                topic = LatexMathTopic.EQUATIONS_DIFF,
                rawInput = rawEq,
                operationLabel = "Équation différentielle linéaire d'ordre 2",
                primaryResult = solPretty,
                unicodeFormula = solPretty,
                latexFormula = solLatex,
                steps = steps,
                relatedFormulas = listOf("Solution LaTeX" to solLatex)
            )
        }
    }

    private fun extractTermCoeff(exprNoSpaces: String, token: String): Double? {
        val escaped = Regex.escape(token)
        val regex = Regex("""([+-]?\d*\.?\d*)\*?""" + escaped)
        val match = regex.find(exprNoSpaces) ?: return null
        val raw = match.groupValues[1]
        return when (raw) {
            "", "+" -> 1.0
            "-" -> -1.0
            else -> raw.toDoubleOrNull()
        }
    }

    private fun handleSolveCommand(args: List<String>, rawInput: String, topic: LatexMathTopic): MathComputationResult {
        if (args.size == 2 && isMatrixLiteral(args[0])) {
            return handleLinearSystem(args[0], args[1], rawInput, topic)
        }
        val rawEq = args.getOrNull(0).orEmpty().removePrefix("[").removeSuffix("]")
        val varName = args.getOrNull(1)?.removePrefix("[")?.removeSuffix("]")?.trim()?.ifBlank { "x" } ?: "x"
        return solveEquationInternal(rawEq, varName, rawInput, topic)
    }

    private fun handleRoots(args: List<String>, rawInput: String, topic: LatexMathTopic): MathComputationResult {
        val coeffs = parseNumericVector(args.firstOrNull().orEmpty())
        if (coeffs.isEmpty()) return errorResult(topic, rawInput, "Vecteur de coefficients invalide.")
        val rootsList = solvePolynomialCoeffs(coeffs)
        val joined = rootsList.joinToString(", ")
        return MathComputationResult(
            topic = LatexMathTopic.ALGEBRE,
            rawInput = rawInput,
            operationLabel = "Racines polynomiales LaTeX",
            primaryResult = joined,
            unicodeFormula = "Racines = { $joined }",
            latexFormula = "x \\in \\left\\{ $joined \\right\\}",
            steps = listOf(
                "Coefficients (degré ${coeffs.size - 1}) : ${coeffs.joinToString(", ") { MathSymbolicAst.formatNum(it) }}",
                "Racines calculées : $joined"
            )
        )
    }

    private fun handleFactor(args: List<String>, rawInput: String, topic: LatexMathTopic): MathComputationResult {
        val exprStr = args.firstOrNull().orEmpty()
        val parsed = MathSymbolicAst.parseExpr(exprStr)
        val expanded = MathSymbolicAst.expand(parsed)
        val varName = findFirstVar(expanded) ?: "x"
        val poly = extractQuadraticCoeffs(expanded, varName)
        val factoredStr = if (poly != null && abs(poly.first) > 1e-12) {
            val (a, b, c) = poly
            val disc = b * b - 4 * a * c
            if (disc >= 0) {
                val r1 = (-b + sqrt(disc)) / (2 * a)
                val r2 = (-b - sqrt(disc)) / (2 * a)
                val lead = if (abs(a - 1.0) < 1e-9) "" else "${MathSymbolicAst.formatNum(a)}·"
                "$lead${linearFactorStr(varName, r1)}·${linearFactorStr(varName, r2)}"
            } else MathSymbolicAst.toPrettyString(expanded)
        } else {
            MathSymbolicAst.toPrettyString(expanded)
        }
        return MathComputationResult(
            topic = LatexMathTopic.ALGEBRE,
            rawInput = rawInput,
            operationLabel = "Factorisation algébrique",
            primaryResult = factoredStr,
            unicodeFormula = "${MathSymbolicAst.toPrettyString(parsed)} = $factoredStr",
            latexFormula = "${MathSymbolicAst.toLatex(parsed)} = ${factoredStr.replace("·", " ")}",
            steps = listOf("Forme développée : ${MathSymbolicAst.toPrettyString(expanded)}", "Factorisation : $factoredStr"),
            plotSeries = samplePlot(expanded, varName, -6.0, 6.0, "Polynôme f($varName)")
        )
    }

    private fun handleExpand(args: List<String>, rawInput: String, topic: LatexMathTopic): MathComputationResult {
        val exprStr = args.firstOrNull().orEmpty()
        val parsed = MathSymbolicAst.parseExpr(exprStr)
        val expanded = MathSymbolicAst.expand(parsed)
        val pretty = MathSymbolicAst.toPrettyString(expanded)
        val varName = findFirstVar(expanded)
        return MathComputationResult(
            topic = LatexMathTopic.ALGEBRE,
            rawInput = rawInput,
            operationLabel = "Développement algébrique",
            primaryResult = pretty,
            unicodeFormula = "${MathSymbolicAst.toPrettyString(parsed)} = $pretty",
            latexFormula = "${MathSymbolicAst.toLatex(parsed)} = ${MathSymbolicAst.toLatex(expanded)}",
            steps = listOf("Distributivité & identités remarquables : $pretty"),
            plotSeries = varName?.let { samplePlot(expanded, it, -5.0, 5.0, "Courbe f($it)") }
        )
    }

    private fun handleSimplify(args: List<String>, rawInput: String, topic: LatexMathTopic): MathComputationResult {
        val exprStr = args.firstOrNull().orEmpty()
        val parsed = MathSymbolicAst.parseExpr(exprStr)
        val simplified = MathSymbolicAst.simplify(MathSymbolicAst.expand(parsed))
        val pretty = MathSymbolicAst.toPrettyString(simplified)
        return MathComputationResult(
            topic = LatexMathTopic.ALGEBRE,
            rawInput = rawInput,
            operationLabel = "Simplification symbolique",
            primaryResult = pretty,
            unicodeFormula = "${MathSymbolicAst.toPrettyString(parsed)} = $pretty",
            latexFormula = MathSymbolicAst.toLatex(simplified),
            steps = listOf("Expression simplifiée : $pretty")
        )
    }

    private fun handleLimit(args: List<String>, rawInput: String, topic: LatexMathTopic): MathComputationResult {
        val exprStr = args.getOrNull(0).orEmpty()
        val parsed = MathSymbolicAst.parseExpr(exprStr)
        val arg1 = args.getOrNull(1).orEmpty().trim()
        val (varName, targetExpr) = when {
            arg1.contains("->") -> arg1.substringBefore("->").trim() to arg1.substringAfter("->").trim()
            arg1.contains("=") -> arg1.substringBefore("=").trim() to arg1.substringAfter("=").trim()
            args.size >= 3 -> stripQuotes(arg1) to args[2].trim()
            else -> "x" to "0"
        }
        val cleanTarget = targetExpr.removePrefix("{").removeSuffix("}").trim()
        val value = when {
            cleanTarget.equals("Infinity", true) || cleanTarget.equals("inf", true) ||
                cleanTarget == "\\infty" || cleanTarget == "∞" ->
                MathSymbolicAst.evaluate(parsed, mapOf(varName to 1e6))
            cleanTarget.equals("-Infinity", true) || cleanTarget.equals("-inf", true) ||
                cleanTarget == "-\\infty" ->
                MathSymbolicAst.evaluate(parsed, mapOf(varName to -1e6))
            else -> {
                val t = MathSymbolicAst.evaluate(MathSymbolicAst.parseExpr(cleanTarget))
                val direct = runCatching { MathSymbolicAst.evaluate(parsed, mapOf(varName to t)) }.getOrNull()
                if (direct != null && direct.isFinite()) direct
                else {
                    val left = MathSymbolicAst.evaluate(parsed, mapOf(varName to (t - 1e-6)))
                    val right = MathSymbolicAst.evaluate(parsed, mapOf(varName to (t + 1e-6)))
                    (left + right) / 2.0
                }
            }
        }
        val formatted = MathSymbolicAst.formatNum(value)
        return MathComputationResult(
            topic = LatexMathTopic.ANALYSE,
            rawInput = rawInput,
            operationLabel = "Limite mathématique LaTeX",
            primaryResult = formatted,
            unicodeFormula = "lim($varName → $cleanTarget) ${MathSymbolicAst.toPrettyString(parsed)} = $formatted",
            latexFormula = "\\lim_{$varName \\to $cleanTarget} ${MathSymbolicAst.toLatex(parsed)} = $formatted",
            steps = listOf("Évaluation asymptotique en $varName → $cleanTarget : $formatted")
        )
    }

    private fun handleMatrixUnary(op: String, args: List<String>, rawInput: String, topic: LatexMathTopic): MathComputationResult {
        val mat = parseMatrix(args.firstOrNull().orEmpty())
            ?: return errorResult(topic, rawInput, "Format matriciel invalide. Exemple : \\begin{pmatrix}4 & 2 \\\\ 1 & 3\\end{pmatrix} ou [[4,2],[1,3]]")
        return when (op) {
            "det" -> {
                val det = determinant(mat)
                val f = MathSymbolicAst.formatNum(det)
                MathComputationResult(
                    topic = LatexMathTopic.ALGEBRE_LINEAIRE,
                    rawInput = rawInput,
                    operationLabel = "Déterminant matriciel LaTeX",
                    primaryResult = f,
                    unicodeFormula = "det(A) = $f",
                    latexFormula = "\\det${matrixToLatex(mat)} = $f",
                    steps = listOf("Matrice ${mat.size}×${mat.first().size} analysée", "det(A) = $f")
                )
            }
            "inv" -> {
                val inv = inverseMatrix(mat)
                    ?: return errorResult(topic, rawInput, "Matrice singulière (det = 0) ou non carrée.")
                val formatted = formatMatrixText(inv)
                MathComputationResult(
                    topic = LatexMathTopic.ALGEBRE_LINEAIRE,
                    rawInput = rawInput,
                    operationLabel = "Matrice inverse LaTeX",
                    primaryResult = formatted,
                    unicodeFormula = "A⁻¹ = $formatted",
                    latexFormula = "${matrixToLatex(mat)}^{-1} = ${matrixToLatex(inv)}",
                    steps = listOf("Pivot de Gauss-Jordan exécuté", "A⁻¹ = $formatted")
                )
            }
            "eig" -> {
                val eigs = eigenvalues2x2OrDiagonal(mat)
                    ?: return errorResult(topic, rawInput, "Calcul direct des valeurs propres supporté pour 1×1, 2×2 ou matrices triangulaires.")
                val joined = eigs.joinToString(", ")
                MathComputationResult(
                    topic = LatexMathTopic.ALGEBRE_LINEAIRE,
                    rawInput = rawInput,
                    operationLabel = "Valeurs propres LaTeX",
                    primaryResult = joined,
                    unicodeFormula = "λ(A) = { $joined }",
                    latexFormula = "\\operatorname{spec}${matrixToLatex(mat)} = \\left\\{ $joined \\right\\}",
                    steps = listOf("Polynôme caractéristique det(A - λI) = 0 résolu", "Valeurs propres : $joined")
                )
            }
            "transpose" -> {
                val tr = List(mat[0].size) { c -> List(mat.size) { r -> mat[r][c] } }
                val formatted = formatMatrixText(tr)
                MathComputationResult(
                    topic = LatexMathTopic.ALGEBRE_LINEAIRE,
                    rawInput = rawInput,
                    operationLabel = "Transposée matricielle LaTeX",
                    primaryResult = formatted,
                    unicodeFormula = "Aᵀ = $formatted",
                    latexFormula = "${matrixToLatex(mat)}^T = ${matrixToLatex(tr)}",
                    steps = listOf("Transposition lignes ↔ colonnes : $formatted")
                )
            }
            "trace" -> {
                val n = min(mat.size, mat[0].size)
                val trVal = (0 until n).sumOf { mat[it][it] }
                val f = MathSymbolicAst.formatNum(trVal)
                MathComputationResult(
                    topic = LatexMathTopic.ALGEBRE_LINEAIRE,
                    rawInput = rawInput,
                    operationLabel = "Trace matricielle LaTeX",
                    primaryResult = f,
                    unicodeFormula = "tr(A) = $f",
                    latexFormula = "\\operatorname{tr}${matrixToLatex(mat)} = $f",
                    steps = listOf("Somme de la diagonale principale = $f")
                )
            }
            else -> errorResult(topic, rawInput, "Opération matricielle inconnue")
        }
    }

    private fun handleLinearSystem(matStr: String, vecStr: String, rawInput: String, topic: LatexMathTopic): MathComputationResult {
        val mat = parseMatrix(matStr) ?: return errorResult(topic, rawInput, "Matrice A invalide")
        val vec = parseNumericVector(vecStr)
        if (mat.size != vec.size) return errorResult(topic, rawInput, "Dimensions de A et b incompatibles")
        val inv = inverseMatrix(mat) ?: return errorResult(topic, rawInput, "Système singulier (det(A) = 0)")
        val sol = inv.map { row -> row.zip(vec).sumOf { (a, b) -> a * b } }
        val solStr = "[${sol.joinToString(", ") { MathSymbolicAst.formatNum(it) }}]"
        val solLatex = "\\begin{pmatrix}${sol.joinToString(" \\\\ ") { MathSymbolicAst.formatNum(it) }}\\end{pmatrix}"
        return MathComputationResult(
            topic = LatexMathTopic.ALGEBRE_LINEAIRE,
            rawInput = rawInput,
            operationLabel = "Système linéaire Ax = b",
            primaryResult = solStr,
            unicodeFormula = "x = A⁻¹b = $solStr",
            latexFormula = "${matrixToLatex(mat)} x = ${matrixToLatex(vec.map { listOf(it) })} \\implies x = $solLatex",
            steps = listOf("Inversion par pivot de Gauss-Jordan sur A", "Solution x = $solStr")
        )
    }

    private fun handleStats(op: String, args: List<String>, rawInput: String, topic: LatexMathTopic): MathComputationResult {
        val vec = parseNumericVector(if (args.size == 1) args[0] else args.joinToString(","))
        if (vec.isEmpty()) return errorResult(topic, rawInput, "Vecteur de données vide ou invalide.")
        val sorted = vec.sorted()
        val meanVal = vec.average()
        val resVal = when (op) {
            "mean" -> meanVal
            "median" -> {
                val m = sorted.size / 2
                if (sorted.size % 2 == 0) (sorted[m - 1] + sorted[m]) / 2.0 else sorted[m]
            }
            "var" -> if (vec.size > 1) vec.sumOf { (it - meanVal).pow(2) } / (vec.size - 1) else 0.0
            "sd" -> if (vec.size > 1) sqrt(vec.sumOf { (it - meanVal).pow(2) } / (vec.size - 1)) else 0.0
            "sum" -> vec.sum()
            "prod" -> vec.fold(1.0) { acc, v -> acc * v }
            "min" -> sorted.first()
            "max" -> sorted.last()
            else -> meanVal
        }
        val f = MathSymbolicAst.formatNum(resVal)
        val plot = PlotSeries(
            title = "Distribution (${op.uppercase()} = $f)",
            xLabel = "Indice",
            yLabel = "Valeur",
            xValues = vec.indices.map { (it + 1).toDouble() },
            yValues = vec
        )
        return MathComputationResult(
            topic = LatexMathTopic.STATISTIQUES,
            rawInput = rawInput,
            operationLabel = "Statistiques ($op)",
            primaryResult = f,
            unicodeFormula = "$op(${vec.size} valeurs) = $f",
            latexFormula = "\\mathrm{$op}(x) = $f \\quad (\\bar{x} = ${MathSymbolicAst.formatNum(meanVal)})",
            steps = listOf(
                "Échantillon n = ${vec.size} : ${vec.joinToString(", ") { MathSymbolicAst.formatNum(it) }}",
                "Moyenne = ${MathSymbolicAst.formatNum(meanVal)}",
                "Résultat ($op) = $f"
            ),
            plotSeries = plot
        )
    }

    private fun handleLinspace(args: List<String>, rawInput: String, topic: LatexMathTopic): MathComputationResult {
        val a = MathSymbolicAst.evaluate(MathSymbolicAst.parseExpr(args.getOrNull(0) ?: "0"))
        val b = MathSymbolicAst.evaluate(MathSymbolicAst.parseExpr(args.getOrNull(1) ?: "1"))
        val n = (args.getOrNull(2)?.toDoubleOrNull()?.roundToInt() ?: 5).coerceIn(2, 100)
        val pts = (0 until n).map { i -> a + (b - a) * i / (n - 1) }
        val text = "[${pts.joinToString(", ") { MathSymbolicAst.formatNum(it) }}]"
        return MathComputationResult(
            topic = topic,
            rawInput = rawInput,
            operationLabel = "Discrétisation linspace",
            primaryResult = text,
            unicodeFormula = text,
            latexFormula = "\\left[${pts.joinToString(", ") { MathSymbolicAst.formatNum(it) }}\\right]",
            steps = listOf("$n points uniformément espacés sur [${MathSymbolicAst.formatNum(a)}, ${MathSymbolicAst.formatNum(b)}]")
        )
    }

    private fun handleSeq(args: List<String>, rawInput: String, topic: LatexMathTopic): MathComputationResult {
        val a = MathSymbolicAst.evaluate(MathSymbolicAst.parseExpr(args.getOrNull(0) ?: "1"))
        val b = MathSymbolicAst.evaluate(MathSymbolicAst.parseExpr(args.getOrNull(1) ?: "5"))
        val step = args.getOrNull(2)?.let { MathSymbolicAst.evaluate(MathSymbolicAst.parseExpr(it)) } ?: 1.0
        val pts = mutableListOf<Double>()
        var cur = a
        var guard = 0
        while (((step > 0 && cur <= b + 1e-9) || (step < 0 && cur >= b - 1e-9)) && guard < 100) {
            pts.add(cur)
            cur += step
            guard++
        }
        val text = "[${pts.joinToString(", ") { MathSymbolicAst.formatNum(it) }}]"
        return MathComputationResult(
            topic = topic,
            rawInput = rawInput,
            operationLabel = "Séquence numérique",
            primaryResult = text,
            unicodeFormula = text,
            latexFormula = "\\left[${pts.joinToString(", ") { MathSymbolicAst.formatNum(it) }}\\right]",
            steps = listOf("${pts.size} éléments générés")
        )
    }

    private fun handleNumericEval(inner: String, rawInput: String, topic: LatexMathTopic): MathComputationResult {
        val expr = MathSymbolicAst.parseExpr(inner)
        val valNum = MathSymbolicAst.evaluate(expr)
        val f = MathSymbolicAst.formatNum(valNum)
        return MathComputationResult(
            topic = topic,
            rawInput = rawInput,
            operationLabel = "Évaluation numérique LaTeX",
            primaryResult = f,
            unicodeFormula = "${MathSymbolicAst.toPrettyString(expr)} ≈ $f",
            latexFormula = "${MathSymbolicAst.toLatex(expr)} \\approx $f",
            steps = listOf("Valeur exacte / approchée : $f")
        )
    }

    private fun handleDirectExpressionOrEquation(cleaned: String, topic: LatexMathTopic): MathComputationResult {
        val eqNormalized = cleaned.replace("==", "=")
        if (eqNormalized.contains("=") && !eqNormalized.startsWith("=")) {
            val v = findFirstVar(MathSymbolicAst.parseExpr(eqNormalized.replace("=", "-(") + ")")) ?: "x"
            return solveEquationInternal(eqNormalized, v, cleaned, topic)
        }
        val expr = MathSymbolicAst.parseExpr(cleaned)
        val simplified = MathSymbolicAst.simplify(expr)
        return try {
            val numeric = MathSymbolicAst.evaluate(simplified)
            val f = MathSymbolicAst.formatNum(numeric)
            val pretty = MathSymbolicAst.toPrettyString(expr)
            MathComputationResult(
                topic = topic,
                rawInput = cleaned,
                operationLabel = "Calcul numérique LaTeX",
                primaryResult = f,
                unicodeFormula = "$pretty = $f",
                latexFormula = "${MathSymbolicAst.toLatex(expr)} = $f",
                steps = listOf("Simplification : ${MathSymbolicAst.toPrettyString(simplified)}", "Résultat : $f")
            )
        } catch (_: Exception) {
            val expanded = MathSymbolicAst.simplify(MathSymbolicAst.expand(simplified))
            val pretty = MathSymbolicAst.toPrettyString(expanded)
            val varName = findFirstVar(expanded)
            MathComputationResult(
                topic = topic,
                rawInput = cleaned,
                operationLabel = "Expression symbolique LaTeX",
                primaryResult = pretty,
                unicodeFormula = "${MathSymbolicAst.toPrettyString(expr)} = $pretty",
                latexFormula = MathSymbolicAst.toLatex(expanded),
                steps = listOf("Forme symbolique simplifiée : $pretty"),
                plotSeries = varName?.let { samplePlot(expanded, it, -5.0, 5.0, "Courbe f($it)") }
            )
        }
    }

    private fun solveEquationInternal(rawEq: String, varName: String, rawInput: String, topic: LatexMathTopic): MathComputationResult {
        val parts = rawEq.replace("==", "=").split("=")
        val lhsStr = parts.getOrNull(0)?.trim().orEmpty()
        val rhsStr = parts.getOrNull(1)?.trim().orEmpty().ifBlank { "0" }
        val diffExpr = MathSymbolicAst.expand(
            Expr.Sub(MathSymbolicAst.parseExpr(lhsStr), MathSymbolicAst.parseExpr(rhsStr))
        )
        val quad = extractQuadraticCoeffs(diffExpr, varName)
        val plot = samplePlot(diffExpr, varName, -6.0, 6.0, "f($varName) = 0")
        if (quad != null) {
            val (a, b, c) = quad
            val roots = if (abs(a) < 1e-12) {
                if (abs(b) < 1e-12) listOf("Aucune solution (ou infinité)")
                else listOf(MathSymbolicAst.formatNum(-c / b))
            } else {
                solvePolynomialCoeffs(listOf(a, b, c))
            }
            val joined = roots.joinToString(", ")
            return MathComputationResult(
                topic = LatexMathTopic.ALGEBRE,
                rawInput = rawInput,
                operationLabel = "Résolution d'équation LaTeX",
                primaryResult = "$varName = $joined",
                unicodeFormula = "$lhsStr = $rhsStr  ⇒  $varName ∈ { $joined }",
                latexFormula = "${MathSymbolicAst.toLatex(diffExpr)} = 0 \\implies $varName \\in \\left\\{ $joined \\right\\}",
                steps = listOf(
                    "Forme standard : ${MathSymbolicAst.toPrettyString(diffExpr)} = 0",
                    if (abs(a) > 1e-12) "Discriminant Δ = ${MathSymbolicAst.formatNum(b * b - 4 * a * c)}" else "Équation linéaire",
                    "Solutions : $varName = $joined"
                ),
                plotSeries = plot
            )
        }

        val found = mutableListOf<Double>()
        for (seed in listOf(0.0, 1.0, -1.0, 2.0, -2.0, 5.0, -5.0, 0.5)) {
            var x = seed
            var converged = false
            for (iter in 0 until 40) {
                val fx = runCatching { MathSymbolicAst.evaluate(diffExpr, mapOf(varName to x)) }.getOrNull() ?: break
                if (abs(fx) < 1e-9) { converged = true; break }
                val h = 1e-6
                val dfx = runCatching {
                    (MathSymbolicAst.evaluate(diffExpr, mapOf(varName to x + h)) -
                        MathSymbolicAst.evaluate(diffExpr, mapOf(varName to x - h))) / (2 * h)
                }.getOrNull() ?: break
                if (abs(dfx) < 1e-12) break
                x -= fx / dfx
            }
            if (converged && x.isFinite() && found.none { abs(it - x) < 1e-4 }) {
                found.add(x)
            }
        }
        val solStr = if (found.isNotEmpty()) {
            found.sorted().joinToString(", ") { MathSymbolicAst.formatNum(it) }
        } else {
            "Aucune racine réelle trouvée par Newton-Raphson (utilisez l'Assistant IA)."
        }
        return MathComputationResult(
            topic = LatexMathTopic.ALGEBRE,
            rawInput = rawInput,
            operationLabel = "Résolution numérique Newton-Raphson",
            primaryResult = "$varName ≈ $solStr",
            unicodeFormula = "$lhsStr = $rhsStr  ⇒  $varName ≈ $solStr",
            latexFormula = "${MathSymbolicAst.toLatex(diffExpr)} = 0 \\implies $varName \\approx $solStr",
            steps = listOf("f($varName) = ${MathSymbolicAst.toPrettyString(diffExpr)}", "Itérations Newton-Raphson : $varName ≈ $solStr"),
            plotSeries = plot
        )
    }

    private fun samplePlot(expr: Expr, varName: String, minX: Double, maxX: Double, title: String): PlotSeries? {
        val lo = min(minX, maxX)
        val hi = max(minX, maxX).let { if (abs(it - lo) < 1e-6) lo + 5.0 else it }
        val steps = 41
        val xs = mutableListOf<Double>()
        val ys = mutableListOf<Double>()
        for (i in 0 until steps) {
            val x = lo + (hi - lo) * i / (steps - 1)
            val y = runCatching { MathSymbolicAst.evaluate(expr, mapOf(varName to x)) }.getOrNull()
            if (y != null && y.isFinite() && abs(y) < 1e6) {
                xs.add(x)
                ys.add(y)
            }
        }
        return if (xs.size >= 3) PlotSeries(title = title, xLabel = varName, yLabel = "y", xValues = xs, yValues = ys) else null
    }

    private fun extractQuadraticCoeffs(expr: Expr, v: String): Triple<Double, Double, Double>? {
        val c0 = runCatching { MathSymbolicAst.evaluate(expr, mapOf(v to 0.0)) }.getOrNull() ?: return null
        val p1 = runCatching { MathSymbolicAst.evaluate(expr, mapOf(v to 1.0)) }.getOrNull() ?: return null
        val m1 = runCatching { MathSymbolicAst.evaluate(expr, mapOf(v to -1.0)) }.getOrNull() ?: return null
        val p2 = runCatching { MathSymbolicAst.evaluate(expr, mapOf(v to 2.0)) }.getOrNull() ?: return null
        val a = (p1 + m1 - 2.0 * c0) / 2.0
        val b = (p1 - m1) / 2.0
        val expected2 = 4.0 * a + 2.0 * b + c0
        if (abs(expected2 - p2) > 1e-6) return null
        val p35 = runCatching { MathSymbolicAst.evaluate(expr, mapOf(v to 3.5)) }.getOrNull() ?: return null
        if (abs((12.25 * a + 3.5 * b + c0) - p35) > 1e-6) return null
        return Triple(a, b, c0)
    }

    private fun solvePolynomialCoeffs(coeffsDesc: List<Double>): List<String> {
        val c = coeffsDesc.dropWhile { abs(it) < 1e-12 }
        return when (c.size) {
            0, 1 -> emptyList()
            2 -> listOf(MathSymbolicAst.formatNum(-c[1] / c[0]))
            3 -> {
                val (a, b, d) = Triple(c[0], c[1], c[2])
                val disc = b * b - 4 * a * d
                if (disc >= 0) {
                    val r1 = (-b + sqrt(disc)) / (2 * a)
                    val r2 = (-b - sqrt(disc)) / (2 * a)
                    if (abs(disc) < 1e-11) listOf(MathSymbolicAst.formatNum(r1))
                    else listOf(MathSymbolicAst.formatNum(r2), MathSymbolicAst.formatNum(r1))
                } else {
                    val re = MathSymbolicAst.formatNum(-b / (2 * a))
                    val im = MathSymbolicAst.formatNum(sqrt(-disc) / (2 * a))
                    listOf("$re + ${im}i", "$re - ${im}i")
                }
            }
            else -> listOf("Degré ${c.size - 1} — utilisez l'Assistant IA")
        }
    }

    private fun parseMatrix(raw: String): List<List<Double>>? {
        var s = raw.trim()
        // LaTeX \begin{pmatrix} ... \end{pmatrix} or \begin{bmatrix} ... \end{bmatrix}
        val envMatch = Regex("""\\begin\{[pb]matrix\}(.*?)\\end\{[pb]matrix\}""", RegexOption.DOT_MATCHES_ALL).find(s)
        if (envMatch != null) {
            val inner = envMatch.groupValues[1].trim()
            val rows = inner.split("\\\\").filter { it.isNotBlank() }.map { row ->
                row.trim().split("&").mapNotNull { cell ->
                    runCatching { MathSymbolicAst.evaluate(MathSymbolicAst.parseExpr(cell.trim())) }.getOrNull()
                }
            }
            return rows.takeIf { it.isNotEmpty() && it.all { r -> r.size == it[0].size && r.isNotEmpty() } }
        }
        if (s.startsWith("[") && s.endsWith("]") && s.contains(";")) {
            val body = s.removePrefix("[").removeSuffix("]")
            val rows = body.split(";").map { row ->
                row.trim().split(Regex("""[\s,]+""")).filter { it.isNotBlank() }
                    .mapNotNull { it.toDoubleOrNull() }
            }
            return rows.takeIf { it.isNotEmpty() && it.all { r -> r.size == it[0].size && r.isNotEmpty() } }
        }
        s = s.replace("{", "[").replace("}", "]")
        if (s.startsWith("[[") && s.endsWith("]]")) {
            val rowMatches = Regex("""\[([^\[\]]+)]""").findAll(s).toList()
            val rows = rowMatches.map { m ->
                m.groupValues[1].split(Regex("""[,\s]+""")).filter { it.isNotBlank() }
                    .mapNotNull { it.toDoubleOrNull() }
            }
            return rows.takeIf { it.isNotEmpty() && it.all { r -> r.size == it[0].size && r.isNotEmpty() } }
        }
        return null
    }

    private fun isMatrixLiteral(raw: String): Boolean = parseMatrix(raw) != null

    private fun parseNumericVector(raw: String): List<Double> {
        val s = raw.trim().removePrefix("[").removeSuffix("]").removePrefix("{").removeSuffix("}")
        return s.split(Regex("""[,;\s]+""")).filter { it.isNotBlank() }
            .mapNotNull { token -> runCatching { MathSymbolicAst.evaluate(MathSymbolicAst.parseExpr(token)) }.getOrNull() }
    }

    private fun matrixToLiteralString(m: List<List<Double>>): String =
        "[[" + m.joinToString("],[") { r -> r.joinToString(",") { MathSymbolicAst.formatNum(it) } } + "]]"

    private fun determinant(m: List<List<Double>>): Double {
        val n = m.size
        require(n == m[0].size) { "La matrice doit être carrée" }
        if (n == 1) return m[0][0]
        if (n == 2) return m[0][0] * m[1][1] - m[0][1] * m[1][0]
        val a = Array(n) { r -> DoubleArray(n) { c -> m[r][c] } }
        var det = 1.0
        for (i in 0 until n) {
            var pivot = i
            for (r in i + 1 until n) {
                if (abs(a[r][i]) > abs(a[pivot][i])) pivot = r
            }
            if (abs(a[pivot][i]) < 1e-12) return 0.0
            if (pivot != i) {
                val tmp = a[i]; a[i] = a[pivot]; a[pivot] = tmp
                det = -det
            }
            det *= a[i][i]
            for (r in i + 1 until n) {
                val factor = a[r][i] / a[i][i]
                for (c in i until n) a[r][c] -= factor * a[i][c]
            }
        }
        return det
    }

    private fun inverseMatrix(m: List<List<Double>>): List<List<Double>>? {
        val n = m.size
        if (n != m[0].size) return null
        val aug = Array(n) { r ->
            DoubleArray(2 * n) { c ->
                if (c < n) m[r][c] else if (c - n == r) 1.0 else 0.0
            }
        }
        for (i in 0 until n) {
            var pivot = i
            for (r in i + 1 until n) {
                if (abs(aug[r][i]) > abs(aug[pivot][i])) pivot = r
            }
            if (abs(aug[pivot][i]) < 1e-12) return null
            if (pivot != i) {
                val tmp = aug[i]; aug[i] = aug[pivot]; aug[pivot] = tmp
            }
            val div = aug[i][i]
            for (c in 0 until 2 * n) aug[i][c] /= div
            for (r in 0 until n) {
                if (r != i) {
                    val f = aug[r][i]
                    for (c in 0 until 2 * n) aug[r][c] -= f * aug[i][c]
                }
            }
        }
        return List(n) { r -> List(n) { c -> aug[r][n + c] } }
    }

    private fun eigenvalues2x2OrDiagonal(m: List<List<Double>>): List<String>? {
        val n = m.size
        if (n != m[0].size) return null
        if (n == 1) return listOf(MathSymbolicAst.formatNum(m[0][0]))
        if (n == 2) {
            val tr = m[0][0] + m[1][1]
            val det = m[0][0] * m[1][1] - m[0][1] * m[1][0]
            return solvePolynomialCoeffs(listOf(1.0, -tr, det))
        }
        val isUpperOrLower = (0 until n).all { r ->
            (0 until n).all { c -> r <= c || abs(m[r][c]) < 1e-11 }
        } || (0 until n).all { r ->
            (0 until n).all { c -> r >= c || abs(m[r][c]) < 1e-11 }
        }
        return if (isUpperOrLower) (0 until n).map { MathSymbolicAst.formatNum(m[it][it]) } else null
    }

    private fun formatMatrixText(m: List<List<Double>>): String =
        "[${m.joinToString("; ") { row -> row.joinToString(" ") { MathSymbolicAst.formatNum(it) } }}]"

    private fun matrixToLatex(m: List<List<Double>>): String =
        "\\begin{pmatrix}${m.joinToString(" \\\\ ") { r -> r.joinToString(" & ") { MathSymbolicAst.formatNum(it) } }}\\end{pmatrix}"

    private fun parseVarAndOrder(arg1: String?, arg2: String?): Pair<String, Int> {
        val a1 = stripQuotes(arg1.orEmpty()).trim()
        val v = a1.ifBlank { "x" }
        val ord = arg2?.trim()?.toIntOrNull()?.coerceIn(1, 5) ?: 1
        return v to ord
    }

    private fun stripQuotes(s: String): String =
        s.trim().removeSurrounding("'").removeSurrounding("\"")

    private fun findFirstVar(e: Expr): String? = when (e) {
        is Expr.Num -> null
        is Expr.Sym -> if (e.name.lowercase() in setOf("pi", "e")) null else e.name
        is Expr.Neg -> findFirstVar(e.inner)
        is Expr.Add -> findFirstVar(e.left) ?: findFirstVar(e.right)
        is Expr.Sub -> findFirstVar(e.left) ?: findFirstVar(e.right)
        is Expr.Mul -> findFirstVar(e.left) ?: findFirstVar(e.right)
        is Expr.Div -> findFirstVar(e.left) ?: findFirstVar(e.right)
        is Expr.Pow -> findFirstVar(e.base) ?: findFirstVar(e.exp)
        is Expr.Call -> findFirstVar(e.arg)
    }

    private fun linearFactorStr(v: String, root: Double): String = when {
        abs(root) < 1e-11 -> v
        root > 0 -> "($v - ${MathSymbolicAst.formatNum(root)})"
        else -> "($v + ${MathSymbolicAst.formatNum(-root)})"
    }

    private fun errorResult(topic: LatexMathTopic, rawInput: String, msg: String) = MathComputationResult(
        topic = topic,
        rawInput = rawInput,
        operationLabel = "Syntaxe LaTeX",
        primaryResult = msg,
        unicodeFormula = rawInput,
        latexFormula = "",
        steps = emptyList(),
        verificationSource = MathVerificationSource.COMPUTED_LOCAL,
        isSuccess = false,
        errorMessage = msg
    )
}
