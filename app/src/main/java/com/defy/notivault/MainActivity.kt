package com.defy.notivault

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.defy.notivault.data.AppDatabase
import com.defy.notivault.data.UberCallEntity
import com.defy.notivault.ui.UberCallViewModel
import com.defy.notivault.ui.UberCallViewModelFactory
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        setContent {
            MaterialTheme {
                val dao = AppDatabase.getInstance(applicationContext).uberCallDao()
                val viewModel: UberCallViewModel = viewModel(
                    factory = UberCallViewModelFactory(dao)
                )

                val calls by viewModel.calls.collectAsStateWithLifecycle()
                UberCallsScreen(
                    calls = calls,
                    onClearHistory = { viewModel.clearHistory() }
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun UberCallsScreen(calls: List<UberCallEntity>, onClearHistory: () -> Unit) {
    val context = LocalContext.current
    var isEnabled by remember {
        mutableStateOf(
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        )
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Chamadas Uber",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    isEnabled = NotificationManagerCompat.getEnabledListenerPackages(context)
                        .contains(context.packageName)
                }) {
                    Text(if (isEnabled) "Permissão ativa" else "Ativar acesso")
                }
            }

            if (calls.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onClearHistory) {
                        Text("Limpar histórico")
                    }
                }
            }

            if (calls.isEmpty()) {
                Text(
                    text = "Nenhuma chamada de Uber registrada ainda.",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(calls, key = { it.id }) { item ->
                        UberCallCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun UberCallCard(item: UberCallEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "Contato: ${item.contactName}", fontWeight = FontWeight.SemiBold)
            Text(text = "Palavra-chave: ${item.keyword}")
            Text(text = "Origem: ${item.pickupAddress}")
            Text(text = "Destino: ${item.dropoffAddress}")
            Text(text = "Notificação: ${item.sourceTitle} - ${item.sourceContent}")
            Text(
                text = DateFormat.getDateTimeInstance().format(Date(item.calledAt)),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
