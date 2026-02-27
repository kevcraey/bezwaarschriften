package be.vlaanderen.omgeving.bezwaarschriften.config;

import com.amazonaws.services.s3.AmazonS3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

public class S3HealthCheck implements HealthIndicator {
  @Autowired
  private AmazonS3 s3Client;

  private String bucketName;

  public S3HealthCheck(AmazonS3 s3Client, String bucketName) {
    super();
    this.s3Client = s3Client;
    this.bucketName = bucketName;
  }

  @Override
  public Health health() {
    try {
      if (s3Client.doesBucketExistV2(bucketName)) {
        return healthUp();
      } else {
        return healthDown();
      }
    } catch (Exception e) {
      return healthDown();
    }
  }

  private Health healthDown() {
    return Health.down().withDetail("bucket", bucketName).build();
  }

  private Health healthUp() {
    return Health.up().withDetail("bucket", bucketName).build();
  }
}
