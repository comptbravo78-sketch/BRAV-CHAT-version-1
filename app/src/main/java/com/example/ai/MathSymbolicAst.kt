package com.example.ai

import kotlin.math.*

enum class MathVerificationSource(val labelFr: String) {
    COMPUTED_LOCAL("Calculé / Vérifié localement"),
    AI_ESTIMATED("Analysé / Estimé par IA")
}

enum class LatexMathTopic(
    val displayName: String,
    val sampleLatex: String,
    val syntaxHint: String
) {
    ANALYSE(
        displayName = "Analyse & Calcul",
        sampleLatex = "\\int_{0}^{\\pi} \\sin(x) + x^2\\,dx",
        syntaxHint = "\\frac{d}{dx}(f), \\int_{a}^{b} f\\,dx, \\lim_{x\\to a} f, \\sum_{k=1}^{n} a_k, taylor(f,x,0,4)"
    ),
    ALGEBRE(
        displayName = "Algèbre & Équations",
        sampleLatex = "x^2 - 5x + 6 = 0",
        syntaxHint = "\\frac{x^2-9}{x-3}, solve(eq=0,x), factor(x^2-9), expand((x+2)^3), \\sqrt{x}"
    ),
    ALGEBRE_LINEAIRE(
        displayName = "Matrices & Systèmes",
        sampleLatex = "\\det\\begin{pmatrix}4 & 2 \\\\ 1 & 3\\end{pmatrix}",
        syntaxHint = "\\det\\begin{pmatrix}a & b \\\\ c & d\\end{pmatrix}, \\begin{pmatrix}4 & 7 \\\\ 2 & 6\\end{pmatrix}^{-1}, eig([[4,1],[2,3]])"
    ),
    EQUATIONS_DIFF(
        displayName = "Équations Diff.",
        sampleLatex = "y'' - 5y' + 6y = 0",
        syntaxHint = "y' + 2y = 0, y'' - 5y' + 6y = 0, ode(y' - 3y = 0)"
    ),
    STATISTIQUES(
        displayName = "Statistiques & Séries",
        sampleLatex = "sd(12, 15, 18, 22, 25)",
        syntaxHint = "mean(...), median(...), var(...), sd(...), \\sum_{k=1}^{10} k^2, \\prod_{k=1}^{5} k"
    );

    companion object {
        fun fromId(id: String?): LatexMathTopic =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: ANALYSE
    }
}

sealed interface Expr {
    data class Num(val value: Double) : Expr
    data class Sym(val name: String) : Expr
    data class Neg(val inner: Expr) : Expr
    data class Add(val left: Expr, val right: Expr) : Expr
    data class Sub(val left: Expr, val right: Expr) : Expr
    data class Mul(val left: Expr, val right: Expr) : Expr
    data class Div(val left: Expr, val right: Expr) : Expr
    data class Pow(val base: Expr, val exp: Expr) : Expr
    data class Call(val name: String, val arg: Expr) : Expr
}

object MathSymbolicAst {

    fun latexToInfix(rawLatex: String): String {
        var s = rawLatex.trim()
            .replace("\$\$", "")
            .replace("$", "")
            .replace("\\left", "")
            .replace("\\right", "")
            .replace("\\,", " ")
            .replace("\\;", " ")
            .replace("\\:", " ")
            .replace("\\!", "")
            .replace("\\quad", " ")
            .replace("\\qquad", " ")
            .replace("\\cdot", "*")
            .replace("\\times", "*")
            .replace("\\div", "/")
            .replace("\\pi", " pi ")
            .replace("%pi", " pi ")
            .replace("π", " pi ")
            .replace("%e", " e ")
            .replace("−", "-")
            .replace("×", "*")
            .replace("÷", "/")
            .replace("**", "^")
            .replace("==", "=")
            .removeSuffix(";")
            .trim()

        // Scientific/LaTeX functions
        val fnReplacements = listOf(
            "\\arcsin" to "asin",
            "\\arccos" to "acos",
            "\\arctan" to "atan",
            "\\sinh" to "sinh",
            "\\cosh" to "cosh",
            "\\tanh" to "tanh",
            "\\sin" to "sin",
            "\\cos" to "cos",
            "\\tan" to "tan",
            "\\exp" to "exp",
            "\\ln" to "ln",
            "\\log_{10}" to "log10",
            "\\log_{2}" to "log2",
            "\\log" to "ln"
        )
        fnReplacements.forEach { (k, v) -> s = s.replace(k, v) }

        // Resolve \frac{num}{den} and \dfrac{num}{den} recursively
        s = replaceLatexFractions(s)
        // Resolve \sqrt[n]{x} and \sqrt{x}
        s = replaceLatexSqrts(s)
        // Resolve remaining ^{...} and {...}
        s = s.replace('{', '(').replace('}', ')')
        return s.trim()
    }

