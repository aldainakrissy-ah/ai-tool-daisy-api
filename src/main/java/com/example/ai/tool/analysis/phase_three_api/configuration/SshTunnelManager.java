package com.example.ai.tool.analysis.phase_three_api.configuration;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component("sshTunnelManager")
@ConfigurationProperties(prefix = "ssh")
@Data
public class SshTunnelManager {

    private static final Logger logger = LoggerFactory.getLogger(SshTunnelManager.class);

    private boolean enabled = false;
    private String host;
    private int port = 22;
    private String user;
    private String password;
    private Remote remote = new Remote();
    private int localPort = 5433;
    
    // Add timeout configurations
    private int connectionTimeoutMs = 10000; // 10 seconds
    private int maxRetryAttempts = 3;
    private int retryDelayMs = 2000; // 2 seconds

    private Session session;

    @Data
    public static class Remote {
        private String host = "localhost";
        private int port = 5432;
    }

    @PostConstruct
    public void start() {
        if (!enabled) {
            logger.info("SSH tunnel is disabled.");
            return;
        }

        validateSshProperties();
        connectWithRetry();
    }

    private void connectWithRetry() {
        int attempt = 1;
        
        while (attempt <= maxRetryAttempts) {
            try {
                logger.info("SSH tunnel connection attempt {} of {}", attempt, maxRetryAttempts);
                establishConnection();
                logger.info("SSH tunnel established successfully on attempt {}", attempt);
                return;
                
            } catch (Exception e) {
                logger.warn("SSH tunnel attempt {} failed: {}", attempt, e.getMessage());
                
                if (attempt == maxRetryAttempts) {
                    logger.error("All SSH tunnel connection attempts failed. Final error:", e);
                    throw new RuntimeException("Failed to establish SSH tunnel after " + maxRetryAttempts + " attempts", e);
                }
                
                attempt++;
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("SSH tunnel connection interrupted", ie);
                }
            }
        }
    }

    private void establishConnection() throws JSchException {
        logger.info("Initializing SSH tunnel to {}:{}...", host, port);
        
        JSch jsch = new JSch();
        session = jsch.getSession(user, host, port);
        session.setPassword(password);
        
        // Production-friendly configurations
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("ServerAliveInterval", "30000"); // 30 seconds
        session.setConfig("ServerAliveMaxCount", "3");
        session.setConfig("ConnectTimeout", String.valueOf(connectionTimeoutMs));
        
        // Additional production settings
        session.setConfig("TCPKeepAlive", "yes");
        session.setConfig("Compression", "yes");
        
        session.connect(connectionTimeoutMs);
        logger.info("SSH session connected successfully to {}:{}", host, port);

        int assignedPort = session.setPortForwardingL(localPort, remote.getHost(), remote.getPort());
        logger.info("SSH tunnel established: localhost:{} -> {}:{}", assignedPort, remote.getHost(), remote.getPort());
        
        // Verify tunnel is working
        verifyTunnel();
    }
    
    private void verifyTunnel() {
        if (session == null || !session.isConnected()) {
            throw new RuntimeException("SSH tunnel verification failed: session not connected");
        }
        logger.info("SSH tunnel verification passed");
    }

    private void validateSshProperties() {
        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException("SSH host ('ssh.host') must be set when SSH is enabled.");
        }
        if (!StringUtils.hasText(user)) {
            throw new IllegalArgumentException("SSH user ('ssh.user') must be set when SSH is enabled.");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("SSH password ('ssh.password') must be set when SSH is enabled.");
        }
    }

    @PreDestroy
    public void stop() {
        if (session != null && session.isConnected()) {
            logger.info("Closing SSH tunnel...");
            try {
                session.disconnect();
                logger.info("SSH tunnel closed successfully.");
            } catch (Exception e) {
                logger.warn("Error while closing SSH tunnel: {}", e.getMessage());
            }
        }
    }
    
    // Health check method
    public boolean isConnected() {
        return session != null && session.isConnected();
    }
}