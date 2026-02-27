package be.vlaanderen.omgeving.juris.config;

import java.net.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("storage.s3")
public class S3ConfigurationProperties {
  
  private URL url;
  private String accessKey;
  private String secretKey;
  private Integer maxConnections;
  private Integer socketTimeout;
  private Integer connectionTimeout;
  private Integer requestTimeout;
  private Integer clientExecutionTimeout;
  private Long connectionTtl;
  private Long connectionMaxIdle;
  private Integer validateAfterInactivity;
  private String signerOverride;
  private String bucketBestanden;

  public URL getUrl() {
    return url;
  }

  public void setUrl(URL url) {
    this.url = url;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(String accessKey) {
    this.accessKey = accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }

  public Integer getMaxConnections() {
    return maxConnections;
  }

  public void setMaxConnections(Integer maxConnections) {
    this.maxConnections = maxConnections;
  }

  public Integer getSocketTimeout() {
    return socketTimeout;
  }

  public void setSocketTimeout(Integer socketTimeout) {
    this.socketTimeout = socketTimeout;
  }

  public Integer getConnectionTimeout() {
    return connectionTimeout;
  }

  public void setConnectionTimeout(Integer connectionTimeout) {
    this.connectionTimeout = connectionTimeout;
  }

  public Integer getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(Integer requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  public Integer getClientExecutionTimeout() {
    return clientExecutionTimeout;
  }

  public void setClientExecutionTimeout(Integer clientExecutionTimeout) {
    this.clientExecutionTimeout = clientExecutionTimeout;
  }

  public Long getConnectionTtl() {
    return connectionTtl;
  }

  public void setConnectionTtl(Long connectionTtl) {
    this.connectionTtl = connectionTtl;
  }

  public Long getConnectionMaxIdle() {
    return connectionMaxIdle;
  }

  public void setConnectionMaxIdle(Long connectionMaxIdle) {
    this.connectionMaxIdle = connectionMaxIdle;
  }

  public Integer getValidateAfterInactivity() {
    return validateAfterInactivity;
  }

  public void setValidateAfterInactivity(Integer validateAfterInactivity) {
    this.validateAfterInactivity = validateAfterInactivity;
  }

  public String getSignerOverride() {
    return signerOverride;
  }

  public void setSignerOverride(String signerOverride) {
    this.signerOverride = signerOverride;
  }

  public String getBucketBestanden() {
    return bucketBestanden;
  }

  public void setBucketBestanden(String bucketBestanden) {
    this.bucketBestanden = bucketBestanden;
  }

}
