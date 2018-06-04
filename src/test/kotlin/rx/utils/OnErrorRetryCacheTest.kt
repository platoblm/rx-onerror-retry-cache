package rx.utils

import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.assertj.core.api.Condition
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

import org.assertj.core.api.Assertions.assertThat

class OnErrorRetryCacheTest {

    var attempts = AtomicInteger()

    private val subscriber1 = TestSubscriber()
    private val subscriber2 = TestSubscriber()
    private val subscriber3 = TestSubscriber()

    private val longTask: Single<Int> = Single.fromCallable {
        attempts.incrementAndGet() // track attempts made
        sleepABit()
        0
    }.subscribeOn(Schedulers.newThread())

    @Test
    fun shouldExecuteOnceAndCacheResultIfFirstCompletes() {
        val observable = longTask.cacheButRetryOnError()

        subscribeAllAndWaitUntilDone(observable)

        assertThat(attempts.get()).isEqualTo(1) // 1st cached, 2nd and 3rd don't happen
        assertThat(subscriber1).has(succeeded())
        assertThat(subscriber2).has(succeeded())
        assertThat(subscriber3).has(succeeded())
    }

    @Test
    fun shouldRetryAndThenCacheResultIfFirstFails() {
        val observable = taskThatFailsTheFirstTime().cacheButRetryOnError()

        subscribeAllAndWaitUntilDone(observable)

        assertThat(attempts.get()).isEqualTo(2) // 2nd cached, 3rd doesn't happen
        assertThat(subscriber1).has(errored())
        assertThat(subscriber2).has(succeeded())
        assertThat(subscriber3).has(succeeded())
    }

    @Test
    fun shouldRetryIfFirstTwoFail() {
        val observable = taskThatFailsTheFirstTwoTimes().cacheButRetryOnError()

        subscribeAllAndWaitUntilDone(observable)

        assertThat(attempts.get()).isEqualTo(3)
        assertThat(subscriber1).has(errored())
        assertThat(subscriber2).has(errored())
        assertThat(subscriber3).has(succeeded())
    }

    private fun subscribeAllAndWaitUntilDone(observable: Single<Int>) {
        observable.subscribe(subscriber1)
        observable.subscribe(subscriber2)
        observable.subscribe(subscriber3)
        waitUntilAllComplete()
    }

    private class TestSubscriber : SingleObserver<Any> {
        var succeeded: Boolean = false
        var errored: Boolean = false

        override fun onSubscribe(d: Disposable) {}

        override fun onError(e: Throwable) {
            errored = true
        }

        override fun onSuccess(t: Any) {
            succeeded = true
        }

        fun completed() = succeeded || errored
    }

    private fun waitUntilAllComplete() {
        while (true) {
            if (subscriber1.completed() && subscriber2.completed() && subscriber3.completed()) {
                break
            } else {
                sleepABit()
            }
        }
    }

    private fun taskThatFailsTheFirstTime(): Single<Int> =
            longTask.map {
                if (attempts.get() == 1) throw RuntimeException()
                0
            }

    private fun taskThatFailsTheFirstTwoTimes(): Single<Int> =
            longTask.map {
                if (attempts.get() <= 2) throw RuntimeException()
                0
            }

    private fun succeeded() = object : Condition<TestSubscriber>("Succeeded") {
        override fun matches(subscriber: TestSubscriber) = subscriber.succeeded
    }

    private fun errored() = object : Condition<TestSubscriber>("Errored") {
        override fun matches(subscriber: TestSubscriber) = subscriber.errored
    }

    private fun sleepABit() = try {
        Thread.sleep(50L)
    } catch (ignore: InterruptedException) {
    }
}
