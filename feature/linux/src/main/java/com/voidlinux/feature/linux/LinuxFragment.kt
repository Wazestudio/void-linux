package com.voidlinux.feature.linux

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.voidlinux.core.designsystem.Components
import com.voidlinux.feature.linux.databinding.FragmentLinuxBinding
import kotlinx.coroutines.launch

class LinuxFragment : Fragment() {

    private var _binding: FragmentLinuxBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LinuxViewModel by viewModels()
    private val bootstrapViewModel: LinuxBootstrapViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLinuxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val names = DistroCatalog.all.map { it.displayName }
        binding.distroSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            names
        )
        binding.distroSpinner.isEnabled = false

        binding.installButton.setOnClickListener {
            val idx = binding.distroSpinner.selectedItemPosition
            val distro = DistroCatalog.all.getOrNull(idx) ?: return@setOnClickListener
            viewModel.selectDistro(distro.id)
            viewModel.install()
        }

        binding.uninstallButton.setOnClickListener { viewModel.uninstall() }
        binding.networkToolsButton.setOnClickListener {
            viewModel.installToolCollection("network")
        }
        binding.webToolsButton.setOnClickListener {
            viewModel.installToolCollection("web")
        }
        binding.analysisToolsButton.setOnClickListener {
            viewModel.installToolCollection("analysis")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.distroSpinner.isEnabled =
                    !state.installing && !state.installingTools
                binding.statusText.text = state.statusMessage
                binding.progressBar.visibility =
                    if (state.installing) View.VISIBLE else View.GONE
                binding.progressBar.progress = state.progress

                binding.installButton.isEnabled =
                    !state.installing && !state.installingTools && !state.installed
                binding.uninstallButton.isEnabled =
                    !state.installing && !state.installingTools && state.installed
                binding.nativeWarning.visibility =
                    if (state.nativeReady) View.GONE else View.VISIBLE
                val canInstallTools = state.installed && state.nativeReady &&
                    !state.installing && !state.installingTools
                binding.networkToolsButton.isEnabled = canInstallTools
                binding.webToolsButton.isEnabled = canInstallTools
                binding.analysisToolsButton.isEnabled = canInstallTools
                binding.toolsProgress.visibility =
                    if (state.installingTools) View.VISIBLE else View.GONE
                binding.toolOutput.text = state.toolOutput
                binding.toolOutput.visibility =
                    if (state.toolOutput.isNotEmpty()) View.VISIBLE else View.GONE

                state.errorMessage?.let { msg ->
                    Components.showSnackLong(binding.root, msg)
                    viewModel.clearError()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            bootstrapViewModel.state.collect(viewModel::updateBootstrapState)
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
