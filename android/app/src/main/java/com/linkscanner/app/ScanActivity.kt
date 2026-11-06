package com.linkscanner.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Recibe cada link que el usuario toca en otra app, lo analiza y deja decidir si abrirlo.
 * Nunca abre un link por su cuenta, ni siquiera si el backend no responde.
 */
class ScanActivity : AppCompatActivity() {

    private val backend = BackendClient(BuildConfig.BACKEND_URL)

    private lateinit var link: Uri
    private lateinit var verdict: TextView
    private lateinit var verdictText: TextView
    private lateinit var host: TextView
    private lateinit var score: TextView
    private lateinit var findings: TextView
    private lateinit var progress: View
    private lateinit var scanStatus: TextView
    private lateinit var deepScan: Button
    private lateinit var deepScanHint: View
    private lateinit var retry: Button
    private lateinit var open: Button

    private var scanJob: Job? = null
    private var lastAnalysis: Analysis? = null
    private var deepScanRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent.data
        if (!isWebLink(data)) {
            finish()
            return
        }

        setContentView(R.layout.activity_scan)
        verdict = findViewById(R.id.verdict)
        verdictText = findViewById(R.id.verdict_text)
        host = findViewById(R.id.host)
        score = findViewById(R.id.score)
        findings = findViewById(R.id.findings)
        progress = findViewById(R.id.progress)
        scanStatus = findViewById(R.id.scan_status)
        deepScan = findViewById(R.id.deep_scan)
        deepScanHint = findViewById(R.id.deep_scan_hint)
        retry = findViewById(R.id.retry)
        open = findViewById(R.id.open)

        findViewById<Button>(R.id.close).setOnClickListener { finish() }
        open.setOnClickListener { onOpenClicked() }
        deepScan.setOnClickListener { analyze(deepScan = true) }
        retry.setOnClickListener { analyze(deepScanRequested) }

