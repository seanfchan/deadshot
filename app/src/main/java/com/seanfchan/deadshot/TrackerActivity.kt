package com.seanfchan.deadshot

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.seanfchan.deadshot.api.APRSResponse
import com.seanfchan.deadshot.api.APRSService
import com.seanfchan.deadshot.util.Constants
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class TrackerActivity : AppCompatActivity() {

    lateinit var aprsService: APRSService
    lateinit var subscriptions: CompositeDisposable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val DeadShot = application as DeadShot
        aprsService = DeadShot.aprsService
        subscriptions = CompositeDisposable()
    }

    override fun onResume() {
        super.onResume()
        subscriptions.add(aprsPollingObservable().subscribe())
    }

    override fun onStop() {
        super.onStop()
        subscriptions.clear()
    }

    fun aprsPollingObservable(): Observable<APRSResponse> {
        return Observable.interval(Constants.APRS_POLLING_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .observeOn(Schedulers.io())
                .flatMap {
                    aprsService.getAPRData(Constants.CALL_SIGN)
                }
                .doOnNext {
                    handleAPRSResponse(it)
                }
    }

    fun handleAPRSResponse(aprsResponse: APRSResponse) {

    }
}
