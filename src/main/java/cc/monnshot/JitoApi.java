package cc.monnshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.encoding.Base58;
import software.sava.core.tx.Instruction;
import software.sava.rpc.json.http.request.Commitment;
import software.sava.rpc.json.http.response.TxStatus;
import software.sava.solana.programs.system.SystemProgram;
import software.sava.solana.web2.jito.client.http.JitoClient;

import static cc.monnshot.HttpClient.HTTP_CLIENT;

@Slf4j
public class JitoApi {

  private static final String MAIN_URL = "https://mainnet.block-engine.jito.wtf";

  private static final String AMSTERDAM_URL = "https://amsterdam.mainnet.block-engine.jito.wtf";

  private static final String FRANKFURT_URL = "https://frankfurt.mainnet.block-engine.jito.wtf";

  private static final String NY_URL = "https://ny.mainnet.block-engine.jito.wtf";

  private static final String TOKYO_URL = "https://tokyo.mainnet.block-engine.jito.wtf";

  private static final String SLC_URL = "https://slc.mainnet.block-engine.jito.wtf";

  private static final String TRANSACTIONS_API = "/api/v1/transactions";

  private static final Duration TIMEOUT = Duration.ofMillis(5000);

  private static final long LAMPORTS_PER_SOL = 1_000_000_000; // 1 SOL = 10^9 lamports

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static final JitoClient SLC_RPC_TRANSACTIONS_CLIENT =
      JitoClient.createHttpClient(
          buildUrl(SLC_URL, TRANSACTIONS_API),HTTP_CLIENT, TIMEOUT, Commitment.CONFIRMED);

  private static final JitoClient MAIN_RPC_TRANSACTIONS_CLIENT =
      JitoClient.createHttpClient(
          buildUrl(MAIN_URL, TRANSACTIONS_API), HTTP_CLIENT, TIMEOUT, Commitment.CONFIRMED);

  private static final JitoClient AMSTERDAM_RPC_TRANSACTIONS_CLIENT =
      JitoClient.createHttpClient(
          buildUrl(AMSTERDAM_URL, TRANSACTIONS_API), HTTP_CLIENT, TIMEOUT, Commitment.CONFIRMED);

  private static final JitoClient FRANKFURT_RPC_TRANSACTIONS_CLIENT =
      JitoClient.createHttpClient(
          buildUrl(FRANKFURT_URL, TRANSACTIONS_API), HTTP_CLIENT, TIMEOUT, Commitment.CONFIRMED);

  private static final JitoClient NY_RPC_TRANSACTIONS_CLIENT =
      JitoClient.createHttpClient(
          buildUrl(NY_URL, TRANSACTIONS_API), HTTP_CLIENT, TIMEOUT, Commitment.CONFIRMED);

  private static final JitoClient TOKYO_RPC_TRANSACTIONS_CLIENT =
      JitoClient.createHttpClient(
          buildUrl(TOKYO_URL, TRANSACTIONS_API),HTTP_CLIENT, TIMEOUT, Commitment.CONFIRMED);

  private static final Double DEFAULT_TIP_FEE = 0.0025D;

  public static final List<JitoClient> JITO_TRANSACTIONS_CLIENTS =
      List.of(
          SLC_RPC_TRANSACTIONS_CLIENT, // first 该区域延迟最低
          NY_RPC_TRANSACTIONS_CLIENT,
          TOKYO_RPC_TRANSACTIONS_CLIENT,
          MAIN_RPC_TRANSACTIONS_CLIENT,
          AMSTERDAM_RPC_TRANSACTIONS_CLIENT,
          FRANKFURT_RPC_TRANSACTIONS_CLIENT);

  // requests per second per IP per region. 每个区域每个 IP 每秒 5 个请求。  // 每200ms 等待
  private static final double REQUESTS_PER_SECOND = 5d;

  // max zone number
  private static final int ZONE_COUNT = 6;

