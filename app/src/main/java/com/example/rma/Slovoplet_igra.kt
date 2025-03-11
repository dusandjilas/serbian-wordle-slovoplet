package com.example.rma

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.BufferedReader
import java.io.InputStreamReader

import kotlin.random.Random

val fonttri = FontFamily(
    Font(R.font.fonttri)
)

class Slovoplet_igra : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        setContentView(R.layout.activity_slovoplet_igra)


        val composeView = findViewById<ComposeView>(R.id.composeView)


        composeView.setContent {
            WordleGame()
        }


        dijalogObjasnjenje()
    }

    private fun dijalogObjasnjenje() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_objasnjenje)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(true)

        val dialogBtnCancel = dialog.findViewById<Button>(R.id.buttonIskljuci)
        dialogBtnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}




@Composable
fun WordleGame(
    tacnaBoja: Color = Color(0xFF162B4A),
    poluTacnaBoja: Color = Color(0xFFC11521),
    netacnaBoja: Color = Color(0xFF8995A3),
    defaultBoja: Color = Color.LightGray
) {
    val context = LocalContext.current

    var stanjeSlovo by remember { mutableStateOf(mutableMapOf<Char, Color>()) } //Trenutna boja slova

    val validneReci = remember { petSlovaReci(context) }//Reci u opticaju

    var trazenaRec = remember { izaberiRec(context) } //Izvucena rec iz csv fajla

    var prikaziDijalog by remember { mutableStateOf(false) } //Dijalog objasnjenje igre

    var pokuseneReciiDoSada by remember { mutableStateOf(mutableListOf<String>()) } //Koliko je user submitovao guess

    var trenutniPokusaj by remember { mutableStateOf("") } //Trenutni guess od usera

    var attempts by remember { mutableStateOf(0) } //Trenutni broja guessova od usera

    val maxPokusaja = 6
    val maxDuzinaReci = 5




    if (prikaziDijalog) {
        EndGameDialog(
            rec = trazenaRec,
            onPlayAgain = {
                pokuseneReciiDoSada.clear()
                trenutniPokusaj = ""
                attempts = 0
                stanjeSlovo.clear()
                trazenaRec = izaberiRec(context)
                prikaziDijalog = false
            },
            onExit = {
                prikaziDijalog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Словоплет",
                fontSize = 40.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp),
                color = Color(0xFF162B4A),
                fontFamily = fonttri
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (i in 0 until maxPokusaja) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                ) {
                    val pokusaj = if (i < pokuseneReciiDoSada.size) pokuseneReciiDoSada[i] else trenutniPokusaj.padEnd(5, ' ')
                    val boje = MutableList(5) { defaultBoja }

                    if (i < pokuseneReciiDoSada.size) {

                        val targetCharCounts = trazenaRec.groupingBy { it }.eachCount().toMutableMap()

                        // Oznacava slova sa tacnom pozicijom
                        for (j in 0 until maxDuzinaReci) {
                            if (pokusaj[j] == trazenaRec[j]) {
                                val toSlovo = pokusaj[j]
                                boje[j] = tacnaBoja
                                targetCharCounts[toSlovo] = targetCharCounts[toSlovo]?.minus(1) ?: 0
                                stanjeSlovo[toSlovo] = tacnaBoja
                            }
                        }

                        // Oznacava slova sa polutacnom  ili netacnom pozicijom
                        for (j in 0 until maxDuzinaReci) {
                            if (boje[j] == defaultBoja) {
                                val toSlovo = pokusaj[j]
                                if (toSlovo in trazenaRec && targetCharCounts[toSlovo]?.let { it > 0 } == true) {
                                    boje[j] = poluTacnaBoja
                                    targetCharCounts[toSlovo] = targetCharCounts[toSlovo]?.minus(1) ?: 0
                                    stanjeSlovo[toSlovo] = poluTacnaBoja
                                }
                                else {
                                    boje[j] = netacnaBoja
                                    stanjeSlovo[toSlovo] = netacnaBoja
                                }
                            }
                        }
                    }

                    for (j in 0 until maxDuzinaReci) {
                        val slovo = pokusaj[j]
                        val boje = boje[j]

                        if (i < pokuseneReciiDoSada.size) {
                            stanjeSlovo[slovo] = boje
                        }

                        Grid(toSlovo = slovo, color = boje)
                    }
                }


                Spacer(modifier = Modifier.height(4.dp))
            }


        }


        // In game tastatura
        VirtualKeyboard(
            onKeyClick = { key ->
                if (key == "ENTER") {
                    if (trenutniPokusaj.length == maxDuzinaReci && trenutniPokusaj.lowercase() in validneReci ) {
                        pokuseneReciiDoSada.add(trenutniPokusaj)
                        attempts++
                        if (trenutniPokusaj == trazenaRec) {
                            prikaziDijalog = false
                        }
                        else if (attempts >= maxPokusaja){
                            prikaziDijalog = true
                        }
                        trenutniPokusaj = ""
                    }else {

                        Toast.makeText(context, "Реч није важећа", Toast.LENGTH_SHORT).show()
                    }
                } else if (key == "DEL") {
                    if (trenutniPokusaj.isNotEmpty()) {
                        trenutniPokusaj = trenutniPokusaj.dropLast(1)
                    }
                } else if (trenutniPokusaj.length < maxDuzinaReci) {
                    trenutniPokusaj += key
                }
            },
            stanjeSlovo = stanjeSlovo // Salje tastaturi
        )
    }
}


