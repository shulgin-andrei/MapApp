package com.andrey.mapapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import com.andrey.mapapp.data.local.AppDataBase
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class MarkerBottomSheet : BottomSheetDialogFragment() {

    private var markerId: Int? = null
    private var defaultLat: Double = 0.0
    private var defaultLon: Double = 0.0

    // for getting info out of db
    private lateinit var db: AppDataBase

    // call-backs for Activity
    var onSave: ((String, String, String, Double, Double) -> Unit)? = null
    var onDelete: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        markerId = arguments?.getInt("ARG_ID").takeIf { it != -1 }
        defaultLat = arguments?.getDouble("ARG_LAT") ?: 0.0
        defaultLon = arguments?.getDouble("ARG_LON") ?: 0.0


        db = AppDataBase.createDataBase(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.marker_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titleEdit = view.findViewById<EditText>(R.id.title_edit)
        val descEdit = view.findViewById<EditText>(R.id.description_edit)
        val codeEdit = view.findViewById<EditText>(R.id.code_edit)
        val latEdit = view.findViewById<EditText>(R.id.latitude_edit)
        val lonEdit = view.findViewById<EditText>(R.id.longitude_edit)
        val saveBtn = view.findViewById<Button>(R.id.save_button)
        val delBtn = view.findViewById<Button>(R.id.delete_button)

        latEdit.setText(defaultLat.toString())
        lonEdit.setText(defaultLon.toString())

        // if edit
        if (markerId != null) {
            lifecycleScope.launch {
                val entity = db.sampleDao().findById(markerId!!)
                entity?.let {
                    titleEdit.setText(it.title)
                    descEdit.setText(it.description)
                    codeEdit.setText(it.code)
                    latEdit.setText(it.lat.toString())
                    lonEdit.setText(it.lon.toString())
                }
            }
        } else {
            // if add
            delBtn.visibility = View.GONE
        }

        // save
        saveBtn.setOnClickListener {
            val title = titleEdit.text.toString()
            val desc = descEdit.text.toString()
            val code = codeEdit.text.toString()
            val finalLat = latEdit.text.toString().replace(',', '.').toDoubleOrNull() ?: defaultLat
            val finalLon = lonEdit.text.toString().replace(',', '.').toDoubleOrNull() ?: defaultLon

            onSave?.invoke(title, desc, code, finalLat, finalLon)
            dismiss()
        }

        // delete
        delBtn.setOnClickListener {
            onDelete?.invoke()
            dismiss()
        }
    }

    // start initialization
    companion object {
        fun newInstance(id: Int?, lat: Double, lon: Double): MarkerBottomSheet {
            val fragment = MarkerBottomSheet()
            val args = Bundle()
            args.putInt("ARG_ID", id ?: -1)
            args.putDouble("ARG_LAT", lat)
            args.putDouble("ARG_LON", lon)
            fragment.arguments = args
            return fragment
        }
    }
}