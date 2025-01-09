package cc.monnshot;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpClient {

  public static final java.net.http.HttpClient HTTP_CLIENT = httpClient();

  private static java.net.http.HttpClient httpClient() {
    log.debug("create http client");
    final String httpRpcProxy = System.getProperty("HTTP_RPC_PROXY", String.valueOf(false));
    if (Boolean.parseBoolean(httpRpcProxy)) {
      return java.net.http.HttpClient.newBuilder()
          .version(java.net.http.HttpClient.Version.HTTP_2)
          .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
          .proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1", 7890)))
          .connectTimeout(Duration.ofSeconds(5))
          .build();
    } else {
      return java.net.http.HttpClient.newBuilder()
          .version(java.net.http.HttpClient.Version.HTTP_2)
          .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
          .connectTimeout(Duration.ofSeconds(5))
          .build();
    }
  }
}
