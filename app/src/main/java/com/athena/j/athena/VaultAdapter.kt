package com.athena.j.athena

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject

/**
 * Displays password entries in the vault.
 *
 * Each displayed JSONObject may contain:
 *
 * "realIndex"
 *
 * This maps the filtered/displayed entry back to its true
 * index inside the encrypted vault JSONArray.
 */
class VaultAdapter(
    private var entries: List<JSONObject>,
    private val onItemClick: (JSONObject, Int) -> Unit
) : RecyclerView.Adapter<VaultAdapter.ViewHolder>() {

    // =============================================================
    // VIEW HOLDER
    // =============================================================

    class ViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {

        val hostname: TextView =
            view.findViewById(
                R.id.hostname
            )

        val username: TextView =
            view.findViewById(
                R.id.username
            )
    }

    // =============================================================
    // CREATE VIEW HOLDER
    // =============================================================

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(
                    R.layout.item_vault_entry,
                    parent,
                    false
                )

        return ViewHolder(
            view
        )
    }

    // =============================================================
    // ITEM COUNT
    // =============================================================

    override fun getItemCount(): Int {
        return entries.size
    }

    // =============================================================
    // BIND
    // =============================================================

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        val entry =
            entries[position]

        val realIndex =
            entry.optInt(
                "realIndex",
                position
            )

        val hostname =
            entry.optString(
                "hostname",
                "Unknown"
            )

        val username =
            entry.optString(
                "username",
                ""
            )

        holder.hostname.text =
            hostname

        holder.username.text =
            username

        holder.itemView
            .setOnClickListener {

                onItemClick(
                    entry,
                    realIndex
                )
            }
    }

    // =============================================================
    // UPDATE DATA
    // =============================================================

    /**
     * Replaces the adapter contents with data from a vault array.
     *
     * Each item receives its real vault array index.
     */
    fun updateData(
        newEntries: JSONArray
    ) {

        val list =
            mutableListOf<JSONObject>()

        for (
        i in 0 until newEntries.length()
        ) {

            val entry =
                newEntries.optJSONObject(
                    i
                )
                    ?: continue

            /*
             * Preserve a realIndex already assigned by VaultActivity.
             *
             * This is critical for filtered/search results because the
             * visible position may differ from the credential's true
             * index inside the encrypted vault JSONArray.
             */
            if (
                !entry.has(
                    "realIndex"
                )
            ) {

                entry.put(
                    "realIndex",
                    i
                )
            }

            list.add(
                entry
            )
        }

        entries =
            list

        notifyDataSetChanged()
    }
}