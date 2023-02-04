package idv.markkuo.cscblebridge.service
import android.content.Context
import android.util.Log
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.MqttClient

class MqttClient(private val context: Context) {
    val client by lazy {
        val clientId = MqttClient.generateClientId()
        MqttAndroidClient(context, "tcp://192.168.50.96:1883",
                clientId)
    }

    companion object {
        const val TAG = "MqttClient"
    }

    fun connect(topics: Array<String>? = null,
                messageCallBack: ((topic: String, message: MqttMessage) -> Unit)? = null) {
        try {
            var opts = MqttConnectOptions()
            opts.isAutomaticReconnect = true
            opts.maxReconnectDelay = 5000
            opts.maxInflight = 10
            client.connect(opts)
            client.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String) {
                    topics?.forEach {
                        subscribeTopic(it)
                    }
                    Log.d(TAG, "Connected to: $serverURI")
                }

                override fun connectionLost(cause: Throwable) {
                    Log.d(TAG, "The Connection was lost.")
                }

                @Throws(Exception::class)
                override fun messageArrived(topic: String, message: MqttMessage) {
                    Log.d(TAG, "Incoming message from $topic: " + message.toString())
                    messageCallBack?.invoke(topic, message)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken) {

                }
            })


        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun publishMessage(topic: String, msg: String) {

        try {
            val message = MqttMessage()
            message.payload = msg.toByteArray()
            client.publish(topic, message.payload, 1, false)
            // Log.d(TAG, "$msg published to $topic")
        } catch (e: MqttException) {
            Log.d(TAG, "Error Publishing to $topic: " + e.message)
            e.printStackTrace()
        }

    }

    fun subscribeTopic(topic: String, qos: Int = 0) {
        client.subscribe(topic, qos).actionCallback = object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken) {
                Log.d(TAG, "Subscribed to $topic")
            }

            override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                Log.d(TAG, "Failed to subscribe to $topic")
                exception.printStackTrace()
            }
        }
    }

    fun close() {
        client.apply {
            unregisterResources()
            close()
        }
    }
}