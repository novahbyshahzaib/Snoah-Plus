package com.snoah.plus.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.snoah.plus.data.Song
import com.snoah.plus.util.FormatUtils

@Composable
fun AlbumArt(
    song: Song?,
    modifier: Modifier = Modifier,
    cornerDp: Int = 16,
    isPlaying: Boolean = false
) {
    val shape = RoundedCornerShape(cornerDp.dp)
    val hasArt = song?.albumArtUri != null
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (hasArt) {
            // Load a small thumbnail, not the full-size album art:
            // full-size images on every row made fast scrolling jitter.
            val artRequest = coil.request.ImageRequest.Builder(LocalContext.current)
                .data(song!!.albumArtUri)
                .size(256)
                .crossfade(true)
                .build()
            AsyncImage(
                model = artRequest,
                contentDescription = song.title,
                modifier = Modifier.matchParentSize().clip(shape),
                contentScale = ContentScale.Crop
            )
        } else {
            // Premium default artwork: a clean music note on a muted
            // surface. While playing, moving equaliser bars replace
            // the note so songs without a picture still feel alive.
            if (isPlaying) {
                VisualizerBars(
                    isPlaying = true,
                    modifier = Modifier.size(width = 36.dp, height = 28.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    isFavourite: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onFavouriteClick: () -> Unit,
    onAddToPlaylistClick: (() -> Unit)? = null,
    onPlayNextClick: (() -> Unit)? = null,
    onAddToQueueClick: (() -> Unit)? = null,
    onDetailsClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(song = song, modifier = Modifier.size(52.dp), cornerDp = 14, isPlaying = isPlaying)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${song.artist} • ${FormatUtils.formatDuration(song.durationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isPlaying) {
                        Spacer(modifier = Modifier.width(6.dp))
                        VisualizerBars(isPlaying = true, modifier = Modifier.size(width = 18.dp, height = 16.dp))
                    }
                }
            }
            IconButton(onClick = onFavouriteClick) {
                Icon(
                    imageVector = if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
                    tint = if (isFavourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onPlayNextClick != null || onAddToQueueClick != null || onAddToPlaylistClick != null || onDetailsClick != null || onShareClick != null) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Song options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (onPlayNextClick != null) {
                            DropdownMenuItem(text = { Text("Play next") }, onClick = { menuExpanded = false; onPlayNextClick() })
                        }
                        if (onAddToQueueClick != null) {
                            DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menuExpanded = false; onAddToQueueClick() })
                        }
                        if (onAddToPlaylistClick != null) {
                            DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { menuExpanded = false; onAddToPlaylistClick() })
                        }
                        if (onShareClick != null) {
                            DropdownMenuItem(text = { Text("Share song") }, onClick = { menuExpanded = false; onShareClick() })
                        }
                        if (onDetailsClick != null) {
                            DropdownMenuItem(text = { Text("Details") }, onClick = { menuExpanded = false; onDetailsClick() })
                        }
                    }
                }
            }
        }
    }
}

/** Small animated bars that dance only while music is playing. */
@Composable
fun VisualizerBars(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "visualizer")
    val bar1 by transition.animateFloat(0.25f, 0.45f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "bar1")
    val bar2 by transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(560), RepeatMode.Reverse), label = "bar2")
    val bar3 by transition.animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(670), RepeatMode.Reverse), label = "bar3")
    val bar4 by transition.animateFloat(0.25f, 0.85f, infiniteRepeatable(tween(780), RepeatMode.Reverse), label = "bar4")
    val fractions = listOf(bar1, bar2, bar3, bar4)
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)) {
        fractions.forEach { fraction ->
            val h = if (isPlaying) fraction else 0.25f
            Box(modifier = Modifier.weight(1f).height(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((16f * h).dp)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurface)
                )
            }
        }
    }
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = onClick
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArt(song = song, modifier = Modifier.size(44.dp), cornerDp = 12, isPlaying = isPlaying)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isPlaying) {
                    VisualizerBars(isPlaying = true, modifier = Modifier.size(width = 20.dp, height = 16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier.fillMaxWidth().height(3.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(progress).height(3.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun EmptyState(title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun GradientHeader(title: String, subtitle: String) {
    // Kept name for call sites; the look is now premium monochrome:
    // solid background, strong title, muted subtitle, hairline divider.
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}
