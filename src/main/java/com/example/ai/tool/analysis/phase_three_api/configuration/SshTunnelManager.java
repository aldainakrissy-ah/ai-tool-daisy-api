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

import java.net.Socket;
import java.net.InetSocketAddress;

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
    
    // Add graceful failure option for production
    private boolean failOnError = true;
    
    // Increased timeout configurations for production
    private int connectionTimeoutMs = 30000; // 30 seconds
    private int maxRetryAttempts = 3;
    private int retryDelayMs = 5000; // 5 seconds

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

        try {
            validateSshProperties();
            
            // Test basic network connectivity first
            if (!testNetworkConnectivity()) {
                String error = String.format("Cannot reach SSH host %s:%d. Please check network connectivity.", host, port);
                logger.error(error);
                if (failOnError) {
                    throw new RuntimeException(error);
                } else {
                    logger.warn("Continuing without SSH tunnel due to failOnError=false");
                    return;
                }
            }
            
            connectWithRetry();
        } catch (Exception e) {
            if (failOnError) {
                logger.error("SSH tunnel failed and failOnError=true. Application will not start.", e);
                throw e;
            } else {
                logger.warn("SSH tunnel failed but failOnError=false. Application will continue.", e);
            }
        }
    }
    
    private boolean testNetworkConnectivity() {
        logger.info("Testing network connectivity to {}:{}...", host, port);
        
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 10000); // 10 second timeout
            logger.info("Network connectivity test successful to {}:{}", host, port);
            return true;
        } catch (Exception e) {
            logger.error("Network connectivity test failed to {}:{}: {}", host, port, e.getMessage());
            return false;
        }
    }

    private void connectWithRetry() {
        int attempt = 1;
        
        while (attempt <= maxRetryAttempts) {
            try {
                logger.info("SSH tunnel connection attempt {} of {} to {}:{}", 
                           attempt, maxRetryAttempts, host, port);
                establishConnection();
                logger.info("SSH tunnel established successfully on attempt {}", attempt);
                return;
                
            } catch (Exception e) {
                logger.warn("SSH tunnel attempt {} failed: {} - {}", 
                           attempt, e.getClass().getSimpleName(), e.getMessage());
                
                // Log more details for specific exceptions
                if (e instanceof JSchException) {
                    JSchException jschEx = (JSchException) e;
                    logger.error("JSch Exception details: {}", jschEx.getMessage());
                    
                    // Provide specific guidance based on error message
                    String message = jschEx.getMessage();
                    if (message.contains("timeout")) {
                        logger.error("Connection timeout. Possible causes:");
                        logger.error("1. SSH server is not running on {}:{}", host, port);
                        logger.error("2. Network connectivity issues");
                        logger.error("3. Firewall blocking the connection");
                    } else if (message.contains("Auth fail")) {
                        logger.error("Authentication failed. Check username/password for user '{}'", user);
                    } else if (message.contains("Connection refused")) {
                        logger.error("Connection refused. SSH service may not be running on {}:{}", host, port);
                    }
                }
                
                if (attempt == maxRetryAttempts) {
                    String errorMsg = String.format(
                        "Failed to establish SSH tunnel after %d attempts to %s:%d. " +
                        "Please verify: 1) SSH service is running, 2) Credentials are correct, " +
                        "3) Network connectivity, 4) No firewall blocking connection",
                        maxRetryAttempts, host, port
                    );
                    logger.error(errorMsg);
                    throw new RuntimeException(errorMsg, e);
                }
                
                attempt++;
                logger.info("Waiting {} seconds before retry attempt {}", retryDelayMs / 1000, attempt);
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
        logger.info("Establishing SSH connection to {}:{} with user '{}'", host, port, user);
        
        JSch jsch = new JSch();
        session = jsch.getSession(user, host, port);
        session.setPassword(password);
        
        // Configure session with more lenient settings
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("ServerAliveInterval", "60000"); // 60 seconds
        session.setConfig("ServerAliveMaxCount", "3");
        session.setConfig("ConnectTimeout", String.valueOf(connectionTimeoutMs));
        session.setConfig("TCPKeepAlive", "yes");
        session.setConfig("Compression", "yes");
        
        // Add more detailed logging
        logger.debug("SSH Config: StrictHostKeyChecking=no, Timeout={}ms", connectionTimeoutMs);
        
        session.connect(connectionTimeoutMs);
        logger.info("SSH session connected successfully to {}:{}", host, port);

        int assignedPort = session.setPortForwardingL(localPort, remote.getHost(), remote.getPort());
        logger.info("SSH tunnel established: localhost:{} -> {}:{} (via {}:{})", 
                   assignedPort, remote.getHost(), remote.getPort(), host, port);
        
        verifyTunnel();
    }
    
    private void verifyTunnel() {
        if (session == null || !session.isConnected()) {
            throw new RuntimeException("SSH tunnel verification failed: session not connected");
        }
        logger.info("SSH tunnel verification passed - tunnel is operational");
    }

    private void validateSshProperties() {
        logger.info("Validating SSH properties...");
        
        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException("SSH host ('ssh.host') must be set when SSH is enabled.");
        }
        if (!StringUtils.hasText(user)) {
            throw new IllegalArgumentException("SSH user ('ssh.user') must be set when SSH is enabled.");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("SSH password ('ssh.password') must be set when SSH is enabled.");
        }
        
        logger.info("SSH validation passed - Host: {}, Port: {}, User: {}", host, port, user);
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
    
    public boolean isConnected() {
        return session != null && session.isConnected();
    }
}