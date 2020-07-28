package jenkins.plugins.ssh2easy.gssh.client;

import com.jcraft.jsch.SftpProgressMonitor;
import jenkins.plugins.ssh2easy.gssh.LoggerDecorator;

public class GsshProgressMonitor implements SftpProgressMonitor {
    private final LoggerDecorator logger;

    public GsshProgressMonitor(LoggerDecorator logger) {
        this.logger = logger;
    }

    @Override
    public void init(int op, String src, String dest, long max) {
        String operation = op == SftpProgressMonitor.PUT ? "PUT" : "GET";
        logger.log("Starting %s from %s to %s [%d]", operation, src, dest, max);
    }

    @Override
    public boolean count(long count) {
        logger.log("=====> Loaded %d bytes", count);
        return true;
    }

    @Override
    public void end() {
        logger.log("Finished processing SFTP transfer");
    }
}
