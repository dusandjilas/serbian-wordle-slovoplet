package com.example.rma

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.rma.game.GameMode
import com.example.rma.game.GuessResult
import com.example.rma.game.LetterState
import com.example.rma.game.WordRepository
import com.example.rma.viewmodel.WordleViewModel
import com.example.rma.viewmodel.WordleViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val fonttri = FontFamily(Font(R.font.fonttri))
private lateinit var coinManager: CoinManager

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

// ─────────────────────────────────────────────────────────────────────────────
class SlovopletIgra : AppCompatActivity() {

    private lateinit var adManager: AdManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_slovoplet_igra)

        adManager = AdManager(this)
        adManager.loadAd("ca-app-pub-3940256099942544/5224354917")

        coinManager = CoinManager(this)

        val repository = WordRepository(this)
        val viewModel: WordleViewModel by viewModels { WordleViewModelFactory(repository) }

        val modeString   = intent.getStringExtra("game_mode") ?: "CLASSIC"
        val selectedMode = if (modeString == "DAILY") GameMode.DAILY else GameMode.CLASSIC
        viewModel.setMode(selectedMode)

        val profileManager = GameProfileManager(this)

        val composeView = findViewById<ComposeView>(R.id.composeView)
        composeView.setContent {
            WordleRoot(
                viewModel      = viewModel,
                adManager      = adManager,
                coinManager    = coinManager,
                profileManager = profileManager,
                onShowInfo     = { showInfoDialog() }
            )
        }

        showInfoDialog()
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

// ── Priority merge ─────────────────────────────────────────────────────────────
private fun LetterState.priority(): Int = when (this) {
    LetterState.CORRECT -> 3
    LetterState.PRESENT -> 2
    LetterState.ABSENT  -> 1
}

private fun mergeState(old: LetterState?, new: LetterState): LetterState {
    if (old == null) return new
    return if (new.priority() > old.priority()) new else old
}

private enum class Screen { GAME, SHOP }

