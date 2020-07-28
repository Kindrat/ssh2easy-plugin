package jenkins.plugins.ssh2easy.gssh.client;

import com.jcraft.jsch.*;
import hudson.FilePath;
import io.jenkins.cli.shaded.org.apache.sshd.client.subsystem.sftp.SftpClient;
import jenkins.plugins.ssh2easy.gssh.GsshPluginException;
import jenkins.plugins.ssh2easy.gssh.GsshUserInfo;
import jenkins.plugins.ssh2easy.gssh.LoggerDecorator;
import jenkins.plugins.ssh2easy.gssh.ServerGroup;
import org.apache.commons.lang.StringEscapeUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

/**
 * This is Ssh handler , user for handling SSH related event and requirments
 *
 * @author Jerry Cai
 */
public class DefaultSshClient extends AbstractSshClient {

    public static final String SSH_BEY = "\nexit $?";

    private String ip;
    private int port;
    private String username;
    private String password;

    public DefaultSshClient(String ip, int port, String username, String password) {
        this.ip = ip;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public DefaultSshClient(ServerGroup serverGroup, String ip) {
        this.port = serverGroup.getPort();
        this.username = serverGroup.getUsername();
        this.password = serverGroup.getPassword();
        this.ip = ip;
    }

    public static SshClient newInstance(String ip, int port, String username, String password) {
        return new DefaultSshClient(ip, port, username, password);
    }

    public static SshClient newInstance(ServerGroup group, String ip) {
        return new DefaultSshClient(group, ip);
    }

    public Session createSession(LoggerDecorator logger) {
        JSch jsch = new JSch();
        try {
            Session session = jsch.getSession(username, ip, port);
            session.setPassword(password);

            UserInfo ui = new GsshUserInfo(password);
            session.setUserInfo(ui);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setDaemonThread(false);
            session.connect();
            logger.log("Created ssh session ip=[%s],port=[%d],username=[%s],password=[*******]",
                    ip, port, username);
            return session;
        } catch (Exception e) {
            logger.log(e, "Failed to create ssh session ip=[%s],port=[%d],username=[%s],password=[*******]",
                    ip, port, username);
            throw new GsshPluginException(e);
        }
    }

    @Override
    public int uploadFile(LoggerDecorator logger, String fileName, InputStream fileContent, String serverLocation) {
        Session session = null;
        ChannelSftp sftp = null;
        OutputStream out = null;
        try {
            session = createSession(logger);
            Channel channel = session.openChannel("sftp");
            channel.setOutputStream(logger.outputStream(), true);
            channel.setExtOutputStream(logger.outputStream(), true);
            channel.connect();
            Thread.sleep(2000);
            sftp = (ChannelSftp) channel;
            sftp.setFilenameEncoding(StandardCharsets.UTF_8.displayName());
            prepareUpload(sftp, serverLocation, false);
            sftp.cd(serverLocation);
            out = sftp.put(fileName, 777);
            Thread.sleep(2000);
            byte[] buffer = new byte[2048];
            int n = -1;
            while ((n = fileContent.read(buffer, 0, 2048)) != -1) {
                out.write(buffer, 0, n);
            }
            out.flush();
            logger.log("Uploaded file [%s] to remote [%s]", fileName, serverLocation);
            return STATUS_SUCCESS;
        } catch (Exception e) {
            logger.log(e, "Failed to upload file: %s", e.getMessage());
            throw new GsshPluginException(e);
        } finally {
            if (sftp != null) {
                logger.log("SFTP exit status is " + sftp.getExitStatus());
            }
            Optional.ofNullable(out).ifPresent(outputStream -> {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    // ignored
                }
            });
            Optional.ofNullable(sftp).ifPresent(ChannelSftp::disconnect);
            Optional.ofNullable(session).ifPresent(Session::disconnect);
        }
    }

