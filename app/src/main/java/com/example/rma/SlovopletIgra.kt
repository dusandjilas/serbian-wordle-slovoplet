package com.example.rma

import android.app.Dialog
import android.app.AlertDialog as AndroidAlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.rma.game.GameMode
import com.example.rma.game.GuessResult
import com.example.rma.game.LetterState
import com.example.rma.game.WordRepository
import com.example.rma.viewmodel.WordleViewModel
import com.example.rma.viewmodel.WordleViewModelFactory
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.*

val fonttri = FontFamily(Font(R.font.fonttri))

// ── Palette ───────────────────────────────────────────────────────────────────
private val BG            = Color(0xFFDDE8A0)
private val CELL_EMPTY_BG = Color(0xFFEAEFC0)
private val CELL_BORDER   = Color(0xFFC8D080)
private val KEY_DEFAULT   = Color(0xFFFFFFFF)
private val KEY_CORRECT   = Color(0xFF1A3A5C)
private val KEY_PRESENT   = Color(0xFFBF1020)
private val KEY_ABSENT    = Color(0xFF8995A3)
private val HINT_ORANGE   = Color(0xFFFF9C3A)
private val SUBMIT_GRAY   = Color(0xFF9E9E9E)
private val SUBMIT_BLUE   = Color(0xFF2979FF)
private val SUBMIT_RED    = Color(0xFFD32F2F)
private val SKIP_COLOR    = Color(0xFFB5B870)

// ── SharedPrefs key for first-launch tracking ─────────────────────────────────
private const val PREFS_ONBOARDING = "onboarding_prefs"
private const val KEY_HAS_SEEN_INFO = "has_seen_info"

// ─────────────────────────────────────────────────────────────────────────────
class SlovopletIgra : AppCompatActivity() {

    private lateinit var adManager: AdManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_slovoplet_igra)

        adManager = AdManager(this)
        adManager.loadAd("ca-app-pub-3940256099942544/5224354917")

        val repository = WordRepository(this)
        val viewModel: WordleViewModel by viewModels { WordleViewModelFactory(repository) }

        val modeString   = intent.getStringExtra("game_mode") ?: "CLASSIC"
        val selectedMode = if (modeString == "DAILY") GameMode.DAILY else GameMode.CLASSIC
        val profileManager = GameProfileManager(this)

        if (selectedMode == GameMode.DAILY && profileManager.hasPlayedDailyToday()) {
            AndroidAlertDialog.Builder(this)
                .setTitle("Реч дана је већ одиграна")
                .setMessage("Данашњу Реч дана си већ завршио/ла. Врати се сутра за нову реч.")
                .setCancelable(false)
                .setPositiveButton("Назад") { _, _ ->
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                .show()
            return
        }

        viewModel.setMode(selectedMode)

        val firstTimeFlow = intent.getBooleanExtra("first_time_flow", false)

        // ── Only show the "how to play" dialog on first ever launch ───────────
        val onboardingPrefs = getSharedPreferences(PREFS_ONBOARDING, MODE_PRIVATE)
        val hasSeenInfo = onboardingPrefs.getBoolean(KEY_HAS_SEEN_INFO, false)
        if (!hasSeenInfo) {
            showInfoDialog()
            onboardingPrefs.edit().putBoolean(KEY_HAS_SEEN_INFO, true).apply()
        }

        val composeView = findViewById<ComposeView>(R.id.composeView)
        composeView.setContent {
            WordleRoot(
                viewModel      = viewModel,
                adManager      = adManager,
                profileManager = profileManager,
                // Info button in TopBar still works manually any time
                onShowInfo     = { showInfoDialog() },
                firstTimeFlow  = firstTimeFlow
            )
        }
    }

    fun showInfoDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_objasnjenje)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(true)
        dialog.findViewById<android.widget.Button>(R.id.buttonIskljuci)
            .setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun LetterState.priority(): Int = when (this) {
    LetterState.CORRECT -> 3; LetterState.PRESENT -> 2; LetterState.ABSENT -> 1
}
private fun mergeState(old: LetterState?, new: LetterState) =
    if (old == null || new.priority() > old.priority()) new else old

