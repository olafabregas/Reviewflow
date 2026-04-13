package com.reviewflow.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.reviewflow.exception.StorageException;
import com.reviewflow.exception.StorageNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket:${app.s3.bucket}}")
    private String bucket;

    @Value("${aws.region:${app.s3.region}}")
    private String region;

    @Value("${aws.s3.presigned-url-expiry-minutes:15}")
    private int presignedUrlExpiryMinutes;

    @Value("${aws.s3.avatar-url-expiry-minutes:60}")
    private int avatarUrlExpiryMinutes;

    public String putObject(String key, byte[] data, String contentType) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(data));
            log.debug("S3 upload successful: {}", key);
            return buildUrl(key);
        } catch (S3Exception e) {
            log.error("S3 upload failed for key {}: {}", key, e.getMessage());
            throw new StorageException("File upload failed. Please try again.", e);
        }
    }

    public byte[] getObject(String key) {
        try {
            ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            return bytes.asByteArray();
        } catch (NoSuchKeyException e) {
            throw new StorageNotFoundException("File not found: " + key);
        } catch (S3Exception e) {
            log.error("S3 download failed for key {}: {}", key, e.getMessage());
            throw new StorageException("File download failed. Please try again.", e);
        }
    }

    public void deleteObject(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            log.debug("S3 delete successful: {}", key);
        } catch (S3Exception e) {
            log.warn("S3 delete failed for key {} (non-critical): {}", key, e.getMessage());
        }
    }

    public boolean objectExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            log.warn("S3 headObject failed for key {}: {}", key, e.getMessage());
            return false;
        }
    }

    public String generatePresignedDownloadUrl(String key) {
        return generatePresignedDownloadUrl(key, Duration.ofMinutes(presignedUrlExpiryMinutes));
    }

    public String generatePresignedAvatarUrl(String key) {
        return generatePresignedDownloadUrl(key, Duration.ofMinutes(avatarUrlExpiryMinutes));
    }

    public String generatePresignedPreviewUrl(String key, String contentType) {
        return generatePresignedPreviewUrl(key, contentType, Duration.ofMinutes(presignedUrlExpiryMinutes));
    }

    public String generatePresignedPreviewUrl(String key, String contentType, Duration expiry) {
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {
            return presigner.presignGetObject(
                    GetObjectPresignRequest.builder()
                            .signatureDuration(expiry)
                            .getObjectRequest(req -> req
                            .bucket(bucket)
                            .key(key)
                            .responseContentDisposition("inline")
                            .responseContentType(contentType))
                            .build())
                    .url()
                    .toString();
        } catch (S3Exception e) {
            log.error("Presigned preview URL generation failed for key {}: {}", key, e.getMessage());
            throw new StorageException("Could not generate preview link. Please try again.", e);
        }
    }

    public long getObjectSize(String key) {
        try {
            HeadObjectResponse response = s3Client.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return response.contentLength();
        } catch (NoSuchKeyException e) {
            throw new StorageNotFoundException("File not found: " + key);
        } catch (S3Exception e) {
            log.error("Failed to get object size for key {}: {}", key, e.getMessage());
            throw new StorageException("Could not retrieve file size. Please try again.", e);
        }
    }

    private String generatePresignedDownloadUrl(String key, Duration expiry) {
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {
            return presigner.presignGetObject(
                    GetObjectPresignRequest.builder()
                            .signatureDuration(expiry)
                            .getObjectRequest(req -> req.bucket(bucket).key(key))
                            .build())
                    .url()
                    .toString();
        } catch (S3Exception e) {
            log.error("Presigned URL generation failed for key {}: {}", key, e.getMessage());
            throw new StorageException("Could not generate download link. Please try again.", e);
        }
    }

    private String buildUrl(String key) {
        String baseUrl = (region != null && !region.isBlank())
                ? String.format("https://%s.s3.%s.amazonaws.com", bucket, region)
                : String.format("https://%s.s3.amazonaws.com", bucket);
        return baseUrl + "/" + key;
    }
}
