import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static rx.schedulers.Schedulers.computation;


public class OnErrorRetryCacheTest {

    private static final int TASK_DURATION = 200;

    AtomicInteger attempts = new AtomicInteger();

    TestSubscriber subscriber1 = new TestSubscriber();
    TestSubscriber subscriber2 = new TestSubscriber();
    TestSubscriber subscriber3 = new TestSubscriber();
 
    Observable<Integer> longTask;
    
    @Before
    public void setup(){
        longTask = Observable.just(0)
                .map(x -> {
                    attempts.incrementAndGet();
                    sleep(TASK_DURATION);
                    return 0;
                })
                .subscribeOn(computation());
    }
    
    @Test
    public void shouldExecuteOnceAndCacheResultIfFirstCompletes() {
        Observable<Integer> observable = OnErrorRetryCache.from(longTask);
        int expectedAttempts = 1;// just the first that completed and cached

        subscribeAllAndWaitUntilDone(observable);

        assertThat(attempts.get()).isEqualTo(expectedAttempts);
        assertThat(subscriber1.next).isTrue();
        assertThat(subscriber2.next).isTrue();
        assertThat(subscriber3.next).isTrue();
    }

    @Test
    public void shouldRetryAndThenCacheResultIfFirstFails() {
        Observable<Integer> observable = longTask
                .map(x -> {
                    failOnFirstAttempt();
                    return 0;
                });
        observable = OnErrorRetryCache.from(observable);
        int expectedAttempts = 2;// the first that failed and the second that completed and cached

        subscribeAllAndWaitUntilDone(observable);

        assertThat(attempts.get()).isEqualTo(expectedAttempts);
        assertThat(subscriber1.error).isTrue();
        assertThat(subscriber2.next).isTrue();
        assertThat(subscriber3.next).isTrue();
    }

    @Test
    public void shouldRetryIfFirstTwoFail() {
        Observable<Integer> observable = longTask
                .map(x -> {
                    failOnFirstTwoAttempts();
                    return 0;
                });
        observable = OnErrorRetryCache.from(observable);
        int expectedAttempts = 3; // the first two that failed and the third that completed

        subscribeAllAndWaitUntilDone(observable);

        assertThat(attempts.get()).isEqualTo(expectedAttempts);
        assertThat(subscriber1.error).isTrue();
        assertThat(subscriber2.error).isTrue();
        assertThat(subscriber3.next).isTrue();
    }

    private void failOnFirstAttempt() {
        if (attempts.get() == 1) {
            throw new RuntimeException();
        }
    }

    private void failOnFirstTwoAttempts() {
        if (attempts.get() <= 2) {
            throw new RuntimeException();
        }
    }

    private void subscribeAllAndWaitUntilDone(Observable<Integer> observable) {
        observable.subscribe(subscriber1);
        observable.subscribe(subscriber2);
        observable.subscribe(subscriber3);
        waitUntilAllDone();
    }
    
    private static class TestSubscriber extends Subscriber {
        boolean completed;
        boolean error;
        boolean next;

        @Override
        public void onCompleted() {
            completed = true;
        }

        @Override
        public void onError(Throwable e) {
            error = true;
        }

        @Override
        public void onNext(Object o) {
            next = true;
        }

        boolean done() {
            return completed || error;
        }
    }

    private void waitUntilAllDone() {
        while (true) {
            boolean done = subscriber1.done() && subscriber2.done() && subscriber3.done();
            if (done) {
                break;
            } else {
                sleep(50);
            }
        }
    }
    
    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignore) {}
    }
}
