package cc.monnshot.trade;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.util.List;
import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;

public class MonnshotPDAs {
  public static ProgramDerivedAddress bondingCurvePDA(
      final PublicKey program, final PublicKey mintAccount) {
    return PublicKey.findProgramAddress(
        List.of("token".getBytes(US_ASCII), mintAccount.toByteArray()), program);
  }

  public static void main(String[] args) {
    final ProgramDerivedAddress curvePDA =
        bondingCurvePDA(
            PublicKey.fromBase58Encoded("MoonCVVNZFSYkqNXP6bxHLPL6QQJiMagDL3qcqUQTrG"),
            PublicKey.fromBase58Encoded("87YsRJ8s1dkGLegD9wJvtdomsb94M7rCYhCzLuyHr4TU"));
    System.out.println(curvePDA.publicKey());

    final ProgramDerivedAddress curveTokenPDA =
        bondingCurvePDA(
            PublicKey.fromBase58Encoded("MoonCVVNZFSYkqNXP6bxHLPL6QQJiMagDL3qcqUQTrG"),
            curvePDA.publicKey());
    System.out.println(curveTokenPDA.publicKey());
  }
}
