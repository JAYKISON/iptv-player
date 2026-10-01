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
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

	private val prefs by lazy { getSharedPreferences("iptv", MODE_PRIVATE) }
	private var all: List<Channel> = emptyList()
	private var groups: List<String> = emptyList()

	private lateinit var adapter: ChannelAdapter
	private lateinit var status: TextView
	private lateinit var search: EditText
	private lateinit var urlInput: EditText
	private lateinit var groupSpinner: Spinner
	private lateinit var serverInput: EditText
	private lateinit var userInput: EditText
	private lateinit var passInput: EditText
	private lateinit var sourcePanel: LinearLayout
	private lateinit var btnToggle: Button

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
		serverInput = findViewById(R.id.serverInput)
		userInput = findViewById(R.id.userInput)
		passInput = findViewById(R.id.passInput)
		sourcePanel = findViewById(R.id.sourcePanel)
		btnToggle = findViewById(R.id.btnToggle)

		serverInput.setText(prefs.getString("server", ""))
		userInput.setText(prefs.getString("user", ""))
		passInput.setText(prefs.getString("pass", ""))
		findViewById<Button>(R.id.btnLogin).setOnClickListener { loginFromInput() }
		passInput.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_GO) { loginFromInput(); true } else false
		}
		btnToggle.setOnClickListener {
			sourcePanel.visibility = if (sourcePanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
		}

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
			if (saved.startsWith("http") && prefs.getString("mode", "") != "login") urlInput.setText(saved)
			load(saved)
		}
	}

	private fun loadFromInput() {
		val url = urlInput.text.toString().trim()
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			Toast.makeText(this, "Digite um link começando com http:// ou https://", Toast.LENGTH_LONG).show()
			return
		}
		prefs.edit().putString("source", url).putString("mode", "m3u").apply()
		load(url)
	}

	/** Login estilo Xtream: monta o link da lista a partir de servidor, usuário e senha. */
	private fun loginFromInput() {
		var server = serverInput.text.toString().trim().trimEnd('/')
		val user = userInput.text.toString().trim()
		val pass = passInput.text.toString().trim()
		if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
			Toast.makeText(this, "Preencha o link do servidor, o usuário e a senha", Toast.LENGTH_LONG).show()
			return
		}
		if (!server.startsWith("http://") && !server.startsWith("https://")) server = "http://$server"
		val url = "$server/get.php?username=${URLEncoder.encode(user, "UTF-8")}" +
			"&password=${URLEncoder.encode(pass, "UTF-8")}&type=m3u_plus&output=ts"
		prefs.edit()
			.putString("server", server).putString("user", user).putString("pass", pass)
			.putString("source", url).putString("mode", "login")
			.apply()
		load(url)
	}

	private fun load(source: String) {
		status.text = "Conectando e carregando canais..."
		lifecycleScope.launch {
			try {
				val text = withContext(Dispatchers.IO) { readSource(this@MainActivity, source) }
				val channels = withContext(Dispatchers.Default) { M3uParser.parse(text) }
				all = channels
				setupGroups()
				applyFilter()
				if (channels.isNotEmpty()) {
					sourcePanel.visibility = View.GONE
					btnToggle.visibility = View.VISIBLE
				}
				status.text = if (channels.isEmpty()) "Nenhum canal encontrado nessa lista."
				else "${channels.size} canais • toque e segure para favoritar"
			} catch (e: Exception) {
				val msg = e.message ?: ""
				status.text = if (msg.contains("401") || msg.contains("403")) "Login recusado: confira usuário e senha."
				else "Erro ao carregar a lista: $msg"
				sourcePanel.visibility = View.VISIBLE
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
