package com.defy.notivault.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.defy.notivault.BuildConfig
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

object UberAuthManager {

    private const val PREFS_NAME = "uber_auth_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_PKCE_VERIFIER = "pkce_verifier"
    private const val KEY_PKCE_STATE = "pkce_state"

    private const val AUTH_ENDPOINT = "https://login.uber.com/oauth/v2/authorize"
    private const val TOKEN_ENDPOINT = "https://login.uber.com/oauth/v2/token"

    fun startAuthorization(context: Context): Result<Unit> {
        if (BuildConfig.UBER_CLIENT_ID.isBlank()) {
            return Result.failure(IllegalStateException("UBER_CLIENT_ID não configurado"))
        }

        val verifier = randomUrlSafe(64)
        val challenge = verifier.sha256UrlSafe()
        val state = randomUrlSafe(24)
        savePkce(context, verifier, state)

        val uri = Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", BuildConfig.UBER_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", BuildConfig.UBER_REDIRECT_URI)
            .appendQueryParameter("scope", BuildConfig.UBER_OAUTH_SCOPES)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return Result.success(Unit)
    }

    fun isRedirectUri(uri: Uri?): Boolean {
        if (uri == null) return false
        val expected = Uri.parse(BuildConfig.UBER_REDIRECT_URI)
        return uri.scheme == expected.scheme && uri.host == expected.host
    }

    fun handleAuthorizationResponse(context: Context, uri: Uri): Result<Unit> {
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            return Result.failure(IllegalStateException("OAuth Uber retornou erro: $error"))
        }

        val code = uri.getQueryParameter("code")
            ?: return Result.failure(IllegalStateException("OAuth Uber sem code"))

        val returnedState = uri.getQueryParameter("state")
            ?: return Result.failure(IllegalStateException("OAuth Uber sem state"))

        val prefs = prefs(context)
        val savedState = prefs.getString(KEY_PKCE_STATE, null)
            ?: return Result.failure(IllegalStateException("State OAuth ausente"))
        if (returnedState != savedState) {
            return Result.failure(IllegalStateException("State OAuth inválido"))
        }

        val verifier = prefs.getString(KEY_PKCE_VERIFIER, null)
            ?: return Result.failure(IllegalStateException("Code verifier ausente"))

        return runCatching {
            val tokenResult = tokenRequest(
                grantType = "authorization_code",
                code = code,
                codeVerifier = verifier,
                redirectUri = BuildConfig.UBER_REDIRECT_URI,
                refreshToken = null
            )
            storeTokens(
                context = context,
                accessToken = tokenResult.accessToken,
                refreshToken = tokenResult.refreshToken,
                expiresInSeconds = tokenResult.expiresIn
            )
            clearPkce(context)
        }
    }

    fun hasUsableToken(context: Context): Boolean {
        val prefs = prefs(context)
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val now = System.currentTimeMillis()
        return !token.isNullOrBlank() && expiresAt > now + 30_000
    }

    fun getValidAccessToken(context: Context): Result<String> {
        val prefs = prefs(context)
        val savedToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val now = System.currentTimeMillis()

        if (!savedToken.isNullOrBlank() && expiresAt > now + 30_000) {
            return Result.success(savedToken)
        }

        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        if (!refreshToken.isNullOrBlank() && BuildConfig.UBER_CLIENT_ID.isNotBlank()) {
            return runCatching {
                val tokenResult = tokenRequest(
                    grantType = "refresh_token",
                    code = null,
                    codeVerifier = null,
                    redirectUri = BuildConfig.UBER_REDIRECT_URI,
                    refreshToken = refreshToken
                )
                storeTokens(
                    context = context,
                    accessToken = tokenResult.accessToken,
                    refreshToken = tokenResult.refreshToken.ifBlank { refreshToken },
                    expiresInSeconds = tokenResult.expiresIn
                )
                tokenResult.accessToken
            }
        }

        if (BuildConfig.UBER_RIDER_BEARER_TOKEN.isNotBlank()) {
            return Result.success(BuildConfig.UBER_RIDER_BEARER_TOKEN)
        }

        return Result.failure(IllegalStateException("Sem token Uber válido; conecte sua conta no app"))
    }

    private fun tokenRequest(
        grantType: String,
        code: String?,
        codeVerifier: String?,
        redirectUri: String,
        refreshToken: String?
    ): TokenResult {
        val bodyParams = mutableListOf(
            "client_id=${urlEncode(BuildConfig.UBER_CLIENT_ID)}",
            "grant_type=${urlEncode(grantType)}",
            "redirect_uri=${urlEncode(redirectUri)}"
        )

        if (grantType == "authorization_code") {
            bodyParams += "code=${urlEncode(code.orEmpty())}"
            bodyParams += "code_verifier=${urlEncode(codeVerifier.orEmpty())}"
        }
        if (grantType == "refresh_token") {
            bodyParams += "refresh_token=${urlEncode(refreshToken.orEmpty())}"
        }

        val body = bodyParams.joinToString("&")

        val connection = (URL(TOKEN_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (responseCode !in 200..299) {
                throw IOException("Uber OAuth erro HTTP $responseCode: $responseText")
            }

            val json = JSONObject(responseText)
            val accessToken = json.optString("access_token")
            if (accessToken.isBlank()) {
                throw IOException("Resposta OAuth sem access_token")
            }

            TokenResult(
                accessToken = accessToken,
                refreshToken = json.optString("refresh_token"),
                expiresIn = json.optLong("expires_in", 3600L)
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun savePkce(context: Context, verifier: String, state: String) {
        prefs(context).edit()
            .putString(KEY_PKCE_VERIFIER, verifier)
            .putString(KEY_PKCE_STATE, state)
            .apply()
    }

    private fun clearPkce(context: Context) {
        prefs(context).edit()
            .remove(KEY_PKCE_VERIFIER)
            .remove(KEY_PKCE_STATE)
            .apply()
    }

    private fun storeTokens(
        context: Context,
        accessToken: String,
        refreshToken: String,
        expiresInSeconds: Long
    ) {
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000)
        prefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun randomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return bytes.toBase64Url()
    }

    private fun String.sha256UrlSafe(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return digest.toBase64Url()
    }

    private fun ByteArray.toBase64Url(): String {
        return android.util.Base64.encodeToString(this, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    }

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private data class TokenResult(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long
    )
}
