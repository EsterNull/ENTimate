package com.example.entimate.ui

fun String.stripNewlines(): String = this.replace("\n", "").replace("\r", "")
