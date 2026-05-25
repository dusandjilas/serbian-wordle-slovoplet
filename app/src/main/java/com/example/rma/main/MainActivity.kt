package com.example.rma.main

import com.example.rma.R
import com.example.rma.ads.AdManager
import com.example.rma.auth.SignInActivity
import com.example.rma.core.managers.AudioSettingsManager
import com.example.rma.core.managers.GameProfileManager
import com.example.rma.core.managers.PlayerNameManager
import com.example.rma.core.repository.CoinRepository
import com.example.rma.core.repository.FirebaseStatsRepository
import com.example.rma.game.ui.SlovopletIgra
import com.example.rma.profile.ProfileActivity
import com.example.rma.shop.ShopActivity

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.ui.unit.Dp
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import com.example.rma.game.ui.NeedCoinsDialog
import kotlin.math.roundToInt
import kotlin.math.min

private val BG_TOP       = Color(0xFF243B5C)
private val BG_BOT       = Color(0xFF162B4A)
private val STRIPE_COLOR = Color(0x228995A3)
private val GOLD_LIGHT   = Color(0xFFA8B1BE)
private val GOLD_MID     = Color(0xFF8995A3)
private val GOLD_DARK    = Color(0xFF5E6B7A)
private val GOLD_STRIPE  = Color(0x22C11521)
private val FOOTER_BG    = Color(0xFF0F1E33)

private enum class LeaderboardMetric(val label: String) {
    LEVEL("Nivo"),
    STREAK("Streak"),
    GAMES("Igre"),
    WINRATE("Pobede %")
}

class MainActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var adManager: AdManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        firebaseAuth = FirebaseAuth.getInstance()
        MobileAds.initialize(this)
        adManager = AdManager(this).also { it.loadAd("ca-app-pub-3940256099942544/5224354917") }

        val profileManager = GameProfileManager(this)
        val coinRepo = CoinRepository(this)

        val composeContainer = findViewById<ComposeView>(R.id.composeMenu)
        composeContainer.setContent {
            MaterialTheme {
                MainScreen(
                    firebaseAuth   = firebaseAuth,
                    profileManager = profileManager,
                    coinRepo       = coinRepo,
                    adManager      = adManager,
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

}

@Composable
private fun MainScreen(
    firebaseAuth: FirebaseAuth,
    profileManager: GameProfileManager,
    coinRepo: CoinRepository,
    adManager: AdManager,
    onOpenShop: () -> Unit,
    onStartClassic: () -> Unit,
    onStartDaily: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val widthScale = configuration.screenWidthDp / 390f
    val heightScale = configuration.screenHeightDp / 820f
    val scale = min(widthScale, heightScale).coerceIn(0.72f, 1.12f)
    var currentUser by remember(firebaseAuth) { mutableStateOf(firebaseAuth.currentUser) }
    val isGuest = currentUser == null

    var coins by remember { mutableIntStateOf(coinRepo.getLocal()) }
    var level by remember { mutableIntStateOf(if (isGuest) 1 else profileManager.getLevel()) }
    var xpProgress by remember { mutableFloatStateOf(if (isGuest) 0f else profileManager.getXpProgress()) }
    var classicStreak by remember { mutableIntStateOf(profileManager.getClassicStreak()) }
    var avatarEmoji by remember { mutableStateOf(if (isGuest) "?" else profileManager.getProfileAvatar()) }

    fun refreshHeaderStats() {
        if (isGuest) {
            coins = coinRepo.getLocal()
            level = 1
            xpProgress = 0f
            avatarEmoji = "?"
        } else {
            coinRepo.load { reconciled -> coins = reconciled }
            level = profileManager.getLevel()
            xpProgress = profileManager.getXpProgress()
            avatarEmoji = profileManager.getProfileAvatar()
        }
        classicStreak = profileManager.getClassicStreak()
    }

    DisposableEffect(firebaseAuth) {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        firebaseAuth.addAuthStateListener(authStateListener)
        onDispose { firebaseAuth.removeAuthStateListener(authStateListener) }
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
    val adReady by adManager.adReady.collectAsState()
    var showNeedCoinsPopup by remember { mutableStateOf(false) }
    var pendingReward by remember { mutableIntStateOf(0) }
    var showWordChoiceInfo by remember { mutableStateOf(false) }
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
                    avatarEmoji = avatarEmoji,
                    scale      = scale,
                    widthScale = widthScale,
                    onAdClick  = {
                        pendingReward = 25
                        showNeedCoinsPopup = true
                    },
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
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            FooterNavBar(
                isGuest       = isGuest,
                scale         = scale,
                onHowTo       = { showWordChoiceInfo = true },
                onShop        = onOpenShop,
                onSignIn      = { showAuthDialog = true },
                onSettings    = { showSettings = true },
                onLeaderboard = { showLeaderboard = true }
            )

            // Temporarily disabled banner ad to prevent Chromium WebView rendering issues on some devices.
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
        if (showNeedCoinsPopup) {
            NeedCoinsDialog(
                reward = pendingReward,
                adReady = adReady,
                onClaimAd = {
                    if (adReady) {
                        adManager.showAd {
                            val newTotal = coinRepo.add(pendingReward)
                            coins = newTotal
                            showNeedCoinsPopup = false
                        }
                    } else {
                        Toast.makeText(context, "Reklama nije spremna, pokušaj kasnije", Toast.LENGTH_SHORT).show()
                    }
                },
                onNoThanks = { showNeedCoinsPopup = false }
            )
        }
        if (showWordChoiceInfo) {
            WordChoiceInfoDialog(onDismiss = { showWordChoiceInfo = false })
        }
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
                        Text("U redu")
                    }
                },
                title = { Text("Reč dana je već odigrana") },
                text = { Text("Već si odigrao/la današnju Reč dana. Vrati se sutra za novu reč!") }
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
                if (isRegister) "REGISTRACIJA" else "PRIJAVA",
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = email, onValueChange = { email = it; error = "" },
                label = { Text("E-mail", color = Color(0xAAFFFFFF)) },
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
                label = { Text("Lozinka", color = Color(0xAAFFFFFF)) },
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
                    label = if (isRegister) "NAPRAVI NALOG" else "PRIJAVI SE",
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
                                        is FirebaseAuthWeakPasswordException -> "Lozinka mora imati bar 6 znakova"
                                        is FirebaseAuthUserCollisionException -> "E-mail je već u upotrebi"
                                        is FirebaseAuthInvalidCredentialsException -> "Neispravan e-mail"
                                        else -> "Greška: ${e.message}"
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
                                        is FirebaseAuthInvalidCredentialsException -> "Pogrešan e-mail ili lozinka"
                                        else -> "Greška: ${e.message}"
                                    }
                                }
                        }
                    }
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    if (isRegister) "Već imaš nalog? Prijavi se" else "Nemaš nalog? Registruj se",
                    color = Color(0xFF60DDFF), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.clickable { isRegister = !isRegister; error = "" }
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Odustani",
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
            Text("PROMENI IME", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = ""
                },
                singleLine = true,
                label = { Text("Ime igrača") },
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
                label = if (loading) "ČUVANJE..." else "SAČUVAJ",
                onClick = {
                    val newName = name.trim()
                    val user = firebaseAuth.currentUser
                    if (newName.length < 3) {
                        error = "Ime mora imati bar 3 karaktera."
                        return@GoldButton
                    }
                    if (user == null) {
                        error = "Niste prijavljeni."
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
                                Toast.makeText(context, "Ime je sačuvano", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            onFailure = {
                                loading = false
                                error = "Ime je promenjeno lokalno, ali ne i na listi."
                            }
                        )
                    }.addOnFailureListener {
                        loading = false
                        error = "Nije uspelo čuvanje imena."
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "OTKAŽI",
                color = Color(0xAAFFFFFF),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onDismiss() }
            )
        }
    }
}

@Composable
private fun TopHeaderBar(
    isGuest: Boolean, coins: Int, level: Int, xpProgress: Float, avatarEmoji: String,
    scale: Float,
    widthScale: Float,
    onAdClick: () -> Unit, onAvatarClick: () -> Unit, onPlusClick: () -> Unit
) {
    val adSize = (34 * scale).dp
    val avatarSize = (112 * min(scale, widthScale)).coerceIn(84f, 112f).dp
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Box(
            modifier = Modifier.align(Alignment.CenterStart).size(adSize).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF7ED4FF), Color(0xFF2A6CD4)))).border(2.dp, Color.White.copy(alpha = 0.45f), CircleShape).clickable { onAdClick() },
            contentAlignment = Alignment.Center
        ) {
            Text("AD", color = Color.White, fontWeight = FontWeight.Black, fontSize = (11 * scale).sp)
        }

        Box(Modifier.align(Alignment.Center).clickable { onAvatarClick() }) {
            CenterAvatar(level = level, xpProgress = xpProgress, isGuest = isGuest, avatarEmoji = avatarEmoji, size = avatarSize)
        }

        Box(Modifier.align(Alignment.CenterEnd)) {
            CoinPill(coins = coins, isGuest = isGuest, scale = scale, onPlusClick = onPlusClick)
        }
    }
}

