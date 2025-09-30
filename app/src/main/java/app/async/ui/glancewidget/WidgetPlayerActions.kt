package app.async.ui.glancewidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSessionService
import app.async.data.service.MusicService

object WidgetPlayerActions {
    const val PLAY_PAUSE = "app.async.PLAY_PAUSE"
    const val NEXT = "app.async.NEXT"
    const val PREVIOUS = "app.async.PREVIOUS"

    @OptIn(UnstableApi::class)
    fun playPause(context: Context): PendingIntent {
        val intent = Intent(context, MusicService::class.java).setAction(PLAY_PAUSE)
        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @OptIn(UnstableApi::class)
    fun next(context: Context): PendingIntent {
        val intent = Intent(context, MusicService::class.java).setAction(NEXT)
        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @OptIn(UnstableApi::class)
    fun previous(context: Context): PendingIntent {
        val intent = Intent(context, MusicService::class.java).setAction(PREVIOUS)
        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}