// ─────────────────────────────────────────────────────────────────────────────
// ROOT
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun WordleRoot(
    viewModel: WordleViewModel,
    adManager: AdManager,
    coinManager: CoinManager,
    profileManager: GameProfileManager,
    onShowInfo: () -> Unit
) {
    var screen    by remember { mutableStateOf(Screen.GAME) }
    var coins     by remember { mutableIntStateOf(0) }
    val statsRepo = remember { FirebaseStatsRepository() }
    val context   = LocalContext.current

    // Load coins from Firebase once on start
    LaunchedEffect(Unit) {
        statsRepo.loadStats(
            onSuccess = { data ->
                val remote = (data["storedCoins"] as? Long)?.toInt() ?: coinManager.getCoins()
                coins = remote
                coinManager.setCoins(remote)
            },
            onNoData  = { coins = coinManager.getCoins() },
            onFailure = { coins = coinManager.getCoins() }
        )
    }

    // Unified coin setter: updates local + remote atomically
    fun persistCoins(newVal: Int) {
        coins = newVal
        coinManager.setCoins(newVal)
        statsRepo.syncStats(profileManager)
    }

    when (screen) {
        Screen.GAME -> WordleGameScreen(
            viewModel      = viewModel,
            adManager      = adManager,
            coinManager    = coinManager,
            profileManager = profileManager,
            statsRepo      = statsRepo,
            coins          = coins,
            setCoins       = { persistCoins(it) },
            onOpenShop     = { screen = Screen.SHOP },
            onShowInfo     = onShowInfo
        )

        Screen.SHOP -> ShopScreen(
            coins      = coins,
            onBack     = { screen = Screen.GAME },
            onFreeAd25 = {
                if (adManager.isAdReady()) {
                    adManager.showAd {
                        persistCoins(coinManager.getCoins() + 25)
                    }
                } else {
                    Toast.makeText(context, "Реклама није спремна, покушај касније", Toast.LENGTH_SHORT).show()
                }
            },
            onBuyCoins = { amount -> persistCoins(coinManager.getCoins() + amount) },
            onBuyNoAds = { /* TODO: real purchase */ }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GAME SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun WordleGameScreen(
    viewModel: WordleViewModel,
    adManager: AdManager,
    coinManager: CoinManager,
    profileManager: GameProfileManager,
    statsRepo: FirebaseStatsRepository,
    coins: Int,
    setCoins: (Int) -> Unit,
    onOpenShop: () -> Unit,
    onShowInfo: () -> Unit
) {
    val context = LocalContext.current

    val hint1Cost = 150
    val hint3Cost = 125

    var trenutniPokusaj    by remember { mutableStateOf("") }
    var prikaziDijalog     by remember { mutableStateOf(false) }
    var resultHandled      by remember { mutableStateOf(false) }
    val guesses            = viewModel.guesses
    var lastHint           by remember { mutableStateOf<Char?>(null) }
    val keyboardState      = remember { mutableStateMapOf<Char, LetterState>() }
    var showNeedCoinsPopup by remember { mutableStateOf(false) }
    var pendingReward      by remember { mutableIntStateOf(0) }
    var pendingSubmit      by remember { mutableStateOf(false) }
    var lastSubmittedRow   by remember { mutableIntStateOf(-1) }

    // Per-cell flip animatables [row][col] — recreated on reset
    val cellFlip = remember {
        Array(viewModel.maxAttempts) { Array(viewModel.wordLength) { Animatable(0f) } }
    }

    // ── Submit-button validity state ──────────────────────────────────────
    // null = word too short, true = valid, false = typed but invalid
    val submitState: Boolean? = when {
        trenutniPokusaj.length < viewModel.wordLength -> null
        viewModel.checkGuess(trenutniPokusaj.uppercase()) -> true
        else -> false
    }

    // ── Hints ──────────────────────────────────────────────────────────────
    fun revealOneLetterHint() {
        val unrevealed = viewModel.targetWord.filter { keyboardState[it] == null }
        if (unrevealed.isEmpty()) {
            Toast.makeText(context, "Нема скривених слова!", Toast.LENGTH_SHORT).show()
            return
        }
        val letter = unrevealed.random()
        keyboardState[letter] = LetterState.PRESENT
        lastHint = letter
    }

    fun revealThreeRandomKeysHint() {
        val allKeys  = ("ЉЊЕРТЗУИОПШ" + "АСДФГХЈКЛЧЋ" + "ЏЦВБНМЂЖ").toList()
        val unrevealed = allKeys.filter { keyboardState[it] == null }   // skip already-known
        val pick     = unrevealed.shuffled().take(3)
        pick.forEach { ch ->
            val state = if (viewModel.targetWord.contains(ch)) LetterState.PRESENT else LetterState.ABSENT
            keyboardState[ch] = mergeState(keyboardState[ch], state)
        }
    }

    fun trySpendOrPopup(cost: Int, onSuccess: () -> Unit) {
        if (coinManager.spendCoins(cost)) {
            setCoins(coinManager.getCoins())
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
            val rowIndex     = guesses.size - 1
            lastSubmittedRow = rowIndex
            pendingSubmit    = true
            trenutniPokusaj  = ""
            result.guess.forEachIndexed { i, c ->
                keyboardState[c] = mergeState(keyboardState[c], result.letterStates[i])
            }
        } else {
            Toast.makeText(context, "Реч није важећа", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Flip animation triggered after each valid submit ──────────────────
    LaunchedEffect(lastSubmittedRow) {
        val row = lastSubmittedRow
        if (row < 0) return@LaunchedEffect

        for (col in 0 until viewModel.wordLength) {
            launch {
                delay(col * 130L)
                val anim = cellFlip[row][col]
                anim.snapTo(0f)
                // Rotate to 90° → hide old face
                anim.animateTo(90f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                // Jump to -90° → start revealing colored face
                anim.snapTo(-90f)
                // Rotate to 0° → fully revealed
                anim.animateTo(0f, animationSpec = tween(200, easing = FastOutSlowInEasing))
            }
        }

        // Wait for all cells to finish then open dialog if game over
        delay(viewModel.wordLength * 130L + 450L)
        pendingSubmit = false
        if (viewModel.hasWon || viewModel.hasLost) prikaziDijalog = true
    }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val gridGap       = 8.dp * (viewModel.wordLength - 1)
    val cellSize: Dp  = ((screenWidthDp - 24.dp - gridGap) / viewModel.wordLength).coerceAtMost(62.dp)

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
                    profileManager.recordDailyResult(viewModel.hasWon)
                }
            }
            statsRepo.syncStats(profileManager)
        }
    }

    // ── Layout ─────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopBar(score = guesses.size, coins = coins, onInfo = onShowInfo, onPlusCoins = onOpenShop)

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                GuessGrid(
                    viewModel       = viewModel,
                    guesses         = guesses,
                    trenutniPokusaj = trenutniPokusaj,
                    cellSize        = cellSize,
                    cellFlip        = cellFlip,
                    lastSubmittedRow = lastSubmittedRow
                )
            }

            Spacer(Modifier.height(6.dp))

            VirtualKeyboard(keyboardState = keyboardState, lastHint = lastHint) { key ->
                if (viewModel.hasWon || viewModel.hasLost || pendingSubmit) return@VirtualKeyboard
                when (key) {
                    "ENTER" -> submitGuessNow()
                    "DEL"   -> if (trenutniPokusaj.isNotEmpty()) trenutniPokusaj = trenutniPokusaj.dropLast(1)
                    else    -> if (trenutniPokusaj.length < viewModel.wordLength) trenutniPokusaj += key
                }
            }

            Spacer(Modifier.height(8.dp))

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

            Spacer(Modifier.height(8.dp))

            // ── Ad banner ─────────────────────────────────────────────────
            AdBannerPlaceholder()
        }

        // ── Overlays ──────────────────────────────────────────────────────
        if (showNeedCoinsPopup) {
            NeedCoinsDialog(
                reward    = pendingReward,
                adReady   = adManager.isAdReady(),
                onClaimAd = {
                    if (adManager.isAdReady()) {
                        adManager.showAd {
                            val newCoins = coinManager.getCoins() + pendingReward
                            coinManager.setCoins(newCoins)
                            setCoins(newCoins)
                            showNeedCoinsPopup = false
                        }
                    } else {
                        Toast.makeText(context, "Реклама није спремна, покушај касније", Toast.LENGTH_SHORT).show()
                    }
                },
                onNoThanks = { showNeedCoinsPopup = false }
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
                    trenutniPokusaj  = ""
                    keyboardState.clear()
                    lastHint         = null
                    prikaziDijalog   = false
                    resultHandled    = false
                    lastSubmittedRow = -1
                    pendingSubmit    = false
                    // Reset all flip animations
                    for (r in 0 until viewModel.maxAttempts)
                        for (c in 0 until viewModel.wordLength)
                            cellFlip[r][c] = Animatable(0f)
                },
                onBackToMain = { (context as? AppCompatActivity)?.finish() },
                onDismiss    = { prikaziDijalog = false }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AD BANNER PLACEHOLDER
// Replace the inner content with your real AdMob BannerAd AndroidView
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AdBannerPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x22000000))
            .border(1.dp, Color(0x33000000), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = "ADVERTISEMENT",
            color      = Color(0x77000000),
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP BAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(score: Int, coins: Int, onInfo: () -> Unit, onPlusCoins: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFE24A3B))
                .clickable { /* remove ads */ },
            contentAlignment = Alignment.Center
        ) {
            Text("AD", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
        }

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SCORE", fontSize = 13.sp, color = Color(0xFF555544), fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(score.toString(), fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color(0xFF222211))
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFF4AABFF))
                    .clickable { onInfo() },
                contentAlignment = Alignment.Center
            ) {
                Text("i", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.weight(1f))

        CoinPill(coins = coins, onPlus = onPlusCoins)
    }
}

