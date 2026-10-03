package com.voidlinux.feature.tor

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.voidlinux.core.designsystem.Components
import com.voidlinux.feature.tor.databinding.FragmentTorBinding
import kotlinx.coroutines.launch

class TorFragment : Fragment() {

    private var _binding: FragmentTorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TorViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.startTor.setOnClickListener { viewModel.startTor() }
        binding.stopTor.setOnClickListener { viewModel.stopTor() }
        binding.installOrbot.setOnClickListener { viewModel.installOrbot() }
        binding.openBrowser.setOnClickListener {
            startActivity(Intent(requireContext(), OnionBrowserActivity::class.java))
        }
        binding.confirmTor.setOnClickListener { viewModel.confirmTorRunning() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.torStatus.text = state.statusMessage
                binding.startTor.isEnabled = state.orbotInstalled && !state.torRunning
                binding.stopTor.isEnabled = state.torRunning
                binding.installOrbot.visibility =
                    if (state.orbotInstalled) View.GONE else View.VISIBLE
                binding.confirmTor.visibility =
                    if (state.orbotInstalled && !state.torRunning) View.VISIBLE else View.GONE
                binding.openBrowser.isEnabled = state.torRunning

                state.errorMessage?.let { msg ->
                    Components.showSnackLong(binding.root, msg)
                    viewModel.clearError()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}