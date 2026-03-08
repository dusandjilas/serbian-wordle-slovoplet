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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import androidx.compose.animation.core.*
import kotlin.math.roundToInt

// ── Palette ───────────────────────────────────────────────────────────────────
private val BG_TOP       = Color(0xFFE8845A)
private val BG_BOT       = Color(0xFFD4694A)
private val STRIPE_COLOR = Color(0x18FFFFFF)
private val GOLD_LIGHT   = Color(0xFFFFD754)
private val GOLD_MID     = Color(0xFFFFC430)
private val GOLD_DARK    = Color(0xFFE8A800)
private val GOLD_STRIPE  = Color(0x22FFFFFF)
private val FOOTER_BG    = Color(0xFF2A1A0E)
private val FOOTER_ITEM  = Color(0xFF3D2810)

class MainActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        firebaseAuth = FirebaseAuth.getInstance()
        MobileAds.initialize(this)

        val profileManager = GameProfileManager(this)
        // CoinRepository is the ONLY coin source of truth
        val coinRepo = CoinRepository(this)

        val composeContainer = findViewById<ComposeView>(R.id.composeMenu)
        composeContainer.setContent {
            MaterialTheme {
                MainScreen(
                    firebaseAuth   = firebaseAuth,
                    profileManager = profileManager,
                    coinRepo       = coinRepo,
                    onHowTo        = { dijalogObjasnjenjeMain() },
                    onStartClassic = {
                        startActivity(Intent(this, SlovopletIgra::class.java)
                            .putExtra("game_mode", "CLASSIC"))
                    },
                    onStartDaily = {
                        startActivity(Intent(this, SlovopletIgra::class.java)
                            .putExtra("game_mode", "DAILY"))
                    },
                    onOpenProfile = {
                        startActivity(Intent(this, ProfileActivity::class.java))
                    }
                )
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
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN SCREEN COMPOSABLE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MainScreen(
    firebaseAuth: FirebaseAuth,
    profileManager: GameProfileManager,
    coinRepo: CoinRepository,
    onHowTo: () -> Unit,
    onStartClassic: () -> Unit,
    onStartDaily: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val user = firebaseAuth.currentUser
    val isGuest = user == null

    // ── Coin state: start from local, reconcile with Firebase once ────────────
    var coins by remember { mutableIntStateOf(coinRepo.getLocal()) }
    LaunchedEffect(Unit) {
        coinRepo.load { reconciled -> coins = reconciled }
    }

    // ── XP / level — only read for logged-in users ────────────────────────────
    val level     = if (isGuest) 1    else profileManager.getLevel()
    val xpProgress = if (isGuest) 0f else profileManager.getXpProgress()
    val classicStreak = profileManager.getClassicStreak()

    // ── Dialog state ──────────────────────────────────────────────────────────
    var showStats     by remember { mutableStateOf(false) }
    var showRemoveAds by remember { mutableStateOf(false) }
    var showWip       by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BG_TOP, BG_BOT)))
            .drawBehind {
                val sw = 28.dp.toPx(); val total = sw * 2f
                var x = -size.height.toFloat()
                while (x < size.width + size.height) {
                    withTransform({ rotate(-35f, Offset(size.width / 2f, size.height / 2f)) }) {
                        drawRect(STRIPE_COLOR, Offset(x, -size.height), Size(sw, size.height * 3))
                    }
                    x += total
                }
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                TopHeaderBar(
                    isGuest    = isGuest,
                    coins      = coins,
                    level      = level,
                    xpProgress = xpProgress,
                    onAdClick  = { showRemoveAds = true },
                    onAvatarClick = {
                        if (isGuest) showAuthDialog = true else onOpenProfile()
                    }
                )
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    WordleBubblesRow()
                }
                Spacer(Modifier.height(14.dp))
                GameButtonsSection(
                    classicStreak  = classicStreak,
                    onStatsClick   = { showStats = true },
                    onClassicClick = onStartClassic,
                    onDailyClick   = onStartDaily,
                    onWipClick     = { showWip = true }
                )
                Spacer(Modifier.height(14.dp))
            }

            FooterNavBar(
                isGuest   = isGuest,
                onHowTo   = onHowTo,
                onWip     = { showWip = true },
                onSignIn  = { showAuthDialog = true },
                onSettings = { showSettings = true }
            )
            BannerAdContainer()
        }

        // ── Dialogs ────────────────────────────────────────────────────────────
        if (showStats) {
            ComposeDialog(onDismissRequest = { showStats = false }) {
                MaterialTheme {
                    StatsDialogContent(profileManager) { showStats = false }
                }
            }
        }
        if (showRemoveAds) {
            RemoveAdsDialog(
                onDismiss = { showRemoveAds = false },
                onBuy     = { showRemoveAds = false }
            )
        }
        if (showWip) WipDialog { showWip = false }
        if (showSettings) {
            SettingsDialog(onDismiss = { showSettings = false })
        }
        if (showAuthDialog) {
            AuthDialog(
                firebaseAuth = firebaseAuth,
                onSuccess    = { showAuthDialog = false },
                onDismiss    = { showAuthDialog = false }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AUTH DIALOG  (Sign in / Register)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AuthDialog(
    firebaseAuth: FirebaseAuth,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var isRegister by remember { mutableStateOf(false) }
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error    by remember { mutableStateOf("") }
    var loading  by remember { mutableStateOf(false) }

    ComposeDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth().padding(16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1E3560), Color(0xFF162B4A))))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (isRegister) "РЕГИСТРАЦИЈА" else "ПРИЈАВА",
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = email, onValueChange = { email = it; error = "" },
                label = { Text("Е-маил", color = Color(0xAAFFFFFF)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF60DDFF),
                    unfocusedBorderColor = Color(0x66FFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF60DDFF)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it; error = "" },
                label = { Text("Лозинка", color = Color(0xAAFFFFFF)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF60DDFF),
                    unfocusedBorderColor = Color(0x66FFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF60DDFF)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (error.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(22.dp))

            if (loading) {
                CircularProgressIndicator(color = Color(0xFF60DDFF))
            } else {
                GoldButton(
                    label = if (isRegister) "НАПРАВИ НАЛОГ" else "ПРИЈАВИ СЕ",
                    onClick = {
                        loading = true
                        if (isRegister) {
                            firebaseAuth.createUserWithEmailAndPassword(email.trim(), password)
                                .addOnSuccessListener {
                                    loading = false
                                    onSuccess()
                                }
                                .addOnFailureListener { e ->
                                    loading = false
                                    error = when (e) {
                                        is FirebaseAuthWeakPasswordException -> "Лозинка мора имати бар 6 знакова"
                                        is FirebaseAuthUserCollisionException -> "Е-маил је већ у употреби"
                                        is FirebaseAuthInvalidCredentialsException -> "Неисправан е-маил"
                                        else -> "Грешка: ${e.message}"
                                    }
                                }
                        } else {
                            firebaseAuth.signInWithEmailAndPassword(email.trim(), password)
                                .addOnSuccessListener {
                                    loading = false
                                    onSuccess()
                                }
                                .addOnFailureListener { e ->
                                    loading = false
                                    error = when (e) {
                                        is FirebaseAuthInvalidCredentialsException -> "Погрешан е-маил или лозинка"
                                        else -> "Грешка: ${e.message}"
                                    }
                                }
                        }
                    }
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    if (isRegister) "Већ имаш налог? Пријави се" else "Немаш налог? Региструј се",
                    color = Color(0xFF60DDFF), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.clickable { isRegister = !isRegister; error = "" }
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Одустани",
                    color = Color(0xAAFFFFFF), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP HEADER BAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TopHeaderBar(
    isGuest: Boolean, coins: Int, level: Int, xpProgress: Float,
    onAdClick: () -> Unit, onAvatarClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Box(
            modifier = Modifier.align(Alignment.CenterStart).size(52.dp).clip(CircleShape)
                .background(Color.Black).border(3.dp, Color(0xFFCC2222), CircleShape)
                .clickable { onAdClick() },
            contentAlignment = Alignment.Center
        ) {
            Text("AD", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }

        Box(Modifier.align(Alignment.Center).clickable { onAvatarClick() }) {
            CenterAvatar(level = level, xpProgress = xpProgress, isGuest = isGuest)
        }

        Box(Modifier.align(Alignment.CenterEnd)) {
            CoinPill(coins = coins, isGuest = isGuest)
        }
    }
}

@Composable
private fun CenterAvatar(level: Int, xpProgress: Float, isGuest: Boolean) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
        Box(
            Modifier.size(116.dp).clip(CircleShape)
                .background(Color(0xFF6B3A1F)).border(5.dp, Color(0xFF9B6A3F), CircleShape)
        )
        CircularProgressIndicator(
            progress   = { xpProgress.coerceIn(0f, 1f) },
            modifier   = Modifier.size(108.dp),
            strokeWidth = 7.dp,
            color      = Color(0xFF60DDFF),
            trackColor = Color(0x33004466)
        )
        Box(
            modifier = Modifier.size(90.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFCCF870), Color(0xFF88D030))))
                .border(4.dp, Color(0xFF558820), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(if (isGuest) "?" else "🍎", fontSize = 40.sp)
        }
        Box(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF70DAFF), Color(0xFF1A90D8))))
                .border(2.dp, Color(0xFF006BB0), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 3.dp)
        ) {
            Text(level.toString(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = 2.dp)
                .size(28.dp).clip(CircleShape)
                .background(Color(0xFFE8A800)).border(2.dp, Color(0xFFA06000), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("W", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
        // "TAP TO SIGN IN" hint for guests
        if (isGuest) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = 18.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Color(0xCC1A3A5C))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("Пријави се", color = Color(0xFF60DDFF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CoinPill(coins: Int, isGuest: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.height(38.dp).widthIn(min = 90.dp)
                .clip(RoundedCornerShape(19.dp)).background(Color(0xFF5A3A1A))
                .border(2.dp, Color(0xFF8B5A2B), RoundedCornerShape(19.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isGuest) "--" else coins.toString(),
                    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(Color(0xFFE8A800))
                        .border(2.dp, Color(0xFFA06000), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("W", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF44BB55))
                .border(2.dp, Color(0xFF226622), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WORD BUBBLES
// ─────────────────────────────────────────────────────────────────────────────
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

// ─────────────────────────────────────────────────────────────────────────────
// GAME BUTTONS
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GameButtonsSection(
    classicStreak: Int, onStatsClick: () -> Unit,
    onClassicClick: () -> Unit, onDailyClick: () -> Unit, onWipClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(26.dp)).background(Color(0x55A03018))
            .border(2.dp, Color(0x44FFFFFF), RoundedCornerShape(26.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GoldSquareButton(modifier = Modifier.size(82.dp), onClick = onStatsClick) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📊", fontSize = 26.sp)
                    Text("Stats", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                }
            }
            GoldWideButton(
                title = "КЛАСИЧАН", rightTop = classicStreak.toString(), rightBottom = "STREAK",
                modifier = Modifier.weight(1f).height(82.dp), onClick = onClassicClick
            )
        }
        GoldWideButton(
            title = "РЕЧ ДАНА", rightTop = "🌟", rightBottom = "+100",
            modifier = Modifier.fillMaxWidth().height(82.dp), onClick = onDailyClick
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GoldWideButton(
                title = "РЕЧЕНИЦА", rightTop = "WIP", rightBottom = "скоро",
                modifier = Modifier.weight(1f).height(72.dp), onClick = onWipClick, titleSize = 14.sp
            )
            GoldWideButton(
                title = "ТАЈНА РЕЧ", rightTop = "WIP", rightBottom = "скоро",
                modifier = Modifier.weight(1f).height(72.dp), onClick = onWipClick, titleSize = 14.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FOOTER NAV BAR  — redesigned: dark pill cards with emoji + label
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FooterNavBar(
    isGuest: Boolean,
    onHowTo: () -> Unit,
    onWip: () -> Unit,
    onSignIn: () -> Unit,
    onSettings: () -> Unit
) {
    data class NavItem(val emoji: String, val label: String, val onClick: () -> Unit,
                       val highlight: Boolean = false)
    val items = listOf(
        NavItem("🛒", "SHOP",     onWip),
        NavItem("🛡️", "LEAGUE",  onWip),
        NavItem("❓", "УПУТСТВО",  onHowTo),
        NavItem("📖", "ВОДИЧ",    onWip),
        NavItem(
            if (isGuest) "🔑" else "⚙️",
            if (isGuest) "ПРИЈАВА" else "ПОДЕШАВАЊЕ",
            if (isGuest) onSignIn else onSettings,
            highlight = isGuest
        )
    )

    Row(
        modifier = Modifier.fillMaxWidth().background(FOOTER_BG).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val bg = if (item.highlight) Color(0xFF1A4A8A) else FOOTER_ITEM
            val border = if (item.highlight) Color(0xFF60DDFF) else Color(0xFF5A3820)
            Box(
                modifier = Modifier.weight(1f).height(58.dp)
                    .clip(RoundedCornerShape(14.dp)).background(bg)
                    .border(1.5.dp, border, RoundedCornerShape(14.dp))
                    .clickable { item.onClick() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(item.emoji, fontSize = 20.sp)
                    Text(
                        item.label,
                        color = if (item.highlight) Color(0xFF60DDFF) else Color(0xCCFFFFFF),
                        fontSize = 7.5.sp, fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center, maxLines = 1
                    )
                }
            }
        }
    }
}


@Composable
private fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { AudioSettingsManager(context) }
    var settings by remember { mutableStateOf(manager.getSettings()) }

    ComposeDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1E3560), Color(0xFF162B4A))))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ПОДЕШАВАЊА", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Музика", color = Color.White, fontWeight = FontWeight.Bold)
                Switch(
                    checked = settings.musicEnabled,
                    onCheckedChange = {
                        settings = settings.copy(musicEnabled = it)
                        manager.saveSettings(settings)
                    }
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Звучни ефекти", color = Color.White, fontWeight = FontWeight.Bold)
                Switch(
                    checked = settings.effectsEnabled,
                    onCheckedChange = {
                        settings = settings.copy(effectsEnabled = it)
                        manager.saveSettings(settings)
                    }
                )
            }

            Spacer(Modifier.height(10.dp))
            Text("Јачина музике: ${(settings.musicVolume * 100).roundToInt()}%", color = Color(0xFFD6D9E0), fontSize = 12.sp)
            Slider(
                value = settings.musicVolume,
                onValueChange = {
                    settings = settings.copy(musicVolume = it)
                    manager.saveSettings(settings)
                },
                valueRange = 0f..1f
            )

            Text("Јачина ефеката: ${(settings.effectsVolume * 100).roundToInt()}%", color = Color(0xFFD6D9E0), fontSize = 12.sp)
            Slider(
                value = settings.effectsVolume,
                onValueChange = {
                    settings = settings.copy(effectsVolume = it)
                    manager.saveSettings(settings)
                },
                valueRange = 0f..1f
            )

            Spacer(Modifier.height(12.dp))
            GoldButton("ЗАТВОРИ", onDismiss)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STATS DIALOG
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StatsDialogContent(profileManager: GameProfileManager, onClose: () -> Unit) {
    val maxValue = (profileManager.getAllGuessDistribution().maxOrNull() ?: 1).coerceAtLeast(1)
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1E3560), Color(0xFF162B4A))))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("СТАТИСТИКА", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatBox(profileManager.getClassicGamesPlayed().toString(), "ИГРЕ")
            StatBox(profileManager.getClassicWins().toString(), "ПОБЕДЕ")
            StatBox("${profileManager.getClassicWinRate()}%", "WIN %")
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatBox(profileManager.getClassicStreak().toString(), "STREAK")
            StatBox(profileManager.getBestClassicStreak().toString(), "BEST")
            StatBox(profileManager.getClassicLosses().toString(), "ПОРАЗИ")
        }
        Spacer(Modifier.height(18.dp))
        Text("Расподела погађања", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            profileManager.getAllGuessDistribution().forEachIndexed { i, v ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text((i + 1).toString(), color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2A355A))) {
                        val frac = if (maxValue == 0) 0f else v.toFloat() / maxValue
                        Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f))
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

