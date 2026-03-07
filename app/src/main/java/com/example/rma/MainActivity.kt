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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog as ComposeDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween

// ── Palette ───────────────────────────────────────────────────────────────────
private val BG_TOP       = Color(0xFFE8845A)
private val BG_BOT       = Color(0xFFD4694A)
private val STRIPE_COLOR = Color(0x18FFFFFF)
private val GOLD_LIGHT   = Color(0xFFFFD754)
private val GOLD_MID     = Color(0xFFFFC430)
private val GOLD_DARK    = Color(0xFFE8A800)
private val GOLD_STRIPE  = Color(0x22FFFFFF)
private val FOOTER_BG    = Color(0xFFC05A3A)
private val FOOTER_DARK  = Color(0xFF8B3A20)

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

        val profileManager    = GameProfileManager(this)
        val firebaseStatsRepo = FirebaseStatsRepository()
        val coinManager       = CoinManager(this)

        val classicStreak     = profileManager.getClassicStreak()
        val bestClassicStreak = profileManager.getBestClassicStreak()
        val level             = if (isGuest || user == null) 1 else profileManager.getLevel()
        val xpProgress        = if (isGuest || user == null) 0f else profileManager.getXpProgress()

        if (!isGuest && user != null) firebaseStatsRepo.syncStats(profileManager)

        MobileAds.initialize(this)

        val composeContainer = findViewById<ComposeView>(R.id.composeMenu)
        composeContainer.setContent {
            MaterialTheme {
                // ── Firebase coin load ────────────────────────────────────
                var coins by remember { mutableStateOf(coinManager.getCoins()) }
                LaunchedEffect(Unit) {
                    if (!isGuest && user != null) {
                        firebaseStatsRepo.loadStats(
                            onSuccess = { data ->
                                val remote = (data["storedCoins"] as? Long)?.toInt()
                                if (remote != null) { coins = remote; coinManager.setCoins(remote) }
                            },
                            onNoData  = {},
                            onFailure = {}
                        )
                    }
                }

                var showStats     by remember { mutableStateOf(false) }
                var showRemoveAds by remember { mutableStateOf(false) }
                var showWip       by remember { mutableStateOf(false) }

                // ── Diagonal-stripe background ────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(BG_TOP, BG_BOT)))
                        .drawBehind {
                            val sw    = 28.dp.toPx()
                            val total = sw * 2f
                            var x     = -size.height.toFloat()
                            while (x < size.width + size.height) {
                                withTransform({
                                    rotate(-35f, Offset(size.width / 2f, size.height / 2f))
                                }) {
                                    drawRect(STRIPE_COLOR, Offset(x, -size.height),
                                        Size(sw, size.height * 3))
                                }
                                x += total
                            }
                        }
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Column(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            TopHeaderBar(
                                isGuest    = isGuest || user == null,
                                coins      = coins,
                                level      = level,
                                xpProgress = xpProgress,
                                onAdClick  = { showRemoveAds = true }
                            )
                            Spacer(Modifier.height(14.dp))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                WordleBubblesRow()
                            }
                            Spacer(Modifier.height(14.dp))
                            GameButtonsSection(
                                classicStreak  = classicStreak,
                                onStatsClick   = { showStats = true },
                                onClassicClick = {
                                    startActivity(Intent(this@MainActivity, SlovopletIgra::class.java)
                                        .putExtra("game_mode", "CLASSIC"))
                                },
                                onDailyClick   = {
                                    startActivity(Intent(this@MainActivity, SlovopletIgra::class.java)
                                        .putExtra("game_mode", "DAILY"))
                                },
                                onWipClick     = { showWip = true }
                            )
                            Spacer(Modifier.height(14.dp))
                        }

                        FooterIconBar(
                            onHowToClick    = { dijalogObjasnjenjeMain() },
                            onWipClick      = { showWip = true }
                        )
                        BannerAdContainer()
                    }

                    // Dialogs
                    if (showStats) {
                        ComposeDialog(onDismissRequest = { showStats = false }) {
                            MaterialTheme {
                                StatsDialogContent(
                                    classicStreak      = profileManager.getClassicStreak(),
                                    bestClassicStreak  = profileManager.getBestClassicStreak(),
                                    classicWins        = profileManager.getClassicWins(),
                                    classicLosses      = profileManager.getClassicLosses(),
                                    classicGamesPlayed = profileManager.getClassicGamesPlayed(),
                                    winRate            = profileManager.getClassicWinRate(),
                                    guessDistribution  = profileManager.getAllGuessDistribution(),
                                    onClose            = { showStats = false }
                                )
                            }
                        }
                    }
                    if (showRemoveAds) RemoveAdsDialog(
                        onDismiss = { showRemoveAds = false },
                        onBuy     = { /* TODO: wire IAP */ showRemoveAds = false }
                    )
                    if (showWip) WipDialog(onDismiss = { showWip = false })
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
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.setCancelable(true)
        dialog.findViewById<Button>(R.id.buttonIskljuci).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    @Composable
    private fun StatsDialogContent(
        classicStreak: Int, bestClassicStreak: Int,
        classicWins: Int, classicLosses: Int,
        classicGamesPlayed: Int, winRate: Int,
        guessDistribution: List<Int>, onClose: () -> Unit
    ) {
        val maxValue = (guessDistribution.maxOrNull() ?: 1).coerceAtLeast(1)
        Column(
            modifier = Modifier
                .fillMaxWidth().padding(16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1E3560), Color(0xFF162B4A))))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("СТАТИСТИКА", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBox(classicGamesPlayed.toString(), "ИГРЕ")
                StatBox(classicWins.toString(), "ПОБЕДЕ")
                StatBox("$winRate%", "WIN %")
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBox(classicStreak.toString(), "STREAK")
                StatBox(bestClassicStreak.toString(), "BEST")
                StatBox(classicLosses.toString(), "ПОРАЗИ")
            }
            Spacer(Modifier.height(18.dp))
            Text("Расподела погађања", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                guessDistribution.forEachIndexed { i, v ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text((i+1).toString(), color = Color.White, fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f).height(26.dp)
                            .clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A355A))
                        ) {
                            val frac = if (maxValue == 0) 0f else v.toFloat() / maxValue
                            Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f,1f))
                                .clip(RoundedCornerShape(10.dp)).background(Color(0xFFC11521)))
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                                Text(v.toString(), color = Color.White, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 8.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            GoldButton("ЗАТВОРИ", onClose)
        }
    }

    @Composable
    private fun StatBox(value: String, label: String) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(86.dp).clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF31406B)).padding(vertical = 10.dp, horizontal = 6.dp)
        ) {
            Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = Color(0xFFD6D9E0), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center)
        }
    }
}

