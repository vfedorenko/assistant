package by.vfedorenko.assistant

import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import java.io.IOException
import android.util.Log
import com.google.android.things.contrib.driver.button.Button

class MainActivity : Activity() {

    companion object {
        const val TAG = "1111"
        private const val gpioButtonPinName = "BUS NAME"
    }

    private lateinit var mButton: Button

    private lateinit var bluetoothInteractor: BluetoothInteractor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupButton()

        bluetoothInteractor = BluetoothInteractor(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyButton()

        bluetoothInteractor.clear()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        bluetoothInteractor.onActivityResult(requestCode, resultCode, data)
    }

    private fun setupButton() {
        try {
            mButton = Button(gpioButtonPinName,
                    // high signal indicates the button is pressed
                    // use with a pull-down resistor
                    Button.LogicState.PRESSED_WHEN_HIGH
            )
            mButton.setOnButtonEventListener { _, _ ->
                bluetoothInteractor.enableDiscoverable()
            }
        } catch (e: IOException) {
            // couldn't configure the button...
        }

    }

    private fun destroyButton() {
        Log.i(TAG, "Closing button")
        try {
            mButton.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing button", e)
        }
    }
}
