package app.async.ui.theme

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import app.async.presentation.viewmodel.ColorSchemePair
import kotlin.math.max
import androidx.core.graphics.scale

private fun Color.toHct(): Triple<Float, Float, Float> {
    val hsl = FloatArray(3)
    ColorUtils.RGBToHSL(red.toByteInt(), green.toByteInt(), blue.toByteInt(), hsl)
    return Triple(hsl[0], hsl[1], hsl[2])
}

private fun hctToColor(hue: Float, chroma: Float, tone: Float): Color {
    val hsl = floatArrayOf(hue.coerceIn(0f, 360f), chroma.coerceIn(0f, 1f), tone.coerceIn(0f, 1f))
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.tone(targetTone: Int): Color {
    val (_, chroma, _) = this.toHct()
    return hctToColor(this.toHct().first, chroma, targetTone / 100f)
}
private fun Color.withChroma(targetChroma: Float): Color {
    val (hue, _, tone) = this.toHct()
    return hctToColor(hue, targetChroma.coerceIn(0f,1f), tone)
}

private fun Float.toByteInt(): Int = (this * 255f).toInt()


private val extractedColorCache = LruCache<Int, Color>(20)

// --- Optimized Color Scheme Generation ---
fun extractSeedColor(bitmap: Bitmap): Color {
    val bitmapHash = bitmap.hashCode()

    extractedColorCache.get(bitmapHash)?.let { return it }

    val scaledBitmap = if (bitmap.width > 200 || bitmap.height > 200) {
        val scale = 200f / max(bitmap.width, bitmap.height)
        val scaledWidth = (bitmap.width * scale).toInt()
        val scaledHeight = (bitmap.height * scale).toInt()
        bitmap.scale(scaledWidth, scaledHeight)
    } else {
        bitmap
    }

    val palette = Palette.Builder(scaledBitmap)
        .maximumColorCount(16)
        .resizeBitmapArea(0)
        .clearFilters()
        .generate()

    val color = palette.vibrantSwatch?.rgb?.let { Color(it) }
        ?: palette.mutedSwatch?.rgb?.let { Color(it) }
        ?: palette.dominantSwatch?.rgb?.let { Color(it) }
        ?: DarkColorScheme.primary // Fallback
    
    // Store in cache
    extractedColorCache.put(bitmapHash, color)

    if (scaledBitmap != bitmap) {
        scaledBitmap.recycle()
    }
    
    return color
}

fun generateColorSchemeFromSeed(seedColor: Color): ColorSchemePair {

    // --- Tonal Palettes ---
    // Primary Tones
    val primary10 = seedColor.tone(10)
    val primary20 = seedColor.tone(20)
    val primary30 = seedColor.tone(30)
    val primary40 = seedColor.tone(40) // Primary Light
    val primary80 = seedColor.tone(80) // Primary Dark
    val primary90 = seedColor.tone(90)
    val primary100= seedColor.tone(100)


    // Secondary Tones (Shift hue, adjust chroma)
    val secondarySeed = hctToColor((seedColor.toHct().first + 60f) % 360f, 0.16f, seedColor.toHct().third)
    val secondary10 = secondarySeed.tone(10)
    val secondary20 = secondarySeed.tone(20)
    val secondary30 = secondarySeed.tone(30)
    val secondary40 = secondarySeed.tone(40)
    val secondary80 = secondarySeed.tone(80)
    val secondary90 = secondarySeed.tone(90)
    val secondary100= secondarySeed.tone(100)

    // Tertiary Tones (Shift hue differently, adjust chroma)
    val tertiarySeed = hctToColor((seedColor.toHct().first + 120f) % 360f, 0.24f, seedColor.toHct().third)
    val tertiary10 = tertiarySeed.tone(10)
    val tertiary20 = tertiarySeed.tone(20)
    val tertiary30 = tertiarySeed.tone(30)
    val tertiary40 = tertiarySeed.tone(40)
    val tertiary80 = tertiarySeed.tone(80)
    val tertiary90 = tertiarySeed.tone(90)
    val tertiary100= tertiarySeed.tone(100)

    // Neutral Tones (Very low chroma from seed)
    val neutralSeed = seedColor.withChroma(0.04f)
    val neutral10 = neutralSeed.tone(10) // Surface Dark, Background Dark
    val neutral20 = neutralSeed.tone(20)
    val neutral30 = neutralSeed.tone(30) // SurfaceVariant Dark
    val neutral90 = neutralSeed.tone(90) // SurfaceVariant Light, OnPrimaryContainer Dark
    val neutral95 = neutralSeed.tone(95)
    val neutral99 = neutralSeed.tone(99) // Surface Light, Background Light
    val neutral100= neutralSeed.tone(100)


    // Light Color Scheme
    val lightScheme = lightColorScheme(
        primary = primary40,
        onPrimary = primary100,
        primaryContainer = primary90,
        onPrimaryContainer = primary10,
        secondary = secondary40,
        onSecondary = secondary100,
        secondaryContainer = secondary90,
        onSecondaryContainer = secondary10,
        tertiary = tertiary40,
        onTertiary = tertiary100,
        tertiaryContainer = tertiary90,
        onTertiaryContainer = tertiary10,
        error = Color(0xFFBA1A1A), // M3 Defaults
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = neutral99,
        onBackground = neutral10,
        surface = neutral99,
        onSurface = neutral10,
        surfaceVariant = neutral90,
        onSurfaceVariant = neutral30,
        outline = neutralSeed.tone(50),
        inverseOnSurface = neutral95,
        inverseSurface = neutral20,
        inversePrimary = primary80,
        surfaceTint = primary40,
        outlineVariant = neutralSeed.tone(80),
        scrim = Color.Black
    )

    // Dark Color Scheme
    val darkScheme = darkColorScheme(
        primary = primary80,
        onPrimary = primary20,
        primaryContainer = primary30,
        onPrimaryContainer = primary90,
        secondary = secondary80,
        onSecondary = secondary20,
        secondaryContainer = secondary30,
        onSecondaryContainer = secondary90,
        tertiary = tertiary80,
        onTertiary = tertiary20,
        tertiaryContainer = tertiary30,
        onTertiaryContainer = tertiary90,
        error = Color(0xFFFFB4AB), // M3 Defaults
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = neutral10, // Darker background
        onBackground = neutral90,
        surface = neutral10.copy(alpha = (neutral10.toHct().third + 0.05f).coerceIn(0f,1f)), // Slightly lighter surface
        onSurface = neutral90,
        surfaceVariant = neutral30,
        onSurfaceVariant = neutralSeed.tone(80),
        outline = neutralSeed.tone(60),
        inverseOnSurface = neutral20,
        inverseSurface = neutral90,
        inversePrimary = primary40,
        surfaceTint = primary80,
        outlineVariant = neutral30,
        scrim = Color.Black
    )
    return ColorSchemePair(lightScheme, darkScheme)
}