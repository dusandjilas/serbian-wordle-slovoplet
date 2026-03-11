package com.example.rma

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ShopOfferUi(
    val coins: Int,
    val price: String,
    val saleText: String,
    val ribbon: String? = null,
    val emoji: String,
    val isAdReward: Boolean = false
)

@Composable
fun ShopScreen(
    coins: Int,
    onBack: () -> Unit,
    onFreeAd25: () -> Unit,
    onBuyCoins: (Int) -> Unit,
    onBuyNoAds: () -> Unit
) {
    val smallOffers = listOf(
        ShopOfferUi(25, "БЕСПЛАТНО", "ПОГЛЕДАЈ\nРЕКЛАМУ", "БЕСПЛАТНО", "🎁", true),
        ShopOfferUi(1000, "$4.99", "50%\nSALE", "ПОПУЛАРНО", "🪙"),
        ShopOfferUi(3000, "$6.99", "50%\nSALE", emoji = "💰"),
        ShopOfferUi(4000, "$8.99", "50%\nSALE", emoji = "🏺")
    )

    val bigOffers = listOf(
        ShopOfferUi(7000, "$12.99", "25%\nBONUS", "ИСПЛАТИВО", "💰"),
        ShopOfferUi(12000, "$19.99", "30%\nBONUS", emoji = "🏆"),
        ShopOfferUi(20000, "$29.99", "35%\nBONUS", "НАЈБОЉЕ", "👑")
    )

    Box(Modifier.fillMaxSize().background(Color(0xFF155844))) {
        ShamrockPatternBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                GameTitle(text = "ПРОДАВНИЦА", modifier = Modifier.align(Alignment.Center))
                PurpleCloseButton(onClick = onBack, modifier = Modifier.align(Alignment.TopEnd))
            }

            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CoinCounterPill(coins = coins)
            }

            Spacer(Modifier.height(14.dp))
            StarterPackCard(onClick = { onBuyNoAds(); onBuyCoins(600) })

            Spacer(Modifier.height(18.dp))
            SectionTitle("Пакети новчића")
            Spacer(Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                items(smallOffers) { offer ->
                    ShopOfferCard(offer = offer, onClick = {
                        if (offer.isAdReward) onFreeAd25() else onBuyCoins(offer.coins)
                    })
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("Мега пакети")
            Spacer(Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                items(bigOffers) { offer -> ShopOfferCard(offer = offer, onClick = { onBuyCoins(offer.coins) }) }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StarterPackCard(onClick: () -> Unit) { /* unchanged UI */
    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFFF9B51E)).border(5.dp, Color(0xFFE38217), RoundedCornerShape(26.dp)).padding(10.dp)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Color(0xFFF2E0CF))) {
            BeigeBurstBackground(Modifier.matchParentSize())
            SaleBadge(text = "НАЈБОЉА\nПОНУДА", modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFB950FF), Color(0xFF7F2DFF)))).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("ПОЧЕТНИ ПАКЕТ", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🪙", fontSize = 54.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        OutlinedGameText("600", fill = Color.White, stroke = Color(0xFFBF5A07), fontSize = 30.sp)
                        Text("Новчићи + Без реклама", color = Color(0xFF7A3E05), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.weight(1f))
                GreenBuyButton(price = "$2.99", onClick = onClick, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable private fun SectionTitle(text: String) { Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x33000000)).padding(horizontal = 14.dp, vertical = 8.dp)) { Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) } }

@Composable
private fun ShopOfferCard(offer: ShopOfferUi, onClick: () -> Unit) {
    Box(Modifier.width(220.dp).height(340.dp)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)).background(Color(0xFFF9B51E)).border(5.dp, Color(0xFFE38217), RoundedCornerShape(26.dp)).padding(10.dp)) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Color(0xFFF2E0CF))) {
                    BeigeBurstBackground(Modifier.matchParentSize())
                    Text(offer.emoji, fontSize = 88.sp, modifier = Modifier.align(Alignment.Center))
                    SaleBadge(text = offer.saleText, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
                }
                Spacer(Modifier.height(10.dp))
                CoinAmountPlate(amount = offer.coins, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                GreenBuyButton(price = offer.price, onClick = onClick, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
            }
        }
        if (offer.ribbon != null) CornerRibbon(text = offer.ribbon, modifier = Modifier.align(Alignment.TopStart).offset(x = (-8).dp, y = 16.dp))
    }
}

