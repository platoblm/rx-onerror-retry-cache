import org.assertj.core.api.Condition;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class OnErrorRetryCacheTest {

    AtomicInteger attempts = new AtomicInteger();

    TestSubscriber subscriber1 = new TestSubscriber();
    TestSubscriber subscriber2 = new TestSubscriber();
    TestSubscriber subscriber3 = new TestSubscriber();
 
    Observable<Integer> longTask;
    
    @Before public void setup()
    {
        longTask = Observable.just(0)
                .map(x -> {
                    attempts.incrementAndGet(); // track attempts made
                    sleep(50);
                    return 0;
                })
                .subscribeOn(Schedulers.newThread());
    }
    
    @Test public void shouldExecuteOnceAndCacheResultIfFirstCompletes() 
    {
        Observable<Integer> observable = OnErrorRetryCache.from(longTask);

        subscribeAllAndWaitUntilDone(observable);

        assertThat(attempts.get()).isEqualTo(1); // 1st cached, 2n and 3rd don't happen
        assertThat(subscriber1).has(succeeded());
        assertThat(subscriber2).has(succeeded());
        assertThat(subscriber3).has(succeeded());
    }

    @Test public void shouldRetryAndThenCacheResultIfFirstFails() 
    {
        Observable<Integer> observable = OnErrorRetryCache.from(taskThatFailsOnFirstAttempt());

        subscribeAllAndWaitUntilDone(observable);

        assertThat(attempts.get()).isEqualTo(2); // 2nd cached, 3rd doesn't happen
        assertThat(subscriber1).has(errored());
        assertThat(subscriber2).has(succeeded());
        assertThat(subscriber3).has(succeeded());
    }

    @Test public void shouldRetryIfFirstTwoFail() 
    {
        Observable<Integer> observable = OnErrorRetryCache.from(taskThatFailsOnFirstTwoAttempts());

        subscribeAllAndWaitUntilDone(observable);

        assertThat(attempts.get()).isEqualTo(3);
        assertThat(subscriber1).has(errored());
        assertThat(subscriber2).has(errored());
        assertThat(subscriber3).has(succeeded());
    }

    private void subscribeAllAndWaitUntilDone(Observable<Integer> observable) {
        observable.subscribe(subscriber1);
        observable.subscribe(subscriber2);
        observable.subscribe(subscriber3);
        waitUntilAllDone();
    }
    
    private static class TestSubscriber extends Subscriber {
        boolean completed;
        boolean errored;
        boolean nextCalled;

        @Override
        public void onCompleted() {
            completed = true;
        }

        @Override
        public void onError(Throwable e) {
            errored = true;
        }

        @Override
        public void onNext(Object o) {
            nextCalled = true;
        }

        boolean done() {
            return completed || errored;
        }
    }

    private void waitUntilAllDone() {
        while (true) {
            boolean done = subscriber1.done() && 
                           subscriber2.done() && 
                           subscriber3.done();
            
            if (done) {
                break;
            } else {
                sleep(50);
            }
        }
    }

    private Observable<Integer> taskThatFailsOnFirstAttempt() {
        return longTask
                .map(x -> {
                    if (attempts.get() == 1) {
                        throw new RuntimeException();
                    }
                    return 0;
                });
    }

    private Observable<Integer> taskThatFailsOnFirstTwoAttempts() {
        return longTask
                .map(x -> {
                    if (attempts.get() <= 2) {
                        throw new RuntimeException();
                    }
                    return 0;
                });
    }

    private Condition<TestSubscriber> succeeded() {
        return new Condition<TestSubscriber>("Succeeded"){
            @Override public boolean matches(TestSubscriber subscriber) {
                return subscriber.nextCalled;
            }
        };
    }

    private Condition<TestSubscriber> errored() {
        return new Condition<TestSubscriber>("Errored"){
            @Override public boolean matches(TestSubscriber subscriber) {
                return subscriber.errored;
            }
        };
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignore) {}
    }
}
