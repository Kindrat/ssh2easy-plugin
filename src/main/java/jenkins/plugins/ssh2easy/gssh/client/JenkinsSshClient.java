package jenkins.plugins.ssh2easy.gssh.client;

import com.trilead.ssh2.ChannelCondition;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.Session;
import jenkins.plugins.ssh2easy.gssh.GsshPluginException;
import jenkins.plugins.ssh2easy.gssh.LoggerDecorator;
import jenkins.plugins.ssh2easy.gssh.ServerGroup;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class JenkinsSshClient extends DefaultSshClient {
    private static final Logger LOG = Logger.getLogger(JenkinsSshClient.class);

    public JenkinsSshClient(String ip, int port, String username, String password) {
        super(ip, port, username, password);
    }

    public JenkinsSshClient(ServerGroup serverGroup, String ip) {
        super(serverGroup, ip);
    }

    public static SshClient newInstance(String ip, int port, String username, String password) {
        return new JenkinsSshClient(ip, port, username, password);
    }

    public static SshClient newInstance(ServerGroup group, String ip) {
        return new JenkinsSshClient(group, ip);
    }

    public Connection getConnection() throws IOException {
        Connection conn = new Connection(this.getIp(), this.getPort());
        conn.connect();
        boolean isAuthenticated = conn.authenticateWithPassword(getUsername(), getPassword());
        if (!isAuthenticated) {
            throw new IOException("Authentication failed.");
        }
        LOG.info("create ssh session success with ip=[" + getIp()
                + "],port=[" + getPort() + "],username=[" + getUsername()
                + "],password=[*******]");
        return conn;
    }

    @Override
    public int executeCommand(LoggerDecorator logger, String command) {
        Connection conn;
        try {
            conn = getConnection();
        } catch (Exception e) {
            logger.log(e, "Failed to create ssh session ip=[%s],port=[%d],username=[%s],password=[*******]",
                    getIp(), getPort(), getUsername());
            throw new GsshPluginException(e);
        }
        Session session = null;
        String wrappedCommand = wrapperInput(command);
        try {
            session = conn.openSession();
            session.requestPTY("dumb");
            session.startShell();
            ExecutorService exec = Executors.newSingleThreadExecutor();
            Future<Boolean> task = exec.submit(new OutputTask(session, logger));
            PrintWriter out = new PrintWriter(session.getStdin());
            String[] commands = wrappedCommand.split("\n");
            for (String cmd : commands) {
                if ("".equals(cmd.trim()))
                    continue;
                out.println(cmd);
            }
            out.close();
            task.get();
            exec.shutdown();
            int status = session.getExitStatus();
            logger.log("Command exit status -->" + status);
            return status;
        } catch (Exception e) {
            String msg = "Command [" + wrappedCommand + "] execution failed!";
            logger.log(e, msg);
            throw new GsshPluginException(msg, e);
        } finally {
            Optional.ofNullable(session).ifPresent(Session::close);
            Optional.ofNullable(conn).ifPresent(Connection::close);
        }
    }

    static class OutputTask implements Callable<Boolean> {
        private final LoggerDecorator logger;
        private final Session session;

        public OutputTask(Session session, LoggerDecorator logger) {
            this.session = session;
            this.logger = logger;
        }

        public boolean execute() throws IOException, InterruptedException {
            InputStream stdout = session.getStdout();
            InputStream stderr = session.getStderr();
            byte[] buffer = new byte[8192];
            boolean result = true;
            while (true) {
                if ((stdout.available() == 0) && (stderr.available() == 0)) {
                    int conditions = session.waitForCondition(
                            ChannelCondition.STDERR_DATA
                                    | ChannelCondition.STDOUT_DATA
                                    | ChannelCondition.EXIT_STATUS, 0);
                    if ((conditions & ChannelCondition.TIMEOUT) != 0) {
                        logger.log("Wait timeout and exit now !");
                        break;
                    }
                    if ((conditions & ChannelCondition.EXIT_STATUS) != 0) {
                        break;
                    }
                }

                while (stdout.available() > 0) {
                    int len = stdout.read(buffer);
                    if (len > 0)
                        logger.outputStream().write(buffer, 0, len);
                }

                while (stderr.available() > 0) {
                    int len = stderr.read(buffer);
                    if (len > 0)
                        logger.outputStream().write(buffer, 0, len);
                    result = false;
                }
                if (!result) {
                    break;
                }
            }
            logger.outputStream().flush();
            logger.delimiter();
            return result;
        }

        public Boolean call() throws Exception {
            Thread.sleep(2000);
            return execute();
        }
    }
}
