package cc.monnshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.core.encoding.Base58;
import software.sava.rpc.json.http.SolanaNetwork;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.request.Commitment;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.TokenAmount;
import software.sava.rpc.json.http.response.Tx;
import software.sava.rpc.json.http.response.TxStatus;

@Getter
@Slf4j
public class HttpRpcApi {

  private String endpoint;

  private final HttpClient httpClient;

  private final SolanaRpcClient solanaRpcClient;

  private static final int MAX_RETRY = 3;

  private static final int REQUESTS_PER_SECOND = 8;


  private static final HttpRpcApi HTTP_RPC_API_PUBLICNODE =
      new HttpRpcApi("https://solana-rpc.publicnode.com");

  private static final HttpRpcApi HTTP_RPC_API_DEFAULT =
      new HttpRpcApi(SolanaNetwork.MAIN_NET.getEndpoint().toString());

  private static final PublicKey MPL_TOKEN_METADATA =
      PublicKey.fromBase58Encoded("metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s");

  private HttpRpcApi(String endpoint) {
    this.endpoint = endpoint;
    final String httpRpcProxy = System.getProperty("HTTP_RPC_PROXY", String.valueOf(false));
    if (Boolean.parseBoolean(httpRpcProxy)) {
      log.info("Proxy endpoint:{}", endpoint);
      this.httpClient =
          HttpClient.newBuilder()
              .proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1", 7890)))
              .connectTimeout(Duration.ofSeconds(5))
              .build();
    } else {
      this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }
    this.solanaRpcClient = SolanaRpcClient.createClient(URI.create(endpoint), httpClient);
  }

  private HttpRpcApi(String endpoint, HttpClient httpClient) {
    this.httpClient = httpClient;
    this.solanaRpcClient = SolanaRpcClient.createClient(URI.create(endpoint), httpClient);
  }

  private static final List<HttpRpcApi> RPC =
      List.of(HTTP_RPC_API_DEFAULT, HTTP_RPC_API_PUBLICNODE);

  public static HttpRpcApi httpRpcApi() {
    Random random = new Random();
    final HttpRpcApi httpRpcApi = RPC.get(random.nextInt(RPC.size()));
    log.debug("Current Rpc endpoint:{}", httpRpcApi.getEndpoint());
    return httpRpcApi;
  }

  public static List<AccountInfo<AddressLookupTable>> getMultipleAccounts(
      PublicKey[] lookupTableAccounts) {
    Supplier<List<AccountInfo<AddressLookupTable>>> call =
        () ->
            HttpRpcApi.httpRpcApi()
                .getSolanaRpcClient()
                .getMultipleAccounts(Arrays.asList(lookupTableAccounts), AddressLookupTable.FACTORY)
                .join();
    return retry(call, 3);
  }

  public static List<Tx> getTransaction(
      final Commitment commitment, final List<String> txSignature) {
    final List<Supplier<Tx>> suppliers =
        txSignature.stream()
            .map(
                (Function<String, Supplier<Tx>>)
                    s -> {
                      final Supplier<Tx> supplier =
                          () -> {
                            final Tx tx =
                                HttpRpcApi.httpRpcApi()
                                    .getSolanaRpcClient()
                                    .getTransaction(commitment, s)
                                    .join();
                            if (tx == null || tx.data() == null) {
                              log.trace("txSignature :{} is empty", s);
                              throw new IllegalStateException("Transaction data is null");
                            }
                            return tx;
                          };
                      final Tx tx = retry(supplier, 3);
                      return () -> tx;
                    })
            .toList();
    return AsyncVirtual.runSuppliersResult(suppliers);
  }

  public static HttpRpcApi httpRpcApiDefault() {
    return HTTP_RPC_API_DEFAULT;
  }

  public Tx getTx(String tx) {
    Supplier<Tx> getTx =
        () ->
            HttpRpcApi.httpRpcApi()
                .getSolanaRpcClient()
                .getTransaction(Commitment.CONFIRMED, tx)
                .join();
    return retryTimeOut(getTx, 3, 1000);
  }

  public static <T> T retry(Supplier<T> supplier, int maxRetry) {
    int call = 0;
    T result = null;
    while (call < maxRetry) {
      try {
        result = supplier.get();
        break;
      } catch (Exception e) {
        call++;
        log.trace("call method failed", e);
        try {
          TimeUnit.MILLISECONDS.sleep(10L * call);
        } catch (InterruptedException _) {
        }
      }
    }
    return result;
  }

