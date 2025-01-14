package com.jercel.tech.service;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Client for interacting with Cloudflare R2 Storage using AWS SDK S3
 * compatibility
 */
@Service
@Slf4j
public class CloudflareR2Client {

    /**
     * Configuration class for R2 credentials and endpoint
     */
    @Value("${r2.api.access.key}")
    private String accessKey;
    @Value("${r2.api.secret.key}")
    private String secretKey;
    @Value("${r2.endpoint.url}")
    private String endpoint;
    @Value("${bucket.name}")
    private String bucketName;

    private S3Client s3Client;

    /**
     * Builds and configures the S3 client with R2-specific settings
     */
    @PostConstruct
    private void buildS3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                accessKey,
                secretKey);

        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of("auto"))
                .serviceConfiguration(serviceConfiguration)
                .build();
    }

    /**
     * Lists all objects in the specified bucket
     */
    public List<S3Object> listObjects(String bucketName, String prefix) {
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .build();

            return s3Client.listObjectsV2(request).contents();
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to list objects in bucket " + bucketName + ": " + e.getMessage(), e);
        }
    }

    public void downloadFolder(String gcsFolderPrefix, String localDestinationPath) throws IOException {
        List<S3Object> s3Objects = listObjects(bucketName, gcsFolderPrefix);

        s3Objects.parallelStream().forEach((s3Object) -> {
            {
                String objectName = s3Object.key();

                // Skip "folder objects" (e.g., objects that end with "/")
                if (objectName.endsWith("/")) {
                    return;
                }

                // Resolve local file path
                String relativePath = objectName.substring(gcsFolderPrefix.length());
                File localFile = Paths.get(localDestinationPath, relativePath).toFile();

                // Create directories for nested files
                if (localFile.getParentFile() != null) {
                    localFile.getParentFile().mkdirs();
                }

                // Download the file
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(localFile);

                    GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(objectName)
                            .build();
                    fos.write(s3Client.getObjectAsBytes(getObjectRequest).asByteArray());
                    fos.close();
                } catch (Exception e) {
                    log.error("Exception while trying to download file", e);
                }

                log.info("Downloaded: " + objectName + " to " + localFile.getAbsolutePath());
            }
        });
        // for (S3Object s3Object : s3Objects) {
        // String objectName = s3Object.key();

        // // Skip "folder objects" (e.g., objects that end with "/")
        // if (objectName.endsWith("/")) {
        // continue;
        // }

        // // Resolve local file path
        // String relativePath = objectName.substring(gcsFolderPrefix.length());
        // File localFile = Paths.get(localDestinationPath, relativePath).toFile();

        // // Create directories for nested files
        // if (localFile.getParentFile() != null) {
        // localFile.getParentFile().mkdirs();
        // }

        // // Download the file
        // try (FileOutputStream fos = new FileOutputStream(localFile)) {
        // GetObjectRequest getObjectRequest =
        // GetObjectRequest.builder().bucket(bucketName).key(objectName)
        // .build();
        // fos.write(s3Client.getObjectAsBytes(getObjectRequest).asByteArray());
        // }

        // log.info("Downloaded: " + objectName + " to " + localFile.getAbsolutePath());
        // }
    }

    public String uploadFolder(Path sourceFolder) throws IOException {
        log.info("Upload folder : {}", sourceFolder);
        String folderName = sourceFolder.getFileName().toString();
        List<Path> paths = Files.walk(Paths.get(sourceFolder.toString(), "build")).filter(Files::isRegularFile)
                .collect(Collectors.toList());
        paths.parallelStream().forEach((path) -> {
            try {
                uploadFile(sourceFolder, path, folderName);
            } catch (Exception e) {
                log.error("Exception in uploadfile for " + path, e);
            }
        });

        return folderName;
        // Files.walkFileTree(sourceFolder, new SimpleFileVisitor<Path>() {

        // @Override
        // public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        // try {
        // uploadFile(sourceFolder, file, folderName);
        // } catch (IOException e) {
        // log.error("Failed to upload file: ", e);
        // }
        // return FileVisitResult.CONTINUE;
        // }

        // // Optionally handle symbolic links, errors, etc.

        // });
    }

    private void uploadFile(Path sourceFolder, Path filePath, String sourceFolderName) throws IOException {
        // Compute the relative path to preserve folder structure in GCS
        // log.info("filePath : {} currentPath : {}", filePath,
        // Paths.get("").toAbsolutePath());
        Path relativePath = sourceFolder.relativize(filePath);
        // log.info("Relative Path : {}", relativePath.toString().startsWith("."));
        if (relativePath.toString().startsWith("."))
            return;
        String objectName = sourceFolderName + "/"
                + relativePath.toString().replace(FileSystems.getDefault().getSeparator(), "/"); // GCS uses '/' as
                                                                                                 // separator

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectName)
                .build();
        // Upload the file
        PutObjectResponse putObjectResponse = s3Client.putObject(putObjectRequest, RequestBody.fromFile(filePath));
        // log.info("Uploaded Blob : {}", uploadedBlob.getBlobId());
        // log.info("Uploaded : {}", objectName);
    }

}