package com.jercel.tech.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Service
@Slf4j
public class JercelDeploymentService implements CommandLineRunner {

    @Value("${redis.queue.name}")
    private String redisQueueName;

    @Value("${redis.host.name}")
    private String redisHostName;

    @Value("${redis.port}")
    private int port;

    
    private ExecutorService executorService;
    private Jedis jedis;

    public JercelDeploymentService(@Value("${redis.host.name}") String redisHostName, @Value("${redis.port}") int redisPort){
        log.info("Inside constructor");
        executorService = Executors.newFixedThreadPool(4);
        jedis=new Jedis(redisHostName, redisPort);
    }

    @Autowired
    GCSFolderDownloader gcsFolderDownloader;

    @Autowired
    GCSFolderUploader gcsFolderUploader;

    @Override
    public void run(String... args) throws Exception {
        log.info("Inside run");
        Jedis jedis = new Jedis(redisHostName, port);
        addShutDownHook(jedis);
        try {
            listenMessages(jedis);
        } catch (Exception e) {
            log.error("Exception in run method", e);
        }
    }

    private void listenMessages(Jedis jedis) {
        log.info("Inside listening messages");
        try {
            while (true) {
                String folderId = jedis.brpop(0, redisQueueName).get(1);

                executorService.submit(() -> processMessages(folderId));
            }
        } catch (Exception e) {
            log.error("Exception while listening", e);
        }
    }

    private void processMessages(String folderId) {
        log.info("Inside processMessage : {}",folderId);
        try {
            String outputLocation = "/Users/jaypalchauhan/Documents/Projects/Learn Spring Boot/vercel-clone/jercel-deployment-service/output/"+folderId;
           
            //Download project from GCS
            gcsFolderDownloader.downloadFolder(folderId, outputLocation);

            //Building project using npm install and npm build
            NpmCommandExecutor.buildProject(outputLocation);

            gcsFolderUploader.uploadFolder(Paths.get(outputLocation));

            removeFile(new File(outputLocation));
            
            jedis.set(folderId, "deployed");
        } catch (Exception e) {
            log.info("Exception while processing message",e);
        }
    }

    private void addShutDownHook(Jedis jedis) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Inside shutdown");
            executorService.shutdown();
            jedis.close();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS))
                    executorService.shutdownNow();
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            } catch (Exception e) {
                log.error("Exception in shutDown", e);
            }
        }));
    }

    @Async
    private void removeFile(File file) throws IOException {
        Long startTime = System.currentTimeMillis();

        Path startPath = Paths.get(file.getAbsolutePath());

        try {
            Files.walkFileTree(startPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // log.info("File: " + file.toString());
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException ioc) throws IOException {
                    // log.info("Directory: " + dir.toString());
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // log.error("Failed to access file: " + file.toString() + " due to " + exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.info("Time taken by removeFile : {}", System.currentTimeMillis()-startTime);

    }

}