@Composable
private fun CenterAvatar(level: Int, xpProgress: Float, isGuest: Boolean, avatarEmoji: String, size: Dp = 112.dp) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Box(
            Modifier.size(size * 0.96f).clip(CircleShape)
                .background(Color(0xFF6B3A1F)).border(4.dp, Color(0xFF9B6A3F), CircleShape)
        )
        CircularProgressIndicator(
            progress   = { xpProgress.coerceIn(0f, 1f) },
            modifier   = Modifier.size(size * 0.9f),
            strokeWidth = 6.dp,
            color      = Color(0xFF60DDFF),
            trackColor = Color(0x33004466)
        )
        Box(
            modifier = Modifier.size(size * 0.75f).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFCCF870), Color(0xFF88D030))))
                .border(4.dp, Color(0xFF558820), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(if (isGuest) "?" else avatarEmoji, fontSize = (size.value * 0.33f).sp)
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
                .size(size * 0.24f).clip(CircleShape)
                .background(Color(0xFFE8A800)).border(2.dp, Color(0xFFA06000), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("W", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.1f).sp)
        }
        if (isGuest) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = 18.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Color(0xCC1A3A5C))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("Prijavi se", color = Color(0xFF60DDFF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                Green3DCoinIcon(size = (26 * scale).dp, label = "D", fontSize = (12 * scale).sp)
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
    val letters = "SLOVOPLET".toList()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        val gap = (5 * scale).dp
        val horizontalPadding = (10 * scale).dp
        val maxTile = (36 * scale).dp
        val availableForTiles = (maxWidth - horizontalPadding * 2f - gap * (letters.size - 1).toFloat()) / letters.size.toFloat()
        val tileSize = minOf(maxTile, availableForTiles).coerceAtLeast(24.dp)

        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape((20 * scale).dp))
                .background(Brush.horizontalGradient(listOf(Color(0x332CD6FF), Color(0x2212F29A))))
                .border(1.dp, Color(0x338EEBFF), RoundedCornerShape((20 * scale).dp))
                .padding(horizontal = horizontalPadding, vertical = (10 * scale).dp)
        ) {
            letters.forEachIndexed { index, ch ->
                val colors = when (index % 3) {
                    0 -> listOf(Color(0xFF4DE1FF), Color(0xFF1570FF))
                    1 -> listOf(Color(0xFFFFD166), Color(0xFFE89000))
                    else -> listOf(Color(0xFF7CFFB2), Color(0xFF00A86B))
                }
                Box(
                    modifier = Modifier
                        .size(tileSize)
                        .drawBehind {
                            drawRoundRect(
                                Color(0x55000000),
                                topLeft = Offset(0f, 2.dp.toPx()),
                                size = Size(size.width, size.height),
                                cornerRadius = CornerRadius(10.dp.toPx())
                            )
                        }
                        .clip(RoundedCornerShape((9 * scale).dp))
                        .background(Brush.verticalGradient(colors))
                        .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape((9 * scale).dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        ch.toString(),
                        fontSize = (17 * scale).sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun GameButtonsSection(
    classicStreak: Int, scale: Float, onStatsClick: () -> Unit,
    onClassicClick: () -> Unit, onDailyClick: () -> Unit
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
                    Text("STAT", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (11 * scale).sp)
                }
            }
            GoldWideButton(
                title = "KLASIČAN", rightTop = classicStreak.toString(), rightBottom = "NIZ",
                modifier = Modifier.weight(1f).height((82 * scale).dp), onClick = onClassicClick
            )
        }
        GoldWideButton(
            title = "REČ DANA", rightTop = "", rightBottom = "+100",
            modifier = Modifier.fillMaxWidth().height((82 * scale).dp), onClick = onDailyClick
        )
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
            onDailyClick = {}
        )
    }
}

@Composable
private fun FooterNavBar(
    isGuest: Boolean,
    scale: Float,
    onHowTo: () -> Unit,
    onShop: () -> Unit,
    onSignIn: () -> Unit,
    onSettings: () -> Unit,
    onLeaderboard: () -> Unit
) {
    data class NavItem(val label: String, val onClick: () -> Unit,
                       val highlight: Boolean = false)
    val items = listOf(
        NavItem("PRODAVNICA", onShop),
        NavItem("LISTA", onLeaderboard),
        NavItem("REČI", onHowTo),
        NavItem(
            if (isGuest) "PRIJAVA" else "PODEŠAVANJE",
            if (isGuest) onSignIn else onSettings,
            highlight = isGuest
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FOOTER_BG)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (item in items) {
            GameNavButton(
                label = item.label,
                highlighted = item.highlight,
                scale = scale,
                onClick = item.onClick,
                modifier = Modifier.weight(1f).height((56 * scale).coerceIn(48f, 58f).dp)
            )
        }
    }
}

