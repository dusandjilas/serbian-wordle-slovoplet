package com.example.rma

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profileManager = GameProfileManager(this)
        val isGuest = FirebaseAuth.getInstance().currentUser == null

        setContent {
            MaterialTheme {
                ProfileScreen(
                    profileManager = profileManager,
                    isGuest = isGuest,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
private fun ProfileScreen(profileManager: GameProfileManager, isGuest: Boolean, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1E3560), Color(0xFF162B4A))))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Профил", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (isGuest) "Играш као гост" else "Твој напредак",
                color = Color(0xFFBFE6FF),
                fontSize = 16.sp
            )

            StatCard("Ниво", profileManager.getLevel().toString())
            StatCard("XP", profileManager.getXp().toString())
            StatCard("Classic победе", profileManager.getClassicWins().toString())
            StatCard("Classic порази", profileManager.getClassicLosses().toString())
            StatCard("Најбољи streak", profileManager.getBestClassicStreak().toString())
            StatCard("Daily победе", profileManager.getDailyWins().toString())

            Spacer(Modifier.height(8.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Назад") }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF264A7A)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, color = Color(0xFFAAD9FF), fontSize = 12.sp)
            Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}
