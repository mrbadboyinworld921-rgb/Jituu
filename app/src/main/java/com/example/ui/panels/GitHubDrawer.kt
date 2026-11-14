package com.example.ui.panels

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.model.GitHubFileItem
import com.example.model.GitHubRepoItem
import com.example.ui.hud.hudGlassPanel
import com.example.ui.theme.JarvisColors

@Composable
fun GitHubDrawer(
    savedUsername: String,
    savedToken: String,
    repos: List<GitHubRepoItem>,
    files: List<GitHubFileItem>,
    selectedRepoFullName: String,
    statusMessage: String,
    isLoading: Boolean,
    onLinkAndLoad: (username: String, token: String) -> Unit,
    onInspectFiles: (fullName: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var username by remember(savedUsername) { mutableStateOf(savedUsername) }
    var token by remember(savedToken) { mutableStateOf(savedToken) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = JarvisColors.NeonCyan,
        unfocusedBorderColor = JarvisColors.CyanBorder,
        focusedTextColor = JarvisColors.FrostWhite,
        unfocusedTextColor = JarvisColors.FrostWhite,
        cursorColor = JarvisColors.NeonCyan
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisColors.VoidBlack.copy(alpha = 0.55f))
            .navigationBarsPadding()
            .padding(10.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .heightIn(max = 600.dp)
                .hudGlassPanel(strong = true)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("GITHUB UPLINK", style = MaterialTheme.typography.titleMedium, color = JarvisColors.NeonCyan)
                    Text(
                        text = if (token.isNotBlank()) "PAT SEALED IN VAULT" else "PUBLIC API MODE",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisColors.MatrixEmerald
                    )
                }
                OutlinedButton(
                    onClick = onClose,
                    border = BorderStroke(1.dp, JarvisColors.CyanBorder),
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("close_github_drawer")
                ) {
                    Text("CLOSE", style = MaterialTheme.typography.labelSmall, color = JarvisColors.FrostWhite)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = { Text("Username (e.g. Crosas27)", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.weight(1f).testTag("github_username_input")
                )
                OutlinedButton(
                    onClick = { onLinkAndLoad(username.trim(), token.trim()) },
                    enabled = !isLoading,
                    border = BorderStroke(1.dp, JarvisColors.NeonCyan),
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("github_link_button")
                ) {
                    Text(if (isLoading) "..." else "LINK", style = MaterialTheme.typography.labelLarge, color = JarvisColors.NeonCyan)
                }
            }

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                placeholder = { Text("Optional Fine-Grained PAT (encrypted in PIN vault)", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth().testTag("github_token_input")
            )

            if (statusMessage.isNotBlank()) {
                Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = JarvisColors.MutedCyan)
            }

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(repos, key = { it.fullName }) { repo ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, JarvisColors.CyanBorder.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .background(JarvisColors.CyanTint, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${repo.name}${if (repo.isPrivate) " [PRIVATE]" else ""}",
                                style = MaterialTheme.typography.labelLarge,
                                color = JarvisColors.FrostWhite
                            )
                            Text(
                                text = listOfNotNull(
                                    repo.language.takeIf { it.isNotBlank() },
                                    repo.description.takeIf { it.isNotBlank() }
                                ).joinToString(" — "),
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisColors.MutedCyan
                            )
                        }
                        OutlinedButton(
                            onClick = { onInspectFiles(repo.fullName) },
                            border = BorderStroke(1.dp, JarvisColors.MatrixEmerald),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("FILES", style = MaterialTheme.typography.labelSmall, color = JarvisColors.MatrixEmerald)
                        }
                        if (repo.htmlUrl.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repo.htmlUrl)))
                                    }
                                },
                                border = BorderStroke(1.dp, JarvisColors.CyanBorder),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("OPEN", style = MaterialTheme.typography.labelSmall, color = JarvisColors.NeonCyan)
                            }
                        }
                    }
                }

                if (files.isNotEmpty()) {
                    item {
                        Text(
                            text = "ROOT FILES // $selectedRepoFullName",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisColors.MatrixEmerald,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    items(files, key = { "${selectedRepoFullName}_${it.name}" }) { f ->
                        Text(
                            text = if (f.type == "dir") "[DIR] ${f.name}/" else "  ${f.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisColors.FrostWhite,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(JarvisColors.VoidBlack.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