@Composable
private fun GameNavButton(
    label: String,
    highlighted: Boolean,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val infiniteTransition = rememberInfiniteTransition(label = "navStripe")
    val stripeOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "navStripeOffset"
    )
    val top = if (highlighted) Color(0xFF62E6FF) else GOLD_LIGHT
    val bottom = if (highlighted) Color(0xFF1775FF) else GOLD_MID
    val textColor = if (highlighted) Color.White else Color(0xFFF8FBFF)
    val shadowColor = if (highlighted) Color(0xFF0B3D86) else GOLD_DARK

    Box(
        modifier = modifier
            .offset { IntOffset(0, if (pressed) 3.dp.roundToPx() else 0) }
            .drawBehind {
                if (!pressed) {
                    drawRoundRect(
                        shadowColor,
                        Offset(0f, 4.dp.toPx()),
                        Size(size.width, size.height),
                        CornerRadius(15.dp.toPx())
                    )
                }
            }
            .clip(RoundedCornerShape(15.dp))
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .drawBehind {
                val sw = 14.dp.toPx()
                val total = sw * 2f
                var x = -size.height + (stripeOffset * total)
                while (x < size.width + size.height) {
                    withTransform({ rotate(-35f, Offset(size.width / 2f, size.height / 2f)) }) {
                        drawRect(Color.White.copy(alpha = if (highlighted) 0.12f else 0.08f), Offset(x, -size.height), Size(sw, size.height * 3))
                    }
                    x += total
                }
            }
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(15.dp))
            .clickable(interactionSource = src, indication = null) { onClick() }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = textColor,
            fontSize = (9.5f * scale).coerceIn(8.2f, 10.5f).sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = (11.5f * scale).coerceIn(10f, 12.5f).sp
        )
    }
}