// ── TOP HEADER ────────────────────────────────────────────────────────────────
@Composable
private fun TopHeaderBar(
    isGuest: Boolean, coins: Int, level: Int, xpProgress: Float, onAdClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Left – AD button
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .border(3.dp, Color(0xFFCC2222), CircleShape)
                .clickable { onAdClick() },
            contentAlignment = Alignment.Center
        ) {
            Text("AD", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }

        // Center – big trophy/avatar
        Box(Modifier.align(Alignment.Center)) {
            CenterAvatar(level = level, xpProgress = xpProgress, isGuest = isGuest)
        }

        // Right – coin pill
        Box(Modifier.align(Alignment.CenterEnd)) {
            CoinPill(coins = coins, isGuest = isGuest)
        }
    }
}

@Composable
private fun CenterAvatar(level: Int, xpProgress: Float, isGuest: Boolean) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
        // Outer brown ring
        Box(
            Modifier.size(116.dp).clip(CircleShape)
                .background(Color(0xFF6B3A1F))
                .border(5.dp, Color(0xFF9B6A3F), CircleShape)
        )
        // XP arc
        CircularProgressIndicator(
            progress   = { xpProgress.coerceIn(0f, 1f) },
            modifier   = Modifier.size(108.dp),
            strokeWidth = 7.dp,
            color      = Color(0xFF60DDFF),
            trackColor = Color(0x33004466)
        )
        // Green avatar
        Box(
            modifier = Modifier.size(90.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFCCF870), Color(0xFF88D030))))
                .border(4.dp, Color(0xFF558820), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(if (isGuest) "?" else "🍎", fontSize = 40.sp)
        }
        // Level chip – top
        Box(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF70DAFF), Color(0xFF1A90D8))))
                .border(2.dp, Color(0xFF006BB0), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 3.dp)
        ) {
            Text(level.toString(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
        // W badge – bottom
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = 2.dp)
                .size(28.dp).clip(CircleShape)
                .background(Color(0xFFE8A800))
                .border(2.dp, Color(0xFFA06000), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("W", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CoinPill(coins: Int, isGuest: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.height(38.dp).widthIn(min = 90.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(Color(0xFF5A3A1A))
                .border(2.dp, Color(0xFF8B5A2B), RoundedCornerShape(19.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isGuest) "--" else coins.toString(),
                    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(26.dp).clip(CircleShape)
                        .background(Color(0xFFE8A800))
                        .border(2.dp, Color(0xFFA06000), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("W", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier.size(30.dp).clip(CircleShape)
                .background(Color(0xFF44BB55))
                .border(2.dp, Color(0xFF226622), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
    }
}

// ── WORD BUBBLES ──────────────────────────────────────────────────────────────
@Composable
private fun WordleBubblesRow() {
    data class Bub(val ch: String, val bg: Color, val shadow: Color)
    val bubbles = listOf(
        Bub("С", Color(0xFFE8A0C8), Color(0xFFB06090)),
        Bub("Л", Color(0xFFD0C8E8), Color(0xFF8870B0)),
        Bub("О", Color(0xFF70C8E8), Color(0xFF3090B8)),
        Bub("В", Color(0xFFB0D890), Color(0xFF5A9040)),
        Bub("О", Color(0xFFE8D070), Color(0xFFB09030)),
        Bub("П", Color(0xFFE89060), Color(0xFFB05020)),
        Bub("Л", Color(0xFF80C888), Color(0xFF408840)),
        Bub("Е", Color(0xFFE8B890), Color(0xFFB07050)),
        Bub("Т", Color(0xFFE87070), Color(0xFFB03030)),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(horizontal = 10.dp)) {
        bubbles.forEach { b ->
            Box(
                modifier = Modifier.size(38.dp)
                    .drawBehind {
                        drawCircle(b.shadow, size.minDimension / 2f,
                            Offset(size.width / 2f, size.height / 2f + 3.dp.toPx()))
                    }
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(lightenColor(b.bg, 0.22f), b.bg)))
                    .border(2.dp, b.shadow.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(b.ch, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                    color = b.shadow.copy(alpha = 0.55f), modifier = Modifier.offset(y = 1.5.dp))
                Text(b.ch, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

// ── GAME BUTTONS ──────────────────────────────────────────────────────────────
@Composable
private fun GameButtonsSection(
    classicStreak: Int,
    onStatsClick: () -> Unit,
    onClassicClick: () -> Unit,
    onDailyClick: () -> Unit,
    onWipClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0x55A03018))
            .border(2.dp, Color(0x44FFFFFF), RoundedCornerShape(26.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Stats + Classic
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GoldSquareButton(modifier = Modifier.size(82.dp), onClick = onStatsClick) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📊", fontSize = 26.sp)
                    Text("Stats", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                }
            }
            GoldWideButton(

                title = "КЛАСИЧАН", rightTop = classicStreak.toString(), rightBottom = "BEST",
                modifier = Modifier.weight(1f).height(82.dp), onClick = onClassicClick
            )
        }
        // Daily
        GoldWideButton(
            title = "РЕЧ ДАНА", rightTop = "MAR", rightBottom = "7",
            modifier = Modifier.fillMaxWidth().height(82.dp), onClick = onDailyClick
        )
        // Two WIP mini buttons
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GoldWideButton(
                title = "РЕЧЕНИЦА", rightTop = "0", rightBottom = "BEST",
                modifier = Modifier.weight(1f).height(72.dp),
                onClick = onWipClick, titleSize = 14.sp
            )
            GoldWideButton(
                title = "ТАЈНА РЕЧ", rightTop = "0", rightBottom = "BEST",
                modifier = Modifier.weight(1f).height(72.dp),
                onClick = onWipClick, titleSize = 14.sp
            )
        }
    }
}

// ── GOLD COMPONENTS ───────────────────────────────────────────────────────────
@Composable
fun GoldSquareButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val src     = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val infiniteTransition = rememberInfiniteTransition(label = "stripe")
    val stripeOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing)
        ),
        label = "stripeOffset"
    )
    Box(
        modifier = modifier
            .offset { IntOffset(0, if (pressed) 4.dp.roundToPx() else 0) }
            .drawBehind {
                if (!pressed) drawRoundRect(GOLD_DARK, Offset(0f, 5.dp.toPx()),
                    Size(size.width, size.height), CornerRadius(16.dp.toPx()))
            }
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(GOLD_LIGHT, GOLD_MID)))
            .drawBehind {
                val sw    = 18.dp.toPx()   // stripe width (use 14.dp for square)
                val total = sw * 2f         // stripe + gap
                // stripeOffset moves from 0..1, multiply by total to get one full cycle
                var x = -size.height.toFloat() + (stripeOffset * total)
                while (x < size.width + size.height) {
                    withTransform({ rotate(-35f, Offset(size.width / 2f, size.height / 2f)) }) {
                        drawRect(GOLD_STRIPE, Offset(x, -size.height), Size(sw, size.height * 3))
                    }
                    x += total
                }
            }
            .clickable(interactionSource = src, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun GoldWideButton(
    title: String,
    rightTop: String,
    rightBottom: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    titleSize: TextUnit = 20.sp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "stripe")
    val stripeOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing)
        ),
        label = "stripeOffset"
    )
    val src     = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Row(
        modifier = modifier
            .offset { IntOffset(0, if (pressed) 4.dp.roundToPx() else 0) }
            .drawBehind {
                if (!pressed) drawRoundRect(GOLD_DARK, Offset(0f, 5.dp.toPx()),
                    Size(size.width, size.height), CornerRadius(18.dp.toPx()))
            }
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(GOLD_LIGHT, GOLD_MID)))
            .drawBehind {
                val sw    = 18.dp.toPx()   // stripe width (use 14.dp for square)
                val total = sw * 2f         // stripe + gap
                // stripeOffset moves from 0..1, multiply by total to get one full cycle
                var x = -size.height.toFloat() + (stripeOffset * total)
                while (x < size.width + size.height) {
                    withTransform({ rotate(-35f, Offset(size.width / 2f, size.height / 2f)) }) {
                        drawRect(GOLD_STRIPE, Offset(x, -size.height), Size(sw, size.height * 3))
                    }
                    x += total
                }
            }
            .clickable(interactionSource = src, indication = null) { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(title, fontSize = titleSize, fontWeight = FontWeight.ExtraBold,
                color = Color(0x99894A00), modifier = Modifier.offset(y = 2.dp))
            Text(title, fontSize = titleSize, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        Box(
            Modifier.fillMaxHeight().width(70.dp)
                .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFFFB020), Color(0xFFE89000)))),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(rightTop, fontSize = if (rightTop.length <= 2) 22.sp else 15.sp,
                    fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text(rightBottom, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color(0xDDFFFFFF))
            }
        }
    }
}

