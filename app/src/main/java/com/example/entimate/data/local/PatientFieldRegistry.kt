package com.example.entimate.data.local

val MILITARY_RANKS = listOf(
    "Рядовой", "Ефрейтор", "Младший сержант", "Сержант", "Старший сержант", "Старшина",
    "Прапорщик", "Старший прапорщик",
    "Младший лейтенант", "Лейтенант", "Старший лейтенант", "Капитан", "Майор", "Подполковник", "Полковник",
    "Генерал-майор", "Генерал-лейтенант", "Генерал-полковник", "Генерал армии", "Маршал Российской Федерации",
    "Матрос", "Старший матрос", "Старшина 2 статьи", "Старшина 1 статьи", "Главный старшина", "Главный корабельный старшина",
    "Мичман", "Старший мичман",
    "Капитан-лейтенант", "Капитан 3 ранга", "Капитан 2 ранга", "Капитан 1 ранга",
    "Контр-адмирал", "Вице-адмирал", "Адмирал", "Адмирал флота",
)

data class PatientFieldDef(
    val key: String,
    val label: String,
    val type: String,
    val required: Boolean,
    val defaultVisible: Boolean,
    val options: String = "",
)

val PATIENT_FIELDS: List<PatientFieldDef> = listOf(
    PatientFieldDef("number", "Номер", "NUMBER", false, true),
    PatientFieldDef("lastName", "Фамилия", "TEXT", true, true),
    PatientFieldDef("firstName", "Имя", "TEXT", true, true),
    PatientFieldDef("middleName", "Отчество", "TEXT", false, true),
    PatientFieldDef("birthDate", "Дата рождения", "DATE", true, true),
    PatientFieldDef("sex", "Пол", "SWITCH", true, true),
    PatientFieldDef("idSeries", "Удостоверение личности: Серия", "TEXT", false, false),
    PatientFieldDef("idNumber", "Удостоверение личности: Номер", "TEXT", false, false),
    PatientFieldDef("serviceDate", "Дата поступления на службу", "DATE", false, false),
    PatientFieldDef("rvk", "РВК", "TEXT", false, false),
    PatientFieldDef("rank", "Войсковое звание", "DROPDOWN", true, true, MILITARY_RANKS.joinToString(",")),
    PatientFieldDef("unit", "Войсковая часть", "TEXT", true, true),
    PatientFieldDef("position", "Должность, специальность", "TEXT", false, false),
    PatientFieldDef("admissionDate", "Дата поступления", "DATE", true, true),
    PatientFieldDef("referredBy", "Кем направлен больной", "TEXT", false, false),
    PatientFieldDef("emergency", "Доставлен по экстренным показаниям", "SWITCH", false, false),
    PatientFieldDef("illnessStart", "Начало заболевания, травмы", "DATE", false, false),
    PatientFieldDef("category", "Категория", "DROPDOWN", true, true, "по призыву,по контракту,по мобилизации,военные сборы,курсант,курсант-контрактник,абитуриент"),
    PatientFieldDef("personalNumber", "Личный номер", "TEXT", false, true),
    PatientFieldDef("svo", "СВО", "CHECKBOX", false, true),
    PatientFieldDef("soch", "СОЧ", "CHECKBOX", false, true),
)

val REPORT_SPECIAL_FIELDS: List<PatientFieldDef> = listOf(
    PatientFieldDef("discharged", "Выписан", "CHECKBOX", false, false),
)

fun patientFieldByKey(key: String): PatientFieldDef? =
    PATIENT_FIELDS.firstOrNull { it.key == key }
        ?: REPORT_SPECIAL_FIELDS.firstOrNull { it.key == key }

const val PATIENT_GLOBAL_KEY = "patient_add"

fun isCustomKey(key: String): Boolean = key.startsWith("custom:")

fun customFieldIdFromKey(key: String): Long = key.removePrefix("custom:").toLongOrNull() ?: 0L

fun patientValue(p: PatientEntity, key: String): String = when (key) {
    "number" -> p.number.toString()
    "personalNumber" -> p.personalNumber
    "lastName" -> p.lastName
    "firstName" -> p.firstName
    "middleName" -> p.middleName
    "birthDate" -> p.birthDate
    "sex" -> p.sex
    "idSeries" -> p.idSeries
    "idNumber" -> p.idNumber
    "serviceDate" -> p.serviceDate
    "rvk" -> p.rvk
    "rank" -> p.rank
    "unit" -> p.unit
    "position" -> p.position
    "admissionDate" -> p.admissionDate
    "referredBy" -> p.referredBy
    "emergency" -> p.emergency
    "illnessStart" -> p.illnessStart
    "category" -> p.category
    "svo" -> if (p.svo == 1) "true" else "false"
    "soch" -> if (p.soch == 1) "true" else "false"
    "discharged" -> if (p.discharged == 1) "true" else "false"
    else -> ""
}

fun patientValueFromMap(customValues: Map<Long, String>, key: String): String {
    if (isCustomKey(key)) return customValues[customFieldIdFromKey(key)] ?: ""
    return ""
}
