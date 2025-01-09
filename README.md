# **Moonshot  Java**

> https://dexscreener.com/moonshot moonshot java 实现


# 功能
- `Create Token ` & `Create And Buy`
- `BUY`
- `SELL`


# 环境要求

- Java 23 
- Gradle


# Dependencies

- [sava](https://github.com/sava-software/sava)
- [jackson](https://github.com/FasterXML/jackson-databind)



# 快速开始

```java
import cc.monnshot.trade.MonnShotTrade;
import java.math.BigDecimal;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.Signer;
import software.sava.core.encoding.Base58;

public static void main(String[] args){
    Signer signer =
            Signer.createFromPrivateKey(Base58.decode("xxx")); // Replace with private key

    MonnShotTrade monnShotTrade = new MonnShotTrade();
    PublicKey mint = PublicKey.fromBase58Encoded("CxiwQZ16CjNqHYWwpktfppTDYSeWZRsNP9uuuWUBmoon");
    //  buy 0.001 sol ,buy slippage 5%, jito false
    monnShotTrade.buy(mint, signer, new MonnShotTrade.BuyOptions(BigDecimal.valueOf(0.001d), 5, "", ""), false);
}
```
> 更多例子请查看 `/src/test/java` 目录

# 项目结构
```tree
├── AsyncVirtual.java // Virtual Util
├── HttpClient.java // Http Client 
├── HttpRpcApi.java // RPC Util
├── JitoApi.java // Jito Api 
├── Main.java 
├── Mint.java // Spl Token Util
└── trade
    ├── FixedSide.java 
    ├── MonnShotTrade.java // BUY / Sell / Create & Create And Buy
    ├── MonnshotPDAs.java // PDA
    ├── MonnshotProgram.java
    └── MoonshotCurveProgress.java // CurveProgress

```


# 参考资源

- [Solana Java SDK](https://github.com/sava-software/sava)
- [Moonshot-SDK](https://github.com/wen-moon-ser/moonshot-sdk)


# 免责申明

本项目仅供学习和参考使用，严禁将本项目用于任何形式的商业用途。任何违反此条款的行为，导致的法律责任，由使用者自行承担。