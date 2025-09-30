package app.async.presentation.components.subcomps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import app.async.R
import app.async.data.model.SortOption
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

val defaultShape = RoundedCornerShape(26.dp) // Fallback shape

@Composable
fun LibraryActionRow(
    currentPage: Int,
    onMainActionClick: () -> Unit,
    iconRotation: Float,
    showSortButton: Boolean,
    onSortIconClick: () -> Unit,
    showSortMenu: Boolean,
    onDismissSortMenu: () -> Unit,
    currentSortOptionsForTab: List<SortOption>,
    selectedSortOption: SortOption,
    onSortOptionSelected: (SortOption) -> Unit,
    isPlaylistTab: Boolean,
    onGenerateWithAiClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val newButtonEndCorner by animateDpAsState(
        targetValue = if (isPlaylistTab) 6.dp else 26.dp,
        label = "NewButtonEndCorner"
    )

    val generateButtonStartCorner by animateDpAsState(
        targetValue = if (isPlaylistTab) 6.dp else 26.dp,
        label = "GenerateButtonStartCorner"
    )
    Row(
        modifier = modifier
            //.animateContentSize()
            .fillMaxWidth()
            .padding(start = 4.dp), // Adjusted bottom padding
        horizontalArrangement = Arrangement.Absolute.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                //modifier = Modifier.animateContentSize(),
                onClick = onMainActionClick,
                shape = RoundedCornerShape(
                    topStart = 26.dp,
                    bottomStart = 26.dp,
                    topEnd = newButtonEndCorner,
                    bottomEnd = newButtonEndCorner
                ),
                // shape = AbsoluteSmoothCornerShape(cornerRadiusTL = 26.dp, ...), // Your custom shape
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp, // Slightly reduced elevation for a normal button
                    pressedElevation = 6.dp
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp) // Standard button padding
            ) {
                val icon = if (currentPage == 3) Icons.Rounded.PlaylistAdd else Icons.Rounded.Shuffle
                val text = if (currentPage == 3) "New" else "Shuffle"
                val contentDesc = if (currentPage == 3) "Create New Playlist" else "Shuffle Play"

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDesc,
                        modifier = Modifier
                            .size(20.dp)
                            .then(Modifier.rotate(iconRotation)) // Only rotate shuffle
                    )
                    Text(
                        modifier = Modifier.animateContentSize(),
                        text = text,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            AnimatedVisibility(
                visible = isPlaylistTab,
                enter = fadeIn() + expandHorizontally(
                    expandFrom = Alignment.Start,
                    clip = false, // <— evita el “corte” durante la expansión
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
                exit = fadeOut() + shrinkHorizontally(
                    shrinkTowards = Alignment.Start,
                    clip = false, // <— evita el “corte” durante la expansión
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            ) {
                Row {
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onGenerateWithAiClick,
                        shape = RoundedCornerShape(
                            topStart = generateButtonStartCorner,
                            bottomStart = generateButtonStartCorner,
                            topEnd = 26.dp,
                            bottomEnd = 26.dp
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 6.dp
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.generate_playlist_ai),
                                contentDescription = "Generate with AI",
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Generate",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Sort Button and Dropdown Menu
        if (showSortButton) {
            Box { // Box is needed for DropdownMenu positioning
                FilledTonalIconButton(onClick = onSortIconClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = "Sort Options",
                        //tint = MaterialTheme.colorScheme.onSurfaceVariant // Good contrast
                    )
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = onDismissSortMenu,
                    properties = PopupProperties(
                        clippingEnabled = true
                    ),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTL = 22.dp,
                        smoothnessAsPercentBR = 60,
                        cornerRadiusTR = 22.dp,
                        smoothnessAsPercentTL = 60,
                        cornerRadiusBL = 22.dp,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusBR = 22.dp,
                        smoothnessAsPercentBL = 60
                    ),
                    containerColor = Color.Transparent,
                    shadowElevation = 0.dp,
                    modifier = Modifier.background(
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) // Custom background for dropdown
                ) {
                    currentSortOptionsForTab.forEach { option: SortOption ->
                        val enabled = option == selectedSortOption
                        DropdownMenuItem(
                            modifier = Modifier
                                .padding(4.dp)
                                .padding(horizontal = 8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow, //if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                    shape = if (enabled) CircleShape else AbsoluteSmoothCornerShape(
                                        cornerRadiusTL = 12.dp,
                                        smoothnessAsPercentBR = 60,
                                        cornerRadiusTR = 12.dp,
                                        smoothnessAsPercentTL = 60,
                                        cornerRadiusBL = 12.dp,
                                        smoothnessAsPercentTR = 60,
                                        cornerRadiusBR = 12.dp,
                                        smoothnessAsPercentBL = 60
                                    )
                                )
                                .clip(if (enabled) CircleShape else RoundedCornerShape(12.dp)),
                            text = { Text(option.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {
                                onSortOptionSelected(option)
                                // onDismissSortMenu() // Already called in LibraryScreen's onSortOptionSelected lambda
                            },
                            leadingIcon = if (enabled) { // Check if it's the selected one
                                {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
        } else {
            // If no sort button, add a Spacer to maintain balance if the main button is not centered
            // Or ensure the main button is centered if it's the only element.
            // For SpaceBetween arrangement, this side will just be empty.
        }
    }
}