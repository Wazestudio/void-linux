package com.voidlinux

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.voidlinux.databinding.FragmentDashboardBinding
import com.voidlinux.feature.linux.LinuxBootstrapState
import com.voidlinux.feature.linux.LinuxBootstrapViewModel
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var bootstrapViewModel: LinuxBootstrapViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bootstrapViewModel = ViewModelProvider(requireActivity())
            .get(LinuxBootstrapViewModel::class.java)
        bootstrapViewModel.ensureInitialized()

        binding.openTerminalButton.setOnClickListener {
            findNavController().navigate(R.id.terminalFragment)
        }
        binding.openToolsButton.setOnClickListener {
            findNavController().navigate(R.id.linuxFragment)
        }
        binding.openSecurityButton.setOnClickListener {
            findNavController().navigate(R.id.securityFragment)
        }
        binding.openTorButton.setOnClickListener {
            findNavController().navigate(R.id.torFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            bootstrapViewModel.state.collect(::renderBootstrapState)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::bootstrapViewModel.isInitialized) bootstrapViewModel.ensureInitialized()
    }

    private fun renderBootstrapState(state: LinuxBootstrapState) {
        val abi = BuildConfig.TARGET_ABI
        binding.architectureStatus.text = when (abi) {
            "arm64-v8a" -> "ARM 64 bits"
            "armeabi-v7a" -> "ARM 32 bits"
            else -> abi
        }
        binding.linuxStatus.text = when {
            state.ready -> "ENVIRONNEMENT PRÊT"
            state.initializing -> "PRÉPARATION DE L'ENVIRONNEMENT ${state.progress}%"
            state.suppressed -> "ENVIRONNEMENT DÉSINSTALLÉ"
            else -> "INITIALISATION EN ATTENTE"
        }
        binding.rootfsProgress.visibility =
            if (state.initializing) View.VISIBLE else View.GONE
        binding.rootfsProgress.progress = state.progress
        binding.bootstrapMessage.text = state.errorMessage
            ?: if (state.suppressed) {
                "Ouvre le terminal pour réinitialiser Kali ou passe par l'onglet Outils."
            } else {
                "Le rootfs Kali inclus est extrait dans le stockage privé de l'application."
            }
        binding.bootstrapMessage.visibility =
            if (state.errorMessage != null || state.initializing || state.suppressed) {
                View.VISIBLE
            } else {
                View.GONE
            }
        binding.linuxStatus.setTextColor(
            requireContext().getColor(
                if (state.ready) R.color.status_ok else R.color.void_warning
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
