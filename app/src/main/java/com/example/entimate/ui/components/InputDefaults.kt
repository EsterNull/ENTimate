package com.example.entimate.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

val TextKeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.Sentences,
    imeAction = ImeAction.Next,
)

val TextKeyboardOptionsDone = KeyboardOptions(
    capitalization = KeyboardCapitalization.Sentences,
    imeAction = ImeAction.Done,
)
