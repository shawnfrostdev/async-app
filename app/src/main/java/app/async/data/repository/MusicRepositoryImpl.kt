package app.async.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import app.async.data.model.Song
import app.async.data.database.MusicDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
// import kotlinx.coroutines.withContext // May not be needed for Flow transformations
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri
import app.async.data.model.Album
import app.async.data.database.SearchHistoryDao
import app.async.data.database.SearchHistoryEntity
import app.async.data.database.toSearchHistoryItem
import app.async.data.model.Artist
import app.async.data.model.Playlist
import app.async.data.model.SearchFilterType
import app.async.data.model.SearchHistoryItem
import app.async.data.model.SearchResultItem
import app.async.data.preferences.UserPreferencesRepository

import app.async.data.model.Genre
import app.async.data.database.SongEntity
import app.async.data.database.toAlbum
import app.async.data.database.toArtist
import app.async.data.database.toSong
import app.async.data.model.Lyrics
import app.async.data.model.SyncedLine
import app.async.data.network.lyrics.LrcLibApiService
import app.async.utils.LogUtils
import app.async.utils.LyricsUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first // Still needed for initialSetupDoneFlow.first() if used that way
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
// import kotlinx.coroutines.sync.withLock // May not be needed if directoryScanMutex logic changes
import java.io.File
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val musicDao: MusicDao,
    private val lrcLibApiService: LrcLibApiService
) : MusicRepository {

    private val directoryScanMutex = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAudioFiles(): Flow<List<Song>> {
        LogUtils.d(this, "getAudioFiles")
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.initialSetupDoneFlow
        ) { allowedDirs, initialSetupDone ->
            Pair(allowedDirs.toList(), initialSetupDone)
        }.flatMapLatest { (allowedDirs, initialSetupDone) ->
            if (initialSetupDone && allowedDirs.isEmpty()) {
                flowOf(emptyList<Song>()) // No directories allowed, return empty list of Songs
            } else {
                musicDao.getSongs(
                    allowedParentDirs = allowedDirs,
                    applyDirectoryFilter = initialSetupDone // Only apply filter if setup is done
                ).map { entities -> entities.map { it.toSong() } }
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getAlbums(): Flow<List<Album>> {
        LogUtils.d(this, "getAlbums")
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.initialSetupDoneFlow
        ) { allowedDirs, initialSetupDone ->
            Pair(allowedDirs.toList(), initialSetupDone)
        }.flatMapLatest { (allowedDirs, initialSetupDone) ->
            if (initialSetupDone && allowedDirs.isEmpty()) {
                flowOf(emptyList<Album>())
            } else {
                musicDao.getAlbums(
                    allowedParentDirs = allowedDirs,
                    applyDirectoryFilter = initialSetupDone
                ).map { entities -> entities.map { it.toAlbum() } }
            }
        }.flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAlbumById(id: Long): Flow<Album?> {
        LogUtils.d(this, "getAlbumById: $id")
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.initialSetupDoneFlow
        ) { allowedDirs, initialSetupDone ->
            Pair(allowedDirs.toList(), initialSetupDone)
        }.flatMapLatest { (allowedDirs, initialSetupDone) ->
            // Check if album has any songs in allowed directories
            musicDao.getSongsByAlbumId(id) // Assuming this DAO method exists or is added
                .map { songEntities ->
                    val permittedSongsInAlbum = songEntities.filter { songEntity ->
                        if (!initialSetupDone) true // if setup not done, all songs are permitted for this check
                        else allowedDirs.contains(songEntity.parentDirectoryPath)
                    }
                    if (initialSetupDone && allowedDirs.isEmpty() && songEntities.isNotEmpty()) {
                        null // Setup done, no allowed dirs, so album effectively not accessible
                    } else if (permittedSongsInAlbum.isNotEmpty() || !initialSetupDone) {
                        // Album has permitted songs OR initial setup not done (show all)
                        // Fetch the album details
                        musicDao.getAlbumById(id).map { it?.toAlbum() }
                    } else {
                        flowOf(null) // No permitted songs for this album
                    }
                }.flatMapLatest { it!! } // Flatten the Flow<Flow<Album?>> to Flow<Album?>
        }.flowOn(Dispatchers.IO)
        // Original simpler version (kept for reference, might be okay depending on requirements):
        // return musicDao.getAlbumById(id).map { it?.toAlbum() }.flowOn(Dispatchers.IO)
    }

    override fun getArtists(): Flow<List<Artist>> {
        LogUtils.d(this, "getArtists")
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.initialSetupDoneFlow
        ) { allowedDirs, initialSetupDone ->
            Pair(allowedDirs.toList(), initialSetupDone)
        }.flatMapLatest { (allowedDirs, initialSetupDone) ->
            if (initialSetupDone && allowedDirs.isEmpty()) {
                flowOf(emptyList<Artist>())
            } else {
                musicDao.getArtists(
                    allowedParentDirs = allowedDirs,
                    applyDirectoryFilter = initialSetupDone
                ).map { entities -> entities.map { it.toArtist() } }
            }
        }.flowOn(Dispatchers.IO)
    }

    // getSongsForAlbum and getSongsForArtist should also respect directory permissions
    override fun getSongsForAlbum(albumId: Long): Flow<List<Song>> {
        LogUtils.d(this, "getSongsForAlbum: $albumId")
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.initialSetupDoneFlow
        ) { allowedDirs, initialSetupDone ->
            Pair(allowedDirs.toList(), initialSetupDone)
        }.flatMapLatest { (allowedDirs, initialSetupDone) ->
            musicDao.getSongsByAlbumId(albumId).map { songEntities ->
                songEntities.filter { songEntity ->
                    !initialSetupDone || allowedDirs.contains(songEntity.parentDirectoryPath)
                }.map { it.toSong() }
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getArtistById(artistId: Long): Flow<Artist?> {
        LogUtils.d(this, "getArtistById: $artistId")
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.initialSetupDoneFlow
        ) { allowedDirs, initialSetupDone ->
            Pair(allowedDirs.toList(), initialSetupDone)
        }.flatMapLatest { (allowedDirs, initialSetupDone) ->
            musicDao.getSongsByArtistId(artistId)
                .map { songEntities ->
                    val permittedSongsInArtist = songEntities.filter { songEntity ->
                        if (!initialSetupDone) true
                        else allowedDirs.contains(songEntity.parentDirectoryPath)
                    }
                    if (initialSetupDone && allowedDirs.isEmpty() && songEntities.isNotEmpty()) {
                        null
                    } else if (permittedSongsInArtist.isNotEmpty() || !initialSetupDone) {
                        musicDao.getArtistById(artistId).map { it?.toArtist() }
                    } else {
                        flowOf(null)
                    }
                }.flatMapLatest { it!! }
        }.flowOn(Dispatchers.IO)
    }

    override fun getSongsForArtist(artistId: Long): Flow<List<Song>> {
        LogUtils.d(this, "getSongsForArtist: $artistId")
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.initialSetupDoneFlow
        ) { allowedDirs, initialSetupDone ->
            Pair(allowedDirs.toList(), initialSetupDone)
        }.flatMapLatest { (allowedDirs, initialSetupDone) ->
            musicDao.getSongsByArtistId(artistId).map { songEntities ->
                songEntities.filter { songEntity ->
                    !initialSetupDone || allowedDirs.contains(songEntity.parentDirectoryPath)
                }.map { it.toSong() }
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getAllUniqueAudioDirectories(): Set<String> = withContext(Dispatchers.IO) {
        LogUtils.d(this, "getAllUniqueAudioDirectories")
        directoryScanMutex.withLock {
            val directories = mutableSetOf<String>()
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, null, null
            )?.use { c ->
                val dataColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    File(c.getString(dataColumn)).parent?.let { directories.add(it) }
                }
            }
            LogUtils.i(this, "Found ${directories.size} unique audio directories")
            val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first()
            if (!initialSetupDone && directories.isNotEmpty()) {
                Log.i("MusicRepo", "Initial setup: saving all found audio directories (${directories.size}) as allowed.")
                userPreferencesRepository.updateAllowedDirectories(directories)
            }
            return@withLock directories
        }
    }

    override fun getAllUniqueAlbumArtUris(): Flow<List<Uri>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.initialSetupDoneFlow
        ) { allowedDirs, initialSetupDone ->
            Pair(allowedDirs.toList(), initialSetupDone)
        }.flatMapLatest { (allowedDirs, initialSetupDone) ->
            if (initialSetupDone && allowedDirs.isEmpty()) {
                flowOf(emptyList<Uri>())
            } else {
                // Use the already modified musicDao.getSongs which handles directory filtering
                musicDao.getSongs(
                    allowedParentDirs = allowedDirs,
                    applyDirectoryFilter = initialSetupDone
                ).map { songEntities ->
                    songEntities
                        .mapNotNull { it.albumArtUriString?.toUri() }
                        .distinct()
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    // --- Métodos de Búsqueda ---

    override fun searchSongs(query: String): Flow<List<Song>> {
        if (query.isBlank()) return flowOf(emptyList())
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.initialSetupDoneFlow
        ) { allowedDirs, initialSetupDone ->
            Pair(allowedDirs.toList(), initialSetupDone)
        }.flatMapLatest { (allowedDirs, initialSetupDone) ->
            if (initialSetupDone && allowedDirs.isEmpty()) {
                flowOf(emptyList<Song>())
            } else {
                musicDao.searchSongs(
                    query = query,
                    allowedParentDirs = allowedDirs,
                    applyDirectoryFilter = initialSetupDone
                ).map { entities -> entities.map { it.toSong() } }
            }
        }.flowOn(Dispatchers.IO)
    }


    override fun searchAlbums(query: String): Flow<List<Album>> {
        if (query.isBlank()) return flowOf(emptyList())
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.initialSetupDoneFlow
        ) { allowedDirs, initialSetupDone ->
            Pair(allowedDirs.toList(), initialSetupDone)
        }.flatMapLatest { (allowedDirs, initialSetupDone) ->
            if (initialSetupDone && allowedDirs.isEmpty()) {
                flowOf(emptyList<Album>())
            } else {
                musicDao.searchAlbums(
                    query = query,
                    allowedParentDirs = allowedDirs,
                    applyDirectoryFilter = initialSetupDone
                ).map { entities -> entities.map { it.toAlbum() } }
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun searchArtists(query: String): Flow<List<Artist>> {
        if (query.isBlank()) return flowOf(emptyList())
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.initialSetupDoneFlow
        ) { allowedDirs, initialSetupDone ->
            Pair(allowedDirs.toList(), initialSetupDone)
        }.flatMapLatest { (allowedDirs, initialSetupDone) ->
            if (initialSetupDone && allowedDirs.isEmpty()) {
                flowOf(emptyList<Artist>())
            } else {
                musicDao.searchArtists(
                    query = query,
                    allowedParentDirs = allowedDirs,
                    applyDirectoryFilter = initialSetupDone
                ).map { entities -> entities.map { it.toArtist() } }
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun searchPlaylists(query: String): List<Playlist> {
        if (query.isBlank()) return emptyList()
        Log.d("MusicRepositoryImpl", "searchPlaylists called with query: $query. Not implemented.")
        return emptyList()
    }

    override fun searchAll(query: String, filterType: SearchFilterType): Flow<List<SearchResultItem>> {
        if (query.isBlank()) return flowOf(emptyList())
        val playlistsFlow = flow { emit(searchPlaylists(query)) }

        return when (filterType) {
            SearchFilterType.ALL -> {
                combine(
                    searchSongs(query),
                    searchAlbums(query),
                    searchArtists(query),
                    playlistsFlow
                ) { songs, albums, artists, playlists ->
                    mutableListOf<SearchResultItem>().apply {
                        songs.forEach { add(SearchResultItem.SongItem(it)) }
                        albums.forEach { add(SearchResultItem.AlbumItem(it)) }
                        artists.forEach { add(SearchResultItem.ArtistItem(it)) }
                        playlists.forEach { add(SearchResultItem.PlaylistItem(it)) }
                    }
                }
            }
            SearchFilterType.SONGS -> searchSongs(query).map { songs -> songs.map { SearchResultItem.SongItem(it) } }
            SearchFilterType.ALBUMS -> searchAlbums(query).map { albums -> albums.map { SearchResultItem.AlbumItem(it) } }
            SearchFilterType.ARTISTS -> searchArtists(query).map { artists -> artists.map { SearchResultItem.ArtistItem(it) } }
            SearchFilterType.PLAYLISTS -> playlistsFlow.map { playlists -> playlists.map { SearchResultItem.PlaylistItem(it) } }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun addSearchHistoryItem(query: String) {
        withContext(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(query)
            searchHistoryDao.insert(SearchHistoryEntity(query = query, timestamp = System.currentTimeMillis()))
        }
    }

    override suspend fun getRecentSearchHistory(limit: Int): List<SearchHistoryItem> {
        return withContext(Dispatchers.IO) {
            searchHistoryDao.getRecentSearches(limit).map { it.toSearchHistoryItem() }
        }
    }

    override suspend fun deleteSearchHistoryItemByQuery(query: String) {
        withContext(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(query)
        }
    }

    override suspend fun clearSearchHistory() {
        withContext(Dispatchers.IO) {
            searchHistoryDao.clearAll()
        }
    }

    override fun getMusicByGenre(genreId: String): Flow<List<Song>> {
        return userPreferencesRepository.mockGenresEnabledFlow.flatMapLatest { mockEnabled ->
            if (mockEnabled) {
                // Mock mode: Use the static genre name for filtering.
                val genreName = "Mock"//GenreDataSource.getStaticGenres().find { it.id.equals(genreId, ignoreCase = true) }?.name ?: genreId
                getAudioFiles().map { songs ->
                    songs.filter { it.genre.equals(genreName, ignoreCase = true) }
                }
            } else {
                // Real mode: Use the genreId directly, which corresponds to the actual genre name from metadata.
                getAudioFiles().map { songs ->
                    if (genreId.equals("unknown", ignoreCase = true)) {
                        // Filter for songs with no genre or an empty genre string.
                        songs.filter { it.genre.isNullOrBlank() }
                    } else {
                        // Filter for songs that match the given genre name.
                        songs.filter { it.genre.equals(genreId, ignoreCase = true) }
                    }
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getSongsByIds(songIds: List<String>): Flow<List<Song>> {
        if (songIds.isEmpty()) return flowOf(emptyList())
        val longIds = songIds.mapNotNull { it.toLongOrNull() }
        if (longIds.isEmpty()) return flowOf(emptyList())

        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.initialSetupDoneFlow
        ) { allowedDirs, initialSetupDone ->
            Pair(allowedDirs.toList(), initialSetupDone)
        }.flatMapLatest { (allowedDirs, initialSetupDone) ->
            if (initialSetupDone && allowedDirs.isEmpty() && longIds.isNotEmpty()) {
                // If setup is done, no dirs are allowed, but we are asking for specific songs,
                // effectively these songs are not accessible.
                flowOf(emptyList<Song>())
            } else {
                musicDao.getSongsByIds(
                    songIds = longIds,
                    allowedParentDirs = allowedDirs,
                    applyDirectoryFilter = initialSetupDone
                ).map { entities ->
                    val songsMap = entities.associateBy { it.id.toString() }
                    // Ensure the order of original songIds is preserved
                    songIds.mapNotNull { idToFind -> songsMap[idToFind]?.toSong() }
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun invalidateCachesDependentOnAllowedDirectories() {
        Log.i("MusicRepo", "invalidateCachesDependentOnAllowedDirectories called. Reactive flows will update automatically.")
    }

    suspend fun syncMusicFromContentResolver() {
        // Esta función ahora está en SyncWorker. Se deja el esqueleto por si se llama desde otro lugar.
        Log.w("MusicRepo", "syncMusicFromContentResolver was called directly on repository. This should be handled by SyncWorker.")
    }

    // Implementación de las nuevas funciones suspend para carga única
    override suspend fun getAllAlbumsOnce(): List<Album> = withContext(Dispatchers.IO) {
        val allowedDirs = userPreferencesRepository.allowedDirectoriesFlow.first().toList()
        val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first()

        if (initialSetupDone && allowedDirs.isEmpty()) {
            emptyList()
        } else {
            musicDao.getAllAlbumsList( // Llamando a la nueva función DAO suspend
                allowedParentDirs = allowedDirs,
                applyDirectoryFilter = initialSetupDone
            ).map { it.toAlbum() }
        }
    }

    override suspend fun getAllArtistsOnce(): List<Artist> = withContext(Dispatchers.IO) {
        val allowedDirs = userPreferencesRepository.allowedDirectoriesFlow.first().toList()
        val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first()

        if (initialSetupDone && allowedDirs.isEmpty()) {
            emptyList()
        } else {
            musicDao.getAllArtistsList( // Llamando a la nueva función DAO suspend
                allowedParentDirs = allowedDirs,
                applyDirectoryFilter = initialSetupDone
            ).map { it.toArtist() }
        }
    }

    override suspend fun toggleFavoriteStatus(songId: String): Boolean = withContext(Dispatchers.IO) {
        val songLongId = songId.toLongOrNull()
        if (songLongId == null) {
            Log.w("MusicRepo", "Invalid songId format for toggleFavoriteStatus: $songId")
            // Podrías querer devolver el estado actual o lanzar una excepción.
            // Por ahora, si el ID no es válido, no hacemos nada y devolvemos false (o un estado anterior si lo tuviéramos).
            // Para ser más robusto, deberíamos obtener el estado actual si es posible, pero sin ID válido es difícil.
            return@withContext false // O lanzar IllegalArgumentException
        }
        return@withContext musicDao.toggleFavoriteStatus(songLongId)
    }

    override fun getSong(songId: String): Flow<Song?> {
        val songLongId = songId.toLongOrNull()
        if (songLongId == null) {
            Log.w("MusicRepo", "Invalid songId format for getSong: $songId")
            return flowOf(null)
        }
        // Similar a getAlbumById, necesitamos considerar los directorios permitidos.
        // Si una canción existe pero está en un directorio no permitido, no debería devolverse.
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.initialSetupDoneFlow
        ) { allowedDirs, initialSetupDone ->
            Pair(allowedDirs.toList(), initialSetupDone)
        }.flatMapLatest { (allowedDirs, initialSetupDone) ->
            musicDao.getSongById(songLongId).map { songEntity ->
                if (songEntity == null) {
                    null
                } else {
                    val songIsPermitted = !initialSetupDone || allowedDirs.contains(songEntity.parentDirectoryPath)
                    if (initialSetupDone && allowedDirs.isEmpty()) { // Setup done, no dirs allowed
                        null
                    } else if (songIsPermitted) {
                        songEntity.toSong()
                    } else {
                        null
                    }
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getGenres(): Flow<List<Genre>> {
        return getAudioFiles().map { songs ->
            val genresMap = songs.groupBy { song ->
                song.genre?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown"
            }

            val dynamicGenres = genresMap.keys.mapNotNull { genreName ->
                val id = if (genreName.equals("Unknown", ignoreCase = true)) "unknown" else genreName.lowercase().replace(" ", "_")
                // Generate colors dynamically or use a default for "Unknown"
                val colorInt = genreName.hashCode()
                val lightColorHex = "#${(colorInt and 0x00FFFFFF).toString(16).padStart(6, '0').uppercase()}"
                // Simple inversion for dark color, or use a predefined set
                val darkColorHex = "#${((colorInt xor 0xFFFFFF) and 0x00FFFFFF).toString(16).padStart(6, '0').uppercase()}"

                Genre(
                    id = id,
                    name = genreName,
                    lightColorHex = lightColorHex,
                    onLightColorHex = "#000000", // Default black for light theme text
                    darkColorHex = darkColorHex,
                    onDarkColorHex = "#FFFFFF"  // Default white for dark theme text
                )
            }.sortedBy { it.name }

            // Ensure "Unknown" genre is last if it exists.
            val unknownGenre = dynamicGenres.find { it.id == "unknown" }
            if (unknownGenre != null) {
                (dynamicGenres.filterNot { it.id == "unknown" } + unknownGenre)
            } else {
                dynamicGenres
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getLyrics(song: Song): Lyrics? {
        // 1. Check if lyrics are already in the song object (from DB)
        if (!song.lyrics.isNullOrBlank()) {
            val lines = song.lyrics.lines()
            val isLrc = lines.any { it.matches(Regex("\\[\\d{2}:\\d{2}\\.\\d{2,3}].*")) }
            if (isLrc) {
                val syncedLines = lines.mapNotNull { line ->
                    val match = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)").find(line)
                    if (match != null) {
                        val (min, sec, ms, text) = match.destructured
                        val time = min.toInt() * 60000 + sec.toInt() * 1000 + ms.toInt()
                        SyncedLine(time, text.trim())
                    } else {
                        null
                    }
                }
                return Lyrics(plain = lines, synced = syncedLines, areFromRemote = false)
            } else {
                return Lyrics(plain = lines, synced = null, areFromRemote = false)
            }
        }

        // 2. If not in DB, try to read from the file's metadata
        return try {
            val file = File(song.contentUriString)
            if (!file.exists()) return null

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            val lyricsFromFile = tag?.getFirst(FieldKey.LYRICS)

            if (!lyricsFromFile.isNullOrBlank()) {
                // 3. If found, update DB for caching
                musicDao.updateLyrics(song.id.toLong(), lyricsFromFile)

                // 4. Parse and return lyrics
                val lines = lyricsFromFile.lines()
                val isLrc = lines.any { it.matches(Regex("\\[\\d{2}:\\d{2}\\.\\d{2,3}].*")) }
                 if (isLrc) {
                    val syncedLines = lines.mapNotNull { line ->
                        val match = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)").find(line)
                        if (match != null) {
                            val (min, sec, ms, text) = match.destructured
                            val time = min.toInt() * 60000 + sec.toInt() * 1000 + ms.toInt()
                            SyncedLine(time, text.trim())
                        } else {
                            null
                        }
                    }
                    Lyrics(plain = lines, synced = syncedLines, areFromRemote = false)
                } else {
                    Lyrics(plain = lines, synced = null, areFromRemote = false)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("MusicRepositoryImpl", "Error reading lyrics from file for song: ${song.title}", e)
            null
        }
    }

    /**
     * Obtiene la letra de una canción desde la API de LRCLIB, la persiste en la base de datos
     * y la devuelve como un objeto Lyrics parseado.
     *
     * @param song La canción para la cual se buscará la letra.
     * @return Un objeto Result que contiene el objeto Lyrics si se encontró, o un error.
     */
    override suspend fun getLyricsFromRemote(song: Song): Result<Pair<Lyrics, String>> = withContext(Dispatchers.IO) {
        try {
            val response = lrcLibApiService.getLyrics(
                trackName = song.title,
                artistName = song.artist,
                albumName = song.album,
                duration = (song.duration / 1000).toInt()
            )

            if (response != null && (!response.syncedLyrics.isNullOrEmpty() || !response.plainLyrics.isNullOrEmpty())) {
                // Prioritize synced for saving, but parse both for returning
                val rawLyricsToSave = response.syncedLyrics ?: response.plainLyrics!!
                musicDao.updateLyrics(song.id.toLong(), rawLyricsToSave)

                val synced = response.syncedLyrics?.let { LyricsUtils.parseLyrics(it).synced }
                // If we have plain lyrics from the API, use them. Otherwise, create plain lyrics from the synced ones.
                val plain = response.plainLyrics?.lines() ?: synced?.map { it.line }

                if (synced.isNullOrEmpty() && plain.isNullOrEmpty()) {
                     return@withContext Result.failure(Exception("No lyrics found for this song."))
                }

                val parsedLyrics = Lyrics(
                    plain = plain,
                    synced = synced,
                    areFromRemote = true
                )

                Result.success(Pair(parsedLyrics, rawLyricsToSave))
            } else {
                Result.failure(Exception("No lyrics found for this song."))
            }
        } catch (e: Exception) {
            Log.e("MusicRepositoryImpl", "Error fetching lyrics from remote", e)
            Result.failure(e)
        }
    }
}