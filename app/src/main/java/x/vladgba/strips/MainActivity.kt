package x.vladgba.strips

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import x.vladgba.strips.scanner.HoneywellScanner
import x.vladgba.strips.ui.Actions
import x.vladgba.strips.ui.GeneratePage
import x.vladgba.strips.ui.LastPage
import x.vladgba.strips.ui.ScanPage

class MainActivity : Activity() {
    private lateinit var scan: ScanPage
    private lateinit var last: LastPage
    private lateinit var generate: GeneratePage
    private lateinit var hwScanner: HoneywellScanner
    private lateinit var tabs: List<View>
    private lateinit var pages: List<View>
    private var current = TAB_SCAN
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Actions.applyInsets(this, findViewById(R.id.root))

        scan = ScanPage(this, findViewById(R.id.pageScan))
        last = LastPage(this, findViewById(R.id.pageLast))
        generate = GeneratePage(this, findViewById(R.id.pageGenerate))
        pages = listOf(scan.root, last.root, generate.root)
        tabs = listOf(findViewById(R.id.tabScan), findViewById(R.id.tabLast), findViewById(R.id.tabGenerate))
        tabs.forEachIndexed { i, t -> t.setOnClickListener { select(i) } }

        current = savedInstanceState?.getInt("tab", TAB_SCAN)
            ?: getSharedPreferences("settings", MODE_PRIVATE).getInt("tab", TAB_SCAN)
        select(current, force = true)

        // Hardware trigger works from any tab: it switches to Scan and shows the result there.
        hwScanner = HoneywellScanner(this) { result ->
            select(TAB_SCAN)
            scan.onExternalScan(result)
        }
        hwScanner.open()
    }

    private fun select(index: Int, force: Boolean = false) {
        if (index == current && !force) return
        if (current == TAB_SCAN && index != TAB_SCAN) scan.hide()
        if (current == TAB_GENERATE && index != TAB_GENERATE) {
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(window.decorView.windowToken, 0)
        }
        current = index
        getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("tab", index).apply()
        tabs.forEachIndexed { i, t -> t.isSelected = i == index }
        pages.forEachIndexed { i, p -> p.visibility = if (i == index) View.VISIBLE else View.GONE }
        when (index) {
            TAB_SCAN -> if (resumed) scan.show()
            TAB_LAST -> last.show()
            TAB_GENERATE -> generate.show()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        hwScanner.resume()
        when (current) {
            TAB_SCAN -> scan.show()
            TAB_LAST -> last.show()
        }
    }

    override fun onPause() {
        resumed = false
        hwScanner.pause()
        scan.hide()
        super.onPause()
    }

    override fun onDestroy() {
        hwScanner.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", current)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == ScanPage.REQ_CAMERA) {
            scan.onPermissionResult(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
        }
    }

    @Deprecated("Framework Activity API; fine without androidx")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == ScanPage.REQ_IMAGE && resultCode == RESULT_OK) data?.data?.let(scan::onImagePicked)
    }

    companion object {
        const val TAB_SCAN = 0
        const val TAB_LAST = 1
        const val TAB_GENERATE = 2
    }
}