    private fun readBalancedGroup(src: String, openIdx: Int, openCh: Char = '{', closeCh: Char = '}'): Pair<String, Int>? {
        if (openIdx < 0 || openIdx >= src.length || src[openIdx] != openCh) return null
        var depth = 0
        var i = openIdx
        while (i < src.length) {
            if (src[i] == openCh) depth++
            else if (src[i] == closeCh) {
                depth--
                if (depth == 0) return src.substring(openIdx + 1, i) to (i + 1)
            }
            i++
        }
        return null
    }

    private fun skipSpaces(src: String, start: Int): Int {
        var i = start
        while (i < src.length && src[i].isWhitespace()) i++
        return i
    }

    private fun replaceLatexFractions(input: String): String {
        var s = input
        var guard = 0
        while (guard++ < 30) {
            val idx = listOf("\\dfrac", "\\tfrac", "\\frac")
                .mapNotNull { cmd -> s.indexOf(cmd).takeIf { it >= 0 }?.let { it to cmd.length } }
                .minByOrNull { it.first } ?: break
            val afterCmd = skipSpaces(s, idx.first + idx.second)
            val numGrp = readBalancedGroup(s, afterCmd) ?: break
            val afterNum = skipSpaces(s, numGrp.second)
            val denGrp = readBalancedGroup(s, afterNum) ?: break
            val replacement = "((${numGrp.first})/(${denGrp.first}))"
            s = s.substring(0, idx.first) + replacement + s.substring(denGrp.second)
        }
        return s
    }

    private fun replaceLatexSqrts(input: String): String {
        var s = input
        var guard = 0
        while (guard++ < 30) {
            val idx = s.indexOf("\\sqrt")
            if (idx < 0) break
            var cursor = skipSpaces(s, idx + 5)
            var rootOrder: String? = null
            if (cursor < s.length && s[cursor] == '[') {
                val ordGrp = readBalancedGroup(s, cursor, '[', ']') ?: break
                rootOrder = ordGrp.first
                cursor = skipSpaces(s, ordGrp.second)
            }
            val bodyGrp = readBalancedGroup(s, cursor) ?: break
            val replacement = if (rootOrder != null) {
                "((${bodyGrp.first})^(1/(${rootOrder})))"
            } else {
                "sqrt(${bodyGrp.first})"
            }
            s = s.substring(0, idx) + replacement + s.substring(bodyGrp.second)
        }
        return s
    }

    fun parseExpr(rawInput: String): Expr {
        val normalized = latexToInfix(rawInput)
        return RecursiveParser(tokenize(normalized)).parse()
    }

    private enum class TokType { NUM, IDENT, OP, LPAREN, RPAREN, COMMA }
    private data class Tok(val type: TokType, val text: String)

