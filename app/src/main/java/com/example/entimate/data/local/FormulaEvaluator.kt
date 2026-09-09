package com.example.entimate.data.local

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sqrt

const val COMPUTED_TYPE: String = "COMPUTED"

object FormulaEvaluator {
    /** Evaluates a formula like `2+{number}*ln(10)`; missing references resolve to 0. */
    fun eval(expression: String, lookup: (String) -> Double?): Double? {
        if (expression.isBlank()) return null
        val p = Parser(expression, lookup)
        return try {
            val v = p.parseExpr()
            p.skipWs()
            if (!p.atEnd()) null else v
        } catch (e: Exception) {
            null
        }
    }

    fun formatNumber(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return ""
        val r = (d * 1_000_000_000.0).let { round(it) / 1_000_000_000.0 }
        return if (r == floor(r) && abs(r) < 1e15) r.toLong().toString() else r.toString()
    }

    fun referencedKeys(expression: String): List<String> =
        REF_PATTERN.findAll(expression).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.toList()

    private val REF_PATTERN = Regex("""\{([^{}]+)\}""")

    private class Parser(private val s: String, private val lookup: (String) -> Double?) {
        private var i = 0

        fun atEnd(): Boolean = i >= s.length

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun peek(): Char? = if (i < s.length) s[i] else null

        private fun expect(c: Char) {
            skipWs()
            if (peek() != c) throw IllegalStateException("expected '$c'")
            i++
        }

        fun parseExpr(): Double {
            var v = parseTerm()
            while (true) {
                skipWs()
                val c = peek() ?: break
                if (c == '+') {
                    i++
                    v += parseTerm()
                } else if (c == '-') {
                    i++
                    v -= parseTerm()
                } else break
            }
            return v
        }

        private fun parseTerm(): Double {
            var v = parseUnary()
            while (true) {
                skipWs()
                val c = peek() ?: break
                when (c) {
                    '*' -> {
                        i++
                        v *= parseUnary()
                    }
                    '/' -> {
                        i++
                        v /= parseUnary()
                    }
                    else -> {
                        if (startsPrimary(c)) v *= parseUnary() else break
                    }
                }
            }
            return v
        }

        private fun parseUnary(): Double {
            skipWs()
            val c = peek() ?: throw IllegalStateException("expected value")
            return when (c) {
                '-' -> {
                    i++
                    -parseUnary()
                }
                '+' -> {
                    i++
                    parseUnary()
                }
                else -> parsePower()
            }
        }

        private fun parsePower(): Double {
            val base = parsePrimary()
            skipWs()
            if (peek() == '^') {
                i++
                return base.pow(parseUnary())
            }
            return base
        }

        private fun startsPrimary(c: Char): Boolean =
            c.isDigit() || c == '.' || c == '(' || c == '{' || c.isLetter()

        private fun parsePrimary(): Double {
            skipWs()
            val c = peek() ?: throw IllegalStateException("expected value")
            return when {
                c.isDigit() || c == '.' -> parseNumber()
                c == '(' -> {
                    i++
                    val v = parseExpr()
                    expect(')')
                    v
                }
                c == '{' -> parseReference()
                c.isLetter() -> parseName()
                else -> throw IllegalStateException("unexpected char '$c'")
            }
        }

        private fun parseNumber(): Double {
            val start = i
            var hasDot = false
            while (i < s.length) {
                val c = s[i]
                if (c.isDigit()) i++
                else if (c == '.' && !hasDot) {
                    hasDot = true
                    i++
                } else break
            }
            return s.substring(start, i).toDoubleOrNull() ?: throw IllegalStateException("bad number")
        }

        private fun parseReference(): Double {
            i++
            val start = i
            while (i < s.length && s[i] != '}') i++
            if (i >= s.length) throw IllegalStateException("unclosed '{'")
            val ref = s.substring(start, i).trim()
            i++
            return lookup(ref) ?: 0.0
        }

        private fun parseName(): Double {
            val start = i
            while (i < s.length && s[i].isLetter()) i++
            val name = s.substring(start, i).lowercase()
            skipWs()
            if (peek() == '(') {
                i++
                val a1 = parseExpr()
                skipWs()
                var a2: Double? = null
                if (peek() == ',') {
                    i++
                    a2 = parseExpr()
                    skipWs()
                }
                expect(')')
                if (name in SINGLE_ARG && a2 != null) throw IllegalStateException("$name takes one argument")
                if (name in DOUBLE_ARG && a2 == null) throw IllegalStateException("$name takes two arguments")
                return when (name) {
                    "abs" -> abs(a1)
                    "sqrt" -> sqrt(a1)
                    "cbrt" -> cbrt(a1)
                    "ln" -> ln(a1)
                    "log" -> log10(a1)
                    "exp" -> exp(a1)
                    "floor" -> floor(a1)
                    "ceil" -> ceil(a1)
                    "round" -> round(a1)
                    "sign" -> sign(a1)
                    "min" -> kotlin.math.min(a1, a2!!)
                    "max" -> kotlin.math.max(a1, a2!!)
                    else -> throw IllegalStateException("unknown function '$name'")
                }
            }
            return when (name) {
                "pi" -> kotlin.math.PI
                "e" -> kotlin.math.E
                else -> throw IllegalStateException("unknown name '$name'")
            }
        }

