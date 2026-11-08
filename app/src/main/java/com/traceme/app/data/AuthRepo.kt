package com.traceme.app.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class AuthException(message: String) : Exception(message)

/**
 * Local accounts with PBKDF2 password hashing, plus Gmail sign-in through
 * Credential Manager. Google sign-in needs GOOGLE_WEB_CLIENT_ID (see README).
 */
object AuthRepo {

    fun register(repo: Repo, name: String, email: String, phone: String, password: String): Account {
        val cleanEmail = email.trim().lowercase()
        if (name.isBlank()) throw AuthException("Please tell us your name.")
        if (!cleanEmail.contains("@") || !cleanEmail.substringAfter("@").contains(".")) {
            throw AuthException("That email address doesn't look right.")
        }
        if (password.length < 6) throw AuthException("Use a password with at least 6 characters.")
        if (repo.loadAccounts().any { it.email.equals(cleanEmail, ignoreCase = true) }) {
            throw AuthException("An account already exists for this email.")
        }
        val salt = randomSalt()
        val account = Account(
            name = name.trim(),
            email = cleanEmail,
            phone = phone.trim(),
            provider = "local",
            salt = salt,
            passwordHash = hash(password, salt)
        )
        repo.saveAccounts(repo.loadAccounts() + account)
        repo.sessionEmail = account.email
        return account
    }

    fun login(repo: Repo, email: String, password: String): Account {
        val cleanEmail = email.trim().lowercase()
        val account = repo.loadAccounts().firstOrNull { it.email.equals(cleanEmail, ignoreCase = true) }
            ?: throw AuthException("No account found for that email.")
        if (account.provider == "google") throw AuthException("This account signs in with Google.")
        if (account.passwordHash.isBlank() || hash(password, account.salt) != account.passwordHash) {
            throw AuthException("Wrong password. Try again.")
        }
        repo.sessionEmail = account.email
        return account
    }

    fun signOut(repo: Repo) {
        repo.sessionEmail = null
    }

    /**
     * Launches the Google account picker and signs the user in with an ID token.
     * The returned account is created locally on first use.
     */
    suspend fun googleSignIn(activityContext: Context, serverClientId: String): Account =
        withContext(Dispatchers.IO) {
            if (serverClientId.isBlank()) {
                throw AuthException(
                    "Google sign-in isn't configured yet. Add GOOGLE_WEB_CLIENT_ID to local.properties and rebuild."
                )
            }
            val repo = Repo.get(activityContext)
            val manager = CredentialManager.create(activityContext)
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(serverClientId)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()

            val result = try {
                manager.getCredential(activityContext, request)
            } catch (e: Exception) {
                throw AuthException("Google sign-in was cancelled or unavailable.")
            }

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleCredential.id.trim().lowercase()
                val existing = repo.loadAccounts().firstOrNull { it.email.equals(email, ignoreCase = true) }
                val account = existing ?: Account(
                    name = googleCredential.displayName?.takeIf { it.isNotBlank() }
                        ?: email.substringBefore("@"),
                    email = email,
                    phone = "",
                    provider = "google"
                ).also { repo.saveAccounts(repo.loadAccounts() + it) }
                repo.sessionEmail = account.email
                return@withContext account
            }
            throw AuthException("Google didn't return an ID token. Try again.")
        }

    private fun randomSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hash(password: String, saltHex: String): String {
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
