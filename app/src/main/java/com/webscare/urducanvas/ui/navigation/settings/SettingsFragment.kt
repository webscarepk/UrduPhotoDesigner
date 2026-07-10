package com.webscare.urducanvas.ui.navigation.settings

import android.content.Intent
import android.util.Log
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.navigation.fragment.findNavController
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.webscare.urducanvas.MainActivity
import com.webscare.urducanvas.di.BillingManager
import com.webscare.urducanvas.di.BillingManager.SubscriptionStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var billingManager: BillingManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
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

    override fun onResume() {
        super.onResume()
        billingManager.refreshSnapshot()
    }

    private fun setVersionInfo() {
        val versionName = try {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
        } catch (e: Exception) {
            Log.e("SettingsFragment", "Failed to retrieve package version name", e)
            "—"
        }
        binding.versionInfo.text = "Version $versionName"
    }

    private fun observeSubscription() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                billingManager.snapshot.collect { snap ->
                    val subscribed = snap.status != SubscriptionStatus.NOT_SUBSCRIBED &&
                        snap.status != SubscriptionStatus.PENDING

                    if (subscribed) {
                        binding.subscriptionCard.visibility = View.GONE
                        binding.currentPlanCard.visibility = View.VISIBLE
                        renderCurrentPlanCard(snap)
                    } else {
                        binding.subscriptionCard.visibility = View.VISIBLE
                        binding.currentPlanCard.visibility = View.GONE
                    }
                }
            }
        }
    }

    /**
     * Fills in the compact "current plan" pill card — accent bar, icon
     * chip, and status badge all follow the actual subscription status
     * (same color mapping ManageSubscriptionFragment uses), not a
     * hardcoded "active" look. The subtitle line shows a short renewal/
     * trial/access date when one is available.
     */
    private fun renderCurrentPlanCard(snap: BillingManager.PlayBillingSnapshot) {
        val status = snap.status
        val planName = planFriendlyName(snap.productId)

        val (accentRes, tintRes) = when (status) {
            SubscriptionStatus.TRIAL -> R.color.state_teal to R.color.state_teal_tint
            SubscriptionStatus.ACTIVE -> R.color.state_green to R.color.state_green_tint
            SubscriptionStatus.CANCELED -> R.color.state_amber to R.color.state_amber_tint
            else -> R.color.state_green to R.color.state_green_tint
        }
        val accent = color(accentRes)
        val tint = color(tintRes)

        ViewCompat.setBackgroundTintList(binding.statusAccentBar, ColorStateList.valueOf(accent))
        ViewCompat.setBackgroundTintList(binding.manageCardIcon, ColorStateList.valueOf(tint))
        binding.manageCardIcon.imageTintList = ColorStateList.valueOf(accent)

        binding.manageCardTitle.text = planName

        ViewCompat.setBackgroundTintList(binding.manageCardStatusBadge, ColorStateList.valueOf(tint))
        binding.manageCardStatusBadge.setTextColor(accent)
        binding.manageCardStatusBadge.text = getString(
            when (status) {
                SubscriptionStatus.TRIAL -> R.string.mng_chip_trial
                SubscriptionStatus.CANCELED -> R.string.mng_chip_canceled
                else -> R.string.mng_chip_active
            },
        ).uppercase(Locale.getDefault())

        binding.manageCardSubTitle.text = statusDetailLine(status, snap.expiryTimeMillis)
    }

    /** Short one-line status/date summary — "Renews Aug 1, 2026", "Trial ends ...", etc. */
    private fun statusDetailLine(status: SubscriptionStatus, expiryMillis: Long?): String {
        val date = expiryMillis?.takeIf { it > 0L }?.let {
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it))
        }
        return when (status) {
            SubscriptionStatus.ACTIVE -> if (date != null) "Renews $date" else "Auto-renews"
            SubscriptionStatus.CANCELED -> if (date != null) "Access until $date" else "Auto-renew is off"
            SubscriptionStatus.TRIAL -> if (date != null) "Trial ends $date" else "Free trial"
            else -> "Pro plan"
        }
    }

    private fun planFriendlyName(productId: String?): String = when (productId) {
        "urducanvas_monthly" -> "Monthly"
        "urducanvas_6months" -> "6 Months"
        "urducanvas_yearly" -> "Yearly"
        else -> "Pro"
    }

    private fun goToSubscriptions() {
        view?.post { findNavController().navigate(R.id.subscriptionsFragment) }
    }

    private fun setEvents() {
        // Entire upgrade card + button both navigate to subscriptions.
        binding.subscriptionCard.addPressEffect { goToSubscriptions() }
        binding.upgradeNow.addPressEffect { goToSubscriptions() }

        // Entire manage card + button both navigate to manage screen.
        binding.currentPlanCard.addPressEffect {
            view?.post { findNavController().navigate(R.id.manageSubscriptionFragment) }
        }
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
                body = "",
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
                body = "",
            )
        }

        binding.requestFeature.addPressEffect {
            openEmail(
                to = "support@urducanvas.com",
                subject = "Feature Request",
                body = "Hi, I'd like to request the following feature:\n\n",
            )
        }

        binding.reportBug.addPressEffect {
            openEmail(
                to = "support@urducanvas.com",
                subject = "Bug Report",
                body = "Hi, I'd like to report the following bug:\n\nDevice: ${android.os.Build.MODEL}\nAndroid: ${android.os.Build.VERSION.RELEASE}\n\nDescription:\n",
            )
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Log.e("SettingsFragment", "No browser found to open URL: $url", e)
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
            Log.e("SettingsFragment", "Failed to send email/no email app found", e)
        }
    }

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
