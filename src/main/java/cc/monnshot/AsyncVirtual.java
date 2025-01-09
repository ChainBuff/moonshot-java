package cc.monnshot;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AsyncVirtual {

  public static <T> CompletableFuture<T> run(Supplier<T> supplier) {
    try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
      return CompletableFuture.supplyAsync(supplier, executorService);
    }
  }

  public static <T> List<CompletableFuture<T>> run(List<Supplier<T>> suppliers) {
    try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
      return suppliers.stream()
          .map(supplier -> CompletableFuture.supplyAsync(supplier, executorService))
          .toList();
    }
  }

  public static List<CompletableFuture<?>> runSuppliers(final List<Supplier<?>> functionCalls) {
    try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
      final List<CompletableFuture<?>> completableFutures =
          functionCalls.stream()
              .map(
                  (Function<Supplier<?>, CompletableFuture<?>>)
                      supplier -> CompletableFuture.supplyAsync(supplier, executorService))
              .toList();
      return runCompletableFutures(completableFutures);
    }
  }

  public static <T> List<CompletableFuture<T>> runSuppliersObject(
      final List<Supplier<T>> functionCalls) {
    try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
      final List<CompletableFuture<T>> completableFutures =
          functionCalls.stream()
              .map(supplier -> CompletableFuture.supplyAsync(supplier, executorService))
              .toList();
      CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();
      return completableFutures;
    }
  }

  public static <T> List<T> runSuppliersResult(final List<Supplier<T>> functionCalls) {
    try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
      final List<CompletableFuture<T>> completableFutures =
          functionCalls.stream()
              .map(supplier -> CompletableFuture.supplyAsync(supplier, executorService))
              .toList();
      CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();
      return completableFutures.stream().map(CompletableFuture::join).toList();
    }
  }

  public static <T> List<T> getRunSuppliers(List<CompletableFuture<T>> completableFutures) {
    CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();
    return completableFutures.stream().map(CompletableFuture::join).toList();
  }

  public static <T> List<CompletableFuture<?>> runCompletableFutures(
      final List<CompletableFuture<?>> completableFutures) {
    CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();
    return completableFutures;
  }

  @SuppressWarnings("unchecked")
  public static <T> T getCallRemoteResult(CompletableFuture<?> completableFuture) {
    return (T) completableFuture.join();
  }
}
