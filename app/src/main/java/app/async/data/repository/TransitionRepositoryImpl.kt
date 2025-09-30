package app.async.data.repository

import app.async.data.database.TransitionDao
import app.async.data.database.TransitionRuleEntity
import app.async.data.model.TransitionRule
import app.async.data.model.TransitionSettings
import app.async.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransitionRepositoryImpl @Inject constructor(
    private val transitionDao: TransitionDao,
    private val userPreferences: UserPreferencesRepository
) : TransitionRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun resolveTransitionSettings(
        playlistId: String,
        fromTrackId: String,
        toTrackId: String
    ): Flow<TransitionSettings> {
        // Chain the lookups according to priority: specific -> playlist -> global
        return transitionDao.getSpecificRule(playlistId, fromTrackId, toTrackId)
            .flatMapLatest { specificRule ->
                if (specificRule != null) {
                    flowOf(specificRule.settings)
                } else {
                    transitionDao.getPlaylistDefaultRule(playlistId).flatMapLatest { playlistRule ->
                        if (playlistRule != null) {
                            flowOf(playlistRule.settings)
                        } else {
                            userPreferences.globalTransitionSettingsFlow
                        }
                    }
                }
            }
    }

    override fun getAllRulesForPlaylist(playlistId: String): Flow<List<TransitionRule>> {
        return transitionDao.getAllRulesForPlaylist(playlistId).map { entities ->
            entities.map { it.toModel() }
        }
    }

    override fun getPlaylistDefaultRule(playlistId: String): Flow<TransitionRule?> {
        return transitionDao.getPlaylistDefaultRule(playlistId).map { entity ->
            entity?.toModel()
        }
    }

    override suspend fun saveRule(rule: TransitionRule) {
        transitionDao.setRule(rule.toEntity())
    }

    override suspend fun deleteRule(ruleId: Long) {
        transitionDao.deleteRule(ruleId)
    }

    override suspend fun deletePlaylistDefaultRule(playlistId: String) {
        transitionDao.deletePlaylistDefaultRule(playlistId)
    }

    override fun getGlobalSettings(): Flow<TransitionSettings> {
        return userPreferences.globalTransitionSettingsFlow
    }

    override suspend fun saveGlobalSettings(settings: TransitionSettings) {
        userPreferences.saveGlobalTransitionSettings(settings)
    }

    // --- Mappers ---

    private fun TransitionRuleEntity.toModel(): TransitionRule {
        return TransitionRule(
            id = this.id,
            playlistId = this.playlistId,
            fromTrackId = this.fromTrackId,
            toTrackId = this.toTrackId,
            settings = this.settings
        )
    }

    private fun TransitionRule.toEntity(): TransitionRuleEntity {
        // The ID is included for updates. If it's the default 0, Room treats it as a new entry for auto-generation.
        // The unique index on (playlistId, from, to) ensures upsert logic works correctly.
        return TransitionRuleEntity(
            id = this.id,
            playlistId = this.playlistId,
            fromTrackId = this.fromTrackId,
            toTrackId = this.toTrackId,
            settings = this.settings
        )
    }
}
