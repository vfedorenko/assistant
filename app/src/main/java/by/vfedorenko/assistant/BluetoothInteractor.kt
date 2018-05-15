package by.vfedorenko.assistant

import android.app.Activity
import android.speech.tts.TextToSpeech
import com.google.android.things.contrib.driver.button.ButtonInputDriver
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.app.Activity.RESULT_CANCELED
import android.content.Intent
import com.google.android.things.bluetooth.BluetoothProfileManager
import android.bluetooth.BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED
import android.content.IntentFilter
import android.bluetooth.BluetoothA2dp.STATE_NOT_PLAYING
import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import java.io.IOException
import java.util.*


class BluetoothInteractor(private val activity: Activity) {

    companion object {
        private const val ADAPTER_FRIENDLY_NAME = "Android Things Assistant"
        private const val DISCOVERABLE_TIMEOUT_MS = 10_000
        private const val REQUEST_CODE_ENABLE_DISCOVERABLE = 100
        private const val UTTERANCE_ID = "by.vfedorenko.assistant.UTTERANCE_ID"
    }

    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mA2DPSinkProxy: BluetoothProfile? = null

    private var mPairingButtonDriver: ButtonInputDriver? = null
    private var mDisconnectAllButtonDriver: ButtonInputDriver? = null

    private var mTtsEngine: TextToSpeech? = null

