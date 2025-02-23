package com.mio.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.mio.databinding.model.Path
import com.mio.managers.PathManager
import com.mio.managers.PrefManager
import com.mio.ui.dialog.RenameDialog
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.databinding.ItemPathSettingBinding
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles

class PathSettingAdapter(val context: Context, private val pathList: MutableList<Path>) :
    RecyclerView.Adapter<PathSettingAdapter.ViewHolder>() {
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

    }

    var folderClickListener: ((Int) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            DataBindingUtil.inflate<ItemPathSettingBinding>(
                LayoutInflater.from(context),
                R.layout.item_path_setting,
                parent,
                false
            ).root
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        DataBindingUtil.getBinding<ItemPathSettingBinding>(holder.itemView)?.apply {
            setPath(pathList[position])
            executePendingBindings()
            radio.setOnClickListener {
                select(position)
            }
            rename.setOnClickListener {
                RenameDialog(context, context.getString(R.string.rename)).apply {
                    onConfirm = {
                        pathList[position].name.set(it)
                        PathManager.save()
                    }
                    show()
                }
            }
            delete.setOnClickListener {
                if (pathList.size == 1) return@setOnClickListener
                AlertDialog.Builder(context).apply {
                    setMessage(R.string.dialog_delete_confirm)
                    setPositiveButton(R.string.confirm) { _, _ ->
                        if (pathList.size == 1) {
                            return@setPositiveButton
                        }
                        var pos = position
                        if (pathList[position].selected.get()) {
                            pos -= 1
                            if (pos < 0) {
                                pos = 0
                            }
                            pathList.removeAt(position)
                            for ((index, path) in pathList.withIndex()) {
                                path.selected.set(index == pos)
                            }
                        } else {
                            pathList.removeAt(position)
                        }

                        PathManager.save()
                        notifyItemRemoved(position)
                        notifyItemRangeChanged(position, itemCount - position)
                    }
                    setNegativeButton(R.string.global_cancel, null)
                    show()
                }
            }
            folder.setOnClickListener {
                folderClickListener?.invoke(position)
            }
        }
    }

    override fun getItemCount(): Int {
        return pathList.size
    }

    fun add(path: Path) {
        pathList.add(path)
        notifyItemInserted(itemCount - 1)
        notifyItemRangeChanged(itemCount - 1, 1)
    }

    private fun select(position: Int) {
        for ((index, path) in pathList.withIndex()) {
            path.selected.set(index == position)
        }
        PathManager.save()
        PrefManager
            .setCurrentPath(pathList[position].path.get() ?: Tools.DIR_GAME_HOME)
        PathManager.refreshPath()
        LauncherProfiles.load(context)
    }
}