package com.athena.j.athena

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject

class VaultAdapter(
    private var entries: List<JSONObject>,
    private val onItemClick: (JSONObject, Int) -> Unit
) : RecyclerView.Adapter<VaultAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val hostname: TextView = view.findViewById(R.id.hostname)
        val username: TextView = view.findViewById(R.id.username)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vault_entry, parent, false)
        return ViewHolder(view)
    }
    override fun getItemCount(): Int {
        return entries.size
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        val realIndex = entry.optInt("realIndex", position)
        val hostname = entry.optString("hostname", "Unknown")
        val username = entry.optString("username", "")
        holder.hostname.text = hostname
        holder.username.text = username
        holder.itemView.setOnClickListener {
            onItemClick(entry, realIndex)
        }
    }

    fun updateData(newEntries: JSONArray) {
        val list = mutableListOf<JSONObject>()
        for (i in 0 until newEntries.length()) {
            val entry = newEntries.optJSONObject(i) ?: continue
            if (!entry.has("realIndex")) {
                entry.put("realIndex", i)
            }
            list.add(entry)
        }
        entries = list
        notifyDataSetChanged()
    }
}