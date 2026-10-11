package x.vladgba.strips.ui

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import x.vladgba.strips.R
import x.vladgba.strips.data.Entry
import x.vladgba.strips.data.History
import x.vladgba.strips.engine.CodeRenderer
import x.vladgba.strips.engine.Formats

class GeneratePage(private val activity: Activity, val root: View) {
    private val prefs = activity.getSharedPreferences("settings", Activity.MODE_PRIVATE)
    private val formatSpinner: Spinner = root.findViewById(R.id.genFormat)
    private val ecSpinner: Spinner = root.findViewById(R.id.genEc)
    private val ecBox: View = root.findViewById(R.id.genEcBox)
    private val input: EditText = root.findViewById(R.id.genInput)
    private val error: TextView = root.findViewById(R.id.genError)
    private val preview: ImageView = root.findViewById(R.id.genPreview)
    private val shareBtn: View = root.findViewById(R.id.genShare)
    private val saveBtn: View = root.findViewById(R.id.genSave)

    private val formats = Formats.writable
    private val ecLevels = listOf("L", "M", "Q", "H")
    private val handler = Handler(Looper.getMainLooper())
    private var bitmap: Bitmap? = null
    private val renderTask = Runnable { render() }

    init {
        formatSpinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, formats.map(Formats::label)).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        ecSpinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item,
            listOf("L · 7%", "M · 15%", "Q · 25%", "H · 30%")).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        formatSpinner.setSelection(formats.indexOf(prefs.getString("gen_format", "QR_CODE")).coerceAtLeast(0))
        ecSpinner.setSelection(ecLevels.indexOf(prefs.getString("gen_ec", "M")).coerceAtLeast(0))

        val onSelect = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                prefs.edit().putString("gen_format", format()).putString("gen_ec", ec()).apply()
                updateHint()
                schedule(0)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        formatSpinner.onItemSelectedListener = onSelect
        ecSpinner.onItemSelectedListener = onSelect
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = schedule(200)
        })
        shareBtn.setOnClickListener { bitmap?.let { Actions.shareImage(activity, it) } }
        saveBtn.setOnClickListener {
            if (bitmap != null) {
                History.get(activity).add(format(), input.text.toString(), Entry.GENERATED, null)
                Actions.toast(activity, R.string.saved)
            }
        }
        updateHint()
        render()
    }

    fun show() {}

    private fun format() = formats[formatSpinner.selectedItemPosition.coerceAtLeast(0)]
    private fun ec() = ecLevels[ecSpinner.selectedItemPosition.coerceAtLeast(0)]

    private fun updateHint() {
        ecBox.visibility = if (format() == "QR_CODE") View.VISIBLE else View.GONE
        input.hint = Formats.inputHint(format()) ?: activity.getString(R.string.gen_placeholder)
    }

    private fun schedule(delay: Long) {
        handler.removeCallbacks(renderTask)
        handler.postDelayed(renderTask, delay)
    }

    private fun render() {
        val text = input.text.toString()
        if (text.isEmpty()) {
            setBitmap(null); error.visibility = View.GONE
            return
        }
        try {
            setBitmap(CodeRenderer.render(format(), text, ec()))
            error.visibility = View.GONE
        } catch (e: CodeRenderer.Error) {
            setBitmap(null)
            error.text = e.message
            error.visibility = View.VISIBLE
        }
    }

    private fun setBitmap(b: Bitmap?) {
        bitmap = b
        preview.setImageBitmap(b)
        preview.visibility = if (b != null) View.VISIBLE else View.INVISIBLE
        shareBtn.isEnabled = b != null
        saveBtn.isEnabled = b != null
        shareBtn.alpha = if (b != null) 1f else 0.4f
        saveBtn.alpha = shareBtn.alpha
    }
}
