package com.ryuza.member

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.json.JSONObject

private val Night = Color(0xFF070D1B)
private val Panel = Color(0xFF101B30)
private val Panel2 = Color(0xFF142541)
private val Neon = Color(0xFF36CFFF)
private val Blue = Color(0xFF3478FF)
private val Muted = Color(0xFF91A4C2)
private val White = Color(0xFFF4F8FF)

data class Skin(
    val id: String,
    val title: String,
    val category: String,
    val imageUrl: String,
    val featured: Boolean,
    val isNew: Boolean,
    val options: List<DownloadOption>
)
data class DownloadOption(val label: String, val url: String)
data class SkinCategory(val id: String, val name: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 13, 27)
        window.navigationBarColor = android.graphics.Color.rgb(7, 13, 27)
        val (skins, categories) = loadCatalog()
        val prefs = getSharedPreferences("ryuza_member", MODE_PRIVATE)
        setContent {
            RyuzaApp(
                skins = skins,
                categories = categories,
                initialFavorites = prefs.getStringSet("favorites", emptySet()).orEmpty(),
                onFavoritesChanged = { prefs.edit().putStringSet("favorites", it.toSet()).apply() }
            )
        }
    }

    private fun loadCatalog(): Pair<List<Skin>, List<SkinCategory>> {
        val raw = assets.open("catalog.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        val categoriesJson = root.getJSONArray("categories")
        val categories = (0 until categoriesJson.length()).map { i ->
            val c = categoriesJson.getJSONObject(i)
            SkinCategory(c.optString("id"), c.optString("name"))
        }
        val skinsJson = root.getJSONArray("skins")
        val skins = (0 until skinsJson.length()).map { i ->
            val s = skinsJson.getJSONObject(i)
            val opts = s.optJSONArray("options")
            val options = if (opts == null) emptyList() else (0 until opts.length()).map { j ->
                val o = opts.getJSONObject(j)
                DownloadOption(o.optString("label"), o.optString("url"))
            }
            Skin(
                s.optString("id"), s.optString("title"), s.optString("category"),
                s.optString("imageUrl"), s.optBoolean("featured"), s.optBoolean("isNew"), options
            )
        }
        return skins to categories
    }
}

private enum class Tab(val label: String) {
    HOME("Home"), CATALOG("Katalog"), FAVORITES("Favorit"), PROFILE("Profil")
}

@Composable
private fun RyuzaApp(
    skins: List<Skin>,
    categories: List<SkinCategory>,
    initialFavorites: Set<String>,
    onFavoritesChanged: (Set<String>) -> Unit
) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var selected by remember { mutableStateOf<Skin?>(null) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("all") }
    var favorites by remember { mutableStateOf(initialFavorites.toSet()) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Night, surface = Panel, primary = Neon,
            onBackground = White, onSurface = White
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Night) {
            if (selected != null) {
                SkinDetail(
                    skin = selected!!,
                    favorite = favorites.contains(selected!!.id),
                    onBack = { selected = null },
                    onFavorite = {
                        favorites = if (favorites.contains(selected!!.id)) favorites - selected!!.id
                                    else favorites + selected!!.id
                        onFavoritesChanged(favorites)
                    }
                )
            } else {
                Scaffold(
                    containerColor = Night,
                    bottomBar = {
                        NavigationBar(containerColor = Color(0xFF0C1729), tonalElevation = 0.dp) {
                            Tab.values().forEach { item ->
                                NavigationBarItem(
                                    selected = tab == item,
                                    onClick = { tab = item },
                                    icon = {
                                        Icon(
                                            when (item) {
                                                Tab.HOME -> Icons.Default.Home
                                                Tab.CATALOG -> Icons.Default.GridView
                                                Tab.FAVORITES -> Icons.Default.Favorite
                                                Tab.PROFILE -> Icons.Default.Person
                                            }, contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label, fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Neon,
                                        selectedTextColor = Neon,
                                        indicatorColor = Blue.copy(alpha = .18f),
                                        unselectedIconColor = Muted,
                                        unselectedTextColor = Muted
                                    )
                                )
                            }
                        }
                    }
                ) { padding ->
                    when (tab) {
                        Tab.HOME -> HomeScreen(
                            skins = skins,
                            favoriteCount = favorites.size,
                            onBrowse = { tab = Tab.CATALOG },
                            onSkin = { selected = it }
                        )
                        Tab.CATALOG -> CatalogScreen(
                            skins = skins, categories = categories,
                            query = query, onQuery = { query = it },
                            category = category, onCategory = { category = it },
                            onSkin = { selected = it }
                        )
                        Tab.FAVORITES -> FavoritesScreen(
                            skins.filter { favorites.contains(it.id) },
                            onSkin = { selected = it }
                        )
                        Tab.PROFILE -> ProfileScreen(skins.size, favorites.size)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    skins: List<Skin>, favoriteCount: Int,
    onBrowse: () -> Unit, onSkin: (Skin) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("RYUZA", color = Neon, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Text("Member Hub", color = White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Blue, Neon))),
                contentAlignment = Alignment.Center
            ) { Text("R", color = White, fontSize = 22.sp, fontWeight = FontWeight.Black) }
        }
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF123C78), Color(0xFF111D37), Color(0xFF142A48))))
                .padding(22.dp)
        ) {
            Column {
                Text("SKIN COLLECTION", color = Neon, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                Text("Explore your\nnext favorite skin.", color = White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp)
                Spacer(Modifier.height(8.dp))
                Text("Temukan koleksi skin dan pilihan download dalam satu tempat.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onBrowse,
                    colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Night),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Jelajahi katalog", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, null)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("TOTAL SKIN", skins.size.toString(), Modifier.weight(1f))
            StatCard("FAVORIT", favoriteCount.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(26.dp))
        SectionHeading("Featured collection", "Pilihan untuk kamu")
        Spacer(Modifier.height(12.dp))
        val featured = skins.filter { it.featured }.ifEmpty { skins.take(8) }.take(8)
        featured.forEach { SkinWideCard(it, onClick = { onSkin(it) }) }
        Spacer(Modifier.height(20.dp))
        SectionHeading("Recently added", "Koleksi terbaru")
        Spacer(Modifier.height(12.dp))
        skins.filter { it.isNew }.take(6).forEach { SkinWideCard(it, onClick = { onSkin(it) }) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CatalogScreen(
    skins: List<Skin>, categories: List<SkinCategory>,
    query: String, onQuery: (String) -> Unit,
    category: String, onCategory: (String) -> Unit,
    onSkin: (Skin) -> Unit
) {
    val filtered = skins.filter {
        (category == "all" || it.category == category) &&
            it.title.contains(query, ignoreCase = true)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(22.dp))
        Text("Katalog Skin", color = White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("${filtered.size} koleksi tersedia", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Panel).padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = Neon)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query, onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = White, fontSize = 14.sp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Cari nama skin...", color = Muted, fontSize = 14.sp)
                    inner()
                }
            )
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                Icon(Icons.Default.Close, null, tint = Muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = category == "all", onClick = { onCategory("all") },
                    label = { Text("Semua") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Blue.copy(alpha = .3f), selectedLabelColor = Neon)
                )
            }
            items(categories) { c ->
                FilterChip(
                    selected = category == c.id, onClick = { onCategory(c.id) },
                    label = { Text(c.name) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Blue.copy(alpha = .3f), selectedLabelColor = Neon)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Skin tidak ditemukan.", color = Muted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.id }) { skin ->
                    SkinGridCard(skin) { onSkin(skin) }
                }
            }
        }
    }
}

