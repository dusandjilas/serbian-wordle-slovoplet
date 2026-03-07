package com.example.rma

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog as ComposeDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private var isGuest: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("game_prefs", MODE_PRIVATE)
        isGuest = prefs.getBoolean("isGuest", false)

        firebaseAuth = FirebaseAuth.getInstance()
        val user = firebaseAuth.currentUser

        val profileManager = GameProfileManager(this)
        val firebaseStatsRepository = FirebaseStatsRepository()

        val classicStreak = profileManager.getClassicStreak()
        val bestClassicStreak = profileManager.getBestClassicStreak()

        val coins = if (isGuest || user == null) null else profileManager.getStoredCoins()
        val level = if (isGuest || user == null) 1 else profileManager.getLevel()
        val xpProgress = if (isGuest || user == null) 0f else profileManager.getXpProgress()

        if (!isGuest && user != null) {
            firebaseStatsRepository.syncStats(profileManager)
        }

        MobileAds.initialize(this)

        val composeContainer = findViewById<ComposeView>(R.id.composeMenu)
        composeContainer.setContent {
            MaterialTheme {
                var showStats by remember { mutableStateOf(false) }

                Surface(color = Color.Transparent) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            MainHeader(
                                isGuest = isGuest || user == null,
                                coins = coins,
                                level = level,
                                xpProgress = xpProgress,
                                onNoAdsClick = {
                                    // TODO later
                                },
                                onSettingsClick = {
                                    // TODO later
                                }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            WordleMenuSection(
                                classicStreak = classicStreak,
                                bestClassicStreak = bestClassicStreak,
                                onStatsClick = { showStats = true },
                                onClassicClick = {
                                    startActivity(
                                        Intent(this@MainActivity, SlovopletIgra::class.java)
                                            .putExtra("game_mode", "CLASSIC")
                                    )
                                },
                                onDailyClick = {
                                    startActivity(
                                        Intent(this@MainActivity, SlovopletIgra::class.java)
                                            .putExtra("game_mode", "DAILY")
                                    )
                                }
                            )
                        }

                        FooterButtons(
                            onStoreClick = {},
                            onHowToClick = { dijalogObjasnjenjeMain() },
                            onSettingsClick = {}
                        )

                        BannerAdContainer()

                        if (showStats) {
                            ComposeDialog(
                                onDismissRequest = { showStats = false }
                            ) {
                                MaterialTheme {
                                    StatsDialogContent(
                                        classicStreak = profileManager.getClassicStreak(),
                                        bestClassicStreak = profileManager.getBestClassicStreak(),
                                        classicWins = profileManager.getClassicWins(),
                                        classicLosses = profileManager.getClassicLosses(),
                                        classicGamesPlayed = profileManager.getClassicGamesPlayed(),
                                        winRate = profileManager.getClassicWinRate(),
                                        guessDistribution = profileManager.getAllGuessDistribution(),
                                        onClose = { showStats = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun dijalogObjasnjenjeMain() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_objasnjenje_main)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(true)

        val dialogBtnCancel = dialog.findViewById<Button>(R.id.buttonIskljuci)
        dialogBtnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    @Composable
    private fun StatsDialogContent(
        classicStreak: Int,
        bestClassicStreak: Int,
        classicWins: Int,
        classicLosses: Int,
        classicGamesPlayed: Int,
        winRate: Int,
        guessDistribution: List<Int>,
        onClose: () -> Unit
    ) {
        val maxValue = (guessDistribution.maxOrNull() ?: 1).coerceAtLeast(1)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF162B4A))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "СТАТИСТИКА",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBox(value = classicGamesPlayed.toString(), label = "ИГРЕ")
                StatBox(value = classicWins.toString(), label = "ПОБЕДЕ")
                StatBox(value = "$winRate%", label = "WIN %")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBox(value = classicStreak.toString(), label = "STREAK")
                StatBox(value = bestClassicStreak.toString(), label = "BEST")
                StatBox(value = classicLosses.toString(), label = "ПОРАЗИ")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Расподела погађања",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                guessDistribution.forEachIndexed { index, value ->
                    GuessDistributionRow(
                        attempt = index + 1,
                        value = value,
                        maxValue = maxValue
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFC11521))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ЗАТВОРИ",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }

    @Composable
    private fun StatBox(
        value: String,
        label: String
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(90.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF31406B))
                .padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = Color(0xFFD6D9E0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }

    @Composable
    private fun GuessDistributionRow(
        attempt: Int,
        value: Int,
        maxValue: Int
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = attempt.toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2A355A))
            ) {
                val fraction = if (maxValue == 0) 0f else value.toFloat() / maxValue.toFloat()

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFC11521))
                )

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = value.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }
            }
        }
    }

}

