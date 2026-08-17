package com.webscare.urducanvas.common.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferenceDataStoreKeysConstants {
    val USER_ID_TOKEN = stringPreferencesKey("USER_ID_TOKEN")
    val USER_DISPLAY_NAME = stringPreferencesKey("USER_DISPLAY_NAME")

    val AUTH_TOKEN = stringPreferencesKey("AUTH_TOKEN")
    val LOGGED_IN_USER_ID = stringPreferencesKey("LOGGED_IN_USER_ID")
    val LOGGED_IN_USER_EMAIL = stringPreferencesKey("LOGGED_IN_USER_EMAIL")
    val LOGGED_IN_USER_ROLE = stringPreferencesKey("LOGGED_IN_USER_ROLE")
    val KEY_RESOLUTION = stringPreferencesKey("key_resolution")
    val KEY_QUALITY = stringPreferencesKey("key_quality")
    val KEY_FORMAT = stringPreferencesKey("key_format")

    val PREF_IS_SUBSCRIBED = booleanPreferencesKey("is_subscribed")
    val PREF_ACTIVE_PLAN = stringPreferencesKey("active_plan")
    var isSubscribedValue: Boolean = false
    val REMIND_LATER_TIMESTAMP = longPreferencesKey("remind_later_timestamp")

    val PREF_SUBSCRIPTION_START = longPreferencesKey("subscription_start_ms")
    val PREF_SUBSCRIPTION_EXPIRY = longPreferencesKey("subscription_expiry_ms")
    val PREF_CURRENT_PLAN_ID = stringPreferencesKey("current_plan_id")
    val PREF_SUB_STATUS = stringPreferencesKey("sub_status")
    val KEY_DARK_MODE = booleanPreferencesKey("key_dark_mode")

    val KEY_DEFAULT_URDU_FONT_ID = stringPreferencesKey("key_default_urdu_font_id")
    val KEY_DEFAULT_URDU_FONT_NAME = stringPreferencesKey("key_default_urdu_font_name")
    val KEY_HAPTIC_FEEDBACK = booleanPreferencesKey("key_haptic_feedback")
    val KEY_SMART_SNAPPING = booleanPreferencesKey("key_smart_snapping")
    val KEY_AUTO_SAVE_INTERVAL = stringPreferencesKey("key_auto_save_interval")
}