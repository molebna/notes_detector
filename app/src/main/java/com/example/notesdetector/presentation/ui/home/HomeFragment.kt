package com.example.notesdetector.presentation.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notesdetector.R
import com.example.notesdetector.databinding.FragmentHomeBinding
import com.example.notesdetector.presentation.adapters.NotesAdapter

class HomeFragment : Fragment() {

    private lateinit var viewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        val recycler = view.findViewById<RecyclerView>(R.id.notesList)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = NotesAdapter(viewModel.notes)
    }
}