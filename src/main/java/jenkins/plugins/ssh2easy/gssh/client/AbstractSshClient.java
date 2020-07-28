package jenkins.plugins.ssh2easy.gssh.client;

import jenkins.plugins.ssh2easy.gssh.GsshPluginException;
import jenkins.plugins.ssh2easy.gssh.LoggerDecorator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public abstract class AbstractSshClient implements SshClient {
    public static final String TEMP_PATH = "/var";
    public static final String LATEST_EXEC_DEBUG_SH = "/var/latest_exec_debug.sh";

    @Override
    public int uploadFile(LoggerDecorator logger, String fileName, File file, String serverLocation) {
        logger.log("sftp upload file [%s] to target location [%s] with file name is [%s]",
                file, serverLocation, fileName);
        try (InputStream fileContent = new FileInputStream(file)) {
            return uploadFile(logger, fileName, fileContent, serverLocation);
        } catch (FileNotFoundException e) {
            String message = "ERROR: can't find local file [" + file + "]!";
            logger.log(message, e);
            throw new GsshPluginException(message, e);
        } catch (Exception e) {
            logger.log("Upload failed", e);
            throw new GsshPluginException("Upload failed", e);
        }
    }

    @Override
    public int uploadFile(LoggerDecorator logger, String fileName, String fileContent, String serverLocation) {
        try (InputStream bis = new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8))) {
            return uploadFile(logger, fileName, bis, serverLocation);
        } catch (IOException e) {
            return STATUS_FAILED;
        }
    }

    @Override
    public int executeShellByFTP(LoggerDecorator logger, String shell) {
        Random random = new Random();
        logger.log("Executing shell: %s", shell);
        String shellName = "tempshell_" + System.currentTimeMillis() + random.nextInt() + ".sh";
        String shellFile = TEMP_PATH + "/" + shellName;
        try {
            uploadFile(logger, shellName, shell, TEMP_PATH);
            chmod(logger, 777, shellFile);
            return executeCommand(logger, ". " + shellFile);
        } finally {
            remove(logger, LATEST_EXEC_DEBUG_SH);
            mv(logger, shellFile, LATEST_EXEC_DEBUG_SH);
        }
    }

    @Override
    public int chmod(LoggerDecorator logger, int mode, String path) {
        return executeCommand(logger, "chmod " + mode + " " + path);
    }

    @Override
    public int chown(LoggerDecorator logger, String own, String path) {
        return executeCommand(logger, "chown " + own + " " + path);
    }

    @Override
    public int mv(LoggerDecorator logger, String source, String dest) {
        return executeCommand(logger, "mv " + source + " " + dest);
    }

    @Override
    public int remove(LoggerDecorator logger, String path) {
        return executeCommand(logger, "rm -rf " + path);
    }
}
