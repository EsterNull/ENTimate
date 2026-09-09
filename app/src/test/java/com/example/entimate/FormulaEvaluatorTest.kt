package com.example.entimate

import com.example.entimate.data.local.COMPUTED_TYPE
import com.example.entimate.data.local.PatientCustomFieldEntity
import com.example.entimate.data.local.FormulaEvaluator
import com.example.entimate.data.local.patientFormulaResult
import com.example.entimate.data.local.resolveComputedValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormulaEvaluatorTest {

    private fun lookup(vararg pairs: Pair<String, Double>): (String) -> Double? {
        val m = pairs.toMap()
        return { key -> m[key] }
    }

    @Test
    fun basicArithmetic() {
        assertEquals(7.0, FormulaEvaluator.eval("2+5", { null }))
        assertEquals(1.0, FormulaEvaluator.eval("5-4", { null }))
        assertEquals(12.0, FormulaEvaluator.eval("3*4", { null }))
        assertEquals(2.0, FormulaEvaluator.eval("10/5", { null }))
    }

    @Test
    fun precedenceAndParentheses() {
        assertEquals(14.0, FormulaEvaluator.eval("2+3*4", { null }))
        assertEquals(20.0, FormulaEvaluator.eval("(2+3)*4", { null }))
        assertEquals(-4.0, FormulaEvaluator.eval("-2^2", { null }))
        assertEquals(8.0, FormulaEvaluator.eval("2^3", { null }))
        assertEquals(512.0, FormulaEvaluator.eval("2^3^2", { null }))
        assertEquals(-3.0, FormulaEvaluator.eval("1-4", { null }))
    }

    @Test
    fun implicitMultiplication() {
        assertEquals(10.0, FormulaEvaluator.eval("2(3+2)", { null }))
        assertEquals(6.0, FormulaEvaluator.eval("(1)(2)(3)", { null }))
        assertEquals(18.0, FormulaEvaluator.eval("2(3)^2", { null }))
    }

    @Test
    fun referencesResolve() {
        val lk = lookup("number" to 10.0, "custom:1" to 3.0)
        assertEquals(30.0, FormulaEvaluator.eval("{number}*{custom:1}", lk))
        assertEquals(10.0, FormulaEvaluator.eval("{number}", lk))
    }

    @Test
    fun missingReferenceIsZero() {
        assertEquals(5.0, FormulaEvaluator.eval("5+{missing}", { null }))
    }

    @Test
    fun functions() {
        assertEquals(3.0, FormulaEvaluator.eval("abs(-3)", { null }))
        assertEquals(4.0, FormulaEvaluator.eval("sqrt(16)", { null }))
        assertEquals(3.0, FormulaEvaluator.eval("cbrt(27)", { null }))
        assertEquals(2.0, FormulaEvaluator.eval("log(100)", { null }))
        assertEquals(1.0, FormulaEvaluator.eval("ln(e)", { null }))
        assertEquals(1.0, FormulaEvaluator.eval("exp(0)", { null }))
        assertEquals(1.0, FormulaEvaluator.eval("ln(e)", { null }))
        val expLn = FormulaEvaluator.eval("exp(ln(8))", { null })!!
        assertEquals(8.0, expLn, 1e-9)
        assertEquals(2.0, FormulaEvaluator.eval("floor(2.7)", { null }))
        assertEquals(3.0, FormulaEvaluator.eval("ceil(2.1)", { null }))
        assertEquals(2.0, FormulaEvaluator.eval("round(2.4)", { null }))
        assertEquals(3.0, FormulaEvaluator.eval("round(2.6)", { null }))
        assertEquals(2.0, FormulaEvaluator.eval("min(2,5)", { null }))
        assertEquals(5.0, FormulaEvaluator.eval("max(2,5)", { null }))
        assertEquals(1.0, FormulaEvaluator.eval("sign(4)", { null }))
    }

    @Test
    fun errorsReturnNull() {
        assertNull(FormulaEvaluator.eval("2+", { null }))
        assertNull(FormulaEvaluator.eval("(2+3", { null }))
        assertNull(FormulaEvaluator.eval("2+*3", { null }))
        assertNull(FormulaEvaluator.eval("2 5 + unknown", { null }))
        assertNull(FormulaEvaluator.eval("bogus(2)", { null }))
        assertNull(FormulaEvaluator.eval("", { null }))
        assertEquals(0.0, FormulaEvaluator.eval("2 {a}", { null }))
    }

    @Test
    fun formatNumber() {
        assertEquals("4", FormulaEvaluator.formatNumber(4.0))
        assertEquals("4.5", FormulaEvaluator.formatNumber(4.5))
    }

    @Test
    fun patientFormulaResultComposesComputedFields() {
        val weight = PatientCustomFieldEntity(id = 1, label = "Вес", type = "NUMBER")
        val height = PatientCustomFieldEntity(id = 2, label = "Рост", type = "NUMBER")
        val bmi = PatientCustomFieldEntity(id = 3, label = "ИМТ", type = COMPUTED_TYPE, formula = "{custom:1}/({custom:2}/100)^2")
        val fields = listOf(weight, height, bmi)
        val values = mapOf(1L to "80", 2L to "180")
        assertEquals("24.691358025", patientFormulaResult(bmi.formula, fields, values, "0"))
    }

    @Test
    fun patientFormulaResultBlankNumberIsZero() {
        val weight = PatientCustomFieldEntity(id = 1, label = "Вес", type = "NUMBER")
        val values = mapOf(1L to "")
        assertEquals("2", patientFormulaResult("{custom:1}+2", listOf(weight), values, ""))
    }

    @Test
    fun resolveComputedValuesMap() {
        val a = PatientCustomFieldEntity(id = 1, label = "A", type = COMPUTED_TYPE, formula = "{custom:3}+5")
        val b = PatientCustomFieldEntity(id = 2, label = "B", type = COMPUTED_TYPE, formula = "{custom:1}*2")
        val x = PatientCustomFieldEntity(id = 3, label = "X", type = "NUMBER")
        val fields = listOf(x, a, b)
        val values = mapOf(3L to "10")
        val resolved = resolveComputedValues(fields, values, "0")
        assertEquals("10", resolved[3L])
        assertEquals("15", resolved[1L])
        assertEquals("30", resolved[2L])
    }

    @Test
    fun cycleDoesNotLoopForever() {
        val a = PatientCustomFieldEntity(id = 1, label = "A", type = COMPUTED_TYPE, formula = "{custom:2}")
        val b = PatientCustomFieldEntity(id = 2, label = "B", type = COMPUTED_TYPE, formula = "{custom:1}+1")
        val fields = listOf(a, b)
        val resolved = resolveComputedValues(fields, emptyMap(), "0")
        assertEquals("1", resolved[1L])
        assertEquals("1", resolved[2L])
    }
}