/* ---------------- COMPOSE UI ---------------- */

@Composable
fun WordleMenuSection(
    classicStreak: Int,
    bestClassicStreak: Int,
    onStatsClick: () -> Unit,
    onClassicClick: () -> Unit,
    onDailyClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WordleBubblesRow()

            Spacer(modifier = Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFC2B2DA))
                    .drawBehind {
                        val strokeWidth = 6.dp.toPx()
                        drawRoundRect(
                            color = Color(0x3C986CDA),
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                24.dp.toPx(),
                                24.dp.toPx()
                            ),
                            style = Stroke(width = strokeWidth)
                        )
                    }
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatsButton(
                            onClick = onStatsClick,
                            modifier = Modifier.size(width = 92.dp, height = 86.dp)
                        )

                        FancyMenuButton(
                            title = "Класичан",
                            rightTop = classicStreak.toString(),
                            rightBottom = "STREAK",
                            onClick = onClassicClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(86.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FancyMenuButton(
                        title = "РЕЧ ДАНА",
                        rightTop = "MAR",
                        rightBottom = "5",
                        onClick = onDailyClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(86.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WordleMenuPreview() {
    WordleMenuSection(
        classicStreak = 3,
        bestClassicStreak = 8,
        onStatsClick = {},
        onClassicClick = {},
        onDailyClick = {}
    )
}

@Composable
private fun WordleBubblesRow() {
    val items = listOf(
        Bubble("С", Color(0xFF162B4A), Color(0xFF8995A3)),
        Bubble("Л", Color(0xFF162B4A), Color(0xFF8995A3)),
        Bubble("О", Color(0xFF162B4A), Color(0xFF8995A3)),
        Bubble("В", Color(0xFF162B4A), Color(0xFF8995A3)),
        Bubble("О", Color(0xFF162B4A), Color(0xFF8995A3)),
        Bubble("П", Color(0xFFC11521), Color(0xFF8995A3)),
        Bubble("Л", Color(0xFFC11521), Color(0xFF8995A3)),
        Bubble("Е", Color(0xFFC11521), Color(0xFF8995A3)),
        Bubble("Т", Color(0xFFC11521), Color(0xFF8995A3))
    )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { bubble ->
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .drawBehind {
                        drawCircle(
                            color = bubble.shadow,
                            radius = size.minDimension / 2f,
                            center = Offset(
                                size.width / 2f + 1.5.dp.toPx(),
                                size.height / 2f + 2.dp.toPx()
                            )
                        )
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                lighten(bubble.color, 0.12f),
                                bubble.color
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                ShadowText(
                    text = bubble.text,
                    fontSize = 18.sp
                )
            }
        }
    }
}

private data class Bubble(
    val text: String,
    val color: Color,
    val shadow: Color
)

@Composable
private fun StatsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val offsetY = if (pressed) 6.dp else 0.dp
    val shadowOffset = if (pressed) 0.dp else 4.dp

    Box(
        modifier = modifier
            .offset { IntOffset(0, offsetY.roundToPx()) }
            .drawBehind {
                if (!pressed) {
                    drawRoundRect(
                        color = Color(0xFFC7CCD3),
                        topLeft = Offset(0f, shadowOffset.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            12.dp.toPx(),
                            12.dp.toPx()
                        )
                    )
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF162B4A))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Stats",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun FancyMenuButton(
    title: String,
    rightTop: String,
    rightBottom: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val offsetY = if (pressed) 6.dp else 0.dp
    val shadowOffset = if (pressed) 0.dp else 4.dp

    Row(
        modifier = modifier
            .offset { IntOffset(0, offsetY.roundToPx()) }
            .drawBehind {
                if (!pressed) {
                    drawRoundRect(
                        color = Color(0xFFC7CCD3),
                        topLeft = Offset(0f, shadowOffset.toPx()),
                        size = Size(size.width, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            14.dp.toPx(),
                            14.dp.toPx()
                        )
                    )
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF162B4A), Color(0xFF31406B))
                )
            )
            .stripeOverlay()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            ShadowText(
                text = title,
                fontSize = 22.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(86.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0x603F51B5), Color(0xFF3F51B5))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ShadowText(
                    text = rightTop,
                    fontSize = if (rightTop.length <= 2) 22.sp else 16.sp
                )

                ShadowText(
                    text = rightBottom,
                    fontSize = 16.sp
                )
            }
        }
    }
}

