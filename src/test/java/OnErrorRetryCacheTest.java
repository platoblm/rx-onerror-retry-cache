import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class OnErrorRetryCacheTest {

    private static final int TASK_DURATION = 200;

    AtomicInteger attempts = new AtomicInteger();

    TestSubscriber subscriber1 = new TestSubscriber();
    TestSubscriber subscriber2 = new TestSubscriber();
    TestSubscriber subscriber3 = new TestSubscriber();
 
    Observable<Integer> longTask;
    
    @Before public void setup(){
        longTask = Observable.just(0)
                .map(x -> {
                    attempts.incrementAndGet();
                    sleep(TASK_DURATION);
                    return 0;
                })
                .subscribeOn(Schedulers.newThread());
    }
    
    @Test public void shouldExecuteOnceAndCacheResultIfFirstCompletes() 
    {
        int expectedAttempts = 1; // 1st succeeds and is cached
        Observable<Integer> observable = OnErrorRetryCache.from(longTask);

        subscribeAllAndWaitUntilDone(observable);

        assertThat(attempts.get()).isEqualTo(expectedAttempts);
        assertThat(subscriber1.nextCalled).isTrue();
        assertThat(subscriber2.nextCalled).isTrue();
        assertThat(subscriber3.nextCalled).isTrue();
    }

    @Test public void shouldRetryAndThenCacheResultIfFirstFails() 
    {
        int expectedAttempts = 2; // 1st fails, 2nd succeeds and is cached
        Observable<Integer> observable = OnErrorRetryCache.from(
                taskThatFailsOnFirstAttempt()
        );

        subscribeAllAndWaitUntilDone(observable);

        assertThat(attempts.get()).isEqualTo(expectedAttempts);
        assertThat(subscriber1.errorCalled).isTrue();
        assertThat(subscriber2.nextCalled).isTrue();
        assertThat(subscriber3.nextCalled).isTrue();
    }

    @Test public void shouldRetryIfFirstTwoFail() 
    {
        int expectedAttempts = 3; // 1st and 2nd fail, 3rd succeeds
        Observable<Integer> observable = OnErrorRetryCache.from(
                taskThatFailsOnFirstTwoAttempts()
        );

        subscribeAllAndWaitUntilDone(observable);

        assertThat(attempts.get()).isEqualTo(expectedAttempts);
        assertThat(subscriber1.errorCalled).isTrue();
        assertThat(subscriber2.errorCalled).isTrue();
        assertThat(subscriber3.nextCalled).isTrue();
    }

    private void subscribeAllAndWaitUntilDone(Observable<Integer> observable) {
        observable.subscribe(subscriber1);
        observable.subscribe(subscriber2);
        observable.subscribe(subscriber3);
        waitUntilAllDone();
    }
    
    private static class TestSubscriber extends Subscriber {
        boolean completedCalled;
        boolean errorCalled;
        boolean nextCalled;

        @Override
        public void onCompleted() {
            completedCalled = true;
        }

        @Override
        public void onError(Throwable e) {
            errorCalled = true;
        }

        @Override
        public void onNext(Object o) {
            nextCalled = true;
        }

        boolean done() {
            return completedCalled || errorCalled;
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
    
    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignore) {}
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
}
