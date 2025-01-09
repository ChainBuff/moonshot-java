package cc.monnshot;


import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.tx.Instruction;
import software.sava.core.util.LamportDecimal;
import software.sava.solana.programs.compute_budget.ComputeBudgetProgram;
import software.sava.solana.programs.system.SystemProgram;
import software.sava.solana.programs.token.AssociatedTokenProgram;
import software.sava.solana.programs.token.TokenProgram;

import static cc.monnshot.JitoApi.getTipAccount;

@Slf4j
public class Mint {

  public static PublicKey _associatedToken(PublicKey owner, PublicKey mint) {
    final ProgramDerivedAddress programDerivedAddress =
        AssociatedTokenProgram.findATA(SolanaAccounts.MAIN_NET, owner, mint);
    return programDerivedAddress.publicKey();
  }

  public static Instruction _jitoTip(PublicKey from, BigDecimal tipFeeSol) {
    final BigDecimal solAmount = tipFeeSol.movePointRight(LamportDecimal.LAMPORT_DIGITS);
    final PublicKey tipAccount = getTipAccount();
    return SystemProgram.transfer(
            SolanaAccounts.MAIN_NET.invokedSystemProgram(), from, tipAccount, solAmount.longValue());
  }

  public static Instruction _closeAccount(PublicKey associated, PublicKey owner) {
    return TokenProgram.closeAccount(
        SolanaAccounts.MAIN_NET.invokedTokenProgram(), associated, owner, owner);
  }

  public static Instruction _setComputeUnitPrice(long fee) {
    return ComputeBudgetProgram.setComputeUnitPrice(
        SolanaAccounts.MAIN_NET.invokedComputeBudgetProgram(), fee);
  }
}
