package com.reviewflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Local development S3 configuration Uses static credentials from environment variables For local
 * testing without AWS credentials
 */
@Configuration
@Profile("local")
public class LocalS3Config {

  @Bean
  public S3Client s3Client(
      @Value("${aws.s3.region:us-east-1}") String region,
      @Value("${aws.access-key-id:}") String accessKeyId,
      @Value("${aws.secret-access-key:}") String secretAccessKey) {

    // If credentials are provided, use them; otherwise use default behavior
    if (accessKeyId != null
        && !accessKeyId.isBlank()
        && secretAccessKey != null
        && !secretAccessKey.isBlank()) {
      return S3Client.builder()
          .region(Region.of(region))
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
          .build();
    } else {
      // Use default credential chain (environment variables, IAM role, etc.)
      return S3Client.builder().region(Region.of(region)).build();
    }
  }
}
