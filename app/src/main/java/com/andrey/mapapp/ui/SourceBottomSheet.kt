package com.andrey.mapapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.andrey.mapapp.R
import com.andrey.mapapp.data.local.AppDataBase
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class SourceBottomSheet : BottomSheetDialogFragment() {

    private var sourceId: Int? = null
    private lateinit var db: AppDataBase

    // call-back
    var onDelete: ((Int) -> Unit)? = null
    var onSave: ((String, String) -> Unit)? = null
    var onWindRose: ((Int, Boolean) -> Unit)? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceId = arguments?.getInt("ARG_SOURCE_ID").takeIf { it != -1 }
        db = AppDataBase.Companion.createDataBase(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.source_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titleEdit = view.findViewById<TextView>(R.id.source_title_edit)
        val descEdit = view.findViewById<TextView>(R.id.source_description_edit)
        val saveBtn = view.findViewById<Button>(R.id.source_save_button)
        val delBtn = view.findViewById<Button>(R.id.source_delete_button)
        val windBtn = view.findViewById<Button>(R.id.source_wind_rose_button)
        val updWindBtn = view.findViewById<Button>(R.id.source_update_wind_rose_button)

        // hidden for now
        view.findViewById<View>(R.id.source_latitude_edit).visibility = View.GONE
        view.findViewById<View>(R.id.source_longitude_edit).visibility = View.GONE

        // source data

        if (sourceId != null) {
            // edit
            sourceId?.let { id ->
                lifecycleScope.launch {
                    val source = db.sourceDao().findById(id)
                    source?.let {
                        titleEdit.text = it.title
                        descEdit.text = it.description
                    }
                }
            }
        } else {
            // creating
            delBtn.visibility = View.GONE
            windBtn.visibility = View.GONE
            updWindBtn.visibility = View.GONE
        }

        saveBtn.setOnClickListener {
            val title = titleEdit.text.toString()
            val desc = descEdit.text.toString()
            onSave?.invoke(title, desc)
            dismiss()
        }

        delBtn.setOnClickListener {
            sourceId?.let { id ->
                onDelete?.invoke(id)
                dismiss()
            }
        }

        windBtn.setOnClickListener {
            sourceId?.let { id ->
                onWindRose?.invoke(id, false)
                dismiss()
            }
        }
        // updating by nulling the windDataJson of source
        updWindBtn.setOnClickListener {
            sourceId?.let { id ->
                onWindRose?.invoke(id, true)
                dismiss()
            }
        }

    }

    companion object {
        fun newInstance(id: Int?): SourceBottomSheet {
            return SourceBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt("ARG_SOURCE_ID", id ?: -1)
                }
            }
        }
    }
}