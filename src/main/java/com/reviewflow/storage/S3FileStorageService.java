package com.reviewflow.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.reviewflow.exception.StorageException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

@Service
@Profile("!local")
@RequiredArgsConstructor
public class S3FileStorageService implements StorageService {

    private final S3Client s3Client;

    @org.springframework.beans.factory.annotation.Value("${aws.s3.bucket:${app.s3.bucket}}")
    private String bucket;

    @Override
    public String store(String relativePath, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
            return store(relativePath, inputStream, file.getSize(), contentType);
        } catch (IOException e) {
            throw new StorageException("Failed to read upload file", e);
        }
    }

    @Override
    public String store(String relativePath, InputStream inputStream, long contentLength, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(relativePath)
                .contentType(contentType)
                .build();

        try {
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength));
            return relativePath;
        } catch (RuntimeException e) {
            throw new StorageException("Failed to upload file to S3", e);
        }
    }

    @Override
    public Resource load(String path) {
        try {
            URI uri = s3Client.utilities()
                    .getUrl(GetUrlRequest.builder().bucket(bucket).key(path).build())
                    .toURI();
            return new UrlResource(uri);
        } catch (URISyntaxException | MalformedURLException e) {
            throw new StorageException("Failed to load object from S3", e);
        }
    }

    @Override
    public void delete(String path) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(path)
                .build();
        try {
            s3Client.deleteObject(deleteObjectRequest);
        } catch (RuntimeException e) {
            throw new StorageException("Failed to delete object from S3", e);
        }
    }
}
