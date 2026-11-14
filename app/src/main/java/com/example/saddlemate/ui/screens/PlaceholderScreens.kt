package com.example.saddlemate.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
private fun TitledScreen(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun PlannerScreen() = TitledScreen(
    "Routenplaner",
    "10 km / 20 km Routen, gefiltert nach Pferdeprofil (Rehe, Jungpferd).",
)

@Composable
fun CockpitScreen() = TitledScreen(
    "Ritt-Cockpit",
    "Live-Tempo, Gangart, Km-Splits, Radar und Welfare-Balken.",
)

@Composable
fun WelfareScreen() = TitledScreen(
    "Pferd & Welfare",
    "Pferdeprofile, Hufschmied-Tracker, Welfare-Score.",
)

@Composable
fun FeedbackScreen() = TitledScreen(
    "Feedback",
    "Sprach-Pins und priorisierte Backlog-Liste.",
)