        companion object {
            private val SINGLE_ARG = setOf("abs", "sqrt", "cbrt", "ln", "log", "exp", "floor", "ceil", "round", "sign")
            private val DOUBLE_ARG = setOf("min", "max")
        }
    }
}

/** Formats the evaluated result of a formula, or "" when blank/invalid. */
fun patientFormulaResult(
    formula: String,
    customFields: List<PatientCustomFieldEntity>,
    customValues: Map<Long, String>,
    numberValue: String,
): String {
    if (formula.isBlank()) return ""
    val byId = customFields.associateBy { it.id }
    val done = mutableMapOf<Long, String>()
    val visiting = mutableSetOf<Long>()

    fun ref(v: String): Double? {
        if (v == "number") return numberValue.trim().replace(',', '.').toDoubleOrNull()
        if (!isCustomKey(v)) {
            return when (v.lowercase()) {
                "pi" -> kotlin.math.PI
                "e" -> kotlin.math.E
                else -> null
            }
        }
        val id = customFieldIdFromKey(v)
        val f = byId[id] ?: return null
        done[id]?.let { return it.trim().replace(',', '.').toDoubleOrNull() }
        if (id in visiting) return null
        if (f.type != COMPUTED_TYPE) {
            val raw = customValues[id]?.trim() ?: ""
            done[id] = raw
            return raw.replace(',', '.').toDoubleOrNull()
        }
        val raw = f.formula.trim()
        if (raw.isBlank()) return null
        visiting.add(id)
        val d = FormulaEvaluator.eval(raw) { r -> ref(r) }
        visiting.remove(id)
        done[id] = if (d == null || !d.isFinite()) "" else FormulaEvaluator.formatNumber(d)
        return d?.takeIf { it.isFinite() }
    }

    val d = FormulaEvaluator.eval(formula) { r -> ref(r) }
    return if (d == null || !d.isFinite()) "" else FormulaEvaluator.formatNumber(d)
}

/**
 * Resolves every [COMPUTED_TYPE] custom field of a patient to its numeric result,
 * keeping non-computed fields as-is. Blank/invalid formulas resolve to "".
 */
fun resolveComputedValues(
    customFields: List<PatientCustomFieldEntity>,
    customValues: Map<Long, String>,
    numberValue: String,
): Map<Long, String> {
    if (customFields.none { it.type == COMPUTED_TYPE }) return customValues
    val byId = customFields.associateBy { it.id }
    val done = mutableMapOf<Long, String>()

    fun refRaw(v: String, visiting: MutableSet<Long>): Double? {
        if (v == "number") return numberValue.trim().replace(',', '.').toDoubleOrNull()
        if (!isCustomKey(v)) {
            return when (v.lowercase()) {
                "pi" -> kotlin.math.PI
                "e" -> kotlin.math.E
                else -> null
            }
        }
        val id = customFieldIdFromKey(v)
        val f = byId[id] ?: return null
        done[id]?.let { return it.trim().replace(',', '.').toDoubleOrNull() }
        if (id in visiting) return null
        if (f.type != COMPUTED_TYPE) {
            val raw = customValues[id]?.trim() ?: ""
            done[id] = raw
            return raw.replace(',', '.').toDoubleOrNull()
        }
        val raw = f.formula.trim()
        if (raw.isBlank()) return null
        visiting.add(id)
        val d = FormulaEvaluator.eval(raw) { r -> refRaw(r, visiting) }
        visiting.remove(id)
        done[id] = if (d == null || !d.isFinite()) "" else FormulaEvaluator.formatNumber(d)
        return d?.takeIf { it.isFinite() }
    }

    val out = customValues.toMutableMap()
    for (cf in customFields) {
        if (cf.type == COMPUTED_TYPE) {
            val raw = cf.formula.trim()
            val d = if (raw.isBlank()) null else FormulaEvaluator.eval(raw) { r -> refRaw(r, mutableSetOf()) }
            out[cf.id] = if (d == null || !d.isFinite()) "" else FormulaEvaluator.formatNumber(d)
        }
    }
    return out
}