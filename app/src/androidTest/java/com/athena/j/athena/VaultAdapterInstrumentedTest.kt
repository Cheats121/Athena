package com.athena.j.athena

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultAdapterInstrumentedTest {

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setup() {
        VaultRuntimeSession.clear()

        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
    }

    @After
    fun cleanup() {
        try {
            scenario?.close()
        } catch (_: Exception) {}

        scenario = null
        VaultRuntimeSession.clear()
    }

    private fun runOnActivity(action: (MainActivity) -> Unit) {
        val activeScenario = scenario
            ?: throw IllegalStateException("ActivityScenario is not initialized")

        activeScenario.onActivity { activity ->
            action(activity)
        }
    }

    private fun parent(activity: MainActivity): ViewGroup {
        return FrameLayout(activity)
    }

    private fun entry(
        hostname: String,
        username: String,
        realIndex: Int? = null
    ): JSONObject {
        return JSONObject().apply {
            put("type", "password")
            put("hostname", hostname)
            put("username", username)

            if (realIndex != null) {
                put("realIndex", realIndex)
            }
        }
    }

    @Test
    fun adapter_itemCount_matchesInput() {
        val input = JSONArray().apply {
            put(
                entry(
                    hostname = "Amazon",
                    username = "amazon@example.com"
                )
            )

            put(
                entry(
                    hostname = "GitHub",
                    username = "github@example.com"
                )
            )

            put(
                entry(
                    hostname = "Steam",
                    username = "steam@example.com"
                )
            )
        }

        val adapter = VaultAdapter(emptyList()) { _, _ -> }

        adapter.updateData(input)

        assertEquals(3, adapter.itemCount)
    }

    @Test
    fun adapter_withoutExistingRealIndex_assignsVisibleIndex() {
        var clickedIndex = -1

        val input = JSONArray().apply {
            put(
                entry(
                    hostname = "Amazon",
                    username = "amazon@example.com"
                )
            )

            put(
                entry(
                    hostname = "GitHub",
                    username = "github@example.com"
                )
            )

            put(
                entry(
                    hostname = "Steam",
                    username = "steam@example.com"
                )
            )
        }

        runOnActivity { activity ->
            val adapter = VaultAdapter(emptyList()) { _, realIndex ->
                clickedIndex = realIndex
            }

            adapter.updateData(input)

            val holder = adapter.onCreateViewHolder(
                parent(activity),
                0
            )

            adapter.onBindViewHolder(holder, 2)

            holder.itemView.performClick()
        }

        assertEquals(
            "Unfiltered third entry should use vault index 2",
            2,
            clickedIndex
        )
    }

    @Test
    fun filteredEntry_preservesOriginalVaultIndex() {
        var clickedIndex = -1
        var clickedHostname = ""

        val filtered = JSONArray().apply {
            put(
                entry(
                    hostname = "Steam",
                    username = "steam@example.com",
                    realIndex = 2
                )
            )
        }

        runOnActivity { activity ->
            val adapter = VaultAdapter(emptyList()) { clickedEntry, realIndex ->
                clickedIndex = realIndex
                clickedHostname = clickedEntry.optString("hostname")
            }

            adapter.updateData(filtered)

            val holder = adapter.onCreateViewHolder(
                parent(activity),
                0
            )

            adapter.onBindViewHolder(holder, 0)

            holder.itemView.performClick()
        }

        assertEquals(
            "Steam",
            clickedHostname
        )

        assertEquals(
            "Filtered entry must retain original encrypted-vault index",
            2,
            clickedIndex
        )
    }

    @Test
    fun firstVisibleEntry_canHaveNonZeroRealIndex() {
        var clickedIndex = -1

        val filtered = JSONArray().apply {
            put(
                entry(
                    hostname = "Target",
                    username = "target@example.com",
                    realIndex = 7
                )
            )
        }

        runOnActivity { activity ->
            val adapter = VaultAdapter(emptyList()) { _, realIndex ->
                clickedIndex = realIndex
            }

            adapter.updateData(filtered)

            val holder = adapter.onCreateViewHolder(
                parent(activity),
                0
            )

            adapter.onBindViewHolder(holder, 0)

            holder.itemView.performClick()
        }

        assertEquals(
            "Visible position 0 must not replace original vault index",
            7,
            clickedIndex
        )
    }

    @Test
    fun multipleFilteredEntries_preserveOriginalIndices() {
        val clickedIndices = mutableListOf<Int>()

        val filtered = JSONArray().apply {
            put(
                entry(
                    hostname = "GitHub",
                    username = "one@example.com",
                    realIndex = 1
                )
            )

            put(
                entry(
                    hostname = "GitLab",
                    username = "two@example.com",
                    realIndex = 5
                )
            )

            put(
                entry(
                    hostname = "GitTea",
                    username = "three@example.com",
                    realIndex = 9
                )
            )
        }

        runOnActivity { activity ->
            val adapter = VaultAdapter(emptyList()) { _, realIndex ->
                clickedIndices.add(realIndex)
            }

            adapter.updateData(filtered)

            for (position in 0 until adapter.itemCount) {
                val holder = adapter.onCreateViewHolder(
                    parent(activity),
                    0
                )

                adapter.onBindViewHolder(holder, position)

                holder.itemView.performClick()
            }
        }

        assertEquals(
            listOf(1, 5, 9),
            clickedIndices
        )
    }

    @Test
    fun bind_displaysCorrectHostnameAndUsername() {
        var displayedHostname = ""
        var displayedUsername = ""

        val input = JSONArray().apply {
            put(
                entry(
                    hostname = "GitHub",
                    username = "test@example.com",
                    realIndex = 4
                )
            )
        }

        runOnActivity { activity ->
            val adapter = VaultAdapter(emptyList()) { _, _ -> }

            adapter.updateData(input)

            val holder = adapter.onCreateViewHolder(
                parent(activity),
                0
            )

            adapter.onBindViewHolder(holder, 0)

            displayedHostname = holder.hostname.text.toString()
            displayedUsername = holder.username.text.toString()
        }

        assertEquals(
            "GitHub",
            displayedHostname
        )

        assertEquals(
            "test@example.com",
            displayedUsername
        )
    }

    @Test
    fun emptyInput_resultsInZeroItems() {
        val adapter = VaultAdapter(emptyList()) { _, _ -> }

        adapter.updateData(JSONArray())

        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun updateData_replacesPreviousEntries() {
        val first = JSONArray().apply {
            put(
                entry(
                    hostname = "Amazon",
                    username = "a@example.com"
                )
            )

            put(
                entry(
                    hostname = "GitHub",
                    username = "g@example.com"
                )
            )
        }

        val second = JSONArray().apply {
            put(
                entry(
                    hostname = "Steam",
                    username = "s@example.com",
                    realIndex = 7
                )
            )
        }

        val adapter = VaultAdapter(emptyList()) { _, _ -> }

        adapter.updateData(first)

        assertEquals(2, adapter.itemCount)

        adapter.updateData(second)

        assertEquals(
            "updateData() should replace previous contents",
            1,
            adapter.itemCount
        )
    }
}