package com.reviewflow.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@Profile("local")
public class LocalS3Config {

  @Bean
  public S3Client s3Client(
      @Value("${aws.s3.region:us-east-1}") String region,
      @Value("${aws.access-key-id:}") String accessKeyId,
      @Value("${aws.secret-access-key:}") String secretAccessKey) {

    if (accessKeyId != null && !accessKeyId.isBlank() && secretAccessKey != null && !secretAccessKey.isBlank()) {
      return S3Client.builder()
          .region(Region.of(region))
          .credentialsProvider(StaticCredentialsProvider.create(
              AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
          .build();
    } else {
      return S3Client.builder().region(Region.of(region)).build();
    }
  }
}