        startWith(data!!)
    }

    /** Hay una sola pantalla: un link nuevo reemplaza al anterior en vez de apilarse encima. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val data = intent.data
        if (isWebLink(data)) {
            setIntent(intent)
            startWith(data!!)
        }
    }

    private fun isWebLink(uri: Uri?) = uri?.scheme?.lowercase() in setOf("http", "https")

    private fun startWith(uri: Uri) {
        link = uri
        findViewById<TextView>(R.id.url).text = uri.toString()
        analyze(deepScan = false)
    }

    private fun analyze(deepScan: Boolean) {
        scanJob?.cancel()
        deepScanRequested = deepScan
        scanJob = lifecycleScope.launch {
            showLoading()
            try {
                followScan(backend.analyze(link.toString(), deepScan))
            } catch (ex: BackendException) {
                showFailure(ex)
            }
        }
    }

    /** Muestra el resultado y sigue consultando mientras los servicios externos no terminen. */
    private suspend fun followScan(first: Analysis) {
        val deadline = SystemClock.elapsedRealtime() + POLL_TIMEOUT_MS
        var current = first
        render(current)
        while (current.contentScan == PENDING) {
            val scanId = current.scanId ?: break
            delay(POLL_INTERVAL_MS)
            if (SystemClock.elapsedRealtime() > deadline) {
                showScanInterrupted()
                return
            }
            current = backend.poll(scanId)
            render(current)
        }
    }

    private fun showLoading() {
        lastAnalysis = null
        verdict.setText(R.string.analyzing)
        verdict.setTextColor(getColor(R.color.verdict_unknown))
        verdictText.text = ""
        host.text = link.host.orEmpty()
        score.text = ""
        findings.text = ""
        scanStatus.text = ""
        progress.visibility = View.VISIBLE
        setVisible(deepScan, false)
        setVisible(deepScanHint, false)
        setVisible(retry, false)
        open.setText(R.string.open)
    }

    // El host y los mensajes pueden traer texto armado por un atacante: solo se muestran como texto plano.
    private fun render(analysis: Analysis) {
        lastAnalysis = analysis
        val (label, text, color) = when (analysis.verdict) {
            "SAFE" -> Triple(R.string.verdict_safe, R.string.verdict_safe_text, R.color.verdict_safe)
            "SUSPICIOUS" -> Triple(R.string.verdict_suspicious, R.string.verdict_suspicious_text, R.color.verdict_suspicious)
            "DANGEROUS" -> Triple(R.string.verdict_dangerous, R.string.verdict_dangerous_text, R.color.verdict_dangerous)
            else -> Triple(R.string.verdict_unknown, R.string.verdict_unknown_text, R.color.verdict_unknown)
        }
        verdict.setText(label)
        verdict.setTextColor(getColor(color))
        verdictText.setText(text)
        host.text = analysis.host
        score.text = getString(R.string.score, analysis.score)
        findings.text = analysis.findings.joinToString("\n") { getString(R.string.finding_item, it) }

        val pending = analysis.contentScan == PENDING
        progress.visibility = if (pending) View.VISIBLE else View.GONE
        scanStatus.setText(
            when (analysis.contentScan) {
                PENDING -> R.string.scan_pending
                "COMPLETED" -> R.string.scan_completed
                "UNAVAILABLE" -> R.string.scan_unavailable
                else -> if (deepScanRequested) R.string.scan_skipped_requested else R.string.scan_skipped
            },
        )

        val canDeepScan = analysis.verdict == "UNKNOWN" && analysis.contentScan == "SKIPPED" && !deepScanRequested
        setVisible(deepScan, canDeepScan)
        setVisible(deepScanHint, canDeepScan)
        setVisible(retry, false)
        open.setText(if (analysis.verdict == "SAFE" || analysis.verdict == "UNKNOWN") R.string.open else R.string.open_anyway)
    }

    private fun showFailure(ex: BackendException) {
        progress.visibility = View.GONE
        setVisible(retry, true)
        if (lastAnalysis != null) {
            showScanInterrupted()
            return
        }
        verdict.setText(R.string.verdict_error)
        verdict.setTextColor(getColor(R.color.verdict_unknown))
        verdictText.text = ex.detail ?: getString(R.string.verdict_error_text)
        open.setText(R.string.open_anyway)
    }

    private fun showScanInterrupted() {
        progress.visibility = View.GONE
        scanStatus.setText(R.string.scan_interrupted)
    }

    private fun onOpenClicked() {
        if (lastAnalysis?.verdict == "DANGEROUS") {
            AlertDialog.Builder(this)
                .setTitle(R.string.confirm_title)
                .setMessage(R.string.confirm_text)
                .setPositiveButton(R.string.open_anyway) { _, _ -> openInBrowser() }
                .setNegativeButton(R.string.do_not_open, null)
                .show()
        } else {
            openInBrowser()
        }
    }

    private fun openInBrowser() {
        val browsers = installedBrowsers()
        when (browsers.size) {
            0 -> Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show()
            1 -> openIn(browsers.single().activityInfo.packageName)
            else -> AlertDialog.Builder(this)
                .setTitle(R.string.open_with)
                .setItems(browsers.map { it.loadLabel(packageManager) }.toTypedArray()) { _, which ->
                    openIn(browsers[which].activityInfo.packageName)
                }
                .show()
        }
    }

    /** Sin esta app: como navegador predeterminado, el link volvería acá para siempre. */
    private fun installedBrowsers(): List<ResolveInfo> {
        val anyWebLink = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return packageManager.queryIntentActivities(anyWebLink, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
    }

    private fun openIn(browserPackage: String) {
        val view = Intent(Intent.ACTION_VIEW, link)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(browserPackage)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(view)
            finish()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show()
        }
    }

    private fun setVisible(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private companion object {
        const val PENDING = "PENDING"
        const val POLL_INTERVAL_MS = 3_000L

        // Un poco más que el máximo de espera del backend (3 minutos).
        const val POLL_TIMEOUT_MS = 210_000L
    }
}
