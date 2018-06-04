package rx.utils

import io.reactivex.Single

import java.util.concurrent.Semaphore

fun <T> Single<T>.cacheButRetryOnError(): Single<T> = OnErrorRetryCache(this).result

private class OnErrorRetryCache<T> constructor(source: Single<T>) {

    private val singlePermit = Semaphore(1)
    private var cache: Single<T>? = null

    val result: Single<T> = Single.defer {
        createOnSubscription(source)
    }

    private fun createOnSubscription(source: Single<T>): Single<T>? {
        singlePermit.acquireUninterruptibly()

        cache?.let {
            singlePermit.release()
            return it
        }

        lateinit var call: Single<T>
        call = source
                .doOnSuccess { cache = call }
                .doAfterTerminate(singlePermit::release)
                .cache()
        return call
    }
}
