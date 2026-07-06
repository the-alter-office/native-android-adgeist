package com.examplenativeandroidapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

/**
 * Simple placeholder screen used to navigate away from the Home (ads) screen,
 * so the AdView's detach/destroy behavior can be observed.
 */
class PlaceholderFragment : Fragment() {

    companion object {
        private const val ARG_TITLE = "title"

        fun newInstance(title: String): PlaceholderFragment {
            return PlaceholderFragment().apply {
                arguments = Bundle().apply { putString(ARG_TITLE, title) }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_placeholder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.screenTitle).text =
            arguments?.getString(ARG_TITLE) ?: "Screen"
    }
}
