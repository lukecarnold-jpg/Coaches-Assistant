package com.lonrad.coachesassistant.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun rememberIdFileExportLauncher(
    mimeType: String,
    onExport: (Long, Uri) -> Unit,
): (Long, String) -> Unit {
    var pendingId by remember { mutableStateOf<Long?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType),
    ) { uri ->
        val id = pendingId
        if (uri != null && id != null) {
            onExport(id, uri)
        }
        pendingId = null
    }

    return { id, fileName ->
        pendingId = id
        launcher.launch(fileName)
    }
}