  // send tx
  private static final int TOTAL_DURATION_SECONDS = 10; // 总共请求的持续时间

  private static URI buildUrl(String api, String path) {
    return URI.create(api + path);
  }

  private static final List<PublicKey> TIP_ACCOUNTS =
      List.of(
          PublicKey.fromBase58Encoded("DfXygSm4jCyNCybVYYK6DwvWqjKee8pbDmJGcLWNDXjh"),
          PublicKey.fromBase58Encoded("ADaUMid9yfUytqMBgopwjb2DTLSokTSzL1zt6iGPaS49"),
          PublicKey.fromBase58Encoded("HFqU5x63VTqvQss8hp11i4wVV8bD44PvwucfZ2bU7gRe"),
          PublicKey.fromBase58Encoded("96gYZGLnJYVFmbjzopPSU6QiEV5fGqZNyN9nmNhvrZU5"),
          PublicKey.fromBase58Encoded("Cw8CFyM9FkoMi7K7Crf6HNQqf4uEMzpKw6QNghXLvLkY"),
          PublicKey.fromBase58Encoded("DttWaMuVvTiduZRnguLF7jNxTgiMBZ1hyAumKUiL2KRL"),
          PublicKey.fromBase58Encoded("3AVi9Tg9Uo68tJfuvoKvqKNWKkC5wPdSSdeBnizKZ6jT"),
          PublicKey.fromBase58Encoded("ADuUkR4vqLUMWXxW9gh6D6L8pMSawimctcNZ5pGwDcEt"));



  public static PublicKey getTipAccount() {
    Random random = new Random();
    return TIP_ACCOUNTS.get(random.nextInt(TIP_ACCOUNTS.size()));
  }