// ─────────────────────────────────────────────────────────────────────────────
// ROOT
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun WordleRoot(
    viewModel: WordleViewModel,
    adManager: AdManager,
    profileManager: GameProfileManager,
    onShowInfo: () -> Unit,
    firstTimeFlow: Boolean
) {
    val context   = LocalContext.current
    val coinRepo  = remember { CoinRepository(context) }
    val stateRepo = remember { GameStateRepository(context) }

    // Start with local value immediately — no flicker to 0
    var coins  by remember { mutableIntStateOf(coinRepo.getLocal()) }

    // Keyboard + hint + revealedCells hoisted so they survive GAME↔SHOP navigation
    val keyboardState = remember { mutableStateMapOf<Char, LetterState>() }
    var lastHint      by remember { mutableStateOf<Char?>(null) }
    val revealedCells = remember { mutableStateListOf<Pair<Int, Int>>() }

    // Reconcile with Firestore once — load() emits local immediately, then
    // calls onResult again only if remote is higher (won't reset coins down)
    LaunchedEffect(Unit) {
        coinRepo.load { reconciled -> coins = reconciled }
    }

    WordleGameScreen(
        viewModel        = viewModel,
        adManager        = adManager,
        coinRepo         = coinRepo,
        stateRepo        = stateRepo,
        profileManager   = profileManager,
        coins            = coins,
        onCoinsChanged   = { coins = it },
        keyboardState    = keyboardState,
        lastHint         = lastHint,
        onLastHintChange = { lastHint = it },
        revealedCells    = revealedCells,
        onOpenShop       = { context.startActivity(Intent(context, ShopActivity::class.java)) },
        onShowInfo       = onShowInfo,
        firstTimeFlow    = firstTimeFlow
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// GAME SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun WordleGameScreen(
    viewModel: WordleViewModel,
    adManager: AdManager,
    coinRepo: CoinRepository,
    stateRepo: GameStateRepository,
    profileManager: GameProfileManager,
    coins: Int,
    onCoinsChanged: (Int) -> Unit,
    keyboardState: SnapshotStateMap<Char, LetterState>,
    lastHint: Char?,
    onLastHintChange: (Char?) -> Unit,
    revealedCells: MutableList<Pair<Int, Int>>,
    onOpenShop: () -> Unit,
    onShowInfo: () -> Unit,
    firstTimeFlow: Boolean
) {
    val context   = LocalContext.current
    val hint1Cost = 150
    val hint3Cost = 125

    var trenutniPokusaj    by remember { mutableStateOf("") }
    var prikaziDijalog     by remember { mutableStateOf(false) }
    var resultHandled      by remember { mutableStateOf(false) }
    val guesses            = viewModel.guesses
    var showNeedCoinsPopup by remember { mutableStateOf(false) }
    var showDailyBonusPopup by remember { mutableStateOf(false) }
    var pendingReward      by remember { mutableIntStateOf(0) }
    var pendingSubmit      by remember { mutableStateOf(false) }
    var showFirstTimeAuthPrompt by remember { mutableStateOf(false) }

    // Animation: counter-based so LaunchedEffect fires on every new submit
    var animTrigger by remember { mutableIntStateOf(0) }
    var animRow     by remember { mutableIntStateOf(-1) }

    val cellFlip = remember {
        Array(viewModel.maxAttempts) { Array(viewModel.wordLength) { Animatable(0f) } }
    }

    // ── Restore saved state on first composition ──────────────────────────
    var stateRestored by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!stateRestored) {
            stateRestored = true
            val saved = stateRepo.load(viewModel.gameMode)
            if (saved != null) {
                viewModel.restoreState(saved.target, saved.guesses)
                trenutniPokusaj = saved.currentInput
                // Rebuild keyboard colours instantly (no animation for restored rows)
                saved.guesses.forEach { gr ->
                    gr.guess.forEachIndexed { i, c ->
                        keyboardState[c] = mergeState(keyboardState[c], gr.letterStates[i])
                    }
                }
                // Mark all saved cells as revealed (instant, no flip)
                saved.guesses.forEachIndexed { row, gr ->
                    gr.letterStates.forEachIndexed { col, _ ->
                        if (!revealedCells.contains(Pair(row, col)))
                            revealedCells.add(Pair(row, col))
                    }
                }
                if (viewModel.hasWon || viewModel.hasLost) prikaziDijalog = true
            }
        }
    }

    // ── Auto-save on lifecycle pause ──────────────────────────────────────
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                stateRepo.save(
                    mode         = viewModel.gameMode,
                    targetWord   = viewModel.targetWord,
                    guesses      = viewModel.guesses,
                    currentInput = trenutniPokusaj
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Submit validity ───────────────────────────────────────────────────
    val submitState: Boolean? = when {
        trenutniPokusaj.length < viewModel.wordLength -> null
        viewModel.checkGuess(trenutniPokusaj.uppercase()) -> true
        else -> false
    }

    // ── Hints ──────────────────────────────────────────────────────────────
    fun revealOneLetterHint() {
        val unrevealed = viewModel.targetWord.filter { keyboardState[it] == null }
        if (unrevealed.isEmpty()) {
            Toast.makeText(context, "Нема скривених слова!", Toast.LENGTH_SHORT).show(); return
        }
        val letter = unrevealed.random()
        keyboardState[letter] = LetterState.PRESENT
        onLastHintChange(letter)
    }

    fun revealThreeRandomKeysHint() {
        val allKeys    = ("ЉЊЕРТЗУИОПШ" + "АСДФГХЈКЛЧЋ" + "ЏЦВБНМЂЖ").toList()
        val unrevealed = allKeys.filter { keyboardState[it] == null }
        unrevealed.shuffled().take(3).forEach { ch ->
            keyboardState[ch] = mergeState(
                keyboardState[ch],
                if (viewModel.targetWord.contains(ch)) LetterState.PRESENT else LetterState.ABSENT
            )
        }
    }

    fun trySpendOrPopup(cost: Int, onSuccess: () -> Unit) {
        if (coinRepo.spend(cost)) {
            onCoinsChanged(coinRepo.getLocal())
            onSuccess()
        } else {
            pendingReward = cost
            showNeedCoinsPopup = true
        }
    }

    // ── Submit ──────────────────────────────────────────────────────────────
    fun submitGuessNow() {
        if (trenutniPokusaj.length < viewModel.wordLength) return
        val result = viewModel.submitGuess(trenutniPokusaj.uppercase())
        if (result != null) {
            val rowIndex    = viewModel.guesses.size - 1
            pendingSubmit   = true
            trenutniPokusaj = ""
            result.guess.forEachIndexed { i, c ->
                keyboardState[c] = mergeState(keyboardState[c], result.letterStates[i])
            }
            animRow     = rowIndex
            animTrigger++
            stateRepo.save(viewModel.gameMode, viewModel.targetWord, viewModel.guesses, "")
        } else {
            Toast.makeText(context, "Реч није важећа", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Flip animation ────────────────────────────────────────────────────
    LaunchedEffect(animTrigger) {
        if (animTrigger == 0) return@LaunchedEffect
        val row = animRow
        if (row < 0) return@LaunchedEffect

        for (col in 0 until viewModel.wordLength) {
            launch {
                delay(col * 150L)
                val anim = cellFlip[row][col]
                anim.snapTo(0f)
                anim.animateTo(90f,  animationSpec = tween(220, easing = FastOutSlowInEasing))
                anim.snapTo(-90f)
                anim.animateTo(0f,   animationSpec = tween(220, easing = FastOutSlowInEasing))
                revealedCells.add(Pair(row, col))
            }
        }

        val totalDuration = (viewModel.wordLength - 1) * 150L + 440L + 60L
        delay(totalDuration)
        pendingSubmit = false
        if (viewModel.hasWon || viewModel.hasLost) {
            if (viewModel.gameMode == GameMode.CLASSIC) stateRepo.clearClassic()
            if (firstTimeFlow) {
                AppFlowPrefs.setFirstGameFinished(context, true)
            }
            prikaziDijalog = true
        }
    }

    // ── Stats tracking ────────────────────────────────────────────────────
    LaunchedEffect(viewModel.gameMode) { resultHandled = false }
    LaunchedEffect(viewModel.hasWon, viewModel.hasLost) {
        if (!resultHandled && (viewModel.hasWon || viewModel.hasLost)) {
            resultHandled = true
            when (viewModel.gameMode) {
                GameMode.CLASSIC -> {
                    if (viewModel.hasWon) profileManager.recordClassicWin(guesses.size)
                    else profileManager.recordClassicLoss()
                }
                GameMode.DAILY -> {
                    profileManager.markDailyPlayedToday(viewModel.hasWon)
                    // Pass guessCount so XP scales with performance
                    profileManager.recordDailyResult(
                        won        = viewModel.hasWon,
                        guessCount = if (viewModel.hasWon) guesses.size else 0
                    )
                    // Daily win awards 100 bonus coins
                    if (viewModel.hasWon) {
                        val newTotal = coinRepo.add(100)
                        onCoinsChanged(newTotal)
                        showDailyBonusPopup = true
                    }
                }
            }
        }
    }

    // ── Sizing ──────────────────────────────────────────────────────────────
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val gridGap       = 8.dp * (viewModel.wordLength - 1)
    val cellSize: Dp  = ((screenWidthDp - 24.dp - gridGap) / viewModel.wordLength).coerceAtMost(62.dp)

    // ── Layout ─────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {

            TopBar(score = guesses.size, coins = coins, onInfo = onShowInfo, onPlusCoins = onOpenShop)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                GuessGrid(
                    viewModel       = viewModel,
                    guesses         = guesses,
                    trenutniPokusaj = trenutniPokusaj,
                    cellSize        = cellSize,
                    cellFlip        = cellFlip,
                    revealedCells   = revealedCells
                )
            }

            Spacer(Modifier.height(4.dp))

            VirtualKeyboard(keyboardState = keyboardState, lastHint = lastHint) { key ->
                if (viewModel.hasWon || viewModel.hasLost || pendingSubmit) return@VirtualKeyboard
                when (key) {
                    "УНЕСИ" -> submitGuessNow()
                    "БРИШИ"   -> if (trenutniPokusaj.isNotEmpty()) trenutniPokusaj = trenutniPokusaj.dropLast(1)
                    else    -> if (trenutniPokusaj.length < viewModel.wordLength) trenutniPokusaj += key
                }
            }

            Spacer(Modifier.height(6.dp))

            BottomActionRow(
                hint1Cost   = hint1Cost,
                hint3Cost   = hint3Cost,
                submitState = submitState,
                onHint1     = { trySpendOrPopup(hint1Cost) { revealOneLetterHint() } },
                onHint3     = { trySpendOrPopup(hint3Cost) { revealThreeRandomKeysHint() } },
                onSubmit    = { submitGuessNow() },
                onComplete  = {
                    if (!viewModel.hasWon && !viewModel.hasLost && !pendingSubmit) {
                        trenutniPokusaj = viewModel.targetWord
                        submitGuessNow()
                    }
                }
            )

            // ── Ad banner — lifted slightly, real AdMob view ──────────────
            Spacer(Modifier.height(10.dp))
            AdBanner(adUnitId = "ca-app-pub-3940256099942544/6300978111") // test banner ID
        }

        // ── Overlays ──────────────────────────────────────────────────────
        if (showNeedCoinsPopup) {
            NeedCoinsDialog(
                reward    = pendingReward,
                adReady   = adManager.isAdReady(),
                onClaimAd = {
                    if (adManager.isAdReady()) {
                        adManager.showAd {
                            val newTotal = coinRepo.add(pendingReward)
                            onCoinsChanged(newTotal)
                            showNeedCoinsPopup = false
                        }
                    } else {
                        Toast.makeText(context, "Реклама није спремна, покушај касније", Toast.LENGTH_SHORT).show()
                    }
                },
                onNoThanks = { showNeedCoinsPopup = false }
            )
        }

        if (showDailyBonusPopup) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDailyBonusPopup = false },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showDailyBonusPopup = false }) {
                        Text("Супер")
                    }
                },
                title = { Text("Дневни бонус") },
                text = { Text("Освојио/ла си +100 🪙 за победу у Речи дана!") }
            )
        }

        if (prikaziDijalog) {
            EndGameDialog(
                hasWon      = viewModel.hasWon,
                targetWord  = viewModel.targetWord,
                score       = guesses.size,
                gameMode    = viewModel.gameMode,
                onPlayAgain = {
                    viewModel.reset()
                    trenutniPokusaj = ""
                    keyboardState.clear()
                    onLastHintChange(null)
                    prikaziDijalog   = false
                    resultHandled    = false
                    animRow          = -1
                    animTrigger      = 0
                    pendingSubmit    = false
                    revealedCells.clear()
                    for (r in 0 until viewModel.maxAttempts)
                        for (c in 0 until viewModel.wordLength)
                            cellFlip[r][c] = Animatable(0f)
                    stateRepo.clearClassic()
                },
                onBackToMain = {
                    if (firstTimeFlow) {
                        showFirstTimeAuthPrompt = true
                        prikaziDijalog = false
                    } else {
                        (context as? AppCompatActivity)?.finish()
                    }
                },
                onDismiss    = { prikaziDijalog = false }
            )
        }


        if (showFirstTimeAuthPrompt) {
            FirstGameAuthPromptDialog(
                onSignIn = {
                    showFirstTimeAuthPrompt = false
                    context.startActivity(Intent(context, SignInActivity::class.java))
                    (context as? AppCompatActivity)?.finish()
                },
                onContinueAsGuest = {
                    showFirstTimeAuthPrompt = false
                    context.startActivity(Intent(context, MainActivity::class.java))
                    (context as? AppCompatActivity)?.finish()
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AD BANNER  — real AdMob AdView wrapped in AndroidView
// Replace adUnitId with your production unit ID before release.
// Test ID used here: ca-app-pub-3940256099942544/6300978111
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AdBanner(adUnitId: String) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),   // small lift from the very bottom edge
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        },
        update = { adView ->
            // Re-load if the unit ID ever changes (shouldn't in practice)
            if (adView.adUnitId != adUnitId) {
                adView.adUnitId = adUnitId
                adView.loadAd(AdRequest.Builder().build())
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP BAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(score: Int, coins: Int, onInfo: () -> Unit, onPlusCoins: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFE24A3B)).clickable { onPlusCoins() },
            contentAlignment = Alignment.Center
        ) { Text("AD", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp) }

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SCORE", fontSize = 13.sp, color = Color(0xFF555544), fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(score.toString(), fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color(0xFF222211))
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFF4AABFF)).clickable { onInfo() },
                contentAlignment = Alignment.Center
            ) { Text("і", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp) }
        }

        Spacer(Modifier.weight(1f))
        CoinPill(coins = coins, onPlus = onPlusCoins)
    }
}

