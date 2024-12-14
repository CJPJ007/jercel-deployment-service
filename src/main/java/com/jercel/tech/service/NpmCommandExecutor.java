package com.jercel.tech.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NpmCommandExecutor {

    public static void executeCommand(String command, String workingDirectory) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();

        // Set the command to execute
        processBuilder.command("sh", "-c", command);

        // Set the working directory
        if (workingDirectory != null) {
            processBuilder.directory(new java.io.File(workingDirectory));
        }

        // Start the process
        Process process = processBuilder.start();

        // Read the output from the process
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

            String line;
            log.info("Output:");
            while ((line = reader.readLine()) != null) {
                log.info(line);
            }

            log.info("Errors:");
            while ((line = errorReader.readLine()) != null) {
                log.info(line);
            }
        }

        // Wait for the process to finish
        int exitCode = process.waitFor();
        if (exitCode == 0) {
            log.info("Command executed successfully.");
        } else {
            log.error("Command execution failed with exit code " + exitCode);
        }
    }

    public static void main(String[] args) {
        try {
            // Path to the folder containing your Node.js project
            String projectPath = "/path/to/your/project";

            // Execute 'npm install'
            System.out.println("Running 'npm install'...");
            executeCommand("npm install", projectPath);

            // Execute 'npm run build'
            System.out.println("\nRunning 'npm run build'...");
            executeCommand("npm run build", projectPath);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void buildProject(String projectPath) {
       log.info("Inside buildProject");
        try {
             // Execute 'npm install'
            log.info("Running 'npm install'...");
            executeCommand("npm install", projectPath);
    
            // Execute 'npm run build'
            log.info("\nRunning 'npm run build'...");
            executeCommand("npm run build", projectPath);
            
        } catch (Exception e) {
            log.error("Exception in buildProject", e);
        }
    }
}
