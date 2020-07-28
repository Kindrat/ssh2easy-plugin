package jenkins.plugins.ssh2easy.gssh.client;

import jenkins.plugins.ssh2easy.gssh.GsshPluginException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public abstract class AbstractSshClient implements SshClient {
    public static final String TEMP_PATH = "/var";
    public static final String LATEST_EXEC_DEBUG_SH = "/var/latest_exec_debug.sh";

    @Override
    public int uploadFile(PrintStream logger, String fileName, File file, String serverLocation) {
        logger.println("sftp upload file [" + file + "] to target location [" + serverLocation + "] with file name is" +
				" [" + fileName + "]");
        InputStream fileContent = null;
        try {
            if (!file.exists()) {
                logger.println("[GSSH-FTP] ERROR as: sftp upload local file [" + file + "] can't find !");
            }
            fileContent = new FileInputStream(file);
            return uploadFile(logger, fileName, fileContent, serverLocation);
        } catch (FileNotFoundException e) {
            String message = "[GSSH-FTP] ERROR as: sftp upload local file [" + file + "] can't find !";
            logger.println(message);
            e.printStackTrace(logger);
            throw new GsshPluginException(message, e);
        } catch (Exception e) {
            String message = "[GSSH-FTP] ERROR as with below errors logs:";
            logger.println(message);
            e.printStackTrace(logger);
            throw new GsshPluginException(message, e);
        } finally {
            if (null != fileContent) {
                try {
                    fileContent.close();
                } catch (IOException e) {
                    // ignored
                }
            }
        }
    }

    @Override
    public int uploadFile(PrintStream logger, String fileName, String fileContent, String serverLocation) {
        try (InputStream bis = new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8))) {
            return uploadFile(logger, fileName, bis, serverLocation);
        } catch (IOException e) {
            return STATUS_FAILED;
        }
    }

    @Override
    public int downloadFile(PrintStream logger, String remoteFile, String localFolder) {
        File rf = new File(remoteFile);
        return downloadFile(logger, remoteFile, localFolder, rf.getName());
    }

    @Override
    public int executeShellByFTP(PrintStream logger, String shell) {
        Random random = new Random();
        logger.println("execute shell as : ");
        logger.println(shell);
        String shellName = "tempshell_" + System.currentTimeMillis() + random.nextInt() + ".sh";
        String shellFile = TEMP_PATH + "/" + shellName;
        try {
            uploadFile(logger, shellName, shell, TEMP_PATH);
            chmod(logger, 777, shellFile);
            return executeCommand(logger, ". " + shellFile);
        } finally {
            rm_Rf(logger, LATEST_EXEC_DEBUG_SH);
            mv(logger, shellFile, LATEST_EXEC_DEBUG_SH);
        }
    }

    @Override
    public int chmod(PrintStream logger, int mode, String path) {
        return executeCommand(logger, "chmod " + mode + " " + path);
    }

    @Override
    public int chown(PrintStream logger, String own, String path) {
        return executeCommand(logger, "chown " + own + " " + path);
    }

    @Override
    public int mv(PrintStream logger, String source, String dest) {
        return executeCommand(logger, "mv " + source + " " + dest);
    }

    @Override
    public int rm_Rf(PrintStream logger, String path) {
        return executeCommand(logger, "rm -rf " + path);
    }
}