@Composable
private fun CoinPill(coins: Int, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.height(32.dp).widthIn(min = 80.dp)
                .clip(RoundedCornerShape(16.dp)).background(Color(0xFF8A8A6A))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFCCCCCC)))
                Spacer(Modifier.width(6.dp))
                Text(coins.toString(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFE8A010)).clickable { onPlus() },
            contentAlignment = Alignment.Center
        ) { Text("Д", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp) }
        Spacer(Modifier.width(2.dp))
        Box(
            modifier = Modifier.size(20.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF44BB55)),
            contentAlignment = Alignment.Center
        ) { Text("+", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GRID
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GuessGrid(
    viewModel: WordleViewModel,
    guesses: List<GuessResult>,
    trenutniPokusaj: String,
    cellSize: Dp,
    cellFlip: Array<Array<Animatable<Float, AnimationVector1D>>>,
    revealedCells: List<Pair<Int, Int>>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until viewModel.maxAttempts) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val guessResult = guesses.getOrNull(i)
                for (j in 0 until viewModel.wordLength) {
                    val char: Char = when {
                        guessResult != null && j < guessResult.guess.length -> guessResult.guess[j]
                        i == guesses.size && j < trenutniPokusaj.length     -> trenutniPokusaj[j]
                        else -> ' '
                    }
                    val state      = guessResult?.letterStates?.getOrNull(j)
                    val isRevealed = revealedCells.contains(Pair(i, j))
                    val isFlipping = cellFlip[i][j].value != 0f

                    val displayFill = if (isRevealed) when (state) {
                        LetterState.CORRECT -> KEY_CORRECT
                        LetterState.PRESENT -> KEY_PRESENT
                        LetterState.ABSENT  -> KEY_ABSENT
                        else                -> CELL_EMPTY_BG
                    } else CELL_EMPTY_BG

                    val displayChar = when {
                        isRevealed                  -> char
                        state != null && isFlipping -> ' '
                        else                        -> char
                    }

                    GridCell(char = displayChar, size = cellSize, fill = displayFill, flipAngle = cellFlip[i][j].value)
                }
            }
        }
    }
}

