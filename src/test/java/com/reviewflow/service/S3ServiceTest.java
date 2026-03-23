package com.reviewflow.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URL;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.reviewflow.exception.StorageException;
import com.reviewflow.exception.StorageNotFoundException;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(s3Service, "bucket", "test-bucket");
        ReflectionTestUtils.setField(s3Service, "region", "ca-central-1");
        ReflectionTestUtils.setField(s3Service, "presignedUrlExpiryMinutes", 15);
        ReflectionTestUtils.setField(s3Service, "avatarUrlExpiryMinutes", 60);
    }

    @Test
    void putObject_success_returnsObjectUrl() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        String result = s3Service.putObject("pdfs/H1/report.pdf", "abc".getBytes(), "application/pdf");

        assertEquals("https://test-bucket.s3.ca-central-1.amazonaws.com/pdfs/H1/report.pdf", result);
    }

    @Test
    void putObject_s3Failure_throwsStorageException() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(S3Exception.builder().message("down").build());

        assertThrows(StorageException.class,
                () -> s3Service.putObject("x", "abc".getBytes(), "application/pdf"));
    }

    @Test
    void getObject_success_returnsBytes() {
        ResponseBytes<GetObjectResponse> bytes = ResponseBytes.fromByteArray(GetObjectResponse.builder().build(),
                "hello".getBytes());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(bytes);

        byte[] result = s3Service.getObject("pdfs/H1/report.pdf");

        assertArrayEquals("hello".getBytes(), result);
    }

    @Test
    void getObject_noSuchKey_throwsNotFound() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());

        assertThrows(StorageNotFoundException.class, () -> s3Service.getObject("missing"));
    }

    @Test
    void getObject_s3Failure_throwsStorageException() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("boom").build());

        assertThrows(StorageException.class, () -> s3Service.getObject("x"));
    }

    @Test
    void deleteObject_s3Failure_isSwallowed() {
        org.mockito.Mockito.doThrow(S3Exception.builder().message("boom").build())
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        s3Service.deleteObject("x");
    }

    @Test
    void deleteObject_success_doesNotThrow() {
        s3Service.deleteObject("x");
    }

    @Test
    void objectExists_trueWhenHeadSucceeds() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        assertTrue(s3Service.objectExists("x"));
    }

    @Test
    void objectExists_falseWhenMissingOrS3Error() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());
        assertFalse(s3Service.objectExists("x"));

        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().message("err").build());
        assertFalse(s3Service.objectExists("x"));
    }

    @Test
    void generatePresignedDownloadUrl_success_returnsUrl() throws Exception {
        try (MockedStatic<S3Presigner> presignerStatic = mockStatic(S3Presigner.class)) {
            S3Presigner.Builder builder = mock(S3Presigner.Builder.class);
            S3Presigner presigner = mock(S3Presigner.class);
            PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);

            presignerStatic.when(S3Presigner::builder).thenReturn(builder);
            when(builder.region(any())).thenReturn(builder);
            when(builder.credentialsProvider(any(AwsCredentialsProvider.class))).thenReturn(builder);
            when(builder.build()).thenReturn(presigner);
            when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
            when(presigned.url()).thenReturn(new URL("https://example.com/signed"));

            String url = s3Service.generatePresignedDownloadUrl("pdfs/H1/report.pdf");
            assertEquals("https://example.com/signed", url);
        }
    }

    @Test
    void generatePresignedAvatarUrl_s3Failure_throwsStorageException() {
        try (MockedStatic<S3Presigner> presignerStatic = mockStatic(S3Presigner.class)) {
            S3Presigner.Builder builder = mock(S3Presigner.Builder.class);
            S3Presigner presigner = mock(S3Presigner.class);

            presignerStatic.when(S3Presigner::builder).thenReturn(builder);
            when(builder.region(any())).thenReturn(builder);
            when(builder.credentialsProvider(any(AwsCredentialsProvider.class))).thenReturn(builder);
            when(builder.build()).thenReturn(presigner);
            when(presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                    .thenThrow(S3Exception.builder().message("sign-fail").build());

            assertThrows(StorageException.class, () -> s3Service.generatePresignedAvatarUrl("avatars/U1/avatar.jpg"));
        }
    }

    @Test
    void generatePresignedAvatarUrl_success_returnsUrl() throws Exception {
        try (MockedStatic<S3Presigner> presignerStatic = mockStatic(S3Presigner.class)) {
            S3Presigner.Builder builder = mock(S3Presigner.Builder.class);
            S3Presigner presigner = mock(S3Presigner.class);
            PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);

            presignerStatic.when(S3Presigner::builder).thenReturn(builder);
            when(builder.region(any())).thenReturn(builder);
            when(builder.credentialsProvider(any(AwsCredentialsProvider.class))).thenReturn(builder);
            when(builder.build()).thenReturn(presigner);
            when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
            when(presigned.url()).thenReturn(new URL("https://example.com/avatar-signed"));

            String url = s3Service.generatePresignedAvatarUrl("avatars/U1/avatar.jpg");
            assertEquals("https://example.com/avatar-signed", url);
        }
    }
}
