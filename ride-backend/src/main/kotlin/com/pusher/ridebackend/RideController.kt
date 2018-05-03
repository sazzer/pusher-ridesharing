package com.pusher.ridebackend

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
class RideController(
        private val jobNotifier: JobNotifier
) {
    @RequestMapping(value = ["/request-ride"], method = [RequestMethod.POST])
    @ResponseStatus(HttpStatus.CREATED)
    fun requestRide(@RequestBody location: Location): String {
        val job = UUID.randomUUID().toString()
        jobNotifier.notify(job, Actions.NEW_JOB, location)
        return job
    }

    @RequestMapping(value = ["/accept-job/{job}"], method = [RequestMethod.POST])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun acceptJob(@PathVariable("job") job: String, @RequestBody location: Location) {
        jobNotifier.notify(job, Actions.ACCEPT_JOB, location)
    }

    @RequestMapping(value = ["/update-location/{job}"], method = [RequestMethod.POST])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateLocation(@PathVariable("job") job: String, @RequestBody location: Location) {
        jobNotifier.notify(job, Actions.UPDATE_LOCATION, location)
    }

    @RequestMapping(value = ["/pickup/{job}"], method = [RequestMethod.POST])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun pickup(@PathVariable("job") job: String, @RequestBody location: Location) {
        jobNotifier.notify(job, Actions.PICKUP, location)
    }

    @RequestMapping(value = ["/dropoff/{job}"], method = [RequestMethod.POST])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun dropoff(@PathVariable("job") job: String, @RequestBody location: Location) {
        jobNotifier.notify(job, Actions.DROPOFF, location)
    }

    @RequestMapping(value = ["/rate/{job}"], method = [RequestMethod.POST])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun rate(@PathVariable("job") job: String, @RequestBody rating: Int) {
        jobNotifier.notify(job, rating)
    }
}
