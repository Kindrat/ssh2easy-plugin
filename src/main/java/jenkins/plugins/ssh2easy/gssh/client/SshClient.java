package jenkins.plugins.ssh2easy.gssh.client;

import hudson.FilePath;
import jenkins.plugins.ssh2easy.gssh.LoggerDecorator;

import java.io.File;
import java.io.InputStream;

@SuppressWarnings("UnusedReturnValue")
public interface SshClient {
    int STATUS_SUCCESS = 0;
    int STATUS_FAILED = -1;

    int executeCommand(LoggerDecorator logger, String command);

    int executeShell(LoggerDecorator logger, String shell);

    int executeShellByFTP(LoggerDecorator logger, String shell);

    int uploadFile(LoggerDecorator logger, String fileName, String fileContent, String serverLocation);

    int uploadFile(LoggerDecorator logger, String fileName, InputStream fileContent, String serverLocation);

    int uploadFile(LoggerDecorator logger, String fileName, File file, String serverLocation);

    int downloadFile(LoggerDecorator logger, String remoteFile, FilePath localFile);

    int chmod(LoggerDecorator logger, int mode, String path);

    int chown(LoggerDecorator logger, String own, String path);

    int mv(LoggerDecorator logger, String source, String dest);

    int remove(LoggerDecorator logger, String path);
}