@Composable
private fun GridCell(char: Char, size: Dp, fill: Color, flipAngle: Float = 0f) {
    val isEmpty = fill == CELL_EMPTY_BG
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .graphicsLayer { rotationX = flipAngle; cameraDistance = 12f * density }
            .shadow(if (isEmpty) 2.dp else 0.dp, RoundedCornerShape(10.dp), ambientColor = Color(0x22000000))
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .then(if (isEmpty && flipAngle == 0f) Modifier.border(1.5.dp, CELL_BORDER, RoundedCornerShape(10.dp)) else Modifier)
    ) {
        if (char != ' ') {
            Text(
                text       = char.toString(),
                color      = if (isEmpty) Color(0xFF333322) else Color.White,
                fontWeight = FontWeight.Black,
                fontSize   = (size.value * 0.44f).sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// KEYBOARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun VirtualKeyboard(
    keyboardState: SnapshotStateMap<Char, LetterState>,
    lastHint: Char?,
    onKeyClick: (String) -> Unit
) {
    val rows = listOf("ЉЊЕРТЗУИОПШ", "АСДФГХЈКЛЧЋ", "ЏЦВБНМЂЖ")
    val configuration = LocalConfiguration.current
    val screenW = configuration.screenWidthDp.dp
    val keyW: Dp = ((screenW - 24.dp - 4.dp * 10) / 11).coerceAtMost(40.dp)
    val keyH: Dp = keyW * 1.45f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEachIndexed { rowIdx, row ->
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.fillMaxWidth()
            ) {
                row.forEach { ch ->
                    LetterKey(
                        key = ch.toString(), keyW = keyW, keyH = keyH,
                        keyboardState = keyboardState, lastHint = lastHint, onKeyClick = onKeyClick
                    )
                    Spacer(Modifier.width(4.dp))
                }
                if (rowIdx == 2) BackspaceKey(keyW = keyW, keyH = keyH, onKeyClick = onKeyClick)
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun LetterKey(
    key: String, keyW: Dp, keyH: Dp,
    keyboardState: SnapshotStateMap<Char, LetterState>,
    lastHint: Char?,
    onKeyClick: (String) -> Unit
) {
    val first = key.firstOrNull()
    val bg = when (keyboardState[first]) {
        LetterState.CORRECT -> KEY_CORRECT
        LetterState.PRESENT -> KEY_PRESENT
        LetterState.ABSENT  -> KEY_ABSENT
        else                -> KEY_DEFAULT
    }
    val textColor = if (keyboardState[first] == null) Color(0xFF222222) else Color.White
    val isHinted  = first != null && first == lastHint
    val scale     = remember { Animatable(1f) }
    LaunchedEffect(lastHint) {
        if (isHinted) { scale.animateTo(1.25f, tween(180)); scale.animateTo(1f, tween(180)) }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(keyW).height(keyH).scale(scale.value)
            .shadow(3.dp, RoundedCornerShape(8.dp), ambientColor = Color(0x44000000))
            .clip(RoundedCornerShape(8.dp)).background(bg).clickable { onKeyClick(key) }
    ) {
        Text(key, color = textColor, fontWeight = FontWeight.Bold,
            fontSize = (keyW.value * 0.46f).sp, maxLines = 1)
    }
}

@Composable
private fun BackspaceKey(keyW: Dp, keyH: Dp, onKeyClick: (String) -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width((keyW.value * 1.6f).dp).height(keyH)
            .shadow(3.dp, RoundedCornerShape(8.dp), ambientColor = Color(0x55000000))
            .clip(RoundedCornerShape(8.dp)).background(Color(0xFF555555))
            .clickable { onKeyClick("БРИШИ") }
    ) {
        Text("⌫", color = Color.White, fontSize = (keyW.value * 0.50f).sp, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BOTTOM ACTION ROW
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BottomActionRow(
    hint1Cost: Int, hint3Cost: Int, submitState: Boolean?,
    onHint1: () -> Unit, onHint3: () -> Unit, onSubmit: () -> Unit, onComplete: () -> Unit
) {
    val submitBg       = when (submitState) { true -> SUBMIT_BLUE; false -> SUBMIT_RED; null -> SUBMIT_GRAY }
    val submitLabel    = if (submitState == false) "НИЈЕ ВАЖЕЋЕ" else "ПОТВРДИ"
    val submitFontSize = if (submitState == false) 11.sp else 16.sp

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {

        HintButton(emoji = "🔍", cost = hint1Cost, onClick = onHint1)
        HintButton(emoji = "🪄", cost = hint3Cost, onClick = onHint3)

        Box(
            modifier = Modifier.height(56.dp).weight(1f).padding(horizontal = 8.dp)
                .shadow(4.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))
                .background(submitBg).clickable(enabled = submitState == true) { onSubmit() },
            contentAlignment = Alignment.Center
        ) {
            Text(submitLabel, color = Color.White, fontSize = submitFontSize,
                fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }

        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier.height(56.dp).width(72.dp)
                    .shadow(4.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))
                    .background(SKIP_COLOR).clickable { onComplete() },
                contentAlignment = Alignment.Center
            ) { Text("⏭", fontSize = 22.sp) }
            Box(
                modifier = Modifier.padding(bottom = 4.dp, end = 4.dp).size(18.dp)
                    .clip(RoundedCornerShape(9.dp)).background(Color(0xFF888870)),
                contentAlignment = Alignment.Center
            ) { Text("2", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun HintButton(emoji: String, cost: Int, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
        Box(
            modifier = Modifier.size(50.dp).shadow(4.dp, RoundedCornerShape(25.dp))
                .clip(RoundedCornerShape(25.dp)).background(HINT_ORANGE).clickable { onClick() },
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 22.sp) }
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🪙", fontSize = 11.sp); Spacer(Modifier.width(2.dp))
            Text(cost.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF444433))
        }
    }
}


@Composable
private fun FirstGameAuthPromptDialog(
    onSignIn: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    Dialog(onDismissRequest = onContinueAsGuest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1A3A5C))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Сачувај напредак", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text(
                "Пријави се да сачуваш XP и статистику, или настави као гост.",
                color = Color(0xFFD8E9FF),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(50.dp)
                    .clip(RoundedCornerShape(22.dp)).background(Color(0xFF44BB55))
                    .clickable { onSignIn() },
                contentAlignment = Alignment.Center
            ) { Text("ПРИЈАВИ СЕ", color = Color.White, fontWeight = FontWeight.Black) }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(50.dp)
                    .clip(RoundedCornerShape(22.dp)).background(Color(0x44FFFFFF))
                    .clickable { onContinueAsGuest() },
                contentAlignment = Alignment.Center
            ) { Text("НАСТАВИ КАО ГОСТ", color = Color.White, fontWeight = FontWeight.Black) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// END GAME DIALOG
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun EndGameDialog(
    hasWon: Boolean, targetWord: String, score: Int, gameMode: GameMode,
    onPlayAgain: () -> Unit, onBackToMain: () -> Unit, onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val dialogBg = if (hasWon) Color(0xFF1A3A5C) else Color(0xFF7A1515)
        Box(
            modifier = Modifier.fillMaxWidth(0.92f).clip(RoundedCornerShape(28.dp))
                .background(dialogBg).padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (hasWon) "🎉" else "😞", fontSize = 60.sp)
                Spacer(Modifier.height(10.dp))
                Text(if (hasWon) "ЧЕСТИТАМО!" else "НИСТЕ ПОГОДИЛИ",
                    fontSize = 24.sp, fontWeight = FontWeight.Black,
                    color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Text("Реч је била:", fontSize = 13.sp, color = Color(0xAAFFFFFF))
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    targetWord.forEach { ch ->
                        Box(
                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (hasWon) Color(0xFF2979FF) else Color(0xFFBF1020)),
                            contentAlignment = Alignment.Center
                        ) { Text(ch.toString(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .background(Color(0x33FFFFFF)).padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ПОКУШАЈА:", fontSize = 13.sp, color = Color(0xAAFFFFFF))
                        Spacer(Modifier.width(8.dp))
                        Text(score.toString(), fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
                Spacer(Modifier.height(24.dp))
                if (gameMode == GameMode.CLASSIC) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                            .clip(RoundedCornerShape(26.dp)).background(Color(0xFF44BB55))
                            .clickable { onPlayAgain() },
                        contentAlignment = Alignment.Center
                    ) { Text("НОВА РЕЧ", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black) }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                            .clip(RoundedCornerShape(26.dp)).background(Color(0x44FFFFFF))
                            .clickable { onBackToMain() },
                        contentAlignment = Alignment.Center
                    ) { Text("НАЗАД", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black) }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                            .clip(RoundedCornerShape(26.dp)).background(Color(0x44FFFFFF))
                            .clickable { onBackToMain() },
                        contentAlignment = Alignment.Center
                    ) { Text("НАЗАД НА ГЛАВНИ ЕКРАН", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NEED COINS POPUP
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun NeedCoinsDialog(reward: Int, adReady: Boolean, onClaimAd: () -> Unit, onNoThanks: () -> Unit) {
    androidx.compose.material.AlertDialog(
        onDismissRequest = onNoThanks,
        backgroundColor  = Color(0xFFB04A86),
        shape            = RoundedCornerShape(26.dp),
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("УЗМИ БЕСПЛАТНЕ НОВЧИЋЕ", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                        .clip(RoundedCornerShape(18.dp)).background(Color(0xFFE9E9F2)),
                    contentAlignment = Alignment.Center
                ) { Text("🪙 +$reward", fontSize = 34.sp, fontWeight = FontWeight.Black) }
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (!adReady) Color(0xFF8B4B6C) else Color(0xFFD24A8F))
                        .clickable(enabled = adReady) { onClaimAd() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (!adReady) "РЕКЛАМА СЕ УЧИТАВА..." else "ПРЕУЗМИ",
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(10.dp))
                Text("НЕ, ХВАЛА", color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNoThanks() })
            }
        },
        buttons = {}
    )
}
