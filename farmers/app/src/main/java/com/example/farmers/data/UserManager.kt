package com.example.farmers.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManager @Inject constructor(@ApplicationContext context: Context) {

    private val PREFS_NAME = "FarmersAppPrefs"
    private val KEY_IS_LOGGED_IN = "isLoggedIn"
    private val KEY_IS_DELIVERY_LOGGED_IN = "isDeliveryLoggedIn"
    private val KEY_SESSION_ID = "sessionId"
    private val KEY_USER_ID = "userId"
    private val KEY_LATITUDE = "latitude"
    private val KEY_LONGITUDE = "longitude"
    private val KEY_TIME_ZONE = "timeZone"
    private val KEY_FARMING_YEARS = "farmingYears"
    private val KEY_USER_NAME = "userName"
    private val KEY_PREFERRED_LANGUAGE = "preferredLanguage"

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setLoggedIn(isLoggedIn: Boolean) {
        sharedPrefs.edit { putBoolean(KEY_IS_LOGGED_IN, isLoggedIn) }
    }

    fun isLoggedIn(): Boolean {
        return sharedPrefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun setDeliveryLoggedIn(isLoggedIn: Boolean) {
        sharedPrefs.edit { putBoolean(KEY_IS_DELIVERY_LOGGED_IN, isLoggedIn) }
    }

    fun isDeliveryLoggedIn(): Boolean {
        return sharedPrefs.getBoolean(KEY_IS_DELIVERY_LOGGED_IN, false)
    }


    fun setSessionId(sessionId: String?) {
        sharedPrefs.edit { putString(KEY_SESSION_ID, sessionId) }
    }

    fun getSessionId(): String? {
        return sharedPrefs.getString(KEY_SESSION_ID, null)
    }

    fun setUserName(userName: String?) {
        sharedPrefs.edit { putString(KEY_USER_NAME, userName) }
    }

    fun getUserName(): String? {
        return sharedPrefs.getString(KEY_USER_NAME, null)
    }

    fun setUserId(userId: String?) {
        sharedPrefs.edit { putString(KEY_USER_ID, userId) }
    }

    fun getUserId(): String? {
        return sharedPrefs.getString(KEY_USER_ID, null)
    }

    fun setLatitude(latitude: String?) {
        sharedPrefs.edit { putString(KEY_LATITUDE, latitude) }
    }

    fun getLatitude(): String? {
        return sharedPrefs.getString(KEY_LATITUDE, null)
    }


    fun setLongitude(longitude: String?) {
        sharedPrefs.edit { putString(KEY_LONGITUDE, longitude) }
    }

    fun getLongitude(): String? {
        return sharedPrefs.getString(KEY_LONGITUDE, null)
    }

    fun setTimeZone(timeZone: String?) {
        sharedPrefs.edit { putString(KEY_TIME_ZONE, timeZone) }
    }

    fun getTimeZone(): String? {
        return sharedPrefs.getString(KEY_TIME_ZONE, null)
    }

    fun setFarmingYears(years: Int) {
        sharedPrefs.edit { putInt(KEY_FARMING_YEARS, years) }
    }

    fun getFarmingYears(): Int {
        return sharedPrefs.getInt(KEY_FARMING_YEARS, -1)
    }

    fun setPreferredLanguage(language: String) {
        sharedPrefs.edit { putString(KEY_PREFERRED_LANGUAGE, language) }
    }

    fun getPreferredLanguage(): String {
        // Default to English if no language is set
        return sharedPrefs.getString(KEY_PREFERRED_LANGUAGE, "English") ?: "English"
    }

    fun logout() {
        setLoggedIn(false)
        setDeliveryLoggedIn(false)
        setSessionId(null)
        setUserId(null)
        setUserName(null)
        setLatitude(null)
        setLongitude(null)
        setTimeZone(null)
        setFarmingYears(-1)
        setPreferredLanguage("English")
    }
}