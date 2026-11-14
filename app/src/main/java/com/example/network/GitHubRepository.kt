package com.example.network

import com.example.model.GitHubFileItem
import com.example.model.GitHubRepoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GitHubRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val userRegex = Regex("^[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,37}[a-zA-Z0-9])?$")
    private val repoRegex = Regex("^[a-zA-Z0-9._-]{1,100}$")

    suspend fun fetchRepos(username: String, token: String): Result<List<GitHubRepoItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmedUser = username.trim()
                val trimmedToken = token.trim()
                val url = when {
                    trimmedUser.isNotEmpty() -> {
                        require(userRegex.matches(trimmedUser)) { "Invalid GitHub username format." }
                        val encoded = URLEncoder.encode(trimmedUser, "UTF-8")
                        "https://api.github.com/users/$encoded/repos?sort=updated&per_page=20"
                    }
                    trimmedToken.isNotEmpty() -> {
                        "https://api.github.com/user/repos?sort=updated&per_page=20&affiliation=owner,collaborator"
                    }
                    else -> throw IllegalArgumentException("Enter a GitHub username (e.g. Crosas27).")
                }

                val reqBuilder = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "MAIM-Android-HUD")
                    .header("X-GitHub-Api-Version", "2022-11-28")

                if (trimmedToken.isNotEmpty()) {
                    reqBuilder.header("Authorization", "Bearer $trimmedToken")
                }

                client.newCall(reqBuilder.build()).execute().use { res ->
                    if (!res.isSuccessful) {
                        throw IllegalStateException("GitHub API returned HTTP ${res.code}.")
                    }
                    val body = res.body?.string().orEmpty()
                    val arr = JSONArray(body)
                    buildList {
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val htmlUrl = o.optString("html_url", "")
                            add(
                                GitHubRepoItem(
                                    name = o.optString("name", ""),
                                    fullName = o.optString("full_name", ""),
                                    description = o.optString("description", "").let {
                                        if (it == "null") "" else it.take(180)
                                    },
                                    language = o.optString("language", "Text").let {
                                        if (it == "null" || it.isBlank()) "Text" else it
                                    },
                                    isPrivate = o.optBoolean("private", false),
                                    htmlUrl = if (htmlUrl.startsWith("https://github.com/")) htmlUrl else ""
                                )
                            )
                        }
                    }
                }
            }
        }

    suspend fun fetchRootFiles(owner: String, repo: String, token: String): Result<List<GitHubFileItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(userRegex.matches(owner)) { "Invalid owner name." }
                require(repoRegex.matches(repo)) { "Invalid repository name." }

                val url = "https://api.github.com/repos/${URLEncoder.encode(owner, "UTF-8")}/${URLEncoder.encode(repo, "UTF-8")}/contents/"
                val reqBuilder = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "MAIM-Android-HUD")
                    .header("X-GitHub-Api-Version", "2022-11-28")

                if (token.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer ${token.trim()}")
                }

                client.newCall(reqBuilder.build()).execute().use { res ->
                    if (!res.isSuccessful) {
                        throw IllegalStateException("Could not load repository files (HTTP ${res.code}).")
                    }
                    val arr = JSONArray(res.body?.string().orEmpty())
                    buildList {
                        for (i in 0 until minOf(arr.length(), 40)) {
                            val o = arr.getJSONObject(i)
                            add(
                                GitHubFileItem(
                                    name = o.optString("name", ""),
                                    type = if (o.optString("type") == "dir") "dir" else "file",
                                    sizeBytes = o.optLong("size", 0L)
                                )
                            )
                        }
                    }
                }
            }
        }
}
