package com.example.entimate.data.local

import org.json.JSONObject

/**
 * A patient-creation template is a snapshot of field values. Built-in patient
 * fields are keyed by their registry key; custom fields are keyed by their label,
 * because custom-field ids are not stable across folders or devices.
 */
data class PatientTemplatePayload(
    val builtins: Map<String, String> = emptyMap(),
    val custom: Map<String, String> = emptyMap(),
)

fun encodePatientTemplatePayload(payload: PatientTemplatePayload): String {
    val obj = JSONObject()
    obj.put("builtins", JSONObject().apply { payload.builtins.forEach { (k, v) -> put(k, v) } })
    obj.put("custom", JSONObject().apply { payload.custom.forEach { (k, v) -> put(k, v) } })
    return obj.toString()
}

fun decodePatientTemplatePayload(json: String): PatientTemplatePayload {
    if (json.isBlank()) return PatientTemplatePayload()
    return try {
        val obj = JSONObject(json)
        fun read(mapObj: JSONObject?, key: String): String = mapObj?.optString(key, "") ?: ""
        PatientTemplatePayload(
            builtins = obj.optJSONObject("builtins")?.let { m ->
                m.keys().asSequence().associateWith { k -> read(m, k) }
            } ?: emptyMap(),
            custom = obj.optJSONObject("custom")?.let { m ->
                m.keys().asSequence().associateWith { k -> read(m, k) }
            } ?: emptyMap(),
        )
    } catch (_: Exception) {
        PatientTemplatePayload()
    }
}

/**
 * Built-in fields that must not be baked into a template when saving it from a
 * patient: personal identifiers, number and dates keep the template reusable.
 */
val TEMPLATE_EXCLUDED_FIELD_KEYS = setOf(
    "number",
    "lastName",
    "firstName",
    "middleName",
    "personalNumber",
    "idSeries",
    "idNumber",
    "birthDate",
    "serviceDate",
    "admissionDate",
    "illnessStart",
    "dischargeDate",
    "discharged",
)