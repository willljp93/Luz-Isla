package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LuzIslaRepository
import com.example.domain.model.WidgetCardStyle
import com.example.ui.theme.LuzIslaTheme
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val db = AppDatabase.getInstance(applicationContext)
        val prefsRepo = UserPreferencesRepository(applicationContext)
        val repo = LuzIslaRepository(db, prefsRepo)

        setContent {
            val prefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = com.example.data.preferences.UserPreferences()
            )
            val snapshot by repo.snapshotFlow.collectAsStateWithLifecycle(
                initialValue = com.example.domain.model.ScheduleSnapshot(
                    emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()
                )
            )
            var query by remember { mutableStateOf("") }
            var selectedStyle by remember(prefs.widgetStyleMap[appWidgetId], prefs.widgetCardStyle) {
                mutableStateOf(prefs.widgetStyleMap[appWidgetId] ?: prefs.widgetCardStyle)
            }
            val scope = rememberCoroutineScope()

            val activeCircuits = remember(snapshot.circuits, query) {
                snapshot.circuits
                    .filter { it.isActive }
                    .filter {
                        query.isBlank() ||
                            it.code.contains(query, ignoreCase = true) ||
                            it.customName.contains(query, ignoreCase = true) ||
                            it.comment.contains(query, ignoreCase = true)
                    }
            }

            LuzIslaTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColor
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text("Configurar widget") })
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Estilo de tarjeta",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WidgetCardStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = selectedStyle == style,
                                    onClick = { selectedStyle = style },
                                    label = { Text(style.label) },
                                    modifier = Modifier.testTag("widget_config_style_${style.name.lowercase()}")
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Buscar código, nombre o reparto") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("widget_config_search")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(activeCircuits, key = { it.code }) { circuit ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("widget_config_circuit_${circuit.code}")
                                        .clickable {
                                            scope.launch {
                                                prefsRepo.setWidgetCircuit(appWidgetId, circuit.code)
                                                prefsRepo.setWidgetInstanceStyle(appWidgetId, selectedStyle)
                                                WidgetUpdateHelper.refreshAllWidgets(applicationContext)
                                                val resultValue = Intent().putExtra(
                                                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                                                    appWidgetId
                                                )
                                                setResult(RESULT_OK, resultValue)
                                                finish()
                                            }
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Bolt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 12.dp)
                                        ) {
                                            Text(
                                                text = circuit.displayTitle(),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (circuit.comment.isNotBlank()) {
                                                Text(
                                                    text = circuit.comment,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (circuit.isFavorite) {
                                            Icon(
                                                imageVector = Icons.Filled.Star,
                                                contentDescription = "Favorito",
                                                tint = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
