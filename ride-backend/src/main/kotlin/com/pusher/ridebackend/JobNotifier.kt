package com.pusher.ridebackend

import com.pusher.pushnotifications.PushNotifications
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class JobNotifier(
        @Value("\${pusher.instanceId}") private val instanceId: String,
        @Value("\${pusher.secretKey}") private val secretKey: String
) {
    private val pusher = PushNotifications(instanceId, secretKey)

    fun notify(job: String, action: Actions, location: Location) {
        val interests = when (action) {
            Actions.NEW_JOB -> listOf("driver_broadcast")
            else -> listOf("rider_$job")
        }

        pusher.publish(
                interests,
                mapOf(
                        "fcm" to mapOf(
                                "data" to mapOf(
                                        "action" to action.name,
                                        "job" to job,
                                        "latitude" to location.latitude,
                                        "longitude" to location.longitude
                                )
                        )
                )
        )
    }

    fun notify(job: String, rating: Int) {
        pusher.publish(
                listOf("driver_$job"),
                mapOf(
                        "fcm" to mapOf(
                                "data" to mapOf(
                                        "action" to Actions.RATE.name,
                                        "job" to job,
                                        "rating" to rating
                                )
                        )
                )
        )
    }
}
