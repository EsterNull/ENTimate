package com.example.entimate.data.local

import org.json.JSONObject

/**
 * Global data schema version for user data units.
 * Each record stores the version it was created with; on read we migrate it
 * forward through the chain of registered steps. Unknown/removed fields are
 * kept in [Versioned.extras] so data is never trimmed and lost.
 */
const val CURRENT_DATA_VERSION = 2

interface Versioned {
    var version: Int
    var extras: String
}

object DataMigrations {
    /**
     * A migration step transforms the record's [fields] map in place.
     * Any value that should survive even if a field is removed in a later
     * version can be moved into [history] (key -> value). On read, history is
     * merged back so re-added fields restore their old value.
     */
    private val steps = sortedMapOf<Int, (fields: MutableMap<String, String>, history: MutableMap<String, String>) -> Unit>()

    fun register(fromVersion: Int, step: (fields: MutableMap<String, String>, history: MutableMap<String, String>) -> Unit) {
        steps[fromVersion] = step
    }

    operator fun get(fromVersion: Int): ((MutableMap<String, String>, MutableMap<String, String>) -> Unit)? = steps[fromVersion]

    init {
        // v1 -> v2: report schema gained per-side page margins (real columns added via
        // Room migration 23->24 with defaults). No value transformation needed; this step
        // exists so the global data-version bump is explicit and forward-compatible.
        register(1) { _, _ -> }
    }
}

private fun parseExtras(json: String): MutableMap<String, String> {
    val map = mutableMapOf<String, String>()
    if (json.isBlank()) return map
    try {
        val obj = JSONObject(json)
        obj.keys().forEach { map[it] = obj.optString(it, "") }
    } catch (_: Exception) { }
    return map
}

private fun extrasToJson(map: Map<String, String>): String {
    val obj = JSONObject()
    map.forEach { (k, v) -> obj.put(k, v) }
    return obj.toString()
}

/**
 * Migrates [this] from its stored version up to [CURRENT_DATA_VERSION] by
 * applying each registered step in order. Preserves all current values into a
 * history blob so nothing is lost. Returns the migrated (same-type) record.
 */
fun <T> T.migrateVersioned(
    toFields: (T) -> MutableMap<String, String>,
    fromFields: (T, Map<String, String>) -> T,
): T where T : Versioned {
    var current = this
    val history = parseExtras(current.extras)
    var v = current.version
    while (v < CURRENT_DATA_VERSION) {
        val fields = toFields(current)
        DataMigrations[v]?.invoke(fields, history)
        // keep every current value in history so removed-then-readded data survives
        fields.forEach { (k, value) -> if (value.isNotBlank() && !history.containsKey(k)) history[k] = value }
        current = fromFields(current, fields)
        v += 1
        current.version = v
        current.extras = extrasToJson(history)
    }
    return current
}

// ---- Field mappers per data unit ----

fun DocumentEntity.migrate(): DocumentEntity = migrateVersioned(
    toFields = { d ->
        mutableMapOf(
            "name" to d.name,
            "description" to d.description,
            "colorArgb" to d.colorArgb.toString(),
            "quantity" to d.quantity.toString(),
            "step" to d.step.toString(),
        )
    },
    fromFields = { d, m ->
        d.copy(
            name = m["name"] ?: "",
            description = m["description"] ?: "",
            colorArgb = m["colorArgb"]?.toIntOrNull() ?: 0,
            quantity = m["quantity"]?.toIntOrNull() ?: 0,
            step = m["step"]?.toIntOrNull() ?: 1,
        )
    },
)

fun PatientEntity.migrate(): PatientEntity = migrateVersioned(
    toFields = { p ->
        mutableMapOf(
            "number" to p.number.toString(),
            "personalNumber" to p.personalNumber,
            "lastName" to p.lastName,
            "firstName" to p.firstName,
            "middleName" to p.middleName,
            "birthDate" to p.birthDate,
            "sex" to p.sex,
            "idSeries" to p.idSeries,
            "idNumber" to p.idNumber,
            "serviceDate" to p.serviceDate,
            "rvk" to p.rvk,
            "rank" to p.rank,
            "unit" to p.unit,
            "position" to p.position,
            "admissionDate" to p.admissionDate,
            "referredBy" to p.referredBy,
            "emergency" to p.emergency,
            "illnessStart" to p.illnessStart,
            "category" to p.category,
            "svo" to p.svo.toString(),
            "soch" to p.soch.toString(),
            "discharged" to p.discharged.toString(),
            "colorArgb" to p.colorArgb.toString(),
            "createdAt" to p.createdAt.toString(),
        )
    },
    fromFields = { p, m ->
        p.copy(
            number = m["number"]?.toIntOrNull() ?: 0,
            personalNumber = m["personalNumber"] ?: "",
            lastName = m["lastName"] ?: "",
            firstName = m["firstName"] ?: "",
            middleName = m["middleName"] ?: "",
            birthDate = m["birthDate"] ?: "",
            sex = m["sex"] ?: "М",
            idSeries = m["idSeries"] ?: "",
            idNumber = m["idNumber"] ?: "",
            serviceDate = m["serviceDate"] ?: "",
            rvk = m["rvk"] ?: "",
            rank = m["rank"] ?: "Рядовой",
            unit = m["unit"] ?: "",
            position = m["position"] ?: "",
            admissionDate = m["admissionDate"] ?: "",
            referredBy = m["referredBy"] ?: "",
            emergency = m["emergency"] ?: "Нет",
            illnessStart = m["illnessStart"] ?: "",
            category = m["category"] ?: "по призыву",
            svo = m["svo"]?.toIntOrNull() ?: 0,
            soch = m["soch"]?.toIntOrNull() ?: 0,
            discharged = m["discharged"]?.toIntOrNull() ?: 0,
            colorArgb = m["colorArgb"]?.toIntOrNull() ?: 0,
            createdAt = m["createdAt"]?.toLongOrNull() ?: 0L,
        )
    },
)

fun PatientCustomFieldEntity.migrate(): PatientCustomFieldEntity = migrateVersioned(
    toFields = { f ->
        mutableMapOf(
            "label" to f.label,
            "type" to f.type,
            "options" to f.options,
            "defaultValue" to f.defaultValue,
            "position" to f.position.toString(),
        )
    },
    fromFields = { f, m ->
        f.copy(
            label = m["label"] ?: "",
            type = m["type"] ?: "TEXT",
            options = m["options"] ?: "",
            defaultValue = m["defaultValue"] ?: "",
            position = m["position"]?.toIntOrNull() ?: 0,
        )
    },
)

fun ReportEntity.migrate(): ReportEntity = migrateVersioned(
    toFields = { r ->
        mutableMapOf(
            "name" to r.name,
            "description" to r.description,
            "colorArgb" to r.colorArgb.toString(),
            "kind" to r.kind,
        )
    },
    fromFields = { r, m ->
        r.copy(
            name = m["name"] ?: "",
            description = m["description"] ?: "",
            colorArgb = m["colorArgb"]?.toIntOrNull() ?: 0,
            kind = m["kind"] ?: "DOCUMENTS",
        )
    },
)
