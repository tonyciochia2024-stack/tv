package com.pets.desktoppets.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pets.desktoppets.data.CharacterInfo

@Composable
fun CharactersScreen(ui: UiState, vm: PetsViewModel, onSelectedGoHome: () -> Unit) {
    var previewing by rememberSaveable { mutableStateOf<String?>(null) }   // only one video plays at a time
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    val shown = if (onlyFavorites) ui.characters.filter { it.id in ui.settings.favorites } else ui.characters
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Pets", style = brandTitle(30), modifier = Modifier.padding(start = 20.dp, top = 12.dp))
        Text("Tap a card to play it, then pick your pet.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ui.characters.isEmpty()) {
            Text("No pets available.", Modifier.padding(20.dp)); return@Column
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!onlyFavorites, { onlyFavorites = false }, label = { Text("All ${ui.characters.size}") })
            FilterChip(onlyFavorites, { onlyFavorites = true }, label = { Text("Favourites ${ui.settings.favorites.size}") })
        }
        if (shown.isEmpty()) Text("No favourites yet. Tap the heart on a card to add one.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyVerticalGrid(
            GridCells.Fixed(2), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(shown, key = { it.id }) { c ->
                PetCard(c, vm, favorite = c.id in ui.settings.favorites, selected = ui.selected?.id == c.id, playing = previewing == c.id,
                    onToggle = { previewing = if (previewing == c.id) null else c.id }, onSelect = { vm.select(c) })
            }
            item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun PetCard(c: CharacterInfo, vm: PetsViewModel, favorite: Boolean, selected: Boolean, playing: Boolean, onToggle: () -> Unit, onSelect: () -> Unit) {
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "sel")
    val poster = remember(c.id) { vm.repo.poster(c) }
    Surface(
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp,
        border = BorderStroke(3.dp, border), modifier = Modifier.clickable(onClickLabel = "Play ${c.name}") { onToggle() },
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                PetVideo(c, playing, poster, Modifier.fillMaxWidth(), corner = 18.dp)
                if (!playing) Icon(Icons.Default.PlayArrow, "Play", Modifier.align(Alignment.BottomStart).padding(8.dp).size(26.dp), tint = Color.White)
                IconButton(onClick = { vm.toggleFavorite(c) }, modifier = Modifier.align(Alignment.TopEnd).size(48.dp)) {
                    Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if (favorite) "Remove ${c.name} from favourites" else "Add ${c.name} to favourites", tint = if (favorite) Color(0xFFFF5A7A) else Color.White)
                }
            }
            Text(c.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 4.dp))
            if (selected) {
                FilledTonalButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Active pet")
                }
            } else {
                Button(onClick = onSelect, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp), shape = RoundedCornerShape(14.dp)) { Text("Select") }
            }
        }
    }
}
