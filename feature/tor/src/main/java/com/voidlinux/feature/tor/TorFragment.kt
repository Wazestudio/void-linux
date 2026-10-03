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

                // Orbot non installé : bouton "Installer Orbot" visible
                binding.installOrbot.visibility =
                    if (state.orbotInstalled) View.GONE else View.VISIBLE

                // Orbot installé : boutons de contrôle visibles
                binding.startTor.isEnabled =
                    state.orbotInstalled && !state.torRunning && !state.starting
                binding.stopTor.isEnabled = state.torRunning

                // Confirmation manuelle après activation dans Orbot
                binding.confirmTor.visibility =
                    if (state.orbotInstalled && !state.torRunning) View.VISIBLE
                    else View.GONE

                // Navigateur .onion disponible seulement si Tor est actif
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
        // Détection automatique d'Orbot à chaque retour dans le fragment
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}