  public static <T> T retryTimeOut(Supplier<T> supplier, int maxRetry, int timeOut) {
    int call = 0;
    T result = null;
    while (call < maxRetry) {
      try {
        result = supplier.get();
        break;
      } catch (Exception e) {
        call++;
        log.trace("call method failed", e);
        try {
          TimeUnit.MILLISECONDS.sleep((long) timeOut * call);
        } catch (InterruptedException _) {
        }
      }
    }
    return result;
  }


  public static String sendTransactionSkipPreflight(String signAndBase64Encode) {
    return sendTransactionSkipPreflight(signAndBase64Encode, 10);
  }

  public static String sendTransactionSkipPreflight(
      String signAndBase64Encode, int totalDurationSeconds) {
    log.info("sendTransactionSkipPreflight");
    AtomicBoolean shouldContinue = new AtomicBoolean(true); // 用于中断请求的信号
    final int rpcSize = RPC.size();
    for (int zoneId = 1; zoneId <= rpcSize; zoneId++) {
      final int currentZone = zoneId;
      Thread.ofVirtual()
          .start(
              () -> {
                final HttpRpcApi httpRpcApi = RPC.get(currentZone - 1);
                try {
                  for (int i = 0; i < totalDurationSeconds * REQUESTS_PER_SECOND; i++) {
                    if (!shouldContinue.get()) {
                      log.debug("Zone {} request interrupted.", currentZone);
                      break; // 跳出循环，终止请求
                    }
                    log.debug("Area:{},SendTransactions", currentZone);
                    _sendAreaTransactionsRequest(
                        httpRpcApi.getSolanaRpcClient(), signAndBase64Encode); // 执行请求逻辑
                    Thread.sleep(Duration.ofMillis(1000 / REQUESTS_PER_SECOND)); // 控制每秒发出5次请求
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  log.warn("Zone {} was interrupted.", currentZone);
                }
              });
    }
    log.debug("Http Rpc Confirmed Transaction");
    final String txId =
        confirmedTransaction(signAndBase64Encode, shouldContinue, totalDurationSeconds);
    log.info("Http Rpc Transaction tx :https://solscan.io/tx/{}", txId);
    return txId;
  }

  private static String confirmedTransaction(
      String sign, AtomicBoolean shouldContinue, int totalDurationSeconds) {
    long confirmedTotalDurationSeconds =
        System.currentTimeMillis() + Duration.ofSeconds(totalDurationSeconds).toMillis();
    final String txId = extractTxId(sign);
    while (System.currentTimeMillis() < confirmedTotalDurationSeconds) {
      if (!shouldContinue.get()) {
        break;
      }
      log.info("wait tx commitment confirmation");
      final TxStatus txStatus = getSignatureStatuses(txId);
      if (txStatus == null) {
        try {
          TimeUnit.MILLISECONDS.sleep(200);
        } catch (InterruptedException _) {

        }
        continue;
      }
      final Commitment confirmationStatus = txStatus.confirmationStatus();
      if (Commitment.CONFIRMED.equals(confirmationStatus)
          || Commitment.FINALIZED.equals(confirmationStatus)) {
        shouldContinue.set(false);
        log.info("tx {} confirmed!", txId);
        break;
      }
      try {
        TimeUnit.MILLISECONDS.sleep(200);
      } catch (InterruptedException _) {
      }
    }
    return txId;
  }

  private static TxStatus getSignatureStatuses(String txId) {
    final Map<String, TxStatus> statusMap =
        HttpRpcApi.httpRpcApi().getSolanaRpcClient().getSignatureStatuses(List.of(txId)).join();
    return statusMap.get(txId);
  }

  private static String extractTxId(String signAndBase64Encode) {
    final byte[] data = Base64.getDecoder().decode(signAndBase64Encode);
    return Base58.encode(data, 1, 1 + 64);
  }

  private static void _sendAreaTransactionsRequest(SolanaRpcClient solanaRpcClient, String sign) {
    try {
      solanaRpcClient.sendTransactionSkipPreflight(Commitment.CONFIRMED, sign, 0).join();
    } catch (Exception e) {
      log.error("_sendAreaTransactionsRequest Error", e);
    }
  }
}