@Composable
private fun GameTitle(text: String, modifier: Modifier = Modifier) { Box(modifier) { Text(text, fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color(0xFF5F2BA8), modifier = Modifier.offset(0.dp, 4.dp)); Text(text, fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color(0xFFEDE2FF)) } }

@Composable
private fun PurpleCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF7A3BDB)).border(3.dp, Color(0xFFE5D4FF), RoundedCornerShape(12.dp)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text("X", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

@Composable private fun CoinCounterPill(coins: Int) { Box(Modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xFF6D8F86)).border(3.dp, Color(0xFFAAC8BF), RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 7.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { CoinIcon(18.dp); Spacer(Modifier.width(8.dp)); Text(coins.toString(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black) } } }

@Composable
private fun SaleBadge(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFF1A927)).border(2.dp, Color(0xFFC76A0C), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 12.sp)
    }
}

@Composable private fun CornerRibbon(text: String, modifier: Modifier = Modifier) { Box(modifier.clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)).background(Color(0xFF8A4B14)).padding(horizontal = 10.dp, vertical = 6.dp)) { Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black) } }

@Composable
private fun CoinAmountPlate(amount: Int, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF7748B8)).border(2.dp, Color(0xFFD3B7FF), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            CoinIcon(16.dp)
            Spacer(Modifier.width(6.dp))
            OutlinedGameText(text = amount.toString(), fill = Color.White, stroke = Color(0xFF3E0C7F), fontSize = 24.sp)
        }
    }
}

@Composable private fun GreenBuyButton(price: String, onClick: () -> Unit, modifier: Modifier = Modifier) { Box(modifier.height(54.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF36B452)).border(3.dp, Color(0xFF96F3A5), RoundedCornerShape(16.dp)).clickable { onClick() }, contentAlignment = Alignment.Center) { Text(price, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp) } }

@Composable
private fun CoinIcon(iconSize: Dp) {
    Box(Modifier.size(iconSize).drawBehind {
        val r = size.minDimension / 2f
        drawCircle(Color(0xFFFFC928), radius = r)
        drawCircle(Color(0xFFE98D09), radius = r, style = Stroke(width = 3.dp.toPx()))
        drawCircle(Color(0xFFFFE17A), radius = r / 1.7f)
    })
}

@Composable
private fun OutlinedGameText(text: String, fill: Color, stroke: Color, fontSize: TextUnit) {
    Box {
        Text(text, fontSize = fontSize, fontWeight = FontWeight.Black, color = stroke, modifier = Modifier.offset(2.dp, 2.dp))
        Text(text, fontSize = fontSize, fontWeight = FontWeight.Black, color = fill)
    }
}

@Composable
private fun ShamrockPatternBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val patternColor = Color(0xFF0E4536)
        val cell = 84.dp.toPx()
        val leafR = 12.dp.toPx()
        var y = 0f
        while (y < size.height + cell) {
            var x = 0f
            while (x < size.width + cell) {
                val cx = x + cell / 2
                val cy = y + cell / 2
                drawCircle(patternColor, radius = leafR, center = Offset(cx, cy - leafR))
                drawCircle(patternColor, radius = leafR, center = Offset(cx - leafR, cy))
                drawCircle(patternColor, radius = leafR, center = Offset(cx + leafR, cy))
                drawCircle(patternColor, radius = leafR, center = Offset(cx, cy + leafR * 0.2f))
                drawLine(patternColor, start = Offset(cx, cy + leafR * 0.8f), end = Offset(cx + leafR * 0.8f, cy + leafR * 1.8f), strokeWidth = 4.dp.toPx())
                x += cell
            }
            y += cell
        }
    }
}

@Composable
private fun BeigeBurstBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val rayColor = Color(0xFFF7EBDD)
        for (i in 0 until 16) {
            rotate(degrees = i * 22.5f, pivot = center) {
                drawPath(Path().apply {
                    moveTo(center.x, center.y)
                    lineTo(center.x - 38.dp.toPx(), -40f)
                    lineTo(center.x + 38.dp.toPx(), -40f)
                    close()
                }, color = rayColor)
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ShopScreenPreview() {
    MaterialTheme { ShopScreen(coins = 2500, onBack = {}, onFreeAd25 = {}, onBuyCoins = {}, onBuyNoAds = {}) }
}
