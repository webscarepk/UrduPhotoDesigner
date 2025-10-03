package com.example.urduphotodesigner.ui.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.sealed.SignInUiState
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.FragmentLoginBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    // Inject the ViewModel using the viewModels delegate
    private val authViewModel: AuthViewModel by activityViewModels()
    private var progressDialog: AlertDialog? = null

    private val oneTapResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // Pass the result to the ViewModel for handling
        if (result.resultCode == Activity.RESULT_OK) {
            authViewModel.handleGoogleSignInResult(result.data)
        } else {
            progressDialog?.dismiss()
            Log.w(TAG, "Sign-in canceled or failed. resultCode=${result.resultCode}")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupProgressDialog()
        setClickListeners()
        observeSignInState()
    }

    private fun setupProgressDialog() {
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_progress, null)
        progressDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        progressDialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.5f) // Dim background

            val params = attributes
            params?.width = (resources.displayMetrics.widthPixels * 0.8).toInt()
            params?.height = ViewGroup.LayoutParams.WRAP_CONTENT
            attributes = params
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setClickListeners() {
        binding.googleSignIn.addPressEffect {
            authViewModel.startGoogleSignIn()
        }
        binding.login.addPressEffect {
            validateAndRegister()
        }

        binding.signup.addPressEffect {
            view?.post {
                findNavController().navigate(R.id.signupFragment)
            }
        }

        binding.password.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableRight = binding.password.compoundDrawables[2]
                if (drawableRight != null) {
                    // Calculate clickable area (right side minus padding)
                    val clickableStart = binding.password.width -
                            binding.password.paddingRight -
                            drawableRight.bounds.width()

                    if (event.x >= clickableStart) {
                        togglePasswordVisibility(binding.password)
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }

    private fun validateAndRegister() {
        val email = binding.email.text.toString().trim()
        val password = binding.password.text.toString().trim()

        when {
            email.isEmpty() -> {
                binding.email.error = "Please enter your email"
                binding.email.requestFocus()
            }

            password.isEmpty() -> {
                binding.password.error = "Please enter a password"
                binding.password.requestFocus()
            }

            else -> {
                authViewModel.performApiLogin(email, password)
            }
        }
    }


    private fun togglePasswordVisibility(editText: EditText) {
        val isCurrentlyHidden = editText.inputType ==
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)

        if (isCurrentlyHidden) {
            // Show password
            editText.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_hide_pass, 0)
        } else {
            // Hide password
            editText.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_show_pass, 0)
        }

        // Move cursor to the end
        editText.setSelection(editText.text.length)
    }

    private fun observeSignInState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.signInState.collect { state ->
                    when (state) {
                        is SignInUiState.Idle -> {
                        }

                        is SignInUiState.Loading -> {
                            Log.d(TAG, "Sign-in state: Loading...")
                            // Show loading indicator
                            progressDialog?.show()
                        }

                        is SignInUiState.GoogleSignInFlow -> {
                            Log.d(TAG, "Sign-in state: Launching One Tap UI")
                            val intentSenderRequest =
                                IntentSenderRequest.Builder(state.beginSignInResult.pendingIntent.intentSender)
                                    .build()
                            oneTapResultLauncher.launch(intentSenderRequest)
                            progressDialog?.dismiss()
                        }

                        is SignInUiState.Success -> {
                            Log.d(
                                TAG,
                                "Sign-in successful: Email=${state.email}, DisplayName=${state.displayName}, Token=${state.idToken}"
                            )
                            Snackbar.make(
                                requireView(),   // or binding.root if you’re using ViewBinding
                                "Welcome, ${state.displayName ?: state.email}",
                                Snackbar.LENGTH_LONG
                            ).show()

                            val navOptions = NavOptions.Builder()
                                .setPopUpTo(R.id.loginFragment, true)
                                .build()
                            hideKeyboard()
                            view?.post {
                                findNavController().navigate(R.id.homeFragment, null, navOptions)
                            }
                            authViewModel.clearUserData()
                            progressDialog?.dismiss()
                            // Optionally hide loading indicator
                        }

                        is SignInUiState.Error -> {
                            Log.e(TAG, "Sign-in error: ${state.message}")
                            Snackbar.make(
                                requireView(),
                                "Sign-in failed: ${state.message}",
                                Snackbar.LENGTH_LONG
                            ).show()

                            // Optionally hide loading indicator

                            progressDialog?.dismiss()
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    fun hideKeyboard() {
        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        private const val TAG = "LoginFragment"
    }
}