@Composable
private fun StatBox(value: String, label: String) {
    Column(
        modifier = Modifier
            .widthIn(min = 88.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x332F3338))
            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Color(0xFFDDE3EA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
            Text("PODEŠAVANJA", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Muzika", color = Color.White, fontWeight = FontWeight.Bold)
                Switch(
                    checked = settings.musicEnabled,
                    onCheckedChange = {
                        settings = settings.copy(musicEnabled = it)
                        manager.saveSettings(settings)
                    }
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Zvučni efekti", color = Color.White, fontWeight = FontWeight.Bold)
                Switch(
                    checked = settings.effectsEnabled,
                    onCheckedChange = {
                        settings = settings.copy(effectsEnabled = it)
                        manager.saveSettings(settings)
                    }
                )
            }

            Spacer(Modifier.height(10.dp))
            Text("Jačina muzike: ${(settings.musicVolume * 100).roundToInt()}%", color = Color(0xFFD6D9E0), fontSize = 12.sp)
            Slider(
                value = settings.musicVolume,
                onValueChange = {
                    settings = settings.copy(musicVolume = it)
                    manager.saveSettings(settings)
                },
                valueRange = 0f..1f
            )

            Text("Jačina efekata: ${(settings.effectsVolume * 100).roundToInt()}%", color = Color(0xFFD6D9E0), fontSize = 12.sp)
            Slider(
                value = settings.effectsVolume,
                onValueChange = {
                    settings = settings.copy(effectsVolume = it)
                    manager.saveSettings(settings)
                },
                valueRange = 0f..1f
            )

            Spacer(Modifier.height(12.dp))
            GoldButton("ODJAVI SE", onLogout)
            Spacer(Modifier.height(10.dp))
            GoldButton("ZATVORI", onDismiss)
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
        Text("STATISTIKA", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatBox(profileManager.getClassicGamesPlayed().toString(), "IGRE")
            StatBox(profileManager.getClassicWins().toString(), "POBEDE")
            StatBox("${profileManager.getClassicWinRate()}%", "POBEDE %")
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatBox(profileManager.getClassicStreak().toString(), "NIZ")
            StatBox(profileManager.getBestClassicStreak().toString(), "NAJBOLJI")
            StatBox(profileManager.getClassicLosses().toString(), "PORAZI")
        }
        Spacer(Modifier.height(18.dp))
        Text("Raspodela pogađanja", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val distribution = profileManager.getAllGuessDistribution()
            for (i in distribution.indices) {
                val v = distribution[i]
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
        GoldButton("ZATVORI", onClose)
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
        Text("Leaderboard", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF1B847))
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("World", color = Color.White, fontWeight = FontWeight.ExtraBold)
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            for (tab in LeaderboardMetric.entries) {
                val selected = tab == selectedTab
                Box(
                    modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Color(0xFFF1B847) else Color(0xFF4C6699))
                        .clickable { selectedTab = tab }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) { Text(tab.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1) }
            }
        }

        Spacer(Modifier.height(10.dp))
        when {
            loading -> CircularProgressIndicator(color = Color.White)
            error != null -> Text(error ?: "", color = Color(0xFFFFD6D6))
            sortedEntries.isEmpty() -> Text("No players yet.", color = Color.White)
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val visibleEntries = sortedEntries.take(8)
                for (index in visibleEntries.indices) {
                    val entry = visibleEntries[index]
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
        Text(rank.toString(), color = Color(0xFF1F2E4A), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.width(22.dp))
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(player.uppercase(), color = Color(0xFF1F2E4A), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("LEVEL ${entry.level}", color = Color(0xFF3F526F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("👑", fontSize = 18.sp)
            Text(value, color = Color(0xFF1F2E4A), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
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
            Text("UKLONI REKLAME", color = Color.White, fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Uživaj u igri bez prekida.\nJednom kupi, zauvek bez reklama.",
                color = Color(0xCCFFFFFF), fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            for (text in listOf("Bez baner reklama", "Bez video reklama", "Podrška razvoju igre")) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Badge3DIcon(size = 26.dp, symbol = "✓", bgTop = Color(0xFF6BD8FF), bgBottom = Color(0xFF2F74D8)) { }
                    Spacer(Modifier.width(12.dp))
                    Text(text, color = Color.White, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            GoldButton("KUPI — 2,99 €", onBuy)
            Spacer(Modifier.height(14.dp))
            Text("NE SADA", color = Color(0xAAFFFFFF), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clickable { onDismiss() })
        }
    }
}


@Composable
private fun Green3DCoinIcon(size: Dp, label: String, fontSize: TextUnit) {
    Box(
        modifier = Modifier.size(size).drawBehind {
            val r = this.size.minDimension / 2f
            drawCircle(Color(0xFF0C4F26), radius = r)
            drawCircle(brush = Brush.verticalGradient(listOf(Color(0xFF68E994), Color(0xFF1F9F55), Color(0xFF0A5C2A))), radius = r * 0.92f)
            drawCircle(Color(0xFFD7FFE4).copy(alpha = 0.85f), radius = r * 0.4f, center = Offset(r * 0.7f, r * 0.62f))
            drawCircle(Color(0xFF083D1D), radius = r * 0.92f, style = Stroke(width = 2.dp.toPx()))
        },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = fontSize)
    }
}

@Composable
private fun Badge3DIcon(size: Dp, symbol: String, bgTop: Color, bgBottom: Color, onClick: () -> Unit = {}) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
            .border(2.dp, Color.White.copy(alpha = 0.45f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun WordChoiceInfoDialog(onDismiss: () -> Unit) {
    val bulletItems = listOf(
        "Izvor je Rečnik srpskog jezika, Matica srpska, Novi Sad, 2011.",
        "U igri su glagoli, imenice, pridevi i prilozi.",
        "Glagoli su u infinitivu, imenice u nominativu jednine, a pridevi u muškom rodu, nominativu jednine.",
        "Birali smo reči koje su jasne, prepoznatljive i pogodne za kratku Wordle partiju."
    )

    ComposeDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF27466F), Color(0xFF152844))))
                .border(2.dp, Color(0xFF8FA8C7), RoundedCornerShape(30.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFD76A), Color(0xFFE59B2D))))
                    .border(2.dp, Color(0xFFFFF0B8), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("AB", color = Color(0xFF27324A), fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "KAKO SMO BIRALI REČI?",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Slovoplet koristi pažljivo sužen spisak reči da svaka partija bude fer, razumljiva i zabavna.",
                color = Color(0xFFDCE8F6),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(18.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0x332F3338))
                    .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(22.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (item in bulletItems) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFC11521)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            item,
                            color = Color.White,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            GoldButton("RAZUMEM", onDismiss)
        }
    }
}

