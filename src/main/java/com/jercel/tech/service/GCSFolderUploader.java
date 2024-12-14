package com.jercel.tech.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Random;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GCSFolderUploader {

    private final Storage storage;

    @Value("${bucket.name}")
    private String bucketName;

    public GCSFolderUploader() {
        this.storage = StorageOptions.getDefaultInstance().getService();
    }

    public void uploadFolder(Path sourceFolder) throws IOException {
        String folderName = sourceFolder.getFileName().toString();
        List<Path> paths = Files.walk(Paths.get(sourceFolder.toString(), "build")).filter(Files::isRegularFile).collect(Collectors.toList());
        paths.parallelStream().forEach((path) -> {
            try {
                uploadFile(sourceFolder, path, folderName);
            } catch (Exception e) {
                log.error("Exception in uploadfile for " + path, e);
            }
        });
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

    private String generateRandomId() {
        Random random = new Random();
        StringBuilder id = new StringBuilder("-");
        // Add 4 random lowercase letters
        for (int i = 0; i < 4; i++) {
            id.append((char) (random.nextInt(26) + 'a'));
        }
        // Add 1 random number
        id.append(random.nextInt(10));
        return id.toString();
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

        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

        // Upload the file
        Blob uploadedBlob = storage.create(blobInfo, Files.readAllBytes(filePath));
        // log.info("Uploaded Blob : {}", uploadedBlob.getBlobId());
        // log.info("Uploaded : {}", objectName);
    }

}
