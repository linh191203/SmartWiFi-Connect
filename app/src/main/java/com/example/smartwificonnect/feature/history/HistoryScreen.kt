package com.example.smartwificonnect.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartwificonnect.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onBackClick: () -> Unit,
    onDeleteNetwork: (Long) -> Unit,
    onClearAll: () -> Unit,
) {
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                    Text(
                        text = stringResource(R.string.history_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }

                if (state.networks.isNotEmpty()) {
                    OutlinedButton(onClick = onClearAll) {
                        Text(text = stringResource(R.string.history_clear_all))
                    }
                }
            }
        },
    ) { innerPadding ->
        if (state.networks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.history_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.history_empty_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF667085),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.networks, key = { it.id }) { network ->
                HistoryNetworkCard(
                    network = network,
                    onDelete = { onDeleteNetwork(network.id) },
                )
            }
        }
    }
}

@Composable
private fun HistoryNetworkCard(
    network: HistoryNetworkUiModel,
    onDelete: () -> Unit,
) {
    var isPasswordVisible by rememberSaveable(network.id) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = network.ssid,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.history_delete_one),
                        tint = Color(0xFFD92D20),
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.history_security_label,
                    network.security.ifBlank { stringResource(R.string.history_security_unknown) },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (network.passwordSaved) {
                    stringResource(R.string.history_password_saved_yes)
                } else {
                    stringResource(R.string.history_password_saved_no)
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            if (network.passwordSaved && !network.password.isNullOrBlank()) {
                Text(
                    text = if (isPasswordVisible) {
                        stringResource(R.string.history_password_value_label, network.password)
                    } else {
                        stringResource(R.string.history_password_hidden)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { isPasswordVisible = !isPasswordVisible },
                ) {
                    Text(
                        text = if (isPasswordVisible) {
                            stringResource(R.string.history_hide_password)
                        } else {
                            stringResource(R.string.history_show_password)
                        },
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.history_password_not_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF667085),
                )
            }

            Text(
                text = stringResource(
                    R.string.history_last_connected_label,
                    formatHistoryTime(network.lastConnectedAtMillis),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF667085),
            )
        }
    }
}

private fun formatHistoryTime(timeMillis: Long): String {
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(timeMillis))
}
