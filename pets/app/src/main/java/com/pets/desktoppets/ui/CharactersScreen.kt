package com.pets.desktoppets.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import com.pets.desktoppets.data.CharacterInfo

@Composable
fun CharactersScreen(ui: UiState, vm: PetsViewModel, onSelectedGoHome: () -> Unit) {
    var previewing by rememberSaveable { mutableStateOf<String?>(null) }   // only one animation plays at a time
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Characters", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Hair, modifier = Modifier.padding(start = 20.dp, top = 12.dp))
        Text("Tap a card to preview its animation, then pick your pet.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ui.characters.isEmpty()) {
            Text("No characters available.", Modifier.padding(20.dp))
            return@Column
        }
        LazyVerticalGrid(
            GridCells.Fixed(2), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ui.characters, key = { it.id }) { c ->
                CharacterCard(
                    c, vm, selected = ui.selected?.id == c.id, playing = previewing == c.id,
                    onToggle = { previewing = if (previewing == c.id) null else c.id },
                    onSelect = { vm.select(c) },
                )
            }
            item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun CharacterCard(c: CharacterInfo, vm: PetsViewModel, selected: Boolean, playing: Boolean, onToggle: () -> Unit, onSelect: () -> Unit) {
    val border by animateColorAsState(if (selected) Hair else Color.Transparent, label = "sel")
    val thumb = remember(c.id) { vm.repo.thumb(c) }
    Card(
        shape = MaterialTheme.shapes.medium, border = BorderStroke(3.dp, border),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.clickable(onClickLabel = "Preview ${c.name}") { onToggle() },
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Peach, Color(0xFFE7EEFF)))),
                contentAlignment = Alignment.Center,
            ) {
                if (thumb == null && !playing) Text("Unavailable", color = MaterialTheme.colorScheme.error)
                else PetAnimation(c, playing, thumb, Modifier.fillMaxHeight().padding(4.dp))
                if (!playing && thumb != null) Icon(Icons.Default.PlayArrow, "Preview", Modifier.align(Alignment.BottomEnd).padding(6.dp).size(22.dp), tint = Navy.copy(alpha = 0.45f))
            }
            Text(c.name, fontWeight = FontWeight.Bold)
            if (selected) {
                FilledTonalButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Active pet")
                }
            } else {
                Button(onClick = onSelect, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { Text("Select") }
            }
        }
    }
}
