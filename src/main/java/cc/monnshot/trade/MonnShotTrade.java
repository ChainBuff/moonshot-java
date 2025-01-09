package cc.monnshot.trade;


import cc.monnshot.AsyncVirtual;
import cc.monnshot.HttpRpcApi;
import cc.monnshot.JitoApi;
import cc.monnshot.Mint;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import software.sava.anchor.programs.moonshot.anchor.TokenLaunchpadProgram;
import software.sava.anchor.programs.moonshot.anchor.types.CurveAccount;
import software.sava.anchor.programs.moonshot.anchor.types.TradeParams;
import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.Signer;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.core.util.LamportDecimal;
import software.sava.rpc.json.http.request.Commitment;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.LatestBlockHash;
import software.sava.rpc.json.http.response.TokenAmount;

import static cc.monnshot.HttpClient.HTTP_CLIENT;

@Slf4j
public class MonnShotTrade {


  private static final String API_PATH_TOKEN_CREATE = "https://api.moonshot.cc/tokens/v1";

  private static final String API_PATH_TOKEN_SUBMIT = "https://api.moonshot.cc/tokens/v1/%s/submit";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public MonnShotTrade() {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MintTokenPrepareV1Request(
      String chainId,
      String creatorId,
      String name,
      String symbol,
      String curveType,
      String migrationDex,
      String icon,
      String description,
      String tokenAmount,
      List<Link> links,
      String banner) {}

  @SneakyThrows
  private PrepareMintTxResponse prepareMintTx(MintTokenPrepareV1Request mintTokenPrepareV1Request) {
    log.info("prepareMintTx:{}", mintTokenPrepareV1Request);
    final HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create(API_PATH_TOKEN_CREATE))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(mintTokenPrepareV1Request)))
            .build();
    final HttpResponse<String> response = HTTP_CLIENT
            .send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if(response.statusCode() != 200) {
      throw new IllegalStateException("Unexpected response code: " + response.statusCode());
    }
    final String body = response.body();
    final PrepareMintTxResponse mintTxResponse =
            MAPPER.readValue(body, PrepareMintTxResponse.class);
    log.info("body:{}", mintTxResponse);
    return mintTxResponse;
  }

  public record PrepareMintTxResponse(String draftTokenId, String token, String transaction) {}

  /**
   * 购买参数
   *
   * @param buySol 购买sol 数量
   * @param buySlippage 滑点 5% 5*100=500
   */
  public record BuyOptions(
      BigDecimal buySol, int buySlippage, String priorityFeeLevel, String jitoTipLevel) {}

  /**
   * 卖出参数
   *
   * @param sellRatio 卖出比例
   * @param sellSlippage 滑点 5% 5*100=500
   */
  public record SellOptions(
      BigDecimal sellRatio, int sellSlippage, String priorityFeeLevel, String jitoTipLevel) {}

  public record CreateOptions(
      String icon,
      String name,
      String symbol,
      CurveType curveType,
      MigrationDex migrationDex,
      String description,
      Long tokenAmount,
      List<Link> links,
      String banner) {}

  public record Link(String url, String label) {}

  @AllArgsConstructor
  @Getter
  public enum CurveType {
    LINEAR_V1("LINEAR_V1"),
    CONSTANT_PRODUCT_V1("CONSTANT_PRODUCT_V1"),
    BASE_CONSTANT_PRODUCT_V1("BASE_CONSTANT_PRODUCT_V1");
    private final String name;
  }

  @AllArgsConstructor
  @Getter
  public enum MigrationDex {
    RAYDIUM("RAYDIUM"),
    METEORA("METEORA"),
    UNISWAP("UNISWAP");

    private final String name;
  }

  private BigInteger curvePosition(CurveAccount curveAccount) {
    return BigInteger.valueOf(curveAccount.totalSupply() - curveAccount.curveAmount());
  }

  /**
   * 创建 & buy token
   *
   * @param signer 签名
   * @param createOptions 创建参数
   */
  public void create(Signer signer, CreateOptions createOptions) {
    MintTokenPrepareV1Request mintTokenPrepareV1Request =
        new MintTokenPrepareV1Request(
            "solana",
            signer.publicKey().toBase58(),
            createOptions.name,
            createOptions.symbol,
            createOptions.curveType.getName(),
            createOptions.migrationDex.name,
            createOptions.icon,
            createOptions.description,
            createOptions.tokenAmount == null ? null : createOptions.tokenAmount.toString(),
            createOptions.links,
            createOptions.banner);
    final PrepareMintTxResponse prepareMintTxResponse = prepareMintTx(mintTokenPrepareV1Request);
    final String transaction = prepareMintTxResponse.transaction;
    if (transaction == null) {
      throw new IllegalStateException("create error!");
    }

    final byte[] bytes = Base64.getDecoder().decode(transaction);

    final String signAndBase64Encode = Transaction.signAndBase64Encode(List.of(signer), bytes);

    submitMint(
        prepareMintTxResponse.draftTokenId,
        new MintTokenSubmitV1Request(prepareMintTxResponse.token, signAndBase64Encode));
  }

  private record MintTokenSubmitV1Request(String token, String signedTransaction) {}

  private record MintTokenSubmitV1Response(String txnId, String status, String statusToken) {}

  @SneakyThrows
  private void submitMint(String draftTokenId, MintTokenSubmitV1Request mintTokenSubmitV1Request) {
    final String url = String.format(API_PATH_TOKEN_SUBMIT, draftTokenId);
    log.info("uri:{},submitMint:{}", url, mintTokenSubmitV1Request);

    final String requestBody = MAPPER.writeValueAsString(mintTokenSubmitV1Request);
    final HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
    final HttpResponse<String> httpResponse = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if(httpResponse.statusCode() != 200) {
      throw new IllegalStateException("Unexpected response code: " + httpResponse.statusCode());
    }
    final String body = httpResponse.body();
    final MintTokenSubmitV1Response tokenSubmitV1Response = MAPPER.readValue(body, MintTokenSubmitV1Response.class);
    log.info("submitMint:{}", tokenSubmitV1Response);
  }

  public void buy(PublicKey mint, Signer signer, BuyOptions buyOptions) {
    buy(mint, signer, buyOptions, false);
  }

  /**
   * Buy moonshot mint
   *
   * @param mint mint token
   * @param signer singer
   * @param jito true 通过 jito 提交
   * @param buyOptions buy opt
   */
  public void buy(PublicKey mint, Signer signer, BuyOptions buyOptions, boolean jito) {

    final ProgramDerivedAddress curvePDA =
        MonnshotPDAs.bondingCurvePDA(MonnshotProgram.MOONSHOT, mint);
    final PublicKey bondingCurveKey = curvePDA.publicKey();

    final PublicKey mintAssociatedToken = Mint._associatedToken(signer.publicKey(), mint);

    final PublicKey curveAssociatedToken = Mint._associatedToken(bondingCurveKey, mint);

    final CompletableFuture<AccountInfo<CurveAccount>> accountInfoCompletableFuture =
        HttpRpcApi.httpRpcApi()
            .getSolanaRpcClient()
            .getAccountInfo(Commitment.CONFIRMED, bondingCurveKey, CurveAccount.FACTORY);

    final CompletableFuture<LatestBlockHash> latestBlockHashCompletableFuture =
        HttpRpcApi.httpRpcApi().getSolanaRpcClient().getLatestBlockHash(Commitment.CONFIRMED);

    final List<CompletableFuture<?>> completableFuture =
        AsyncVirtual.runCompletableFutures(
            List.of(accountInfoCompletableFuture, latestBlockHashCompletableFuture));

    final AccountInfo<CurveAccount> curveAccountAccountInfo =
        AsyncVirtual.getCallRemoteResult(completableFuture.getFirst());

    final BigDecimal solAmount = buyOptions.buySol().movePointRight(LamportDecimal.LAMPORT_DIGITS);

    final CurveAccount curveAccount = curveAccountAccountInfo.data();

    if (curveAccount == null) {
      throw new IllegalStateException("MigrateFunds!");
    }

    final CurveAdapter curveAdapter = getCurveAdapter(curveAccount);

    final BigInteger tokenAmount =
        getTokenAmountByCollateralSync(
            new GetTokenAmountSyncOptions(
                curvePosition(curveAccount), solAmount.toBigInteger(), true),
            curveAdapter);

    TradeParams tradeParams =
        new TradeParams(
            tokenAmount.longValue(),
            solAmount.longValue(),
            FixedSide.IN.getValue(),
            buyOptions.buySlippage() * 100L);

    log.info("TradeParams:{}", tradeParams);

    List<Instruction> instructions = new LinkedList<>();

    if (!jito) {
      instructions.add(Mint._setComputeUnitPrice(getUnitPrice(buyOptions.priorityFeeLevel)));
    } else {
      instructions.add(Mint._jitoTip(signer.publicKey(), tipFloor(buyOptions.jitoTipLevel)));
    }
    final Instruction buy =
        TokenLaunchpadProgram.buy(
            AccountMeta.createInvoked(MonnshotProgram.MOONSHOT),
            signer.publicKey(),
            mintAssociatedToken,
            curvePDA.publicKey(),
            curveAssociatedToken,
            MonnshotProgram.DEX_FEE,
            MonnshotProgram.HELIO_FEE,
            mint,
            MonnshotProgram.CONFIG,
            SolanaAccounts.MAIN_NET.tokenProgram(),
            SolanaAccounts.MAIN_NET.associatedTokenAccountProgram(),
            SolanaAccounts.MAIN_NET.systemProgram(),
            tradeParams);
    instructions.add(buy);
    final Transaction transaction = Transaction.createTx(instructions);
    LatestBlockHash latestBlockHash = AsyncVirtual.getCallRemoteResult(completableFuture.getLast());
    transaction.setRecentBlockHash(latestBlockHash.blockHash());
    transaction.sign(signer);
    if (jito) {
      final String tx = JitoApi.sendTransactions(transaction.base64EncodeToString());
      log.info("Buy Jito transactionResult:{}", tx);
    } else {
      final String tx = HttpRpcApi.sendTransactionSkipPreflight(transaction.base64EncodeToString());
      log.info("buy tx:{}", tx);
    }
  }

  public void sell(PublicKey mint, Signer signer, SellOptions sellOptions) {
    sell(mint, signer, sellOptions, false);
  }

  public void sell(PublicKey mint, Signer signer, SellOptions sellOptions, Boolean jito) {

    final ProgramDerivedAddress curvePDA =
        MonnshotPDAs.bondingCurvePDA(MonnshotProgram.MOONSHOT, mint);
    final PublicKey bondingCurveKey = curvePDA.publicKey();

    final PublicKey mintAssociatedToken = Mint._associatedToken(signer.publicKey(), mint);

    final PublicKey curveAssociatedToken = Mint._associatedToken(bondingCurveKey, mint);

    final CompletableFuture<AccountInfo<CurveAccount>> accountInfoCompletableFuture =
        HttpRpcApi.httpRpcApi()
            .getSolanaRpcClient()
            .getAccountInfo(Commitment.CONFIRMED, bondingCurveKey, CurveAccount.FACTORY);

    final CompletableFuture<LatestBlockHash> latestBlockHashCompletableFuture =
        HttpRpcApi.httpRpcApi().getSolanaRpcClient().getLatestBlockHash(Commitment.CONFIRMED);

    final CompletableFuture<TokenAmount> tokenAccountBalance =
        HttpRpcApi.httpRpcApi()
            .getSolanaRpcClient()
            .getTokenAccountBalance(Commitment.CONFIRMED, mintAssociatedToken);

    final List<CompletableFuture<?>> completableFuture =
        AsyncVirtual.runCompletableFutures(
            List.of(
                accountInfoCompletableFuture,
                tokenAccountBalance,
                latestBlockHashCompletableFuture));

    final AccountInfo<CurveAccount> curveAccountAccountInfo =
        AsyncVirtual.getCallRemoteResult(completableFuture.getFirst());
    final CurveAccount curveAccount = curveAccountAccountInfo.data();

    if (curveAccount == null) {
      throw new IllegalStateException("MigrateFunds!");
    }

    final CurveAdapter curveAdapter = getCurveAdapter(curveAccount);

    final TokenAmount tokenAmount = AsyncVirtual.getCallRemoteResult(completableFuture.get(1));
    final BigDecimal sellRatio = sellOptions.sellRatio();

    final BigInteger sellTokenAmount =
        new BigDecimal(tokenAmount.amount()).multiply(sellRatio).toBigInteger();

    final BigInteger solAmount =
        curveAdapter.getCollateralAmountByTokensSync(
            new GetCollateralAmountSyncOptions(
                curvePosition(curveAccount), sellTokenAmount, false));

    TradeParams tradeParams =
        new TradeParams(
            sellTokenAmount.longValue(),
            solAmount.longValue(),
            FixedSide.IN.getValue(),
            sellOptions.sellSlippage() * 100L);

    List<Instruction> instructions = new LinkedList<>();

    if (!jito) {
      instructions.add(Mint._setComputeUnitPrice(getUnitPrice(sellOptions.priorityFeeLevel)));
    } else {
      instructions.add(Mint._jitoTip(signer.publicKey(), tipFloor(sellOptions.jitoTipLevel)));
    }

    final Instruction sell =
        TokenLaunchpadProgram.sell(
            AccountMeta.createInvoked(MonnshotProgram.MOONSHOT),
            signer.publicKey(),
            mintAssociatedToken,
            curvePDA.publicKey(),
            curveAssociatedToken,
            MonnshotProgram.DEX_FEE,
            MonnshotProgram.HELIO_FEE,
            mint,
            MonnshotProgram.CONFIG,
            SolanaAccounts.MAIN_NET.tokenProgram(),
            SolanaAccounts.MAIN_NET.associatedTokenAccountProgram(),
            SolanaAccounts.MAIN_NET.systemProgram(),
            tradeParams);
    instructions.add(sell);

    if (sellOptions.sellRatio.intValue() == 1) {
      log.info("_add close token account");
      final Instruction closeAccount = Mint._closeAccount(mintAssociatedToken, signer.publicKey());
      instructions.add(closeAccount);
    }

    final Transaction transaction = Transaction.createTx(instructions);
    LatestBlockHash latestBlockHash = AsyncVirtual.getCallRemoteResult(completableFuture.getLast());
    transaction.setRecentBlockHash(latestBlockHash.blockHash());
    transaction.sign(signer);

    if (jito) {

      final String tx = JitoApi.sendTransactions(transaction.base64EncodeToString());
      log.info("Sell transactionResult:{}", tx);

    } else {
      final String tx = HttpRpcApi.sendTransactionSkipPreflight(transaction.base64EncodeToString());
      log.info("sell tx:{}", tx);
    }
  }

  private long getUnitPrice(String priorityFeeLevel) {
    return 200_000L;
  }

  private static BigDecimal tipFloor(String jitoTipLevel) {
    // todo 通过订阅wss://bundles.jito.wtf/api/v1/bundles/tip_stream 获取实时 tip
    //    final JitoApi.TipFloor.TipData       tipData = JitoTipFee.currentTip();
    // 使用固定小费
    return BigDecimal.valueOf(0.00001605);
  }

  private CurveAdapter getCurveAdapter(CurveAccount curveAccount) {
    CurveAdapter curveAdapter;
    switch (curveAccount.curveType()) {
      case LinearV1 -> curveAdapter = new LinearCurveV1Adapter();
      case ConstantProductV1 -> curveAdapter = new ConstantProductCurveV1Adapter();
      case null, default ->
          throw new IllegalStateException("Unexpected value: " + curveAccount.curveType());
    }
    return curveAdapter;
  }

  public record GetTokenAmountSyncOptions(
      BigInteger curvePosition, BigInteger collateralAmount, Boolean isBuy) {}

  public record GetCollateralAmountSyncOptions(
      BigInteger curvePosition, BigInteger tokenAmount, Boolean isBuy) {}

  private BigInteger getTokenAmountByCollateralSync(
      GetTokenAmountSyncOptions getTokenAmountSyncOptions, CurveAdapter curveAdapter) {
    return curveAdapter.getTokenAmountByCollateralSync(getTokenAmountSyncOptions);
  }

  public interface CurveAdapter {

    BigInteger getTokenAmountByCollateralSync(GetTokenAmountSyncOptions getTokenAmountSyncOptions);

    BigInteger getCollateralAmountByTokensSync(
        GetCollateralAmountSyncOptions getCollateralAmountSyncOptions);
  }

  public static class ConstantProductCurveV1Adapter implements CurveAdapter {

    private static final int platformFeeBps = 100;

    private static final BigInteger initialVirtualTokenReserves =
        new BigInteger("1073000000000000000");

    private static final BigInteger initialVirtualCollateralReserves =
        new BigInteger("30000000000");

    private static final BigInteger constantProduct =
        initialVirtualTokenReserves.multiply(initialVirtualCollateralReserves);

    private record CurrentReserves(
        BigInteger currentVirtualTokenReserves, BigInteger currentVirtualCollateralReserves) {}

    @Override
    public BigInteger getTokenAmountByCollateralSync(
        GetTokenAmountSyncOptions getTokenAmountSyncOptions) {
      BigInteger amount = getTokenAmountSyncOptions.collateralAmount;
      final BigInteger curvePosition = getTokenAmountSyncOptions.curvePosition;
      final Boolean isBuy = getTokenAmountSyncOptions.isBuy;
      if (isBuy) {
        BigInteger collateralAmount =
            amount.subtract(
                amount
                    .multiply(BigInteger.valueOf(platformFeeBps))
                    .divide(BigInteger.valueOf(10000)));

        return buyInCollateral(collateralAmount, curvePosition);
      }
      BigInteger collateralAmount =
          amount.add(
              amount
                  .multiply(BigInteger.valueOf(platformFeeBps))
                  .divide(BigInteger.valueOf(10000)));
      return sellInCollateral(collateralAmount, curvePosition);
    }

    private BigInteger sellInCollateral(BigInteger collateralAmount, BigInteger curvePosition) {
      final CurrentReserves currentReserves = getCurrentReserves(curvePosition);
      final BigInteger currentVirtualTokenReserves = currentReserves.currentVirtualTokenReserves;
      final BigInteger currentVirtualCollateralReserves =
          currentReserves.currentVirtualCollateralReserves;

      final BigInteger newCollateralReserves =
          currentVirtualCollateralReserves.subtract(collateralAmount);

      final BigInteger ratio = constantProduct.divide(newCollateralReserves);

      return ratio.subtract(currentVirtualTokenReserves);
    }

    private BigInteger buyInCollateral(
        BigInteger collateralAmount, final BigInteger curvePosition) {

      final CurrentReserves currentReserves = getCurrentReserves(curvePosition);
      final BigInteger currentVirtualTokenReserves = currentReserves.currentVirtualTokenReserves;
      final BigInteger currentVirtualCollateralReserves =
          currentReserves.currentVirtualCollateralReserves;

      final BigInteger newCollateralReserves =
          currentVirtualCollateralReserves.add(collateralAmount);

      final BigInteger ratio = constantProduct.divide(newCollateralReserves);

      return currentVirtualTokenReserves.subtract(ratio);
    }

    private CurrentReserves getCurrentReserves(final BigInteger curvePosition) {
      final BigInteger currentVirtualTokenReserves =
          initialVirtualTokenReserves.subtract(curvePosition);
      final BigInteger currentVirtualCollateralReserves =
          constantProduct.divide(currentVirtualTokenReserves);
      return new CurrentReserves(currentVirtualTokenReserves, currentVirtualCollateralReserves);
    }

    @Override
    public BigInteger getCollateralAmountByTokensSync(
        GetCollateralAmountSyncOptions getCollateralAmountSyncOptions) {

      final BigInteger curvePosition = getCollateralAmountSyncOptions.curvePosition;

      final BigInteger tokenAmount = getCollateralAmountSyncOptions.tokenAmount;

      final Boolean isBuy = getCollateralAmountSyncOptions.isBuy;
      if (isBuy) {
        final BigInteger collateralAmount = buyInToken(tokenAmount, curvePosition);
        return collateralAmount.add(
            (collateralAmount
                .multiply(BigInteger.valueOf(platformFeeBps))
                .divide(BigInteger.valueOf(10000))));
      }
      final BigInteger collateralAmount = sellInToken(tokenAmount, curvePosition);
      return collateralAmount.subtract(
          (collateralAmount
              .multiply(BigInteger.valueOf(platformFeeBps))
              .divide(BigInteger.valueOf(10000))));
    }

    private BigInteger sellInToken(BigInteger tokenAmount, BigInteger curvePosition) {

      final CurrentReserves currentReserves = getCurrentReserves(curvePosition);

      final BigInteger currentVirtualTokenReserves = currentReserves.currentVirtualTokenReserves;
      final BigInteger currentVirtualCollateralReserves =
          currentReserves.currentVirtualCollateralReserves;

      final BigInteger newTokenReserves = currentVirtualTokenReserves.add(tokenAmount);

      final BigInteger ratio = constantProduct.divide(newTokenReserves);
      return currentVirtualCollateralReserves.subtract(ratio);
    }

    private BigInteger buyInToken(final BigInteger tokenAmount, final BigInteger curvePosition) {
      final CurrentReserves currentReserves = getCurrentReserves(curvePosition);

      final BigInteger currentVirtualTokenReserves = currentReserves.currentVirtualTokenReserves;
      final BigInteger currentVirtualCollateralReserves =
          currentReserves.currentVirtualCollateralReserves;
      final BigInteger newTokenReserves = currentVirtualTokenReserves.subtract(tokenAmount);
      final BigInteger ratio = constantProduct.divide(newTokenReserves);
      return ratio.subtract(currentVirtualCollateralReserves);
    }
  }

  public static class LinearCurveV1Adapter implements CurveAdapter {

    @Override
    public BigInteger getTokenAmountByCollateralSync(
        GetTokenAmountSyncOptions getTokenAmountSyncOptions) {
      throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public BigInteger getCollateralAmountByTokensSync(
        GetCollateralAmountSyncOptions getCollateralAmountSyncOptions) {
      throw new UnsupportedOperationException("Not supported yet.");
    }
  }
}
