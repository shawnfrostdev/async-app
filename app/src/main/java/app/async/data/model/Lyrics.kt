package app.async.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Modelo de datos para las letras de las canciones.
 *
 * @param plain Lista de líneas de texto plano (no sincronizadas).
 * @param synced Lista de pares (milisegundos, línea) para letras sincronizadas.
 * @param areFromRemote Indica si las letras se obtuvieron de una fuente remota.
 */
@Serializable
data class Lyrics(
    val plain: List<String>? = null,
    val synced: List<SyncedLine>? = null,
    val areFromRemote: Boolean = false
)

@Serializable
data class SyncedLine(val time: Int, val line: String)