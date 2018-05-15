package by.vfedorenko.assistant

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile


/**
 * Profile number for A2DP_SINK profile.
 */
val A2DP_SINK_PROFILE = 11

/**
 * Profile number for AVRCP_CONTROLLER profile.
 */
val AVRCP_CONTROLLER_PROFILE = 12

/**
 * Intent used to broadcast the change in connection state of the A2DP Sink
 * profile.
 *
 *
 * This intent will have 3 extras:
 *
 *  *  [BluetoothProfile.EXTRA_STATE] - The current state of the profile.
 *  *  [BluetoothProfile.EXTRA_PREVIOUS_STATE]- The previous state of the
 * profile.
 *  *  [BluetoothDevice.EXTRA_DEVICE] - The remote device.
 *
 *
 *
 * [BluetoothProfile.EXTRA_STATE] or [BluetoothProfile.EXTRA_PREVIOUS_STATE]
 * can be any of [BluetoothProfile.STATE_DISCONNECTED],
 * [BluetoothProfile.STATE_CONNECTING], [BluetoothProfile.STATE_CONNECTED],
 * [BluetoothProfile.STATE_DISCONNECTING].
 *
 *
 * Requires [android.Manifest.permission.BLUETOOTH] permission to
 * receive.
 */
val ACTION_CONNECTION_STATE_CHANGED = "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED"

/**
 * Intent used to broadcast the change in the Playing state of the A2DP Sink
 * profile.
 *
 *
 * This intent will have 3 extras:
 *
 *  *  [BluetoothProfile.EXTRA_STATE] - The current state of the profile.
 *  *  [BluetoothProfile.EXTRA_PREVIOUS_STATE]- The previous state of the
 * profile.
 *  *  [BluetoothDevice.EXTRA_DEVICE] - The remote device.
 *
 *
 *
 * [BluetoothProfile.EXTRA_STATE] or [BluetoothProfile.EXTRA_PREVIOUS_STATE]
 * can be any of [.STATE_PLAYING], [.STATE_NOT_PLAYING],
 *
 *
 * Requires [android.Manifest.permission.BLUETOOTH] permission to
 * receive.
 */
val ACTION_PLAYING_STATE_CHANGED = "android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED"

/**
 * A2DP sink device is streaming music. This state can be one of
 * [BluetoothProfile.EXTRA_STATE] or [BluetoothProfile.EXTRA_PREVIOUS_STATE] of
 * [.ACTION_PLAYING_STATE_CHANGED] intent.
 */
val STATE_PLAYING = 10

/**
 * A2DP sink device is NOT streaming music. This state can be one of
 * [BluetoothProfile.EXTRA_STATE] or [BluetoothProfile.EXTRA_PREVIOUS_STATE] of
 * [.ACTION_PLAYING_STATE_CHANGED] intent.
 */
val STATE_NOT_PLAYING = 11

fun getPreviousAdapterState(intent: Intent): Int {
    return intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1)
}

fun getCurrentAdapterState(intent: Intent): Int {
    return intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
}

fun getPreviousProfileState(intent: Intent): Int {
    return intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1)
}

fun getCurrentProfileState(intent: Intent): Int {
    return intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
}

fun getDevice(intent: Intent): BluetoothDevice? {
    return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
}
