package com.webscare.urducanvas.ui.splash

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.webscare.urducanvas.R
import com.webscare.urducanvas.databinding.FragmentSplashBinding
import com.webscare.urducanvas.BuildConfig
import com.webscare.ads.WebsCareAds
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SplashFragment : Fragment(), TextureView.SurfaceTextureListener {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Remove the status bar padding MainActivity injects — we want true full screen
        requireActivity().window.decorView.post {
            _binding?.root?.setPadding(0, 0, 0, 0)
        }

        // Hide system bars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requireActivity().window.insetsController?.hide(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            requireActivity().window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )
        }

        binding.splashVideo.surfaceTextureListener = this
    }

    // Called when TextureView is ready
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        val videoUri = Uri.parse(
            "android.resource://${requireContext().packageName}/${R.raw.splash_video}"
        )

        mediaPlayer = MediaPlayer().apply {
            setDataSource(requireContext(), videoUri)
            setSurface(Surface(surface))
            isLooping = false
            setVolume(1f, 1f) // use 0f, 0f to mute

            setOnVideoSizeChangedListener { _, videoWidth, videoHeight ->
                applyCenterCrop(binding.splashVideo, videoWidth, videoHeight)
            }

            setOnCompletionListener {
                navigateToHome()
            }

            setOnErrorListener { _, _, _ ->
                navigateToHome()
                true
            }

            prepareAsync()
            setOnPreparedListener {
                playbackParams = playbackParams.setSpeed(3.4f)
                start()
            }
        }
    }

    /**
     * Scales and centers the TextureView so the video fills the screen (centerCrop).
     * The video is scaled up uniformly until both dimensions cover the view,
     * then centered — identical to ImageView scaleType="centerCrop".
     */
    private fun applyCenterCrop(textureView: TextureView, videoWidth: Int, videoHeight: Int) {
        if (videoWidth == 0 || videoHeight == 0) return

        val viewWidth = textureView.width.toFloat()
        val viewHeight = textureView.height.toFloat()

        val scaleX: Float
        val scaleY: Float

        if (viewWidth / viewHeight > videoWidth.toFloat() / videoHeight) {
            // View is wider than video → scale by width
            scaleX = 1f
            scaleY = (viewWidth / videoWidth) * (videoHeight.toFloat() / viewHeight)
        } else {
            // View is taller than video → scale by height
            scaleX = (viewHeight / videoHeight) * (videoWidth.toFloat() / viewWidth)
            scaleY = 1f
        }

        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
        textureView.setTransform(matrix)
    }

    private fun navigateToHome() {
        if (!isAdded || view == null) return
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.splashFragment, true)
            .build()

        val performNavigation = {
            view?.post {
                findNavController().navigate(R.id.homeFragment, null, navOptions)
            }
        }

        val activity = activity
        if (activity != null) {
            WebsCareAds.showAppOpen(activity, BuildConfig.AD_APP_OPEN_SPLASH) {
                performNavigation()
            }
        } else {
            performNavigation()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        mediaPlayer?.let { applyCenterCrop(binding.splashVideo, it.videoWidth, it.videoHeight) }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        releasePlayer()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun releasePlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releasePlayer()
        _binding = null
    }
}