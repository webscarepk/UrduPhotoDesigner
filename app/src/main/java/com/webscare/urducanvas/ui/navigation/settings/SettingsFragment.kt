package com.webscare.urducanvas.ui.navigation.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.webscare.urducanvas.MainActivity
import com.webscare.urducanvas.di.BillingManager
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var billingManager: BillingManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeSubscription()
        setEvents()
        setVersionInfo()
        (activity as? MainActivity)?.bindScrollToNav(binding.settingsScroll)
    }

    private fun setVersionInfo() {
        val versionName = try {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
        } catch (e: Exception) {
            "—"
        }
        binding.versionInfo.text = "Version $versionName"
    }

    private fun observeSubscription() {
        viewLifecycleOwner.lifecycleScope.launch {
            billingManager.isSubscribed.collect { subscribed ->
                if (subscribed) {
                    binding.subscriptionCard.visibility = View.GONE
                    binding.currentPlanCard.visibility = View.VISIBLE
                } else {
                    binding.subscriptionCard.visibility = View.VISIBLE
                    binding.currentPlanCard.visibility = View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            billingManager.activePlan.collect { productId ->
                binding.manageCardTitle.text = when (productId) {
                    "urducanvas_monthly" -> "Monthly"
                    "urducanvas_6months" -> "6 Months"
                    "urducanvas_yearly" -> "Yearly"
                    else -> "Pro"
                }
            }
        }
    }

    private fun setEvents() {

        binding.upgradeNow.addPressEffect {
            view?.post { findNavController().navigate(R.id.subscriptionsFragment) }
        }

        // Subscribed users land on the Manage screen (state-driven), not the paywall.
        binding.manage.addPressEffect {
            view?.post { findNavController().navigate(R.id.manageSubscriptionFragment) }
        }

        binding.preferences.addPressEffect {
            view?.post { findNavController().navigate(R.id.preferencesFragment) }
        }

        binding.support.addPressEffect {
            openEmail(
                to = "support@urducanvas.com",
                subject = "Support Request",
                body = ""
            )
        }

        binding.privacy.addPressEffect {
            openUrl("https://urducanvas.com/privacy-policy")
        }

        binding.rate.addPressEffect {
            openUrl("https://play.google.com/store/apps/details?id=${requireContext().packageName}")
        }

        binding.whatsappChannel.addPressEffect {
            openUrl("https://whatsapp.com/channel/0029Vb79Ac14IBhIMZYyvj0Y")
        }

        binding.improve.addPressEffect {
            openEmail(
                to = "support@urducanvas.com",
                subject = "Feedback – Help Us Improve",
                body = ""
            )
        }

        binding.requestFeature.addPressEffect {
            openEmail(
                to = "support@urducanvas.com",
                subject = "Feature Request",
                body = "Hi, I'd like to request the following feature:\n\n"
            )
        }

        binding.reportBug.addPressEffect {
            openEmail(
                to = "support@urducanvas.com",
                subject = "Bug Report",
                body = "Hi, I'd like to report the following bug:\n\nDevice: ${android.os.Build.MODEL}\nAndroid: ${android.os.Build.VERSION.RELEASE}\n\nDescription:\n"
            )
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            // no browser installed — silently ignore
        }
    }

    private fun openEmail(to: String, subject: String, body: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:".toUri()
                putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            startActivity(Intent.createChooser(intent, "Send Email"))
        } catch (e: Exception) {
            // no email app installed — silently ignore
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