    /**
     * Handle an intent that is broadcast by the Bluetooth adapter whenever it changes its
     * state (after calling enable(), for example).
     * Action is [BluetoothAdapter.ACTION_STATE_CHANGED] and extras describe the old
     * and the new states. You can use this intent to indicate that the device is ready to go.
     */
    private val mAdapterStateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val oldState = getPreviousAdapterState(intent)
            val newState = getCurrentAdapterState(intent)
            Log.d(MainActivity.TAG, "Bluetooth Adapter changing state from $oldState to $newState")
            if (newState == BluetoothAdapter.STATE_ON) {
                Log.i(MainActivity.TAG, "Bluetooth Adapter is ready")
                initA2DPSink()
            }
        }
    }

    /**
     * Handle an intent that is broadcast by the Bluetooth A2DP sink profile whenever a device
     * connects or disconnects to it.
     * Action is [A2dpSinkHelper.ACTION_CONNECTION_STATE_CHANGED] and
     * extras describe the old and the new connection states. You can use it to indicate that
     * there's a device connected.
     */
    private val mSinkProfileStateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_CONNECTION_STATE_CHANGED) {
                val oldState = getPreviousProfileState(intent)
                val newState = getCurrentProfileState(intent)
                val device = getDevice(intent)
                Log.d(MainActivity.TAG, "Bluetooth A2DP sink changing connection state from " + oldState +
                        " to " + newState + " device " + device)
                if (device != null) {
                    val deviceName = Objects.toString(device!!.getName(), "a device")
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        speak("Connected to $deviceName")
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        speak("Disconnected from $deviceName")
                    }
                }
            }
        }
    }

    /**
     * Handle an intent that is broadcast by the Bluetooth A2DP sink profile whenever a device
     * starts or stops playing through the A2DP sink.
     * Action is [ACTION_PLAYING_STATE_CHANGED] and
     * extras describe the old and the new playback states. You can use it to indicate that
     * there's something playing. You don't need to handle the stream playback by yourself.
     */
    private val mSinkProfilePlaybackChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_PLAYING_STATE_CHANGED) {
                val oldState = getPreviousProfileState(intent)
                val newState = getCurrentProfileState(intent)
                val device = getDevice(intent)
                Log.d(MainActivity.TAG, "Bluetooth A2DP sink changing playback state from " + oldState +
                        " to " + newState + " device " + device)
                if (device != null) {
                    if (newState == STATE_PLAYING) {
                        Log.i(MainActivity.TAG, "Playing audio from device " + device!!.getAddress())
                    } else if (newState == STATE_NOT_PLAYING) {
                        Log.i(MainActivity.TAG, "Stopped playing audio from " + device!!.getAddress())
                    }
                }
            }
        }
    }

    init {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (mBluetoothAdapter == null) {
            Log.w(MainActivity.TAG, "No default Bluetooth adapter. Device likely does not support bluetooth.")
            throw IllegalStateException("No default Bluetooth adapter. Device likely does not support bluetooth.")
        }

        // We use Text-to-Speech to indicate status change to the user
        initTts()

        activity.registerReceiver(mAdapterStateChangeReceiver, IntentFilter(
                BluetoothAdapter.ACTION_STATE_CHANGED))
        activity.registerReceiver(mSinkProfileStateChangeReceiver, IntentFilter(
                ACTION_CONNECTION_STATE_CHANGED))
        activity.registerReceiver(mSinkProfilePlaybackChangeReceiver, IntentFilter(
                ACTION_PLAYING_STATE_CHANGED))

        if (mBluetoothAdapter?.isEnabled == true) {
            Log.d(MainActivity.TAG, "Bluetooth Adapter is already enabled.")
            initA2DPSink()
        } else {
            Log.d(MainActivity.TAG, "Bluetooth adapter not enabled. Enabling.")
            mBluetoothAdapter?.enable()
        }
    }

    fun clear() {
        Log.d(MainActivity.TAG, "onDestroy")

        try {
            mPairingButtonDriver?.close()
        } catch (e: IOException) { /* close quietly */
        }

        try {
            mDisconnectAllButtonDriver?.close()
        } catch (e: IOException) { /* close quietly */
        }

        activity.unregisterReceiver(mAdapterStateChangeReceiver)
        activity.unregisterReceiver(mSinkProfileStateChangeReceiver)
        activity.unregisterReceiver(mSinkProfilePlaybackChangeReceiver)

        if (mA2DPSinkProxy != null) {
            mBluetoothAdapter?.closeProfileProxy(A2DP_SINK_PROFILE,
                    mA2DPSinkProxy)
        }

        if (mTtsEngine != null) {
            mTtsEngine?.stop()
            mTtsEngine?.shutdown()
        }

        // we intentionally leave the Bluetooth adapter enabled, so that other samples can use it
        // without having to initialize it.
    }

    private fun setupBTProfiles() {
        val bluetoothProfileManager = BluetoothProfileManager.getInstance()
        val enabledProfiles = bluetoothProfileManager.enabledProfiles
        if (!enabledProfiles.contains(A2DP_SINK_PROFILE)) {
            Log.d(MainActivity.TAG, "Enabling A2dp sink mode.")
            val toDisable = Arrays.asList(BluetoothProfile.A2DP)
            val toEnable = Arrays.asList(
                    A2DP_SINK_PROFILE,
                    AVRCP_CONTROLLER_PROFILE)
            bluetoothProfileManager.enableAndDisableProfiles(toEnable, toDisable)
        } else {
            Log.d(MainActivity.TAG, "A2dp sink profile is enabled.")
        }
    }

    /**
     * Initiate the A2DP sink.
     */
    private fun initA2DPSink() {
        if (mBluetoothAdapter == null || mBluetoothAdapter?.isEnabled == false) {
            Log.e(MainActivity.TAG, "Bluetooth adapter not available or not enabled.")
            return
        }
        setupBTProfiles()
        Log.d(MainActivity.TAG, "Set up Bluetooth Adapter name and profile")
        mBluetoothAdapter?.name = ADAPTER_FRIENDLY_NAME
        mBluetoothAdapter?.getProfileProxy(activity, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                mA2DPSinkProxy = proxy
                enableDiscoverable()
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, A2DP_SINK_PROFILE)
    }

    /**
     * Enable the current [BluetoothAdapter] to be discovered (available for pairing) for
     * the next [.DISCOVERABLE_TIMEOUT_MS] ms.
     */
    fun enableDiscoverable() {
        Log.d(MainActivity.TAG, "Registering for discovery.")
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                DISCOVERABLE_TIMEOUT_MS)
        activity.startActivityForResult(discoverableIntent, REQUEST_CODE_ENABLE_DISCOVERABLE)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == REQUEST_CODE_ENABLE_DISCOVERABLE) {
            Log.d(MainActivity.TAG, "Enable discoverable returned with result $resultCode")

            // ResultCode, as described in BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE, is either
            // RESULT_CANCELED or the number of milliseconds that the device will stay in
            // discoverable mode. In a regular Android device, the user will see a popup requesting
            // authorization, and if they cancel, RESULT_CANCELED is returned. In Android Things,
            // on the other hand, the authorization for pairing is always given without user
            // interference, so RESULT_CANCELED should never be returned.
            if (resultCode == RESULT_CANCELED) {
                Log.e(MainActivity.TAG, "Enable discoverable has been cancelled by the user. " + "This should never happen in an Android Things device.")
                return
            }
            Log.i(MainActivity.TAG, "Bluetooth adapter successfully set to discoverable mode. " +
                    "Any A2DP source can find it with the name " + ADAPTER_FRIENDLY_NAME +
                    " and pair for the next " + DISCOVERABLE_TIMEOUT_MS + " ms. " +
                    "Try looking for it on your phone, for example.")

            // There is nothing else required here, since Android framework automatically handles
            // A2DP Sink. Most relevant Bluetooth events, like connection/disconnection, will
            // generate corresponding broadcast intents or profile proxy events that you can
            // listen to and react appropriately.

            speak("Bluetooth audio sink is discoverable for " + DISCOVERABLE_TIMEOUT_MS +
                    " milliseconds. Look for a device named " + ADAPTER_FRIENDLY_NAME)

        }
    }

    private fun initTts() {
        mTtsEngine = TextToSpeech(activity,
                TextToSpeech.OnInitListener { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        mTtsEngine?.language = Locale.US
                    } else {
                        Log.w(MainActivity.TAG, "Could not open TTS Engine (onInit status=" + status
                                + "). Ignoring text to speech")
                        mTtsEngine = null
                    }
                })
    }


    private fun speak(utterance: String) {
        Log.i(MainActivity.TAG, utterance)
        mTtsEngine?.speak(utterance, TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID)
    }
}
