package cc.monnshot.trade;

import cc.monnshot.HttpRpcApi;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import software.sava.anchor.programs.moonshot.anchor.types.CurveAccount;
import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.request.Commitment;
import software.sava.rpc.json.http.response.AccountInfo;

@Slf4j
public class MoonshotCurveProgress {

  private static final long MAX_MCP = 799820983207404442L;

  public static double progress(PublicKey mint) {

    final ProgramDerivedAddress curvePDA =
        MonnshotPDAs.bondingCurvePDA(MonnshotProgram.MOONSHOT, mint);

    log.info("mintCurvePDA: {}", curvePDA.publicKey());
    final CompletableFuture<AccountInfo<CurveAccount>> accountInfoCompletableFuture =
        HttpRpcApi.httpRpcApi()
            .getSolanaRpcClient()
            .getAccountInfo(Commitment.CONFIRMED, curvePDA.publicKey(), CurveAccount.FACTORY);

    final AccountInfo<CurveAccount> curveAccountAccountInfo = accountInfoCompletableFuture.join();

    final CurveAccount curveAccount = curveAccountAccountInfo.data();

    final long totalSupply = curveAccount.totalSupply();
    final long curveAmount = curveAccount.curveAmount();

    return progress(totalSupply, curveAmount);
  }

  public static double progress(Long totalSupply, long curveAmount) {

    log.info("totalSupply: {}, curveAmount: {}", totalSupply, curveAmount);

    final long realTokenReserves = totalSupply - curveAmount;

    log.info("realTokenReserves: {}", realTokenReserves);

    final double progress = (double) realTokenReserves / MAX_MCP * 100;
    return Math.round(progress * 100.0) / 100.0;
  }
}