private fun Modifier.stripeOverlay(): Modifier = this.drawBehind {
    drawRoundRect(
        color = Color.White.copy(alpha = 0.16f),
        size = Size(size.width, max(4.dp.toPx(), size.height * 0.10f)),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx(), 20.dp.toPx())
    )
}

@Composable
private fun ShadowText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0x990A0905),
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(y = 2.dp)
        )
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MainHeader(
    isGuest: Boolean,
    coins: Int?,
    level: Int,
    xpProgress: Float,
    onNoAdsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderIconButton(
                text = "AD",
                background = Color(0xFFE34B3F),
                onClick = onNoAdsClick
            )

            Spacer(modifier = Modifier.width(8.dp))

            MiniHeaderBadge(title = "SPIN", subtitle = "1")

            Spacer(modifier = Modifier.width(8.dp))

            MiniHeaderBadge(title = "PIG", subtitle = "385")

            Spacer(modifier = Modifier.weight(1f))

            HeaderIconButton(
                text = "⚙",
                background = Color(0xFFF5A623),
                onClick = onSettingsClick
            )

            Spacer(modifier = Modifier.width(8.dp))

            CoinPill(coins = coins)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            ProfileXpAvatar(
                level = level,
                xpProgress = xpProgress,
                isGuest = isGuest
            )
        }
    }
}

@Composable
private fun MiniHeaderBadge(
    title: String,
    subtitle: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x30FFFFFF))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = subtitle,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp
        )
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun FooterButtons(
    onStoreClick: () -> Unit,
    onHowToClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FooterButton(label = "SHOP", modifier = Modifier.weight(1f), onClick = onStoreClick)
        FooterButton(label = "HOW TO", modifier = Modifier.weight(1f), onClick = onHowToClick)
        FooterButton(label = "SETTINGS", modifier = Modifier.weight(1f), onClick = onSettingsClick)
    }
}

@Composable
private fun FooterButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x339E6F64))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun BannerAdContainer() {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF8B4E3C))
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = {
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = "ca-app-pub-3940256099942544/6300978111"
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}

@Composable
private fun HeaderIconButton(
    text: String,
    background: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(background)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp
        )
    }
}

@Composable
private fun CoinPill(coins: Int?) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF9E6F64))
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = coins?.toString() ?: "--",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp
            )

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF2B632)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "W",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
private fun ProfileXpAvatar(
    level: Int,
    xpProgress: Float,
    isGuest: Boolean
) {
    Box(
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { xpProgress.coerceIn(0f, 1f) },
            modifier = Modifier.size(104.dp),
            strokeWidth = 8.dp,
            color = Color(0xFF57A8FF),
            trackColor = Color(0x332D6CDF)
        )

        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Color(0xFFB7E36A))
                .border(4.dp, Color(0xFF8B4E3C), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isGuest) "?" else "🙂",
                fontSize = 34.sp
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-8).dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF4E8EDB))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = level.toString(),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
        }
    }
}

private fun lighten(color: Color, amount: Float): Color {
    return Color(
        red = color.red + (1f - color.red) * amount,
        green = color.green + (1f - color.green) * amount,
        blue = color.blue + (1f - color.blue) * amount,
        alpha = color.alpha
    )
}