@Composable
fun GoldButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val src     = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        modifier = modifier.fillMaxWidth().height(52.dp)
            .offset { IntOffset(0, if (pressed) 4.dp.roundToPx() else 0) }
            .drawBehind {
                if (!pressed) drawRoundRect(GOLD_DARK, Offset(0f, 5.dp.toPx()),
                    Size(size.width, size.height), CornerRadius(26.dp.toPx()))
            }
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(GOLD_LIGHT, GOLD_MID)))
            .clickable(interactionSource = src, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color(0xFF7A4400), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
    }
}

// ── FOOTER ICON BAR ───────────────────────────────────────────────────────────
@Composable
private fun FooterIconBar(onHowToClick: () -> Unit, onWipClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .drawBehind {
                drawRect(FOOTER_DARK,
                    Offset(0f, size.height - 5.dp.toPx()), Size(size.width, 5.dp.toPx()))
            }
            .background(FOOTER_BG)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        FooterIcon("🛒",  "SHOP",     onWipClick)
        FooterIcon("🛡️",  "LEAGUE",   onWipClick)
        FooterIcon("❓",  "HOW TO",   onHowToClick)
        FooterIcon("📖",  "GUIDE",    onWipClick)
        FooterIcon("⚙️",  "SETTINGS", onWipClick)
    }
}

