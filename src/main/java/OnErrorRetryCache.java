import rx.Observable;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

public class OnErrorRetryCache<T> {

    public static <T> Observable<T> from(Observable<T> source) {
         return new OnErrorRetryCache<>(source).result;
    }

    private final AtomicReference<Observable<T>> cache = new AtomicReference<>();
    private final Observable<T> result;
    private final Semaphore singlePermit = new Semaphore(1);

    private OnErrorRetryCache(Observable<T> source) {
        result = Observable.defer(() -> createObservable(source));
    }

    private Observable<T> createObservable(Observable<T> source) {
        singlePermit.acquireUninterruptibly();

        for(;;) {
            Observable<T> cached = cache.get();
            if (cached != null) {
                singlePermit.release();
                return cached;
            }

            Observable<T> next = source
                    .doOnError(e -> cache.set(null))
                    .doOnTerminate(singlePermit::release)
                    .replay()
                    .autoConnect();

            if (cache.compareAndSet(null, next)) {
                return next;
            }
        }
    }
}
