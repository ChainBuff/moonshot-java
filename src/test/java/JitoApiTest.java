import cc.monnshot.JitoApi;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class JitoApiTest {

  @Test
  public void test() throws IOException, InterruptedException {
    final JitoApi.TipFloor.TipData tipfloor = JitoApi.getTipfloor();
    System.out.println(tipfloor);
  }


}
