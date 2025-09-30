package app.async.data.media

import android.content.Context
import android.net.Uri
import android.util.Log
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import androidx.core.net.toUri
import app.async.data.database.MusicDao
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class SongMetadataEditor(private val context: Context, private val musicDao: MusicDao) {

    fun editSongMetadata(
        contentUri: String,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int
    ): Boolean {
        Timber.d("Editing metadata for URI: $contentUri")
        val uri = contentUri.toUri()
        var tempFile: File? = null
        try {
            // 1. Crear un archivo temporal a partir del URI de contenido
            tempFile = createTempFileFromUri(uri)
            if (tempFile == null) {
                Timber.tag("SongMetadataEditor").e("Failed to create temp file from URI.")
                return false
            }

            // 2. Leer el archivo de audio y modificar los metadatos
            val audioFile: AudioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tagOrCreateDefault
            tag.setField(FieldKey.TITLE, newTitle)
            tag.setField(FieldKey.ARTIST, newArtist)
            tag.setField(FieldKey.ALBUM, newAlbum)
            tag.setField(FieldKey.GENRE, newGenre)
            tag.setField(FieldKey.LYRICS, newLyrics)
            tag.setField(FieldKey.TRACK, newTrackNumber.toString())
            Timber.d("Committing changes to temp file.")
            audioFile.commit() // Esto guarda los cambios en el archivo temporal

            // 3. Sobrescribir el archivo original con el archivo temporal modificado
            Timber.d("Overwriting original file.")
            context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                    FileInputStream(tempFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } ?: run {
                Timber.tag("SongMetadataEditor").e("Failed to open FileDescriptor for writing.")
                return false
            }

            val songId = uri.lastPathSegment?.toLongOrNull()
            if (songId != null) {
                Timber.d("Updating database for songId: $songId")
                runBlocking {
                    musicDao.updateSongMetadata(songId, newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber)
                }
            }

            Timber.tag("SongMetadataEditor")
                .d("Successfully edited and saved metadata for URI: $contentUri")
            return true

        } catch (e: Exception) {
            Timber.tag("SongMetadataEditor").e(e, "Error editing metadata for URI: $contentUri")
            return false
        } finally {
            // 4. Limpiar el archivo temporal
            tempFile?.delete()
        }
    }

    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val tempFile = File.createTempFile("temp_audio", ".mp3", context.cacheDir)
            tempFile.deleteOnExit()
            val outputStream = FileOutputStream(tempFile)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Timber.tag("SongMetadataEditor").e(e, "Error creating temp file from URI")
            null
        }
    }
}
