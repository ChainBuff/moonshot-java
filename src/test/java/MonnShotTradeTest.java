import cc.monnshot.trade.MonnShotTrade;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.Signer;
import software.sava.core.encoding.Base58;

public class MonnShotTradeTest {

  private static final Signer signer =
      Signer.createFromPrivateKey(Base58.decode("xxx")); // Replace with private key

  private static final MonnShotTrade monnShotTrade = new MonnShotTrade();

  @Test
  public void testMonnShotTradeBuy() {

    PublicKey mint = PublicKey.fromBase58Encoded("CxiwQZ16CjNqHYWwpktfppTDYSeWZRsNP9uuuWUBmoon");
    // buy 0.001 sol ,buy slippage 5%, jito false
    monnShotTrade.buy(
        mint, signer, new MonnShotTrade.BuyOptions(BigDecimal.valueOf(0.001d), 5, "", ""), false);
  }

  @Test
  public void testMonnShotTradeSell() {
    PublicKey mint = PublicKey.fromBase58Encoded("CxiwQZ16CjNqHYWwpktfppTDYSeWZRsNP9uuuWUBmoon");
    // sell sellRatio (1 sell all ,0.1 sell 10% ), sell slippage 5%, jito false
    monnShotTrade.sell(
        mint, signer, new MonnShotTrade.SellOptions(BigDecimal.valueOf(1), 5, "", ""), false);
  }

  @Test
  public void testMonnShotTradeBuyJtio() {
    PublicKey mint = PublicKey.fromBase58Encoded("CxiwQZ16CjNqHYWwpktfppTDYSeWZRsNP9uuuWUBmoon");
    // buy 0.001 sol ,buy slippage 5%, jito true
    // TX Will be process by jito
    monnShotTrade.buy(
        mint, signer, new MonnShotTrade.BuyOptions(BigDecimal.valueOf(0.001d), 5, "", ""), true);
  }
}
