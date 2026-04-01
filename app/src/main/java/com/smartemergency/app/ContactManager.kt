package com.smartemergency.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Handles saving and loading emergency contacts from SharedPreferences.
 */
object ContactManager {

    private const val PREFS_NAME = "smart_emergency_prefs"
    private const val KEY_CONTACTS = "saved_contacts"

    data class Contact(val name: String, val phone: String)

    /**
     * Retrieves the list of saved contacts.
     */
    fun getContacts(context: Context): List<Contact> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_CONTACTS, null) ?: return emptyList()

        val list = mutableListOf<Contact>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    Contact(
                        name = obj.getString("name"),
                        phone = obj.getString("phone")
                    )
                )
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * Saves the list of contacts to SharedPreferences.
     */
    fun saveContacts(context: Context, contacts: List<Contact>) {
        val jsonArray = JSONArray()
        for (contact in contacts) {
            val obj = JSONObject()
            try {
                obj.put("name", contact.name)
                obj.put("phone", contact.phone)
                jsonArray.put(obj)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CONTACTS, jsonArray.toString()).apply()
    }
}