  public static Instruction createJitoTip(PublicKey publicKey) {
    final TipFloor.TipData tipfloor;
    try {
      tipfloor = getTipfloor();
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
    return createJitoTip(publicKey, tipfloor.low.value);
  }

  public static Instruction createJitoTip(PublicKey from, Double tipFee) {
    final double fee = tipFee * LAMPORTS_PER_SOL;
    final PublicKey tipAccount = getTipAccount();
    return SystemProgram.transfer(
        SolanaAccounts.MAIN_NET.invokedSystemProgram(), from, tipAccount, (long) fee);
  }

  public static String sendTransactions(String base64SignedTx) {
    return sendTransactions(base64SignedTx, TOTAL_DURATION_SECONDS);
  }

  public static String sendTransactions(String base64SignedTx, int totalDurationSeconds) {
    log.info("SendTransactions tx");
    AtomicBoolean shouldContinue = new AtomicBoolean(true);
    for (int zoneId = 1; zoneId <= ZONE_COUNT; zoneId++) {
      final int currentZone = zoneId;
      Thread.ofVirtual()
          .start(
              () -> {
                final JitoClient solanaRpcClient = JITO_TRANSACTIONS_CLIENTS.get(currentZone - 1);
                try {
                  for (int i = 0; i < totalDurationSeconds * REQUESTS_PER_SECOND; i++) {
                    if (!shouldContinue.get()) {
                      log.debug("Zone {} request interrupted.", currentZone);
                      break; // 跳出循环，终止请求
                    }
                    log.debug("Area:{},SendTransactions", currentZone);
                    _sendAreaTransactionsRequest(solanaRpcClient, base64SignedTx); // 执行请求逻辑
                    Thread.sleep(
                        Duration.ofMillis((long) (1000 / REQUESTS_PER_SECOND))); // 控制每秒发出5次请求
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  log.warn("Zone {} was interrupted.", currentZone);
                }
              });
    }
    log.info("Confirmed Transaction");
    final String txId = confirmedTransaction(base64SignedTx, shouldContinue, totalDurationSeconds);
    log.info("Transaction tx :https://solscan.io/tx/{}", txId);
    return txId;
  }

  public static String confirmedTransaction(
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
          TimeUnit.MILLISECONDS.sleep(100);
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
        TimeUnit.MILLISECONDS.sleep(100);
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

  private static void _sendAreaTransactionsRequest(JitoClient jitoClient, String base64SignedTx) {
    try {
      jitoClient.sendTransactionSkipPreflight(Commitment.CONFIRMED, base64SignedTx, 0).join();
    } catch (Exception e) {
      log.error("_sendAreaTransactionsRequest Error", e);
    }
  }

  public static TipFloor.TipData getTipfloor() throws IOException, InterruptedException {
    final String body=
              HTTP_CLIENT
              .send(
                  HttpRequest.newBuilder()
                      .uri(URI.create("https://bundles.jito.wtf/api/v1/bundles/tip_floor"))
                      .build(),
                  HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
              .body();
    final JsonNode jsonNode = objectMapper.readTree(body);
    // 从 JsonNode 中获取各字段并创建 TipFloor 实例
    return TipFloor.parse(jsonNode.get(0));
  }

  public record TipFloor(
      String time,
      Double landed_tips_25th_percentile,
      Double landed_tips_50th_percentile,
      Double landed_tips_75th_percentile,
      Double landed_tips_95th_percentile,
      Double landed_tips_99th_percentile,
      Double ema_landed_tips_50th_percentile) {

    // 枚举类，定义低、中、中高、高、极高五种类型
    public enum TipTypeEnum {
      LOW,
      MEDIUM,
      MEDIUM_HIGH,
      HIGH,
      EXTREMELY_HIGH
    }

    public static TipData parse(JsonNode jsonNode) {
      // 从 JsonNode 中获取 TipFloor 实例
      TipFloor tipFloor =
          new TipFloor(
              jsonNode.get("time").asText(),
              jsonNode.get("landed_tips_25th_percentile").asDouble(),
              jsonNode.get("landed_tips_50th_percentile").asDouble(),
              jsonNode.get("landed_tips_75th_percentile").asDouble(),
              jsonNode.get("landed_tips_95th_percentile").asDouble(),
              jsonNode.get("landed_tips_99th_percentile").asDouble(),
              jsonNode.get("ema_landed_tips_50th_percentile").asDouble());

      // 封装低、中、中高、高、极高五种类型
      return new TipData(
          new TipType(TipTypeEnum.LOW, tipFloor.landed_tips_25th_percentile()),
          new TipType(TipTypeEnum.MEDIUM, tipFloor.landed_tips_50th_percentile()),
          new TipType(TipTypeEnum.MEDIUM_HIGH, tipFloor.landed_tips_75th_percentile()),
          new TipType(TipTypeEnum.HIGH, tipFloor.landed_tips_95th_percentile()),
          new TipType(TipTypeEnum.EXTREMELY_HIGH, tipFloor.landed_tips_99th_percentile()));
    }

    public record TipType(TipTypeEnum type, Double value) {
      public static final double S_SO = 0.0001;

      @Override
      public Double value() {
        return value;
      }

      @Override
      public String toString() {
        return "TipType{" + "type=" + type + ", value=" + String.format("%.9f", value) + '}';
      }
    }

    public record TipData(
        TipType low, TipType medium, TipType mediumHigh, TipType high, TipType extremelyHigh) {

      public TipType getTipType(String name) {
        final TipTypeEnum tipTypeEnum = TipTypeEnum.valueOf(name.toUpperCase());
        switch (tipTypeEnum) {
          case LOW -> {
            return low;
          }
          case MEDIUM -> {
            return medium;
          }
          case MEDIUM_HIGH -> {
            return mediumHigh;
          }
          case HIGH -> {
            return high;
          }
          case EXTREMELY_HIGH -> {
            return extremelyHigh;
          }
          case null, default -> {
            throw new IllegalArgumentException("Tip type " + name + " not supported");
          }
        }
      }
    }
  }
}
