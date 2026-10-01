package com.iptvplayer.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

	private val prefs by lazy { getSharedPreferences("iptv", MODE_PRIVATE) }
	private var all: List<Channel> = emptyList()
	private var groups: List<String> = emptyList()

	private lateinit var adapter: ChannelAdapter
	private lateinit var status: TextView
	private lateinit var search: EditText
	private lateinit var urlInput: EditText
	private lateinit var groupSpinner: Spinner

	private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri != null) {
			try {
				contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
			} catch (_: Exception) {
			}
			prefs.edit().putString("source", uri.toString()).apply()
			load(uri.toString())
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		status = findViewById(R.id.status)
		search = findViewById(R.id.search)
		urlInput = findViewById(R.id.urlInput)
		groupSpinner = findViewById(R.id.groupSpinner)

		adapter = ChannelAdapter(::openChannel, ::toggleFavorite)
		findViewById<RecyclerView>(R.id.list).apply {
			layoutManager = LinearLayoutManager(this@MainActivity)
			adapter = this@MainActivity.adapter
		}

		findViewById<Button>(R.id.btnLoad).setOnClickListener { loadFromInput() }
		urlInput.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_GO) { loadFromInput(); true } else false
		}
		findViewById<Button>(R.id.btnFile).setOnClickListener { pickFile.launch(arrayOf("*/*")) }
		search.doAfterTextChanged { applyFilter() }
		groupSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = applyFilter()
			override fun onNothingSelected(parent: AdapterView<*>?) {}
		}

		prefs.getString("source", null)?.let { saved ->
			if (saved.startsWith("http")) urlInput.setText(saved)
			load(saved)
		}
	}

	private fun loadFromInput() {
		val url = urlInput.text.toString().trim()
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			Toast.makeText(this, "Digite um link começando com http:// ou https://", Toast.LENGTH_LONG).show()
			return
		}
		prefs.edit().putString("source", url).apply()
		load(url)
	}

	private fun load(source: String) {
		status.text = "Carregando lista..."
		lifecycleScope.launch {
			try {
				val text = withContext(Dispatchers.IO) { readSource(this@MainActivity, source) }
				val channels = withContext(Dispatchers.Default) { M3uParser.parse(text) }
				all = channels
				setupGroups()
				applyFilter()
				status.text = if (channels.isEmpty()) "Nenhum canal encontrado nessa lista."
				else "${channels.size} canais • toque e segure para favoritar"
			} catch (e: Exception) {
				status.text = "Erro ao carregar a lista: ${e.message}"
			}
		}
	}

	private fun setupGroups() {
		groups = listOf("Todos os canais", "★ Favoritos") + all.map { it.group }.distinct().sorted()
		groupSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, groups).also {
			it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
		}
	}

	private fun favorites(): Set<String> = prefs.getStringSet("favs", emptySet())?.toHashSet() ?: hashSetOf()

	private fun applyFilter() {
		val q = search.text.toString().trim().lowercase()
		val g = groupSpinner.selectedItemPosition
		val favs = favorites()
		val filtered = all.filter { ch ->
			val okGroup = when (g) {
				-1, 0 -> true
				1 -> ch.url in favs
				else -> groups.getOrNull(g) == ch.group
			}
			okGroup && (q.isEmpty() || ch.name.lowercase().contains(q))
		}
		adapter.submit(filtered, favs)
	}

	private fun toggleFavorite(ch: Channel) {
		val favs = favorites().toMutableSet()
		val added = favs.add(ch.url)
		if (!added) favs.remove(ch.url)
		prefs.edit().putStringSet("favs", favs).apply()
		Toast.makeText(
			this,
			if (added) "Adicionado aos favoritos" else "Removido dos favoritos",
			Toast.LENGTH_SHORT
		).show()
		applyFilter()
	}

	private fun openChannel(position: Int) {
		Playlist.current = adapter.current
		startActivity(Intent(this, PlayerActivity::class.java).putExtra("index", position))
	}
}
