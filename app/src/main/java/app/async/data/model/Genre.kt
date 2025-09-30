package app.async.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Genre(
    val id: String,
    val name: String,
    val iconResId: Int? = null, // Optional: For a Material symbol or drawable
    val lightColorHex: String? = null,
    val onLightColorHex: String? = null,
    val darkColorHex: String? = null,
    val onDarkColorHex: String? = null
)
