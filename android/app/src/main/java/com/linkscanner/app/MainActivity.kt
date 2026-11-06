package com.linkscanner.app

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/** Pide ser el navegador predeterminado y permite probar un link a mano. */
class MainActivity : AppCompatActivity() {

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshRoleStatus()
    }

    private lateinit var roleManager: RoleManager
    private lateinit var roleStatus: TextView
    private lateinit var setDefault: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        roleManager = getSystemService(RoleManager::class.java)
        roleStatus = findViewById(R.id.role_status)
        setDefault = findViewById(R.id.set_default)
        setDefault.setOnClickListener { requestBrowserRole() }

        findViewById<TextView>(R.id.backend_url).text = getString(R.string.backend_label, BuildConfig.BACKEND_URL)

        val testUrl = findViewById<EditText>(R.id.test_url)
        findViewById<Button>(R.id.test_button).setOnClickListener { analyze(testUrl.text.toString()) }
        testUrl.setOnEditorActionListener { _, actionId, _ ->
            val isGo = actionId == EditorInfo.IME_ACTION_GO
            if (isGo) analyze(testUrl.text.toString())
            isGo
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRoleStatus()
    }

    private fun refreshRoleStatus() {
        val isDefault = roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
        roleStatus.setText(if (isDefault) R.string.role_active else R.string.role_inactive)
        setDefault.isEnabled = !isDefault
    }

    private fun requestBrowserRole() {
        if (roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
            roleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
        } else {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    private fun analyze(input: String) {
        val text = input.trim()
        val uri = Uri.parse(if ("://" in text) text else "https://$text")
        if (text.isEmpty() || uri.scheme?.lowercase() !in setOf("http", "https")) {
            Toast.makeText(this, R.string.invalid_link, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, ScanActivity::class.java).setData(uri))
    }
}