@Composable
private fun FavoritesScreen(skins: List<Skin>, onSkin: (Skin) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Favorit Saya", color = White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Koleksi yang kamu simpan", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        if (skins.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FavoriteBorder, null, tint = Neon, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Belum ada favorit", color = White, fontWeight = FontWeight.Bold)
                    Text("Simpan skin dari halaman detail.", color = Muted, fontSize = 12.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) { items(skins, key = { it.id }) { SkinGridCard(it) { onSkin(it) } } }
        }
    }
}

@Composable
private fun ProfileScreen(total: Int, favorites: Int) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(26.dp))
        Text("Profil", color = White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF123C78), Panel)))
                .padding(22.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(76.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Blue, Neon))), contentAlignment = Alignment.Center) {
                    Text("R", color = White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(12.dp))
                Text("Ryuza Member", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Premium Dark Blue Edition", color = Neon, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        StatCard("TOTAL KOLEKSI", total.toString(), Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        StatCard("SKIN FAVORIT", favorites.toString(), Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Text("Tentang aplikasi", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text("Ryuza Member adalah katalog skin dengan pencarian, kategori, detail pilihan, dan favorit lokal.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(16.dp))
        Text("Versi 2.0.0 Native • Kotlin + Jetpack Compose", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SkinDetail(skin: Skin, favorite: Boolean, onBack: () -> Unit, onFavorite: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = White) }
            Text("Detail Skin", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onFavorite) {
                Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) Neon else White)
            }
        }
        AsyncImage(
            model = skin.imageUrl, contentDescription = skin.title,
            modifier = Modifier.fillMaxWidth().height(310.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop
        )
        Column(Modifier.padding(20.dp)) {
            Text(skin.title.replaceFirstChar { it.uppercase() }, color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(5.dp))
            Text(skin.category.replace('-', ' ').uppercase(), color = Neon, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(22.dp))
            Text("PILIHAN DOWNLOAD", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(10.dp))
            if (skin.options.isEmpty()) Text("Belum ada link download.", color = Muted)
            skin.options.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp))
                        .background(Panel).clickable {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(option.url))) }
                        }.padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(option.label, color = White, fontWeight = FontWeight.SemiBold)
                        Text("Buka tautan download", color = Muted, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.Download, null, tint = Neon)
                }
            }
        }
    }
}

@Composable
private fun SkinGridCard(skin: Skin, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = skin.imageUrl, contentDescription = skin.title,
            modifier = Modifier.fillMaxWidth().height(138.dp),
            contentScale = ContentScale.Crop
        )
        Column(Modifier.padding(11.dp)) {
            Text(skin.title.replaceFirstChar { it.uppercase() }, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(skin.category.replace('-', ' '), color = Neon, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SkinWideCard(skin: Skin, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(17.dp))
            .background(Panel).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = skin.imageUrl, contentDescription = skin.title,
            modifier = Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(skin.title.replaceFirstChar { it.uppercase() }, color = White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(skin.category.replace('-', ' '), color = Muted, fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Neon)
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(17.dp)).background(Panel).padding(16.dp)
    ) {
        Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = Neon, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column {
        Text(title, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}
