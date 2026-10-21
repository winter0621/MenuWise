package com.example.careium.core.planning

import android.content.Context
import com.example.careium.core.database.authentication.LocalAccountStore
import org.json.JSONArray
import org.json.JSONObject

data class HouseholdProfile(val id: String, val name: String, val preferences: String, val excludedFoods: String)

/** Local household preference profiles. They intentionally do not copy or merge meal records. */
class HouseholdProfileStore(context: Context) {
    private val accountId = LocalAccountStore.currentAccountId(context)
    private val prefs = context.getSharedPreferences("household_profiles_$accountId", Context.MODE_PRIVATE)

    fun profiles(): List<HouseholdProfile> = try {
        val values = JSONArray(prefs.getString("profiles", "[]"))
        (0 until values.length()).mapNotNull { index ->
            val value = values.optJSONObject(index) ?: return@mapNotNull null
            val id = value.optString("id")
            val name = value.optString("name").trim()
            if (id.isBlank() || name.isBlank()) null else HouseholdProfile(id, name,
                value.optString("preferences").trim(), value.optString("excluded_foods").trim())
        }
    } catch (_: Exception) { emptyList() }

    fun active(): HouseholdProfile? {
        val activeId = prefs.getString("active_id", null) ?: return null
        return profiles().firstOrNull { it.id == activeId }
    }

    fun save(profile: HouseholdProfile) {
        require(profile.name.length in 1..24) { "成员名称需为1–24个字符" }
        require(profile.preferences.length <= 500) { "饮食偏好最多500个字符" }
        require(profile.excludedFoods.length <= 300) { "忌口食材最多300个字符" }
        val updated = profiles().toMutableList()
        val index = updated.indexOfFirst { it.id == profile.id }
        if (index >= 0) updated[index] = profile else {
            require(updated.size < 8) { "最多保存8位家庭成员" }
            updated += profile
        }
        write(updated)
        if (active() == null) setActive(profile.id)
    }

    fun remove(id: String) {
        write(profiles().filterNot { it.id == id })
        if (prefs.getString("active_id", null) == id) prefs.edit().remove("active_id").apply()
    }

    fun setActive(id: String) {
        require(profiles().any { it.id == id }) { "找不到该成员" }
        prefs.edit().putString("active_id", id).apply()
    }

    private fun write(profiles: List<HouseholdProfile>) {
        val values = JSONArray()
        profiles.forEach { values.put(JSONObject().put("id", it.id).put("name", it.name)
            .put("preferences", it.preferences).put("excluded_foods", it.excludedFoods)) }
        check(prefs.edit().putString("profiles", values.toString()).commit()) { "家庭档案保存失败" }
    }
}
