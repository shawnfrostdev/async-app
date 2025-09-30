package app.async.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import coil.ImageLoader
import app.async.AsyncApplication
import app.async.data.database.AlbumArtThemeDao
import app.async.data.database.MusicDao
import app.async.data.database.AsyncDatabase
import app.async.data.database.SearchHistoryDao
import app.async.data.database.TransitionDao
import app.async.data.preferences.UserPreferencesRepository
import app.async.data.preferences.dataStore
import app.async.data.media.SongMetadataEditor
import app.async.data.network.lyrics.LrcLibApiService
import app.async.data.repository.MusicRepository
import app.async.data.repository.MusicRepositoryImpl
import app.async.data.repository.TransitionRepository
import app.async.data.repository.TransitionRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideApplication(@ApplicationContext app: Context): AsyncApplication {
        return app as AsyncApplication
    }

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.dataStore

    @Singleton
    @Provides
    fun provideJson(): Json { // Proveer Json
        return Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    @Singleton
    @Provides
    fun provideAsyncDatabase(@ApplicationContext context: Context): AsyncDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AsyncDatabase::class.java,
            "pixelplay_database"
        ).addMigrations(
            AsyncDatabase.MIGRATION_3_4,
            AsyncDatabase.MIGRATION_4_5
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Singleton
    @Provides
    fun provideAlbumArtThemeDao(database: AsyncDatabase): AlbumArtThemeDao {
        return database.albumArtThemeDao()
    }

    @Singleton
    @Provides
    fun provideSearchHistoryDao(database: AsyncDatabase): SearchHistoryDao { // NUEVO MÉTODO
        return database.searchHistoryDao()
    }

    @Singleton
    @Provides
    fun provideMusicDao(database: AsyncDatabase): MusicDao { // Proveer MusicDao
        return database.musicDao()
    }

    @Singleton
    @Provides
    fun provideTransitionDao(database: AsyncDatabase): TransitionDao {
        return database.transitionDao()
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .dispatcher(Dispatchers.Default) // Use CPU-bound dispatcher for decoding
            .allowHardware(true) // Re-enable hardware bitmaps for better performance
            .build()
    }

    @Provides
    @Singleton
    fun provideMusicRepository(
        @ApplicationContext context: Context,
        userPreferencesRepository: UserPreferencesRepository,
        searchHistoryDao: SearchHistoryDao,
        musicDao: MusicDao, // Añadir MusicDao como parámetro
        lrcLibApiService: LrcLibApiService
    ): MusicRepository {
        return MusicRepositoryImpl(
            context = context,
            userPreferencesRepository = userPreferencesRepository,
            searchHistoryDao = searchHistoryDao,
            musicDao = musicDao,
            lrcLibApiService = lrcLibApiService // Pasar MusicDao
        )
    }

    @Provides
    @Singleton
    fun provideTransitionRepository(
        transitionRepositoryImpl: TransitionRepositoryImpl
    ): TransitionRepository {
        return transitionRepositoryImpl
    }

    @Singleton
    @Provides
    fun provideSongMetadataEditor(@ApplicationContext context: Context, musicDao: MusicDao): SongMetadataEditor {
        return SongMetadataEditor(context, musicDao)
    }

    /**
     * Provee una instancia singleton de OkHttpClient con un interceptor de logging.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * Provee una instancia singleton de Retrofit para la API de LRCLIB.
     */
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Provee una instancia singleton del servicio de la API de LRCLIB.
     */
    @Provides
    @Singleton
    fun provideLrcLibApiService(retrofit: Retrofit): LrcLibApiService {
        return retrofit.create(LrcLibApiService::class.java)
    }
}