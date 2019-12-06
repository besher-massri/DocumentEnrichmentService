import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

public class ExecutorService {
    private static class Result {
        private final long wait;
        Result(long code) {
            this.wait = code;
        }
    }


    public static<T> void  parallelize(List<T> inputs, Function<T,Long> func, int nThreads) throws InterruptedException,
            ExecutionException {


        List<Callable<Result>> tasks = new ArrayList<>();
        for (T object : inputs) {
            Callable<Result> c = () -> {
                long timeConsumed = func.apply(object);
                return new Result(timeConsumed);
            };
            tasks.add(c);
        }

        java.util.concurrent.ExecutorService exec = Executors.newFixedThreadPool(nThreads);
        // some other exectuors you could try to see the different behaviours
        // ExecutorService exec = Executors.newFixedThreadPool(3);
        // ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            long start = System.currentTimeMillis();
            List<Future<Result>> results = exec.invokeAll(tasks);
            long sum=0L;
            for (Future<Result> fr : results) {
                sum += fr.get().wait;
                //System.out.println(String.format("Task waited %d ms", fr.get().wait));
            }
            long elapsed = System.currentTimeMillis() - start;
            //System.out.println(String.format("Elapsed time: %d ms", elapsed));
            //System.out.println(String.format("... but compute tasks waited for total of %d ms; speed-up of %.2fx", sum, ((double)sum) / (elapsed * 1d)));
        } finally {
            exec.shutdown();
        }
    }
}