package com.johnathaningle.excerpter.ui.settings

import android.content.Intent
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val modelState by viewModel.modelState.collectAsState()
    val context = LocalContext.current
    val installing = modelState is ModelState.Loading || modelState is ModelState.Downloading

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { viewModel.importModel(it) }
    }

    fun modelPickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/octet-stream"
        putExtra(
            DocumentsContract.EXTRA_INITIAL_URI,
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            ).toUri()
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader("Highlighting")

            SettingRow(
                title = "Auto-rotate highlight color",
                subtitle = "Cycle through the color palette after each highlight"
            ) {
                var autoRotate by remember { mutableStateOf(viewModel.autoRotateColor) }
                Switch(
                    checked = autoRotate,
                    onCheckedChange = { viewModel.autoRotateColor = it }
                )
            }

            HorizontalDivider()

            SectionHeader("AI Model")

            ModelStatusCard(modelState)

            SettingRow(
                title = "GPU acceleration",
                subtitle = "Load the model on the GPU (Vulkan) instead of the CPU. Falls back to CPU automatically. Takes effect on next launch."
            ) {
                var useVulkan by remember { mutableStateOf(viewModel.useVulkan) }
                Switch(
                    checked = useVulkan,
                    onCheckedChange = { viewModel.useVulkan = it }
                )
            }

            HorizontalDivider()

            Text("Install a model", style = MaterialTheme.typography.titleSmall)

            popularModels.forEach { model ->
                Card {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(model.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${model.sizeMb} MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { viewModel.downloadModel(model.url) },
                            enabled = !installing
                        ) {
                            Text("Download")
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { modelPickerLauncher.launch(modelPickerIntent()) },
                enabled = !installing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import a model from storage")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        control()
    }
}

@Composable
private fun ModelStatusCard(state: ModelState) {
    val icon: ImageVector?
    val title: String
    val subtitle: String
    val tint: Color
    when (state) {
        is ModelState.Ready -> {
            icon = Icons.Default.Check
            title = state.name
            subtitle = "${state.sizeMb} MB"
            tint = MaterialTheme.colorScheme.primary
        }
        ModelState.NotConfigured -> {
            icon = Icons.Default.Info
            title = "No AI model installed"
            subtitle = "Download or import one below"
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        }
        is ModelState.Downloading -> {
            icon = null
            title = "Downloading ${state.fileName}"
            subtitle = "Saving to app storage"
            tint = MaterialTheme.colorScheme.primary
        }
        ModelState.Loading -> {
            icon = null
            title = "Loading model"
            subtitle = "This can take a few seconds"
            tint = MaterialTheme.colorScheme.primary
        }
        is ModelState.Error -> {
            icon = Icons.Default.Warning
            title = "Model error"
            subtitle = state.message
            tint = MaterialTheme.colorScheme.error
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state is ModelState.Downloading) {
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
            if (state is ModelState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
