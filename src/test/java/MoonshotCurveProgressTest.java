import cc.monnshot.trade.MoonshotCurveProgress;
import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;

public class MoonshotCurveProgressTest {

  @Test
  public void testProgress() {
    final double progress = MoonshotCurveProgress.progress(
            PublicKey.fromBase58Encoded("CxiwQZ16CjNqHYWwpktfppTDYSeWZRsNP9uuuWUBmoon"));
    System.out.println(progress);
  }
}
