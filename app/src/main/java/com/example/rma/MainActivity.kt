package com.example.rma

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog as ComposeDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import androidx.compose.animation.core.*
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt

private val BG_TOP       = Color(0xFFE8845A)
private val BG_BOT       = Color(0xFFD4694A)
private val STRIPE_COLOR = Color(0x18FFFFFF)
private val GOLD_LIGHT   = Color(0xFFFFD754)
private val GOLD_MID     = Color(0xFFFFC430)
private val GOLD_DARK    = Color(0xFFE8A800)
private val GOLD_STRIPE  = Color(0x22FFFFFF)
private val FOOTER_BG    = Color(0xFF2A1A0E)
private val FOOTER_ITEM  = Color(0xFF3D2810)

private enum class LeaderboardMetric(val label: String) {
    LEVEL("Ниво"),
    STREAK("Streak"),
    GAMES("Игре"),
    WINRATE("Победе %")
}

class MainActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        firebaseAuth = FirebaseAuth.getInstance()
        MobileAds.initialize(this)

        val profileManager = GameProfileManager(this)
        val coinRepo = CoinRepository(this)

        val composeContainer = findViewById<ComposeView>(R.id.composeMenu)
        composeContainer.setContent {
            MaterialTheme {
                MainScreen(
                    firebaseAuth   = firebaseAuth,
                    profileManager = profileManager,
                    coinRepo       = coinRepo,
                    onHowTo        = { dijalogObjasnjenjeMain() },
                    onOpenShop     = { startActivity(Intent(this, ShopActivity::class.java)) },
                    onStartClassic = {
                        startActivity(Intent(this, SlovopletIgra::class.java)
                            .putExtra("game_mode", "CLASSIC"))
                    },
                    onStartDaily = {
                        startActivity(Intent(this, SlovopletIgra::class.java)
                            .putExtra("game_mode", "DAILY"))
                    }
                )
            }
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

@Composable
private fun MainScreen(
    firebaseAuth: FirebaseAuth,
    profileManager: GameProfileManager,
    coinRepo: CoinRepository,
    onHowTo: () -> Unit,
    onOpenShop: () -> Unit,
    onStartClassic: () -> Unit,
    onStartDaily: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val scale = (configuration.screenWidthDp / 390f).coerceIn(0.85f, 1.2f)
    val user = firebaseAuth.currentUser
    val isGuest = user == null

    var coins by remember { mutableIntStateOf(coinRepo.getLocal()) }
    var level by remember { mutableIntStateOf(if (isGuest) 1 else profileManager.getLevel()) }
    var xpProgress by remember { mutableFloatStateOf(if (isGuest) 0f else profileManager.getXpProgress()) }
    var classicStreak by remember { mutableIntStateOf(profileManager.getClassicStreak()) }

    fun refreshHeaderStats() {
        if (!isGuest) {
            coinRepo.load { reconciled -> coins = reconciled }
            level = profileManager.getLevel()
            xpProgress = profileManager.getXpProgress()
        }
        classicStreak = profileManager.getClassicStreak()
    }

    LaunchedEffect(isGuest) { refreshHeaderStats() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isGuest) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshHeaderStats()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val statsRepository = remember { FirebaseStatsRepository() }

    var showStats     by remember { mutableStateOf(false) }
    var showRemoveAds by remember { mutableStateOf(false) }
    var showWip       by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLeaderboard by remember { mutableStateOf(false) }
    var showDailyAlreadyPlayedPopup by remember { mutableStateOf(false) }

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
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp)
            ) {
                TopHeaderBar(
                    isGuest    = isGuest,
                    coins      = coins,
                    level      = level,
                    xpProgress = xpProgress,
                    scale      = scale,
                    onAdClick  = { showRemoveAds = true },
                    onAvatarClick = {
                        if (isGuest) showAuthDialog = true else context.startActivity(Intent(context, ProfileActivity::class.java))
                    },
                    onPlusClick = onOpenShop
                )
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    WordleBubblesRow(scale = scale)
                }
                Spacer(Modifier.height(12.dp))
                GameButtonsSection(
                    classicStreak  = classicStreak,
                    scale          = scale,
                    onStatsClick   = { showStats = true },
                    onClassicClick = onStartClassic,
                    onDailyClick   = {
                        if (profileManager.hasPlayedDailyToday()) {
                            showDailyAlreadyPlayedPopup = true
                        } else {
                            onStartDaily()
                        }
                    },
                    onWipClick     = { showWip = true }
                )
                Spacer(Modifier.height(12.dp))
            }

            FooterNavBar(
                isGuest       = isGuest,
                scale         = scale,
                onHowTo       = onHowTo,
                onShop        = onOpenShop,
                onWip         = { showWip = true },
                onSignIn      = { showAuthDialog = true },
                onSettings    = { showSettings = true },
                onLeaderboard = { showLeaderboard = true }
            )
            BannerAdContainer()
        }

        if (showStats) {
            ComposeDialog(onDismissRequest = { showStats = false }) {
                MaterialTheme {
                    StatsDialogContent(profileManager) { showStats = false }
                }
            }
        }
        if (showLeaderboard) {
            ComposeDialog(onDismissRequest = { showLeaderboard = false }) {
                MaterialTheme {
                    LeaderboardDialogContent(
                        statsRepository = statsRepository,
                        onClose = { showLeaderboard = false }
                    )
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
            SettingsDialog(
                onDismiss = { showSettings = false },
                onLogout = {
                    firebaseAuth.signOut()
                    showSettings = false
                    val intent = Intent(context, SignInActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    context.startActivity(intent)
                }
            )
        }
        if (showAuthDialog) {
            AuthDialog(
                firebaseAuth = firebaseAuth,
                onSuccess    = { showAuthDialog = false },
                onDismiss    = { showAuthDialog = false }
            )
        }
        if (showDailyAlreadyPlayedPopup) {
            AlertDialog(
                onDismissRequest = { showDailyAlreadyPlayedPopup = false },
                confirmButton = {
                    TextButton(onClick = { showDailyAlreadyPlayedPopup = false }) {
                        Text("У реду")
                    }
                },
                title = { Text("Реч дана је већ одиграна") },
                text = { Text("Већ си одиграо/ла данашњу Реч дана. Врати се сутра за нову реч!") }
            )
        }
    }
}

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
                                    val createdUser = firebaseAuth.currentUser
                                    if (createdUser == null) {
                                        loading = false
                                        onSuccess()
                                    } else {
                                        PlayerNameManager.assignRandomNameIfMissing(
                                            user = createdUser,
                                            onDone = {
                                                loading = false
                                                onSuccess()
                                            },
                                            onFailure = {
                                                loading = false
                                                onSuccess()
                                            }
                                        )
                                    }
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

@Composable
private fun ChangeDisplayNameDialog(
    firebaseAuth: FirebaseAuth,
    statsRepository: FirebaseStatsRepository,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(firebaseAuth.currentUser?.displayName ?: "") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val context = LocalContext.current

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
            Text("ПРОМЕНИ ИМЕ", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = ""
                },
                singleLine = true,
                label = { Text("Име играча") },
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
                Text(error, color = Color(0xFFFF6B6B), textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(16.dp))
            GoldButton(
                label = if (loading) "ЧУВАЊЕ..." else "САЧУВАЈ",
                onClick = {
                    val newName = name.trim()
                    val user = firebaseAuth.currentUser
                    if (newName.length < 3) {
                        error = "Име мора имати бар 3 карактера."
                        return@GoldButton
                    }
                    if (user == null) {
                        error = "Нисте пријављени."
                        return@GoldButton
                    }

                    loading = true
                    user.updateProfile(
                        com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(newName)
                            .build()
                    ).addOnSuccessListener {
                        statsRepository.updateDisplayName(
                            displayName = newName,
                            onSuccess = {
                                loading = false
                                Toast.makeText(context, "Име је сачувано", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            onFailure = {
                                loading = false
                                error = "Име је промењено локално, али не и на листи."
                            }
                        )
                    }.addOnFailureListener {
                        loading = false
                        error = "Није успело чување имена."
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "ОТКАЖИ",
                color = Color(0xAAFFFFFF),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onDismiss() }
            )
        }
    }
}

@Composable
private fun TopHeaderBar(
    isGuest: Boolean, coins: Int, level: Int, xpProgress: Float,
    scale: Float,
    onAdClick: () -> Unit, onAvatarClick: () -> Unit, onPlusClick: () -> Unit
) {
    val adSize = (36 * scale).dp
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Box(
            modifier = Modifier.align(Alignment.CenterStart).size(adSize).clip(CircleShape)
                .background(Color(0xFFE24A3B)).clickable { onAdClick() },
            contentAlignment = Alignment.Center
        ) {
            Text("AD", color = Color.White, fontWeight = FontWeight.Black, fontSize = (11 * scale).sp)
        }

        Box(Modifier.align(Alignment.Center).clickable { onAvatarClick() }) {
            CenterAvatar(level = level, xpProgress = xpProgress, isGuest = isGuest)
        }

        Box(Modifier.align(Alignment.CenterEnd)) {
            CoinPill(coins = coins, isGuest = isGuest, scale = scale, onPlusClick = onPlusClick)
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
private fun CoinPill(coins: Int, isGuest: Boolean, scale: Float, onPlusClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.height((38 * scale).dp).widthIn(min = (90 * scale).dp)
                .clip(RoundedCornerShape((19 * scale).dp)).background(Color(0xFF5A3A1A))
                .border(2.dp, Color(0xFF8B5A2B), RoundedCornerShape((19 * scale).dp))
                .padding(horizontal = (12 * scale).dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isGuest) "--" else coins.toString(),
                    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (20 * scale).sp
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size((26 * scale).dp).clip(CircleShape).background(Color(0xFFE8A800))
                        .border(2.dp, Color(0xFFA06000), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Д", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (12 * scale).sp)
                }
            }
        }
        Spacer(Modifier.width((4 * scale).dp))
        Box(
            Modifier.size((30 * scale).dp).clip(CircleShape).background(Color(0xFF44BB55))
                .border(2.dp, Color(0xFF226622), CircleShape)
                .clickable { onPlusClick() },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
    }
}

@Composable
private fun WordleBubblesRow(scale: Float) {
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
    Row(horizontalArrangement = Arrangement.spacedBy((5 * scale).dp), modifier = Modifier.padding(horizontal = (10 * scale).dp)) {
        bubbles.forEach { b ->
            Box(
                modifier = Modifier.size((38 * scale).dp)
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
                    color = b.shadow.copy(alpha = 0.55f), modifier = Modifier.offset(y = (1.5f * scale).dp))
                Text(b.ch, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

@Composable
private fun GameButtonsSection(
    classicStreak: Int, scale: Float, onStatsClick: () -> Unit,
    onClassicClick: () -> Unit, onDailyClick: () -> Unit, onWipClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(26.dp)).background(Color(0x55A03018))
            .border(2.dp, Color(0x44FFFFFF), RoundedCornerShape(26.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
            GoldSquareButton(modifier = Modifier.size((82 * scale).dp), onClick = onStatsClick) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📊", fontSize = 26.sp)
                    Text("СТАТ", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (11 * scale).sp)
                }
            }
            GoldWideButton(
                title = "КЛАСИЧАН", rightTop = classicStreak.toString(), rightBottom = "НИЗ",
                modifier = Modifier.weight(1f).height((82 * scale).dp), onClick = onClassicClick
            )
        }
        GoldWideButton(
            title = "РЕЧ ДАНА", rightTop = "", rightBottom = "+100",
            modifier = Modifier.fillMaxWidth().height((82 * scale).dp), onClick = onDailyClick
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
            GoldWideButton(
                title = "РЕЧЕНИЦА", rightTop = "SOON", rightBottom = "soon",
                modifier = Modifier.weight(1f).height((72 * scale).dp), onClick = onWipClick, titleSize = (14 * scale).sp
            )
            GoldWideButton(
                title = "ТАЈНА РЕЧ", rightTop = "SOON", rightBottom = "soon",
                modifier = Modifier.weight(1f).height((72 * scale).dp), onClick = onWipClick, titleSize = (14 * scale).sp
            )
        }
    }
}
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun GameButtonsSectionPreview() {
    MaterialTheme {
        GameButtonsSection(
            classicStreak = 5,
            scale = 1f,
            onStatsClick = {},
            onClassicClick = {},
            onDailyClick = {},
            onWipClick = {}
        )
    }
}

@Composable
private fun FooterNavBar(
    isGuest: Boolean,
    scale: Float,
    onHowTo: () -> Unit,
    onShop: () -> Unit,
    onWip: () -> Unit,
    onSignIn: () -> Unit,
    onSettings: () -> Unit,
    onLeaderboard: () -> Unit
) {
    data class NavItem(val emoji: String, val label: String, val onClick: () -> Unit,
                       val highlight: Boolean = false)
    val items = listOf(
        NavItem("🛒", "ПРОДАВНИЦА",     onShop),
        NavItem("🏆", "ЛИСТА",  onLeaderboard),
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
        modifier = Modifier.fillMaxWidth().background(FOOTER_BG).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
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
                    Text(item.emoji, fontSize = (20 * scale).sp)
                    Text(
                        item.label,
                        color = if (item.highlight) Color(0xFF60DDFF) else Color(0xCCFFFFFF),
                        fontSize = (7 * scale).sp, fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center, maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(onDismiss: () -> Unit, onLogout: () -> Unit) {
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
            GoldButton("ОДЈАВИ СЕ", onLogout)
            Spacer(Modifier.height(10.dp))
            GoldButton("ЗАТВОРИ", onDismiss)
        }
    }
}

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
            StatBox("${profileManager.getClassicWinRate()}%", "ПОБЕДЕ %")
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatBox(profileManager.getClassicStreak().toString(), "НИЗ")
            StatBox(profileManager.getBestClassicStreak().toString(), "НАЈБОЉИ")
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
private fun LeaderboardDialogContent(
    statsRepository: FirebaseStatsRepository,
    onClose: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf<List<FirebaseStatsRepository.LeaderboardEntry>>(emptyList()) }

    LaunchedEffect(Unit) {
        statsRepository.loadLeaderboard(
            onSuccess = {
                entries = it
                loading = false
            },
            onFailure = {
                error = it.localizedMessage ?: "Greška pri učitavanju liste"
                loading = false
            }
        )
    }

    var selectedTab by remember { mutableStateOf(LeaderboardMetric.LEVEL) }
    val sortedEntries = remember(entries, selectedTab) {
        when (selectedTab) {
            LeaderboardMetric.LEVEL -> entries.sortedByDescending { it.level }
            LeaderboardMetric.STREAK -> entries.sortedByDescending { it.bestStreak }
            LeaderboardMetric.GAMES -> entries.sortedByDescending { it.gamesPlayed }
            LeaderboardMetric.WINRATE -> entries.sortedByDescending { it.winRate }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF4E7FC6), Color(0xFF2E4B80))))
            .border(3.dp, Color(0xFF9CC2F8), RoundedCornerShape(24.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Leaderboard", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Friends", "Players", "Teams").forEach { label ->
                Box(
                    modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (label == "Players") Color(0xFF8FC1FF) else Color(0x66587CB2))
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF1B847))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) { Text("World", color = Color.White, fontWeight = FontWeight.ExtraBold) }
            Box(
                modifier = Modifier.weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF6E84B7))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) { Text("Serbia", color = Color.White, fontWeight = FontWeight.ExtraBold) }
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            LeaderboardMetric.entries.forEach { tab ->
                val selected = tab == selectedTab
                Box(
                    modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Color(0xFFF1B847) else Color(0xFF4C6699))
                        .clickable { selectedTab = tab }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) { Text(tab.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
            }
        }

        Spacer(Modifier.height(10.dp))
        when {
            loading -> CircularProgressIndicator(color = Color.White)
            error != null -> Text(error ?: "", color = Color(0xFFFFD6D6))
            sortedEntries.isEmpty() -> Text("No players yet.", color = Color.White)
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sortedEntries.take(8).forEachIndexed { index, entry ->
                    LeaderboardMetricRow(rank = index + 1, player = entry.displayName, selectedMetric = selectedTab, entry = entry)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        GoldButton("CLOSE", onClose)
    }
}