@Composable
private fun FooterIcon(emoji: String, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 24.sp)
        Text(label, color = Color(0xDDFFFFFF), fontSize = 8.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
    }
}

// ── REMOVE ADS DIALOG ─────────────────────────────────────────────────────────
@Composable
fun RemoveAdsDialog(onDismiss: () -> Unit, onBuy: () -> Unit) {
    ComposeDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF8B2080), Color(0xFF4A0060))))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🚫", fontSize = 52.sp)
            Spacer(Modifier.height(10.dp))
            Text("УКЛОНИ РЕКЛАМЕ", color = Color.White, fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Уживај у игри без прекида.\nЈедном купи, заувек без реклама.",
                color = Color(0xCCFFFFFF), fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            listOf("Без банер реклама", "Без видео реклама", "Подршка развоју игре").forEach { text ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                ) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF44BB55)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(text, color = Color.White, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            GoldButton("КУПИ — 2,99 €", onBuy)
            Spacer(Modifier.height(14.dp))
            Text("НЕ САДА", color = Color(0xAAFFFFFF), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clickable { onDismiss() })
        }
    }
}

// ── WIP DIALOG ────────────────────────────────────────────────────────────────
@Composable
fun WipDialog(onDismiss: () -> Unit) {
    ComposeDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2A3A60), Color(0xFF162B4A))))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🚧", fontSize = 52.sp)
            Spacer(Modifier.height(12.dp))
            Text("У ИЗРАДИ", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text("Ова функција је тренутно у развоју.\nУскоро доступно!",
                color = Color(0xAAFFFFFF), fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            GoldButton("У РЕДУ", onDismiss)
        }
    }
}

// ── ADMOB BANNER ──────────────────────────────────────────────────────────────
@Composable
private fun BannerAdContainer() {
    val context = LocalContext.current
    Box(
        Modifier.fillMaxWidth().background(Color(0xFF7A3A1A)).padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(factory = {
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-3940256099942544/6300978111"
                loadAd(AdRequest.Builder().build())
            }
        })
    }
}

// ── HELPERS ───────────────────────────────────────────────────────────────────
private fun lightenColor(color: Color, amount: Float) = Color(
    red   = color.red   + (1f - color.red)   * amount,
    green = color.green + (1f - color.green) * amount,
    blue  = color.blue  + (1f - color.blue)  * amount,
    alpha = color.alpha
)