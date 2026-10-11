package x.vladgba.strips.ui

import android.app.Activity
import android.app.AlertDialog
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import x.vladgba.strips.CodeActivity
import x.vladgba.strips.R
import x.vladgba.strips.data.Entry
import x.vladgba.strips.data.History
import x.vladgba.strips.engine.Formats

class LastPage(private val activity: Activity, val root: View) {
    private val list: ListView = root.findViewById(R.id.lastList)
    private val empty: View = root.findViewById(R.id.lastEmpty)
    private val bar: View = root.findViewById(R.id.lastBar)
    private val count: TextView = root.findViewById(R.id.lastCount)
    private val history = History.get(activity)
    private var items: List<Entry> = emptyList()

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: LayoutInflater.from(activity).inflate(R.layout.item_last, parent, false)
            val e = items[position]
            v.findViewById<TextView>(R.id.itemFormat).text = Formats.label(e.format)
            v.findViewById<TextView>(R.id.itemSource).text = activity.getString(
                if (e.source == Entry.GENERATED) R.string.src_generated else R.string.src_scanned
            ) + (e.engine?.let { " · $it" } ?: "")
            v.findViewById<TextView>(R.id.itemTime).text = DateUtils.getRelativeTimeSpanString(
                e.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
            )
            v.findViewById<TextView>(R.id.itemText).text = e.text
            return v
        }
    }

    init {
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> CodeActivity.open(activity, items[pos].id) }
        list.setOnItemLongClickListener { _, view, pos, _ ->
            val e = items[pos]
            PopupMenu(activity, view).apply {
                menu.add(0, 1, 0, R.string.copy)
                menu.add(0, 2, 1, R.string.share)
                menu.add(0, 3, 2, R.string.delete)
                setOnMenuItemClickListener {
                    when (it.itemId) {
                        1 -> Actions.copy(activity, e.text)
                        2 -> Actions.shareText(activity, e.text)
                        3 -> { history.delete(e.id); reload() }
                    }
                    true
                }
            }.show()
            true
        }
        root.findViewById<View>(R.id.btnClear).setOnClickListener {
            AlertDialog.Builder(activity)
                .setMessage(R.string.clear_confirm)
                .setPositiveButton(R.string.clear) { _, _ -> history.clear(); reload() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    fun show() = reload()

    private fun reload() {
        items = history.all()
        adapter.notifyDataSetChanged()
        val has = items.isNotEmpty()
        list.visibility = if (has) View.VISIBLE else View.GONE
        bar.visibility = if (has) View.VISIBLE else View.GONE
        empty.visibility = if (has) View.GONE else View.VISIBLE
        count.text = activity.getString(R.string.last_count, items.size)
    }
}