    @Override
    public int downloadFile(LoggerDecorator logger, String remoteFile, FilePath localFile) {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = createSession(logger);
            Channel channel = session.openChannel("sftp");
            channel.connect();
            Thread.sleep(2000);
            sftp = (ChannelSftp) channel;
            sftp.setFilenameEncoding(StandardCharsets.UTF_8.displayName());
            if (localFile.exists()) {
                localFile.delete();
                logger.log("Removed existing local file: %s", localFile);
            }
            localFile.touch(System.currentTimeMillis());
            logger.log("Created local file: %s", localFile);
            SftpProgressMonitor progressMonitor = new GsshProgressMonitor(logger);
            sftp.get(remoteFile, localFile.write(), progressMonitor);
            logger.log("Downloaded remote file [%s] to [%s]", remoteFile, localFile.toURI());
            logger.log("Total size of local file: %d", localFile.length());
            return SshClient.STATUS_SUCCESS;
        } catch (Exception e) {
            logger.log(e, "Failed to download file: %s", e.getMessage());
            throw new GsshPluginException(e);
        } finally {
            if (sftp != null) {
                logger.log("SFTP exit status is " + sftp.getExitStatus());
            }
            Optional.ofNullable(sftp).ifPresent(ChannelSftp::disconnect);
            Optional.ofNullable(session).ifPresent(Session::disconnect);
        }
    }

    @Override
    public int executeShell(LoggerDecorator logger, String shell) {
        return executeCommand(logger, shell);
    }

    @Override
    public int executeCommand(LoggerDecorator logger, String command) {
        Session session = null;
        ChannelExec channel = null;
        InputStream in = null;
        try {
            String wrapperCommand = wrapperInput(command);
            logger.log("Executing: %s", wrapperCommand);
            session = createSession(logger);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setOutputStream(logger.outputStream(), true);
            channel.setExtOutputStream(logger.outputStream(), true);
            channel.setPty(Boolean.FALSE);
            channel.setCommand(wrapperCommand);
            channel.connect();
            Thread.sleep(1000);
            while (true) {
                byte[] buffer = new byte[2048];
                int len = -1;
                in = channel.getInputStream();
                while (-1 != (len = in.read(buffer))) {
                    logger.outputStream().write(buffer, 0, len);
                    logger.outputStream().flush();
                }
                if (channel.isEOF()) {
                    break;
                }
                if (!channel.isConnected()) {
                    break;
                }
                if (channel.isClosed()) {
                    break;
                }
                Thread.sleep(1000);
            }
            int status = channel.getExitStatus();
            logger.log("Shell exit status code -->" + status);
            return status;
        } catch (Exception e) {
            logger.log(e, "Command execution exception");
            throw new GsshPluginException(e);
        } finally {
            Optional.ofNullable(in).ifPresent(inputStream -> {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    logger.log(e, "Faield to close input stream");
                }
            });
            Optional.ofNullable(channel).ifPresent(Channel::disconnect);
            Optional.ofNullable(session).ifPresent(Session::disconnect);
        }
    }

    protected String wrapperInput(String input) {
        String output = fixIEIssue(input);
        //		return SSH_PROFILE + output + SSH_BEY;
        return output + SSH_BEY;
        //		return  output;
    }

    /**
     * this is fix the IE issue that it's input shell /command auto add '<br>
     * ' if \n
     *
     * @param input
     * @return
     */
    private String fixIEIssue(String input) {
        return StringEscapeUtils.unescapeHtml(input);
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean prepareUpload(ChannelSftp sftpChannel, String path, boolean overwrite) throws SftpException {

        boolean result = false;

        // Build remote path subfolders inclusive:
        String[] folders = path.split("/");
        for (String folder : folders) {
            if (folder.length() > 0) {
                // This is a valid folder:
                try {
                    System.out.println("Current Folder path before cd:" + folder);
                    sftpChannel.cd(folder);
                } catch (SftpException e) {
                    // No such folder yet:
                    System.out.println("Inside create folders: ");
                    sftpChannel.mkdir(folder);
                    sftpChannel.cd(folder);
                }
            }
        }

        // Folders ready. Remove such a file if exists:
        if (sftpChannel.ls(path).size() > 0) {
            if (!overwrite) {
                System.out.println(
                        "Error - file " + path + " was not created on server. " +
                                "It already exists and overwriting is forbidden.");
            } else {
                // Delete file:
                sftpChannel.ls(path); // Search file.
                sftpChannel.rm(path); // Remove file.
                result = true;
            }
        } else {
            // No such file:
            result = true;
        }

        return result;
    }


    @Override
    public String toString() {
        return "Server Info [" + this.ip + " ," + this.port + ","
                + this.username + "," + this.password + "]";
    }
}
