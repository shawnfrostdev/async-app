package app.async.data.worker

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import android.os.Trace // Import Trace
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import app.async.data.database.AlbumEntity
import app.async.data.database.ArtistEntity
import app.async.data.database.MusicDao
import app.async.data.database.SongEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val musicDao: MusicDao
) : CoroutineWorker(appContext, workerParams) {

    private val contentResolver: ContentResolver = appContext.contentResolver

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Trace.beginSection("SyncWorker.doWork")
        try {
            Log.i(TAG, "Starting MediaStore synchronization...")
            val startTime = System.currentTimeMillis()

            val songs = fetchAllMusicData()
            Log.i(TAG, "Fetched ${songs.size} songs from MediaStore.")

            if (songs.isNotEmpty()) {
                val existingLyricsMap = musicDao.getAllSongsList().associate { it.id to it.lyrics }

                val (correctedSongs, albums, artists) = preProcessAndDeduplicate(songs)

                val songsWithPreservedLyrics = correctedSongs.map { songEntity ->
                    val existingLyrics = existingLyricsMap[songEntity.id]
                    if (!existingLyrics.isNullOrBlank()) {
                        songEntity.copy(lyrics = existingLyrics)
                    } else {
                        songEntity
                    }
                }

                musicDao.clearAllMusicData()
                musicDao.insertMusicData(songsWithPreservedLyrics, albums, artists)

                Log.i(TAG, "Music data insertion call completed.")
            } else {
                Log.w(TAG, "MediaStore fetch resulted in empty list for songs. No data will be inserted.")
            }

            val endTime = System.currentTimeMillis()
            Log.i(TAG, "MediaStore synchronization completed successfully in ${endTime - startTime}ms.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during MediaStore synchronization", e)
            Result.failure()
        } finally {
            Trace.endSection() // End SyncWorker.doWork
        }
    }

    private fun preProcessAndDeduplicate(songs: List<SongEntity>): Triple<List<SongEntity>, List<AlbumEntity>, List<ArtistEntity>> {
        // Artist de-duplication
        val artistMap = mutableMapOf<String, Long>()
        songs.forEach { song ->
            if (!artistMap.containsKey(song.artistName)) {
                artistMap[song.artistName] = song.artistId
            }
        }

        // Album de-duplication
        val albumMap = mutableMapOf<Pair<String, String>, Long>()
        songs.forEach { song ->
            val key = Pair(song.albumName, song.artistName)
            if (!albumMap.containsKey(key)) {
                albumMap[key] = song.albumId
            }
        }

        val correctedSongs = songs.map { song ->
            val canonicalArtistId = artistMap[song.artistName]!!
            val canonicalAlbumId = albumMap[Pair(song.albumName, song.artistName)]!!
            song.copy(artistId = canonicalArtistId, albumId = canonicalAlbumId)
        }

        // Create unique albums
        val albums = correctedSongs.groupBy { it.albumId }.map { (albumId, songsInAlbum) ->
            val firstSong = songsInAlbum.first()
            AlbumEntity(
                id = albumId,
                title = firstSong.albumName,
                artistName = firstSong.artistName,
                artistId = firstSong.artistId,
                albumArtUriString = firstSong.albumArtUriString,
                songCount = songsInAlbum.size
            )
        }

        // Create unique artists
        val artists = correctedSongs.groupBy { it.artistId }.map { (artistId, songsByArtist) ->
            val firstSong = songsByArtist.first()
            ArtistEntity(
                id = artistId,
                name = firstSong.artistName,
                trackCount = songsByArtist.size
            )
        }

        return Triple(correctedSongs, albums, artists)
    }

    private fun fetchAllMusicData(): List<SongEntity> {
        Trace.beginSection("SyncWorker.fetchAllMusicData")
        val songs = mutableListOf<SongEntity>()
        // Removed genre mapping from initial sync for performance.
        // Genre will be "Unknown Genre" or from static genres for now.

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.TRACK
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf("10000")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val genreCol = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)


            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val songArtistId = cursor.getLong(artistIdCol)
                val filePath = cursor.getString(dataCol) ?: ""
                val parentDir = java.io.File(filePath).parent ?: ""

                val contentUriString = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                ).toString()
                val albumArtUriString = ContentUris.withAppendedId(
                    "content://media/external/audio/albumart".toUri(), albumId
                ).toString()

//                val genreName = run {
//                    val staticGenres = GenreDataSource.getStaticGenres()
//                    if (staticGenres.isNotEmpty()) {
//                        staticGenres[(id % staticGenres.size).toInt()].name
//                    } else {
//                        "Unknown Genre"
//                    }
//                }

                songs.add(
                    SongEntity(
                        id = id,
                        title = cursor.getString(titleCol) ?: "Unknown Title",
                        artistName = cursor.getString(artistCol) ?: "Unknown Artist",
                        artistId = songArtistId,
                        albumName = cursor.getString(albumCol) ?: "Unknown Album",
                        albumId = albumId,
                        contentUriString = contentUriString,
                        albumArtUriString = albumArtUriString,
                        duration = cursor.getLong(durationCol),
                        genre = if (genreCol != -1) cursor.getString(genreCol) else null,
                        filePath = filePath,
                        parentDirectoryPath = parentDir,
                        trackNumber = cursor.getInt(trackCol)
                    )
                )
            }
        }
        Trace.endSection() // End SyncWorker.fetchAllMusicData
        return songs
    }

        

    companion object {
        const val WORK_NAME = "app.async.data.worker.SyncWorker"
        private const val TAG = "SyncWorker"

        fun startUpSyncWork() = OneTimeWorkRequestBuilder<SyncWorker>().build()
    }
}