@Composable
private fun CoinPill(coins: Int, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .widthIn(min = 80.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF8A8A6A))
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
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFE8A010))
                .clickable { onPlus() },
            contentAlignment = Alignment.Center
        ) {
            Text("W", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
        Spacer(Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF44BB55)),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
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
    lastSubmittedRow: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until viewModel.maxAttempts) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val guessResult = guesses.getOrNull(i)
                for (j in 0 until viewModel.wordLength) {
                    val char: Char = when {
                        guessResult != null && j < guessResult.guess.length -> guessResult.guess[j]
                        i == guesses.size && j < trenutniPokusaj.length     -> trenutniPokusaj[j]
                        else                                                  -> ' '
                    }
                    val state = guessResult?.letterStates?.getOrNull(j)
                    val coloredFill = when (state) {
                        LetterState.CORRECT -> KEY_CORRECT
                        LetterState.PRESENT -> KEY_PRESENT
                        LetterState.ABSENT  -> KEY_ABSENT
                        else                -> CELL_EMPTY_BG
                    }

                    val flipAngle = cellFlip[i][j].value

                    // During first half (angle > 0) show the "before" face (empty/typed)
                    // During second half (angle ≤ 0) show the "after" face (colored)
                    val isFlipping = flipAngle != 0f
                    val isRevealed = state != null && !isFlipping
                    val displayFill = if (isRevealed) coloredFill else CELL_EMPTY_BG
                    val displayChar = if (isRevealed) char else if (state != null && isFlipping) ' ' else char

                    GridCell(char = displayChar, size = cellSize, fill = displayFill, flipAngle = flipAngle)
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
            .graphicsLayer {
                rotationX      = flipAngle
                cameraDistance = 12f * density
            }
            .shadow(if (isEmpty) 2.dp else 0.dp, RoundedCornerShape(10.dp), ambientColor = Color(0x22000000))
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .then(
                if (isEmpty && flipAngle == 0f)
                    Modifier.border(1.5.dp, CELL_BORDER, RoundedCornerShape(10.dp))
                else Modifier
            )
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
    val screenW       = configuration.screenWidthDp.dp

    val keyW: Dp = ((screenW - 24.dp - 4.dp * 10) / 11).coerceAtMost(38.dp)
    val keyH: Dp = keyW * 1.38f

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
                        key           = ch.toString(),
                        keyW          = keyW,
                        keyH          = keyH,
                        keyboardState = keyboardState,
                        lastHint      = lastHint,
                        onKeyClick    = onKeyClick
                    )
                    Spacer(Modifier.width(4.dp))
                }
                if (rowIdx == 2) {
                    // Backspace — wider, dark, distinct from letter keys
                    BackspaceKey(keyW = keyW, keyH = keyH, onKeyClick = onKeyClick)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun LetterKey(
    key: String,
    keyW: Dp,
    keyH: Dp,
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

    val isHinted = first != null && first == lastHint
    val scale    = remember { Animatable(1f) }
    LaunchedEffect(lastHint) {
        if (isHinted) {
            scale.animateTo(1.25f, tween(180))
            scale.animateTo(1f,    tween(180))
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(keyW)
            .height(keyH)
            .scale(scale.value)
            .shadow(3.dp, RoundedCornerShape(8.dp), ambientColor = Color(0x44000000))
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onKeyClick(key) }
    ) {
        Text(key, color = textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun BackspaceKey(keyW: Dp, keyH: Dp, onKeyClick: (String) -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width((keyW.value * 1.6f).dp)  // wider than a normal letter key
            .height(keyH)
            .shadow(3.dp, RoundedCornerShape(8.dp), ambientColor = Color(0x55000000))
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF555555))
            .clickable { onKeyClick("DEL") }
    ) {
        Text("⌫", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BOTTOM ACTION ROW
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BottomActionRow(
    hint1Cost: Int,
    hint3Cost: Int,
    submitState: Boolean?,   // null = incomplete, true = valid, false = invalid
    onHint1: () -> Unit,
    onHint3: () -> Unit,
    onSubmit: () -> Unit,
    onComplete: () -> Unit
) {
    val submitBg    = when (submitState) { true -> SUBMIT_BLUE; false -> SUBMIT_RED; null -> SUBMIT_GRAY }
    val submitLabel = if (submitState == false) "NOT VALID" else "SUBMIT"
    val submitFontSize = if (submitState == false) 13.sp else 18.sp

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HintButton(emoji = "🔍", cost = hint1Cost, onClick = onHint1)
        HintButton(emoji = "🪄", cost = hint3Cost, onClick = onHint3)

        // SUBMIT pill – fills remaining space
        Box(
            modifier = Modifier
                .height(56.dp)
                .weight(1f)
                .padding(horizontal = 8.dp)
                .shadow(4.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(submitBg)
                .clickable(enabled = submitState == true) { onSubmit() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = submitLabel,
                color      = Color.White,
                fontSize   = submitFontSize,
                fontWeight = FontWeight.Black,
                textAlign  = TextAlign.Center
            )
        }

        // Skip pill with "2" badge
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .height(56.dp)
                    .width(72.dp)
                    .shadow(4.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(SKIP_COLOR)
                    .clickable { onComplete() },
                contentAlignment = Alignment.Center
            ) {
                Text("⏭", fontSize = 22.sp)
            }
            Box(
                modifier = Modifier
                    .padding(bottom = 4.dp, end = 4.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF888870)),
                contentAlignment = Alignment.Center
            ) {
                Text("2", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun HintButton(emoji: String, cost: Int, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .shadow(4.dp, RoundedCornerShape(25.dp))
                .clip(RoundedCornerShape(25.dp))
                .background(HINT_ORANGE)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🪙", fontSize = 11.sp)
            Spacer(Modifier.width(2.dp))
            Text(cost.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF444433))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// END GAME DIALOG  — redesigned, mode-aware
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun EndGameDialog(
    hasWon: Boolean,
    targetWord: String,
    score: Int,
    gameMode: GameMode,
    onPlayAgain: () -> Unit,
    onBackToMain: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogBg = if (hasWon) Color(0xFF1A3A5C) else Color(0xFF7A1515)

        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .background(dialogBg)
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                // Big emoji reaction
                Text(text = if (hasWon) "🎉" else "😞", fontSize = 60.sp)

                Spacer(Modifier.height(10.dp))

                // Title
                Text(
                    text       = if (hasWon) "ЧЕСТИТАМО!" else "НИСТЕ ПОГОДИЛИ",
                    fontSize   = 24.sp,
                    fontWeight = FontWeight.Black,
                    color      = Color.White,
                    textAlign  = TextAlign.Center
                )

                Spacer(Modifier.height(14.dp))

                // Target word display
                Text(text = "Реч је била:", fontSize = 13.sp, color = Color(0xAAFFFFFF))
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    targetWord.forEach { ch ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (hasWon) Color(0xFF2979FF) else Color(0xFFBF1020)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text       = ch.toString(),
                                color      = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize   = 16.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Score chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x33FFFFFF))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ПОКУШАЈА:", fontSize = 13.sp, color = Color(0xAAFFFFFF))
                        Spacer(Modifier.width(8.dp))
                        Text(score.toString(), fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }

                Spacer(Modifier.height(24.dp))

                if (gameMode == GameMode.CLASSIC) {
                    // Classic: New word button + Back button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth().height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFF44BB55))
                            .clickable { onPlayAgain() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("НОВА РЕЧ", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth().height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0x44FFFFFF))
                            .clickable { onBackToMain() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("НАЗАД", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    }
                } else {
                    // Daily: only return to main screen
                    Box(
                        modifier = Modifier
                            .fillMaxWidth().height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0x44FFFFFF))
                            .clickable { onBackToMain() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = "НАЗАД НА ГЛАВНИ ЕКРАН",
                            color      = Color.White,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NEED COINS POPUP
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun NeedCoinsDialog(
    reward: Int,
    adReady: Boolean,
    onClaimAd: () -> Unit,
    onNoThanks: () -> Unit
) {
    androidx.compose.material.AlertDialog(
        onDismissRequest = onNoThanks,
        backgroundColor  = Color(0xFFB04A86),
        shape            = RoundedCornerShape(26.dp),
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.fillMaxWidth()
            ) {
                Text("GET FREE COINS", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth().height(180.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFE9E9F2)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🪙 +$reward", fontSize = 34.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth().height(52.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (!adReady) Color(0xFF8B4B6C) else Color(0xFFD24A8F))
                        .clickable(enabled = adReady) { onClaimAd() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (!adReady) "AD LOADING..." else "CLAIM",
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("NO THANKS", color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNoThanks() })
            }
        },
        buttons = {}
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SHOP SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ShopScreen(
    coins: Int,
    onBack: () -> Unit,
    onFreeAd25: () -> Unit,
    onBuyCoins: (Int) -> Unit,
    onBuyNoAds: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF6D8F86))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(10.dp))
                ShopPill("🪙", coins.toString())
                Spacer(Modifier.width(8.dp))
                ShopPill("🎯", "0")
                Spacer(Modifier.width(8.dp))
                ShopPill("⏩", "2")
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth().height(190.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF77AFCF))
                    .border(4.dp, Color(0xFFF0D277), RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text("STARTER\nPACK", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color(0xFF5A3A00))
                    Spacer(Modifier.height(12.dp))
                    Text("🪙 600",    fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text("🚫 NO ADS", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Spacer(Modifier.weight(1f))
                    Row {
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .height(56.dp).width(140.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF76C04E))
                                .clickable { onBuyNoAds(); onBuyCoins(600) },
                            contentAlignment = Alignment.Center
                        ) { Text("6,99 €", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth().height(72.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFFF2F0EA))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🚫 NO ADS", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF4A4A4A))
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .height(52.dp).width(130.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF76C04E))
                            .clickable { onBuyNoAds() },
                        contentAlignment = Alignment.Center
                    ) { Text("9,99 €", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black) }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShopCard("25",   "FREE",   Color(0xFFC44DA0), badge = "AD", onClick = onFreeAd25,          modifier = Modifier.weight(1f))
                ShopCard("800",  "2,99 €", Color(0xFF76C04E),               onClick = { onBuyCoins(800) },  modifier = Modifier.weight(1f))
                ShopCard("1400", "5,99 €", Color(0xFF76C04E),               onClick = { onBuyCoins(1400) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ShopPill(icon: String, value: String) {
    Box(
        modifier = Modifier
            .height(36.dp).widthIn(min = 90.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF6D8F86))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
    }
}

@Composable
private fun ShopCard(
    title: String,
    buttonText: String,
    buttonColor: Color,
    badge: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(210.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFF2F0EA))
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color(0xFF5A3A00))
            Spacer(Modifier.height(12.dp))
            Text("🪙🪙🪙", fontSize = 26.sp)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth().height(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(buttonColor)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(buttonText, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .padding(8.dp).size(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color(0xFF4A2A3A)),
                contentAlignment = Alignment.Center
            ) {
                Text(badge, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
    }
}