package com.pets.desktoppets.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pets.desktoppets.data.CharacterInfo

@Composable
fun CharactersScreen(ui: UiState, vm: PetsViewModel, onSelectedGoHome: () -> Unit) {
    val p = LocalPalette.current
    var previewing by rememberSaveable { mutableStateOf<String?>(null) }   // only one video plays at a time
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    val shown = if (onlyFavorites) ui.characters.filter { it.id in ui.settings.favorites } else ui.characters
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Pets", style = shimmerTitle(36), modifier = Modifier.padding(start = 20.dp, top = 12.dp).entrance(0, "chars0"))
        Text("Tap a card to play it, then pick your pet.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp).entrance(1, "chars1"),
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = p.muted)
        if (ui.characters.isEmpty()) {
            Text("No pets available.", Modifier.padding(20.dp)); return@Column
        }
        Row(Modifier.padding(horizontal = 16.dp).entrance(2, "chars2"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!onlyFavorites, { onlyFavorites = false }, label = { Text("All ${ui.characters.size}") })
            FilterChip(onlyFavorites, { onlyFavorites = true }, label = { Text("♥ Favourites ${ui.settings.favorites.size}") })
        }
        if (shown.isEmpty()) Text("No favourites yet. Tap the heart on a card to add one.", Modifier.padding(20.dp), color = p.muted)
        LazyVerticalGrid(
            GridCells.Fixed(2), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(shown.size, key = { shown[it].id }) { i ->
                val c = shown[i]
                PetCard(c, vm, i, favorite = c.id in ui.settings.favorites, selected = ui.selected?.id == c.id, playing = previewing == c.id,
                    onToggle = { previewing = if (previewing == c.id) null else c.id }, onSelect = { vm.select(c) })
            }
            item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun PetCard(c: CharacterInfo, vm: PetsViewModel, index: Int, favorite: Boolean, selected: Boolean, playing: Boolean, onToggle: () -> Unit, onSelect: () -> Unit) {
    val p = LocalPalette.current
    val confetti = LocalConfetti.current
    val haptic = LocalHapticFeedback.current
    val poster = rememberPoster(vm.repo, c)
    val heart by animateFloatAsState(if (favorite) 1.25f else 1f, spring(dampingRatio = 0.25f, stiffness = Spring.StiffnessMedium), label = "heart")
    GlassCard(Modifier.entrance(index, c.id).neonBorder(26.dp, 3.dp, active = selected, speedMs = 2800).bouncy { onToggle() }) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                PetVideo(c, playing, poster, Modifier.fillMaxWidth(), corner = 18.dp)
                if (!playing) Icon(Icons.Default.PlayArrow, "Play", Modifier.align(Alignment.BottomStart).padding(8.dp).size(28.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.4f)), tint = Color.White)
                IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.toggleFavorite(c) }, modifier = Modifier.align(Alignment.TopEnd).size(48.dp)) {
                    Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if (favorite) "Remove ${c.name} from favourites" else "Add ${c.name} to favourites",
                        Modifier.graphicsLayer { scaleX = heart; scaleY = heart }, tint = if (favorite) Color(0xFFFF4D7D) else Color.White)
                }
            }
            Text(c.name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp,
                color = p.onSurface, modifier = Modifier.padding(horizontal = 4.dp))
            if (selected) {
                FilledTonalButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Active pet", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSelect(); confetti.fire() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp), shape = RoundedCornerShape(14.dp)) { Text("Select", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