    private fun tokenize(src: String): List<Tok> {
        val raw = mutableListOf<Tok>()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || (c == '.' && i + 1 < src.length && src[i + 1].isDigit()) -> {
                    val start = i
                    while (i < src.length && (src[i].isDigit() || src[i] == '.')) i++
                    if (i < src.length && (src[i] == 'e' || src[i] == 'E') &&
                        (i + 1 < src.length && (src[i + 1].isDigit() || src[i + 1] == '+' || src[i + 1] == '-'))
                    ) {
                        i++
                        if (i < src.length && (src[i] == '+' || src[i] == '-')) i++
                        while (i < src.length && src[i].isDigit()) i++
                    }
                    raw.add(Tok(TokType.NUM, src.substring(start, i)))
                }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                    raw.add(Tok(TokType.IDENT, src.substring(start, i)))
                }
                c == '(' || c == '[' -> { raw.add(Tok(TokType.LPAREN, "(")); i++ }
                c == ')' || c == ']' -> { raw.add(Tok(TokType.RPAREN, ")")); i++ }
                c == ',' -> { raw.add(Tok(TokType.COMMA, ",")); i++ }
                c in charArrayOf('+', '-', '*', '/', '^', '!') -> {
                    raw.add(Tok(TokType.OP, c.toString())); i++
                }
                else -> i++
            }
        }
        val out = mutableListOf<Tok>()
        for (idx in raw.indices) {
            val cur = raw[idx]
            if (out.isNotEmpty()) {
                val prev = out.last()
                val prevIsVal = prev.type == TokType.NUM || prev.type == TokType.RPAREN ||
                    (prev.type == TokType.IDENT && cur.type != TokType.LPAREN)
                val curStartsVal = cur.type == TokType.NUM || cur.type == TokType.IDENT || cur.type == TokType.LPAREN
                if (prevIsVal && curStartsVal) {
                    val prevIsFuncName = prev.type == TokType.IDENT && isFunctionName(prev.text)
                    if (!(prevIsFuncName && cur.type == TokType.LPAREN)) {
                        out.add(Tok(TokType.OP, "*"))
                    }
                }
            }
            out.add(cur)
        }
        return out
    }

    private fun isFunctionName(name: String): Boolean = name.lowercase() in setOf(
        "sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh",
        "exp", "ln", "log", "log10", "log2", "sqrt", "abs"
    )

    private class RecursiveParser(private val toks: List<Tok>) {
        private var pos = 0

        fun parse(): Expr {
            if (toks.isEmpty()) return Expr.Num(0.0)
            return parseAddSub()
        }

        private fun peek(): Tok? = toks.getOrNull(pos)
        private fun consume(): Tok = toks[pos++]

        private fun parseAddSub(): Expr {
            var left = parseMulDiv()
            while (peek()?.type == TokType.OP && (peek()?.text == "+" || peek()?.text == "-")) {
                val op = consume().text
                val right = parseMulDiv()
                left = if (op == "+") Expr.Add(left, right) else Expr.Sub(left, right)
            }
            return left
        }

        private fun parseMulDiv(): Expr {
            var left = parseUnary()
            while (peek()?.type == TokType.OP && (peek()?.text == "*" || peek()?.text == "/")) {
                val op = consume().text
                val right = parseUnary()
                left = if (op == "*") Expr.Mul(left, right) else Expr.Div(left, right)
            }
            return left
        }

        private fun parseUnary(): Expr {
            if (peek()?.type == TokType.OP && peek()?.text == "-") {
                consume()
                return Expr.Neg(parseUnary())
            }
            if (peek()?.type == TokType.OP && peek()?.text == "+") {
                consume()
                return parseUnary()
            }
            return parsePower()
        }

        private fun parsePower(): Expr {
            var base = parsePostfix()
            if (peek()?.type == TokType.OP && peek()?.text == "^") {
                consume()
                val exp = parseUnary()
                base = Expr.Pow(base, exp)
            }
            return base
        }

        private fun parsePostfix(): Expr {
            var expr = parsePrimary()
            while (peek()?.type == TokType.OP && peek()?.text == "!") {
                consume()
                expr = Expr.Call("fact", expr)
            }
            return expr
        }

        private fun parsePrimary(): Expr {
            val t = peek() ?: return Expr.Num(0.0)
            return when (t.type) {
                TokType.NUM -> {
                    consume()
                    Expr.Num(t.text.toDoubleOrNull() ?: 0.0)
                }
                TokType.LPAREN -> {
                    consume()
                    val inner = parseAddSub()
                    if (peek()?.type == TokType.RPAREN) consume()
                    inner
                }
                TokType.IDENT -> {
                    consume()
                    val lower = t.text.lowercase()
                    if (peek()?.type == TokType.LPAREN) {
                        consume()
                        val arg = parseAddSub()
                        if (peek()?.type == TokType.RPAREN) consume()
                        val fn = when (lower) {
                            "loge", "log" -> "ln"
                            else -> lower
                        }
                        Expr.Call(fn, arg)
                    } else {
                        when (lower) {
                            "pi" -> Expr.Sym("pi")
                            "e" -> Expr.Sym("e")
                            else -> Expr.Sym(t.text)
                        }
                    }
                }
                else -> {
                    consume()
                    Expr.Num(0.0)
                }
            }
        }
    }

    fun evaluate(expr: Expr, vars: Map<String, Double> = emptyMap()): Double {
        return when (expr) {
            is Expr.Num -> expr.value
            is Expr.Sym -> when (expr.name.lowercase()) {
                "pi" -> Math.PI
                "e" -> Math.E
                else -> vars[expr.name] ?: vars[expr.name.lowercase()]
                    ?: throw IllegalArgumentException("Variable inconnue : ${expr.name}")
            }
            is Expr.Neg -> -evaluate(expr.inner, vars)
            is Expr.Add -> evaluate(expr.left, vars) + evaluate(expr.right, vars)
            is Expr.Sub -> evaluate(expr.left, vars) - evaluate(expr.right, vars)
            is Expr.Mul -> evaluate(expr.left, vars) * evaluate(expr.right, vars)
            is Expr.Div -> evaluate(expr.left, vars) / evaluate(expr.right, vars)
            is Expr.Pow -> evaluate(expr.base, vars).pow(evaluate(expr.exp, vars))
            is Expr.Call -> {
                val x = evaluate(expr.arg, vars)
                when (expr.name.lowercase()) {
                    "sin" -> sin(x)
                    "cos" -> cos(x)
                    "tan" -> tan(x)
                    "asin" -> asin(x)
                    "acos" -> acos(x)
                    "atan" -> atan(x)
                    "sinh" -> sinh(x)
                    "cosh" -> cosh(x)
                    "tanh" -> tanh(x)
                    "exp" -> exp(x)
                    "ln" -> ln(x)
                    "log10" -> log10(x)
                    "log2" -> log2(x)
                    "sqrt" -> sqrt(x)
                    "abs" -> abs(x)
                    "fact" -> factorial(x.toInt())
                    else -> throw IllegalArgumentException("Fonction inconnue : ${expr.name}")
                }
            }
        }
    }

    private fun factorial(n: Int): Double {
        if (n < 0 || n > 170) return Double.NaN
        var acc = 1.0
        for (k in 2..n) acc *= k
        return acc
    }

    fun simplify(expr: Expr): Expr {
        val raw = when (expr) {
            is Expr.Num, is Expr.Sym -> expr
            is Expr.Neg -> {
                val s = simplify(expr.inner)
                when (s) {
                    is Expr.Num -> Expr.Num(if (s.value == 0.0) 0.0 else -s.value)
                    is Expr.Neg -> s.inner
                    else -> Expr.Neg(s)
                }
            }
            is Expr.Add -> {
                val l = simplify(expr.left)
                val r = simplify(expr.right)
                when {
                    l is Expr.Num && r is Expr.Num -> Expr.Num(l.value + r.value)
                    l is Expr.Num && l.value == 0.0 -> r
                    r is Expr.Num && r.value == 0.0 -> l
                    r is Expr.Neg -> simplify(Expr.Sub(l, r.inner))
                    l == r -> simplify(Expr.Mul(Expr.Num(2.0), l))
                    else -> Expr.Add(l, r)
                }
            }
            is Expr.Sub -> {
                val l = simplify(expr.left)
                val r = simplify(expr.right)
                when {
                    l is Expr.Num && r is Expr.Num -> Expr.Num(l.value - r.value)
                    r is Expr.Num && r.value == 0.0 -> l
                    l is Expr.Num && l.value == 0.0 -> simplify(Expr.Neg(r))
                    l == r -> Expr.Num(0.0)
                    else -> Expr.Sub(l, r)
                }
            }
            is Expr.Mul -> {
                val l = simplify(expr.left)
                val r = simplify(expr.right)
                when {
                    l is Expr.Num && l.value == 0.0 -> Expr.Num(0.0)
                    r is Expr.Num && r.value == 0.0 -> Expr.Num(0.0)
                    l is Expr.Num && l.value == 1.0 -> r
                    r is Expr.Num && r.value == 1.0 -> l
                    l is Expr.Num && l.value == -1.0 -> simplify(Expr.Neg(r))
                    r is Expr.Num && r.value == -1.0 -> simplify(Expr.Neg(l))
                    l is Expr.Num && r is Expr.Num -> Expr.Num(l.value * r.value)
                    l is Expr.Num && r is Expr.Mul && r.left is Expr.Num ->
                        simplify(Expr.Mul(Expr.Num(l.value * r.left.value), r.right))
                    r is Expr.Num && l !is Expr.Num -> simplify(Expr.Mul(r, l))
                    l == r && (l is Expr.Sym || l is Expr.Call) -> Expr.Pow(l, Expr.Num(2.0))
                    else -> Expr.Mul(l, r)
                }
            }
            is Expr.Div -> {
                val l = simplify(expr.left)
                val r = simplify(expr.right)
                when {
                    l is Expr.Num && l.value == 0.0 -> Expr.Num(0.0)
                    r is Expr.Num && r.value == 1.0 -> l
                    l is Expr.Num && r is Expr.Num && r.value != 0.0 -> {
                        val q = l.value / r.value
                        if (abs(q - q.roundToLong()) < 1e-12) Expr.Num(q.roundToLong().toDouble()) else Expr.Div(l, r)
                    }
                    l == r -> Expr.Num(1.0)
                    else -> Expr.Div(l, r)
                }
            }
            is Expr.Pow -> {
                val b = simplify(expr.base)
                val e = simplify(expr.exp)
                when {
                    e is Expr.Num && e.value == 0.0 -> Expr.Num(1.0)
                    e is Expr.Num && e.value == 1.0 -> b
                    b is Expr.Num && b.value == 0.0 -> Expr.Num(0.0)
                    b is Expr.Num && b.value == 1.0 -> Expr.Num(1.0)
                    b is Expr.Num && e is Expr.Num -> Expr.Num(b.value.pow(e.value))
                    else -> Expr.Pow(b, e)
                }
            }
            is Expr.Call -> Expr.Call(expr.name, simplify(expr.arg))
        }
        return raw
    }

    fun expand(expr: Expr): Expr = simplify(expandInternal(expr))

    private fun expandInternal(expr: Expr): Expr = when (expr) {
        is Expr.Num, is Expr.Sym -> expr
        is Expr.Neg -> Expr.Neg(expandInternal(expr.inner))
        is Expr.Add -> Expr.Add(expandInternal(expr.left), expandInternal(expr.right))
        is Expr.Sub -> Expr.Sub(expandInternal(expr.left), expandInternal(expr.right))
        is Expr.Div -> Expr.Div(expandInternal(expr.left), expandInternal(expr.right))
        is Expr.Call -> Expr.Call(expr.name, expandInternal(expr.arg))
        is Expr.Mul -> distributeMul(expandInternal(expr.left), expandInternal(expr.right))
        is Expr.Pow -> {
            val b = expandInternal(expr.base)
            val e = simplify(expr.exp)
            when {
                e is Expr.Num && e.value == 0.0 -> Expr.Num(1.0)
                e is Expr.Num && e.value == 1.0 -> b
                e is Expr.Num && e.value in 2.0..6.0 && abs(e.value - e.value.roundToInt()) < 1e-9 -> {
                    var acc: Expr = b
                    repeat(e.value.roundToInt() - 1) {
                        acc = distributeMul(acc, b)
                    }
                    acc
                }
                else -> Expr.Pow(b, e)
            }
        }
    }

    private fun distributeMul(l: Expr, r: Expr): Expr = when {
        l is Expr.Add -> Expr.Add(distributeMul(l.left, r), distributeMul(l.right, r))
        l is Expr.Sub -> Expr.Sub(distributeMul(l.left, r), distributeMul(l.right, r))
        r is Expr.Add -> Expr.Add(distributeMul(l, r.left), distributeMul(l, r.right))
        r is Expr.Sub -> Expr.Sub(distributeMul(l, r.left), distributeMul(l, r.right))
        l is Expr.Neg -> Expr.Neg(distributeMul(l.inner, r))
        r is Expr.Neg -> Expr.Neg(distributeMul(l, r.inner))
        else -> Expr.Mul(l, r)
    }

    fun differentiate(expr: Expr, variable: String): Expr {
        val v = variable.trim().ifBlank { "x" }
        val raw = when (expr) {
            is Expr.Num -> Expr.Num(0.0)
            is Expr.Sym -> if (expr.name == v) Expr.Num(1.0) else Expr.Num(0.0)
            is Expr.Neg -> Expr.Neg(differentiate(expr.inner, v))
            is Expr.Add -> Expr.Add(differentiate(expr.left, v), differentiate(expr.right, v))
            is Expr.Sub -> Expr.Sub(differentiate(expr.left, v), differentiate(expr.right, v))
            is Expr.Mul -> Expr.Add(
                Expr.Mul(differentiate(expr.left, v), expr.right),
                Expr.Mul(expr.left, differentiate(expr.right, v))
            )
            is Expr.Div -> Expr.Div(
                Expr.Sub(
                    Expr.Mul(differentiate(expr.left, v), expr.right),
                    Expr.Mul(expr.left, differentiate(expr.right, v))
                ),
                Expr.Pow(expr.right, Expr.Num(2.0))
            )
            is Expr.Pow -> {
                val u = expr.base
                val p = expr.exp
                val uHas = containsVar(u, v)
                val pHas = containsVar(p, v)
                when {
                    !uHas && !pHas -> Expr.Num(0.0)
                    uHas && !pHas -> Expr.Mul(
                        Expr.Mul(p, Expr.Pow(u, simplify(Expr.Sub(p, Expr.Num(1.0))))),
                        differentiate(u, v)
                    )
                    !uHas && pHas -> Expr.Mul(
                        Expr.Mul(Expr.Pow(u, p), Expr.Call("ln", u)),
                        differentiate(p, v)
                    )
                    else -> Expr.Mul(
                        Expr.Pow(u, p),
                        Expr.Add(
                            Expr.Mul(differentiate(p, v), Expr.Call("ln", u)),
                            Expr.Mul(p, Expr.Div(differentiate(u, v), u))
                        )
                    )
                }
            }
            is Expr.Call -> {
                val u = expr.arg
                val du = differentiate(u, v)
                val outer = when (expr.name.lowercase()) {
                    "sin" -> Expr.Call("cos", u)
                    "cos" -> Expr.Neg(Expr.Call("sin", u))
                    "tan" -> Expr.Div(Expr.Num(1.0), Expr.Pow(Expr.Call("cos", u), Expr.Num(2.0)))
                    "exp" -> Expr.Call("exp", u)
                    "ln" -> Expr.Div(Expr.Num(1.0), u)
                    "sqrt" -> Expr.Div(Expr.Num(1.0), Expr.Mul(Expr.Num(2.0), Expr.Call("sqrt", u)))
                    "atan" -> Expr.Div(Expr.Num(1.0), Expr.Add(Expr.Num(1.0), Expr.Pow(u, Expr.Num(2.0))))
                    "asin" -> Expr.Div(Expr.Num(1.0), Expr.Call("sqrt", Expr.Sub(Expr.Num(1.0), Expr.Pow(u, Expr.Num(2.0)))))
                    "acos" -> Expr.Neg(Expr.Div(Expr.Num(1.0), Expr.Call("sqrt", Expr.Sub(Expr.Num(1.0), Expr.Pow(u, Expr.Num(2.0))))))
                    "sinh" -> Expr.Call("cosh", u)
                    "cosh" -> Expr.Call("sinh", u)
                    else -> Expr.Call("d_${expr.name}", u)
                }
                Expr.Mul(outer, du)
            }
        }
        return simplify(raw)
    }

    fun integrate(expr: Expr, variable: String): Expr? {
        val v = variable.trim().ifBlank { "x" }
        val s = simplify(expr)
        return when {
            !containsVar(s, v) -> simplify(Expr.Mul(s, Expr.Sym(v)))
            s is Expr.Sym && s.name == v -> simplify(Expr.Div(Expr.Pow(Expr.Sym(v), Expr.Num(2.0)), Expr.Num(2.0)))
            s is Expr.Neg -> integrate(s.inner, v)?.let { simplify(Expr.Neg(it)) }
            s is Expr.Add -> {
                val l = integrate(s.left, v)
                val r = integrate(s.right, v)
                if (l != null && r != null) simplify(Expr.Add(l, r)) else null
            }
            s is Expr.Sub -> {
                val l = integrate(s.left, v)
                val r = integrate(s.right, v)
                if (l != null && r != null) simplify(Expr.Sub(l, r)) else null
            }
            s is Expr.Mul && !containsVar(s.left, v) ->
                integrate(s.right, v)?.let { simplify(Expr.Mul(s.left, it)) }
            s is Expr.Mul && !containsVar(s.right, v) ->
                integrate(s.left, v)?.let { simplify(Expr.Mul(s.right, it)) }
            s is Expr.Div && !containsVar(s.right, v) ->
                integrate(s.left, v)?.let { simplify(Expr.Div(it, s.right)) }
            s is Expr.Div && s.left is Expr.Num && s.right is Expr.Sym && s.right.name == v ->
                simplify(Expr.Mul(s.left, Expr.Call("ln", Expr.Call("abs", Expr.Sym(v)))))
            s is Expr.Pow && s.base is Expr.Sym && s.base.name == v && !containsVar(s.exp, v) -> {
                val n = s.exp
                if (n is Expr.Num && n.value == -1.0) {
                    Expr.Call("ln", Expr.Call("abs", Expr.Sym(v)))
                } else {
                    val np1 = simplify(Expr.Add(n, Expr.Num(1.0)))
                    simplify(Expr.Div(Expr.Pow(Expr.Sym(v), np1), np1))
                }
            }
            s is Expr.Call && s.arg is Expr.Sym && s.arg.name == v -> when (s.name.lowercase()) {
                "sin" -> Expr.Neg(Expr.Call("cos", Expr.Sym(v)))
                "cos" -> Expr.Call("sin", Expr.Sym(v))
                "exp" -> Expr.Call("exp", Expr.Sym(v))
                else -> null
            }
            s is Expr.Call && s.name.lowercase() == "exp" && isLinearInVar(s.arg, v) -> {
                val (a, _) = linearCoeffs(s.arg, v)!!
                simplify(Expr.Div(s, Expr.Num(a)))
            }
            s is Expr.Call && (s.name.lowercase() == "sin" || s.name.lowercase() == "cos") && isLinearInVar(s.arg, v) -> {
                val (a, _) = linearCoeffs(s.arg, v)!!
                if (s.name.lowercase() == "sin") simplify(Expr.Div(Expr.Neg(Expr.Call("cos", s.arg)), Expr.Num(a)))
                else simplify(Expr.Div(Expr.Call("sin", s.arg), Expr.Num(a)))
            }
            else -> null
        }
    }

    fun definiteIntegralSimpson(expr: Expr, variable: String, a: Double, b: Double, steps: Int = 2000): Double {
        if (a == b) return 0.0
        val n = if (steps % 2 == 0) steps else steps + 1
        val h = (b - a) / n
        var sum = evaluate(expr, mapOf(variable to a)) + evaluate(expr, mapOf(variable to b))
        for (i in 1 until n) {
            val x = a + i * h
            val weight = if (i % 2 == 0) 2.0 else 4.0
            sum += weight * evaluate(expr, mapOf(variable to x))
        }
        return sum * h / 3.0
    }

    fun containsVar(expr: Expr, v: String): Boolean = when (expr) {
        is Expr.Num -> false
        is Expr.Sym -> expr.name == v
        is Expr.Neg -> containsVar(expr.inner, v)
        is Expr.Add -> containsVar(expr.left, v) || containsVar(expr.right, v)
        is Expr.Sub -> containsVar(expr.left, v) || containsVar(expr.right, v)
        is Expr.Mul -> containsVar(expr.left, v) || containsVar(expr.right, v)
        is Expr.Div -> containsVar(expr.left, v) || containsVar(expr.right, v)
        is Expr.Pow -> containsVar(expr.base, v) || containsVar(expr.exp, v)
        is Expr.Call -> containsVar(expr.arg, v)
    }

    private fun isLinearInVar(expr: Expr, v: String): Boolean = linearCoeffs(expr, v) != null

    fun linearCoeffs(expr: Expr, v: String): Pair<Double, Double>? {
        val s = simplify(expr)
        return when (s) {
            is Expr.Num -> 0.0 to s.value
            is Expr.Sym -> if (s.name == v) (1.0 to 0.0) else null
            is Expr.Neg -> linearCoeffs(s.inner, v)?.let { (-it.first) to (-it.second) }
            is Expr.Add -> {
                val l = linearCoeffs(s.left, v) ?: return null
                val r = linearCoeffs(s.right, v) ?: return null
                (l.first + r.first) to (l.second + r.second)
            }
            is Expr.Sub -> {
                val l = linearCoeffs(s.left, v) ?: return null
                val r = linearCoeffs(s.right, v) ?: return null
                (l.first - r.first) to (l.second - r.second)
            }
            is Expr.Mul -> when {
                s.left is Expr.Num -> linearCoeffs(s.right, v)?.let { (s.left.value * it.first) to (s.left.value * it.second) }
                s.right is Expr.Num -> linearCoeffs(s.left, v)?.let { (s.right.value * it.first) to (s.right.value * it.second) }
                else -> null
            }
            else -> null
        }
    }

    fun formatNum(value: Double): String {
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
        val clean = if (abs(value) < 1e-11) 0.0 else value
        val rounded = clean.roundToLong()
        return if (abs(clean - rounded) < 1e-9 && abs(clean) < 1e15) {
            rounded.toString()
        } else {
            String.format(java.util.Locale.US, "%.6f", clean).trimEnd('0').trimEnd('.')
        }
    }

    fun toPrettyString(expr: Expr): String = when (expr) {
        is Expr.Num -> formatNum(expr.value)
        is Expr.Sym -> when (expr.name.lowercase()) {
            "pi" -> "π"
            else -> expr.name
        }
        is Expr.Neg -> "-${wrapIfAddSub(expr.inner)}"
        is Expr.Add -> "${toPrettyString(expr.left)} + ${toPrettyString(expr.right)}"
        is Expr.Sub -> "${toPrettyString(expr.left)} - ${wrapIfAddSub(expr.right)}"
        is Expr.Mul -> {
            val l = expr.left
            val r = expr.right
            val lStr = wrapIfAddSub(l)
            val rStr = wrapIfAddSub(r)
            if (l is Expr.Num && (r is Expr.Sym || r is Expr.Pow || r is Expr.Call)) "$lStr·$rStr"
            else "$lStr * $rStr"
        }
        is Expr.Div -> "${wrapIfAddSub(expr.left)} / ${wrapIfMulDiv(expr.right)}"
        is Expr.Pow -> "${wrapForPower(expr.base)}^${wrapForPower(expr.exp)}"
        is Expr.Call -> "${expr.name}(${toPrettyString(expr.arg)})"
    }

    fun toLatex(expr: Expr): String = when (expr) {
        is Expr.Num -> formatNum(expr.value)
        is Expr.Sym -> when (expr.name.lowercase()) {
            "pi" -> "\\pi"
            else -> expr.name
        }
        is Expr.Neg -> "-${toLatex(expr.inner)}"
        is Expr.Add -> "${toLatex(expr.left)} + ${toLatex(expr.right)}"
        is Expr.Sub -> "${toLatex(expr.left)} - ${toLatex(expr.right)}"
        is Expr.Mul -> {
            val l = expr.left
            val r = expr.right
            if (l is Expr.Num && (r is Expr.Sym || r is Expr.Pow || r is Expr.Call)) {
                "${toLatex(l)}${toLatex(r)}"
            } else {
                "${toLatex(l)} \\cdot ${toLatex(r)}"
            }
        }
        is Expr.Div -> "\\frac{${toLatex(expr.left)}}{${toLatex(expr.right)}}"
        is Expr.Pow -> "${wrapLatexBase(expr.base)}^{${toLatex(expr.exp)}}"
        is Expr.Call -> when (expr.name.lowercase()) {
            "sqrt" -> "\\sqrt{${toLatex(expr.arg)}}"
            "abs" -> "\\left|${toLatex(expr.arg)}\\right|"
            "sin", "cos", "tan", "ln", "exp", "sinh", "cosh", "tanh", "atan", "asin", "acos" ->
                "\\${expr.name.lowercase()}\\left(${toLatex(expr.arg)}\\right)"
            else -> "\\mathrm{${expr.name}}\\left(${toLatex(expr.arg)}\\right)"
        }
    }

    private fun wrapIfAddSub(e: Expr): String =
        if (e is Expr.Add || e is Expr.Sub) "(${toPrettyString(e)})" else toPrettyString(e)

    private fun wrapIfMulDiv(e: Expr): String =
        if (e is Expr.Add || e is Expr.Sub || e is Expr.Mul || e is Expr.Div) "(${toPrettyString(e)})" else toPrettyString(e)

    private fun wrapForPower(e: Expr): String =
        if (e is Expr.Num || e is Expr.Sym || e is Expr.Call) toPrettyString(e) else "(${toPrettyString(e)})"

    private fun wrapLatexBase(e: Expr): String =
        if (e is Expr.Num || e is Expr.Sym || e is Expr.Call) toLatex(e) else "\\left(${toLatex(e)}\\right)"
}