@Composable
private fun LeaderboardMetricRow(
    rank: Int,
    player: String,
    selectedMetric: LeaderboardMetric,
    entry: FirebaseStatsRepository.LeaderboardEntry
) {
    val value = when (selectedMetric) {
        LeaderboardMetric.LEVEL -> "${entry.level}"
        LeaderboardMetric.STREAK -> "${entry.bestStreak}"
        LeaderboardMetric.GAMES -> "${entry.gamesPlayed}"
        LeaderboardMetric.WINRATE -> "${entry.winRate}%"
    }
    val rowColor = when (rank) {
        1 -> Color(0xFFE3AE33)
        2 -> Color(0xFFC9CED9)
        3 -> Color(0xFFC98A5A)
        else -> Color(0xFFDFE6F2)
    }

    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(rowColor)
            .border(2.dp, Color(0x885E6D8A), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(rank.toString(), color = Color(0xFF1F2E4A), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.width(26.dp))
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(player.uppercase(), color = Color(0xFF1F2E4A), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("LEVEL ${entry.level}", color = Color(0xFF3F526F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("👑", fontSize = 18.sp)
            Text(value, color = Color(0xFF1F2E4A), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

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
                var x = -size.height + (stripeOffset * total)
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
            Text("", fontSize = 52.sp)
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

@Composable
private fun BannerAdContainer() {
    val context = LocalContext.current
    Box(
        Modifier.fillMaxWidth().background(Color(0xFF1A0E06)).navigationBarsPadding().padding(vertical = 4.dp),
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

private fun lightenColor(color: Color, amount: Float) = Color(
    red   = color.red   + (1f - color.red)   * amount,
    green = color.green + (1f - color.green) * amount,
    blue  = color.blue  + (1f - color.blue)  * amount,
    alpha = color.alpha
)
