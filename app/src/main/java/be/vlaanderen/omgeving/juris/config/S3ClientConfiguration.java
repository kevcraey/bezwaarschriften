package be.vlaanderen.omgeving.juris.config;

import be.cumuli.boot.actuator.autoconfigure.HealthSchedule;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class S3ClientConfiguration {

  @Profile(Constants.SPRING_PROFILE_PRODUCTION)
  @Configuration
  public static class ProdStorageConfiguration {

    @Bean
    public AmazonS3 amazonS3Client(S3ConfigurationProperties configurationProperties) {
      AWSCredentials credentials = new BasicAWSCredentials(configurationProperties.getAccessKey(),
          configurationProperties.getSecretKey());
      ClientConfiguration clientConfiguration = new ClientConfiguration();
      clientConfiguration.withMaxConnections(configurationProperties.getMaxConnections())
          .withConnectionTimeout(configurationProperties.getConnectionTimeout())
          .withRequestTimeout(configurationProperties.getRequestTimeout())
          .withClientExecutionTimeout(configurationProperties.getClientExecutionTimeout())
          .withSocketTimeout(configurationProperties.getSocketTimeout())
          .withConnectionTTL(configurationProperties.getConnectionTtl())
          .withConnectionMaxIdleMillis(configurationProperties.getConnectionMaxIdle())
          .withValidateAfterInactivityMillis(configurationProperties.getValidateAfterInactivity())
          .withProtocol(Protocol.HTTP)
          .withSignerOverride(configurationProperties.getSignerOverride());
      AwsClientBuilder.EndpointConfiguration endpointConfiguration =
          new AwsClientBuilder.EndpointConfiguration(configurationProperties.getUrl().toString(),
              Regions.DEFAULT_REGION.name());
      return AmazonS3ClientBuilder.standard().withClientConfiguration(clientConfiguration)
          .withCredentials(new AWSStaticCredentialsProvider(credentials))
          .withPathStyleAccessEnabled(true).withEndpointConfiguration(endpointConfiguration)
          .build();
    }

    @Bean
    @HealthSchedule(upIntervalAmount = 3, upIntervalUnit = TimeUnit.MINUTES, downIntervalAmount = 1,
        downIntervalUnit = TimeUnit.MINUTES)
    public HealthIndicator bestandenS3HealthIndicator(AmazonS3 s3Client,
        S3ConfigurationProperties configurationProperties) {
      return s3HealthCheckForBucket(s3Client, configurationProperties.getBucketBestanden());
    }

    public HealthIndicator s3HealthCheckForBucket(AmazonS3 s3Client, String bucket) {
      return new S3HealthCheck(s3Client, bucket);
    }
  }
}
