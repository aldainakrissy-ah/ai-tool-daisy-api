package com.example.ai.tool.analysis.phase_three_api.configuration;

import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.jcraft.jsch.JSch;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;

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

    private Session session;

    // Nested class for remote configuration
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

        try {
            logger.info("Initializing SSH tunnel to {}...", host);
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, port);
            session.setPassword(password);
            // This is not recommended for production, but useful for development.
            // For production, it's better to use known hosts or public key auth.
            session.setConfig("StrictHostKeyChecking", "no");

            session.connect();
            logger.info("SSH session connected successfully.");

            int assignedPort = session.setPortForwardingL(localPort, remote.getHost(), remote.getPort());
            logger.info("SSH tunnel established: localhost:{} -> {}:{}", assignedPort, remote.getHost(), remote.getPort());

        } catch (Exception e) {
            logger.error("Failed to create SSH tunnel. Please check SSH credentials and network connectivity.", e);
            throw new RuntimeException("Failed to create SSH tunnel", e);
        }
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
            logger.info("Closing SSH tunnel.");
            session.disconnect();
            logger.info("SSH tunnel closed.");
        }
    }
}