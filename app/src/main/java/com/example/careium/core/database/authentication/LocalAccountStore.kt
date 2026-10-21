package com.example.careium.core.database.authentication

import android.content.Context
import android.util.Base64
import com.example.careium.R
import com.example.careium.core.models.User
import com.example.careium.frontend.factory.FutureGoal
import com.example.careium.frontend.factory.Gender
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/**
 * Device-local account store. Registration and login happen entirely on this device
 * (no network, no VPN), reusing the app's existing offline-mode data path.
 * Passwords are stored as salted SHA-256 hashes, never in plain text.
 */
class LocalAccountStore(private val context: Context) {
    private val accounts = context.getSharedPreferences("menuwise_accounts", Context.MODE_PRIVATE)
    private val session = context.getSharedPreferences("menuwise_local", Context.MODE_PRIVATE)

    fun register(user: User): String? {
        val email = normalize(user.email)
        if (!validEmail(email)) return context.getString(R.string.error_invalid_email)
        if (user.password.length < 6) return context.getString(R.string.error_password_too_short)
        if (accounts.contains(email)) return context.getString(R.string.error_email_registered)

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val record = JSONObject()
            .put("email", email)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("password_hash", hash(user.password, salt))
        writeProfile(record, user)
        check(accounts.edit().putString(email, record.toString()).commit()) { "注册失败，请重试" }

        startSession(user, email)
        return null
    }

    fun login(email: String, password: String): String? {
        val key = normalize(email)
        val record = try {
            JSONObject(accounts.getString(key, null) ?: return context.getString(R.string.error_account_not_found))
        } catch (_: Exception) {
            return context.getString(R.string.error_account_not_found)
        }
        val salt = Base64.decode(record.optString("salt"), Base64.NO_WRAP)
        if (hash(password, salt) != record.optString("password_hash"))
            return context.getString(R.string.error_wrong_password)

        startSession(readProfile(record), key)
        return null
    }

    fun saveProfile(user: User) {
        val key = session.getString("current_email", null) ?: return
        val record = try { JSONObject(accounts.getString(key, null) ?: return) } catch (_: Exception) { return }
        writeProfile(record, user)
        check(accounts.edit().putString(key, record.toString()).commit()) { "资料保存失败" }
        writeSessionProfile(user)
    }

    fun logout() {
        session.edit().remove("current_email").apply()
    }

    private fun startSession(user: User, email: String) {
        session.edit()
            .putBoolean("offline_mode", true)
            .putString("current_email", email)
            .apply()
        writeSessionProfile(user)
    }

    private fun writeSessionProfile(user: User) {
        session.edit()
            .putString("name", user.name)
            .putFloat("height", user.height)
            .putFloat("weight", user.weight)
            .putInt("age", user.age)
            .putFloat("desired_weight", user.desiredWeight)
            .putString("gender", user.gender?.name ?: "Male")
            .putString("goal", user.futureGoal?.name ?: "FitnessTracker")
            .apply()
    }

    private fun writeProfile(record: JSONObject, user: User) {
        record.put("name", user.name)
            .put("height", user.height.toDouble())
            .put("weight", user.weight.toDouble())
            .put("age", user.age)
            .put("desired_weight", user.desiredWeight.toDouble())
            .put("gender", user.gender?.name ?: "Male")
            .put("future_goal", user.futureGoal?.name ?: "FitnessTracker")
    }

    private fun readProfile(record: JSONObject): User = User().apply {
        email = record.optString("email")
        name = record.optString("name")
        height = record.optDouble("height", 0.0).toFloat()
        weight = record.optDouble("weight", 0.0).toFloat()
        age = record.optInt("age", 0)
        desiredWeight = record.optDouble("desired_weight", 0.0).toFloat()
        gender = runCatching { Gender.valueOf(record.optString("gender", "Male")) }.getOrDefault(Gender.Male)
        futureGoal = runCatching { FutureGoal.valueOf(record.optString("future_goal", "FitnessTracker")) }.getOrDefault(FutureGoal.FitnessTracker)
    }

    companion object {
        private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun normalize(email: String) = email.trim().lowercase(Locale.ROOT)

        private fun validEmail(email: String) = EMAIL_PATTERN.matches(email)

        private fun hash(password: String, salt: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(salt)
            return Base64.encodeToString(digest.digest(password.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        }

        /**
         * Resolves the per-account storage id. Local accounts use "local:&lt;email&gt;",
         * the guest uses "local", and a still-signed-in Firebase account keeps its uid.
         */
        fun currentAccountId(context: Context): String {
            val prefs = context.getSharedPreferences("menuwise_local", Context.MODE_PRIVATE)
            val email = prefs.getString("current_email", null)
            return when {
                email != null -> "local:$email"
                prefs.getBoolean("offline_mode", false) -> "local"
                else -> FirebaseAuth.getInstance().currentUser?.uid ?: "local"
            }
        }
    }
}
