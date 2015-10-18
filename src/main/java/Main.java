import rx.Observable;

import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    static AtomicInteger calculations = new AtomicInteger();

    public static void main(String[] args) {
        AtomicInteger calls = new AtomicInteger();
        Observable<Integer> source = Observable
                .just(1)
                .doOnSubscribe(() ->
                        System.out.println("Subscriptions: " + (1 + calls.get())))
                .flatMap(v -> {

                    calculations.incrementAndGet();

                    if (calls.getAndIncrement() == 0) {
                        return Observable.error(new RuntimeException());
                    }
                    return Observable.just(42);
                });
//
//        Observable<Integer> o = OnErrorRetryCache.from(source);
//
//        o.subscribe(System.out::println,
//                Throwable::printStackTrace,
//                () -> System.out.println("Done1" + calculations()));
//
//        o.subscribe(System.out::println,
//                Throwable::printStackTrace,
//                () -> System.out.println("Done2"  + calculations()));
//
//        o.subscribe(System.out::println,
//                Throwable::printStackTrace,
//                () -> System.out.println("Done3" + calculations()));
    }

    private static String calculations() {
        return " calculations " + calculations.get();
    }

}