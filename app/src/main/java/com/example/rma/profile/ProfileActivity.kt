package com.example.rma.profile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rma.core.managers.GameProfileManager
import com.google.firebase.auth.FirebaseAuth

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profileManager = GameProfileManager(this)
        val user = FirebaseAuth.getInstance().currentUser

        setContent {
            MaterialTheme {
                ProfileScreen(
                    profileManager = profileManager,
                    displayName = user?.displayName ?: user?.email?.substringBefore('@') ?: "Gost",
                    email = user?.email,
                    isGuest = user == null,
                    onBack = { finish() }
                )
            }
        }
    }
}

private val ProfileTop = Color(0xFF243B5C)
private val ProfileBottom = Color(0xFF10233F)
private val CardBlue = Color(0xFF1C416E)
private val SoftBlue = Color(0xFF6DDCFF)
private val Gold = Color(0xFFFFC94A)
private val AvatarChoices = listOf("🍎", "🍐", "🍊", "🍋", "🍒", "🍓", "🍇", "🥝", "🌟", "👑", "🦊", "🐺")

@Composable
private fun ProfileScreen(
    profileManager: GameProfileManager,
    displayName: String,
    email: String?,
    isGuest: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedAvatar by remember { mutableStateOf(profileManager.getProfileAvatar()) }
    val level = profileManager.getLevel()
    val xp = profileManager.getXp()
    val currentLevelXp = (level - 1) * (level - 1) * 200
    val nextLevelXp = level * level * 200
    val xpIntoLevel = (xp - currentLevelXp).coerceAtLeast(0)
    val xpNeeded = (nextLevelXp - currentLevelXp).coerceAtLeast(1)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ProfileTop, ProfileBottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Profil igrača", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (isGuest) "Gost nalog" else "Tvoj nalog, avatar i napredak",
                        color = Color(0xFFCBEAFF),
                        fontSize = 14.sp
                    )
                }
                Button(onClick = onBack) { Text("Nazad") }
            }

            HeroProfileCard(
                avatar = selectedAvatar,
                displayName = displayName,
                email = email,
                isGuest = isGuest,
                level = level,
                xpIntoLevel = xpIntoLevel,
                xpNeeded = xpNeeded,
                xpProgress = profileManager.getXpProgress()
            )

            SectionCard(title = "Promeni sliku profila", subtitle = "Izaberi ikonu koja će se prikazivati na početnom ekranu.") {
                AvatarPicker(
                    selectedAvatar = selectedAvatar,
                    onAvatarSelected = { avatar ->
                        selectedAvatar = avatar
                        profileManager.setProfileAvatar(avatar)
                        Toast.makeText(context, "Slika profila je promenjena", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            SectionCard(title = "Brzi pregled", subtitle = "Najvažniji podaci o nalogu i igri.") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStat("Nivo", level.toString(), Modifier.weight(1f))
                    MiniStat("XP", xp.toString(), Modifier.weight(1f))
                    MiniStat("Novčići", profileManager.getStoredCoins().toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStat("Pobede", profileManager.getClassicWins().toString(), Modifier.weight(1f))
                    MiniStat("Win rate", "${profileManager.getClassicWinRate()}%", Modifier.weight(1f))
                    MiniStat("Niz", profileManager.getClassicStreak().toString(), Modifier.weight(1f))
                }
            }

            SectionCard(title = "Statistika", subtitle = "Classic i Daily rezultati na jednom mestu.") {
                ProfileStatRow("Classic odigrano", profileManager.getClassicGamesPlayed().toString())
                ProfileStatRow("Classic pobede", profileManager.getClassicWins().toString())
                ProfileStatRow("Classic porazi", profileManager.getClassicLosses().toString())
                ProfileStatRow("Najbolji streak", profileManager.getBestClassicStreak().toString())
                ProfileStatRow("Daily odigrano", profileManager.getDailyGamesPlayed().toString())
                ProfileStatRow("Daily pobede", profileManager.getDailyWins().toString())
                ProfileStatRow("Daily porazi", profileManager.getDailyLosses().toString())
            }

            SectionCard(title = "Dostignuća", subtitle = "Mali podsetnik šta si već otključao/la.") {
                AchievementRow("🔥", "Prvi niz", if (profileManager.getBestClassicStreak() > 0) "Otključano" else "Osvoji prvu pobedu")
                AchievementRow("🏆", "Pet pobeda", if (profileManager.getClassicWins() >= 5) "Otključano" else "${profileManager.getClassicWins()}/5")
                AchievementRow("⚡", "Nivo 5", if (level >= 5) "Otključano" else "Nivo $level/5")
            }
        }
    }
}

@Composable
private fun HeroProfileCard(
    avatar: String,
    displayName: String,
    email: String?,
    isGuest: Boolean,
    level: Int,
    xpIntoLevel: Int,
    xpNeeded: Int,
    xpProgress: Float
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(Color(0xFF235385), Color(0xFF2D6D8F))))
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(98.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFF08A), Color(0xFF8DDC45))))
                    .border(5.dp, Color.White.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(avatar, fontSize = 42.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(displayName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(email ?: if (isGuest) "Nisi prijavljen/a" else "Bez email adrese", color = Color(0xFFD5F3FF), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nivo $level", color = Gold, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(10.dp))
                    Text("$xpIntoLevel / $xpNeeded XP", color = Color.White, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { xpProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    color = Gold,
                    trackColor = Color(0x5521314D)
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable Column.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBlue.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Color(0xFFBFE6FF), fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun AvatarPicker(selectedAvatar: String, onAvatarSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AvatarChoices.chunked(4).forEach { rowAvatars ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowAvatars.forEach { avatar ->
                    val selected = avatar == selectedAvatar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(62.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (selected) Gold else Color(0xFF173052))
                            .border(2.dp, if (selected) Color.White else Color(0x446DDCFF), RoundedCornerShape(18.dp))
                            .clickable { onAvatarSelected(avatar) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(avatar, fontSize = 30.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF173052))
            .border(1.dp, Color(0x336DDCFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(label, color = Color(0xFFD5F3FF), fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun ProfileStatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFFE5F7FF), fontSize = 15.sp)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AchievementRow(icon: String, title: String, status: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF173052))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 26.sp, modifier = Modifier.widthIn(min = 38.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(status, color = SoftBlue, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(8.dp))
}