// ─────────────────────────────────────────────────────────────────────────────
// GOLD COMPONENTS
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun GoldSquareButton(
    modifier: Modifier = Modifier, onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val src     = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val infiniteTransition = rememberInfiniteTransition(label = "stripe")
    val stripeOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
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
                val sw = 18.dp.toPx(); val total = sw * 2f
                var x = -size.height.toFloat() + (stripeOffset * total)
                while (x < size.width + size.height) {
                    withTransform({ rotate(-35f, Offset(size.width / 2f, size.height / 2f)) }) {
                        drawRect(GOLD_STRIPE, Offset(x, -size.height), Size(sw, size.height * 3))
                    }
                    x += total
                }
            }
            .clickable(interactionSource = src, indication = null) { onClick() },
        contentAlignment = Alignment.Center, content = content
    )
}

@Composable
fun GoldWideButton(
    title: String, rightTop: String, rightBottom: String,
    modifier: Modifier = Modifier, onClick: () -> Unit, titleSize: TextUnit = 20.sp
) {
    val src     = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val infiniteTransition = rememberInfiniteTransition(label = "stripe")
    val stripeOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "stripeOffset"
    )
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
                val sw = 18.dp.toPx(); val total = sw * 2f
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
        Box(Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp),
            contentAlignment = Alignment.CenterStart) {
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
                Text(rightTop, fontSize = if (rightTop.length <= 2) 22.sp else 16.sp,
                    fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text(rightBottom, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
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

// ─────────────────────────────────────────────────────────────────────────────
// REMOVE ADS DIALOG
// ─────────────────────────────────────────────────────────────────────────────
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
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF44BB55)),
                        contentAlignment = Alignment.Center) {
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

// ─────────────────────────────────────────────────────────────────────────────
// WIP DIALOG
// ─────────────────────────────────────────────────────────────────────────────
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

// ─────────────────────────────────────────────────────────────────────────────
// ADMOB BANNER
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BannerAdContainer() {
    val context = LocalContext.current
    Box(
        Modifier.fillMaxWidth().background(Color(0xFF1A0E06)).padding(vertical = 4.dp),
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

// ─────────────────────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────────────────────
private fun lightenColor(color: Color, amount: Float) = Color(
    red   = color.red   + (1f - color.red)   * amount,
    green = color.green + (1f - color.green) * amount,
    blue  = color.blue  + (1f - color.blue)  * amount,
    alpha = color.alpha
)
