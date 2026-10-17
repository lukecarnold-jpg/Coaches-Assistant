package com.lonrad.coachesassistant.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lonrad.coachesassistant.R

/** The shortest password a backup can be protected with. */
const val MIN_BACKUP_PASSWORD_LENGTH = 8

/**
 * Asks for a backup's password. When [saving], it asks twice and a blank password is allowed (the
 * backup is then saved as plain text); otherwise it asks once, for the password the backup was saved
 * with. The password is kept only while the dialog is open, and [onConfirm] gets it as typed.
 */
@Composable
fun BackupPasswordDialog(
    saving: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    val problem: Int? = when {
        !saving || password.isEmpty() -> null
        password.length < MIN_BACKUP_PASSWORD_LENGTH -> R.string.backup_password_too_short
        password != confirmation -> R.string.backup_password_mismatch
        else -> null
    }
    val canConfirm = if (saving) problem == null else password.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (saving) R.string.backup_password_title else R.string.backup_password_restore_title)) },
        text = {
            Column {
                Text(stringResource(if (saving) R.string.backup_password_hint else R.string.backup_password_restore_hint))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.backup_password_field)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = problem != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (saving) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text(stringResource(R.string.backup_password_confirm_field)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = problem != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                problem?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = canConfirm) {
                Text(
                    when {
                        !saving -> stringResource(R.string.restore_from_backup)
                        password.isEmpty() -> stringResource(R.string.backup_password_skip)
                        else -> stringResource(R.string.save)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
