package com.jercel.tech.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GCSFolderDownloader {

    private final Storage storage;

    @Value("${bucket.name}")
    private String bucketName;

    public GCSFolderDownloader() {
        this.storage = StorageOptions.getDefaultInstance().getService();
    }

    public void downloadFolder(String gcsFolderPrefix, String localDestinationPath) throws IOException {
        Iterable<Blob> blobs = storage.list(bucketName, Storage.BlobListOption.prefix(gcsFolderPrefix)).iterateAll();

        for (Blob blob : blobs) {
            String objectName = blob.getName();
            
            // Skip "folder objects" (e.g., objects that end with "/")
            if (objectName.endsWith("/")) {
                continue;
            }

            // Resolve local file path
            String relativePath = objectName.substring(gcsFolderPrefix.length());
            File localFile = Paths.get(localDestinationPath, relativePath).toFile();

            // Create directories for nested files
            if (localFile.getParentFile() != null) {
                localFile.getParentFile().mkdirs();
            }

            // Download the file
            try (FileOutputStream fos = new FileOutputStream(localFile)) {
                fos.write(blob.getContent());
            }

            log.info("Downloaded: " + objectName + " to " + localFile.getAbsolutePath());
        }
    }
}
