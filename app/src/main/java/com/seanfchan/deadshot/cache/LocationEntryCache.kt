package com.seanfchan.deadshot.cache

import android.util.Log
import com.seanfchan.deadshot.api.APRSEntry
import com.seanfchan.deadshot.api.APRSService
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.util.concurrent.TimeUnit

class LocationEntryCache(val api: APRSService) {
    private val TAG = "LocationEntryCache"
    private val POLLING_INTERVAL_MS: Long = 10000

    private val entries: MutableList<APRSEntry> = ArrayList()
    private val cacheSubject: Subject<Unit> = PublishSubject.create()
    private var subscription: Disposable? = null
    private var fullPoll = false

    fun startPolling() {
        subscription = Observable
                .interval(POLLING_INTERVAL_MS, TimeUnit.MILLISECONDS)
                .doOnNext {
                    poll()
                }
                .subscribe()
        poll()
    }

    private fun poll() {
        val observable: Observable<List<APRSEntry>>

        if (!fullPoll) {
            observable = api.getAllAPRData()
            fullPoll = true
        } else {
            observable = api.getAPRData()
        }
        observable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object: Observer<List<APRSEntry>> {
            override fun onComplete() {
            }

            override fun onError(e: Throwable) {
                Log.e(TAG, e.toString())
            }

            override fun onSubscribe(d: Disposable) {

            }

            override fun onNext(entries: List<APRSEntry>) {
                saveEntries(entries)
            }
        })
    }

    fun stopPolling() {
        subscription?.dispose()
        subscription = null
    }

    fun entries(): List<APRSEntry> {
        return entries
    }

    fun cacheUpdatedObservable() : Observable<Unit> {
        return cacheSubject
    }

    private fun saveEntries(entries: List<APRSEntry>) {
        for (newEntry in entries) {
            val alreadyExists = entries().map {
                entry -> entry.name + entry.time
            }.contains(newEntry.name + newEntry.time)
            if (!alreadyExists) {
                this.entries.add(newEntry)
            }
        }
        cacheSubject.onNext(Unit)
    }
}