@Composable
fun VirtualKeyboard(onKeyClick: (String) -> Unit, stanjeSlovo: Map<Char, Color>) {
    val keyboardRows = listOf(
        "ЉЊЕРТЗУИОПШ",
        "АСДФГХЈКЛЧЋ",
        "ЏЦВБНМЂЖ"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keyboardRows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
            ) {
                row.forEach { key ->
                    KeyboardKey(key.toString(), onKeyClick, stanjeSlovo[key] ?: Color.LightGray)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))


        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            KeyboardKeySpecial("DEL", onKeyClick, Color.Black)
            Spacer(modifier = Modifier.width(50.dp))
            KeyboardKeySpecial("ENTER", onKeyClick, Color.Black)
        }
    }
}

@Composable
fun KeyboardKey(key: String, onKeyClick: (String) -> Unit, keyColor: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier


            .clip(RoundedCornerShape(4.dp))
            .background(keyColor)
            .clickable { onKeyClick(key) }
            .height(50.dp)
            .width(29.dp)
    ) {
        Text(
            text = key,
            fontSize = 20.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun KeyboardKeySpecial(key: String, onKeyClick: (String) -> Unit, keyColor: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(4.dp)
            .height(40.dp)
            .width(80.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(keyColor)
            .clickable { onKeyClick(key) }
    ) {
        Text(
            text = key,
            fontSize = 18.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun Grid(toSlovo: Char, color: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(70.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
    ) {
        Text(
            text = toSlovo.toString(),
            fontSize = 24.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

fun petSlovaReci(context: Context): List<String> {
    val reci = mutableListOf<String>()
    try {

        val inputStream = context.resources.openRawResource(R.raw.petslova)
        val citac = BufferedReader(InputStreamReader(inputStream))
        var line: String?


        while (citac.readLine().also { line = it } != null) {
            reci.add(line!!.trim())
        }
        citac.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return reci
}



fun izaberiRec(context: Context): String {
    val rec = petSlovaReci(context)
    val psovke = mutableListOf<String>("")


    val validneReci = rec.filter { it !in psovke }


    return if (validneReci.isNotEmpty()) {

        validneReci[Random.nextInt(validneReci.size)].uppercase()
    } else {

        "ААААА"
    }
}

@Composable
fun EndGameDialog(rec: String, onPlayAgain: () -> Unit, onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = onExit,
        title = {
            Text(text = "Игра завршена")
        },
        text = {
            Text(text = "Тражена реч је била: $rec.")
        },
        confirmButton = {
        },


    )
}
