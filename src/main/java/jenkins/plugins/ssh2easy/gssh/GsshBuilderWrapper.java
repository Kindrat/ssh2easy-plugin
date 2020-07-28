package jenkins.plugins.ssh2easy.gssh;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import jenkins.plugins.ssh2easy.gssh.client.SshClient;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Logger;

/**
 * @author Jerry Cai
 */
public final class GsshBuilderWrapper extends BuildWrapper {
    @Extension
    public static final GsshDescriptorImpl DESCRIPTOR = new GsshDescriptorImpl();
    private boolean disable;
    private String serverInfo;
    private String preScript;
    private String postScript;
    private String groupName;
    private String ip;

    public GsshBuilderWrapper() {
    }

    @DataBoundConstructor
    public GsshBuilderWrapper(boolean disable, String serverInfo, String preScript, String postScript) {
        this.disable = disable;
        this.serverInfo = serverInfo;
        initHook();
        this.preScript = preScript;
        this.postScript = postScript;

    }

    private void initHook() {
        this.groupName = Server.parseServerGroupName(this.serverInfo);
        this.ip = Server.parseIp(this.serverInfo);
    }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        Environment env = new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                executePostBuildScript(new LoggerDecorator(listener.getLogger()));
                return super.tearDown(build, listener);
            }
        };
        executePreBuildScript(new LoggerDecorator(listener.getLogger()));
        return env;
    }

    private boolean executePreBuildScript(LoggerDecorator logger) {
        logger.delimiter();
        logger.log("Running on server -- " + getServerInfo());
        if (isDisable()) {
            logger.log("Current step is disabled, skipping execution");
            return true;
        }
        initHook();
        logger.log("Executing pre build script as below :\n" + preScript);
        SshClient sshHandler = getSshClient();
        int exitStatus = SshClient.STATUS_FAILED;
        if (preScript != null && !preScript.trim().equals("")) {
            exitStatus = sshHandler.executeShellByFTP(logger, preScript);
        }
        logger.delimiter();
        return exitStatus == SshClient.STATUS_SUCCESS;
    }

    private boolean executePostBuildScript(LoggerDecorator logger) {
        logger.delimiter();
        logger.log("Running on server -- " + getServerInfo());
        if (isDisable()) {
            logger.log("Current step is disabled, skipping execution");
            return true;
        }
        initHook();
        logger.log("Executing post build script as below :\n" + postScript);
        SshClient sshHandler = getSshClient();
        int exitStatus = SshClient.STATUS_FAILED;
        if (postScript != null && !postScript.trim().equals("")) {
            exitStatus = sshHandler.executeShellByFTP(logger, postScript);
        }
        logger.delimiter();
        return exitStatus == SshClient.STATUS_SUCCESS;
    }

    public SshClient getSshClient() {
        return DESCRIPTOR.getSshClient(getGroupName(), getIp());
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    public boolean isDisable() {
        return disable;
    }

    public void setDisable(boolean disable) {
        this.disable = disable;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getPreScript() {
        return preScript;
    }

    public void setPreScript(String preScript) {
        this.preScript = preScript;
    }

    public String getPostScript() {
        return postScript;
    }

    public void setPostScript(String postScript) {
        this.postScript = postScript;
    }

    public String getServerInfo() {
        return serverInfo;
    }

    public void setServerInfo(String serverInfo) {
        this.serverInfo = serverInfo;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String toString() {
        return this.groupName + " +++ " + this.ip + " +++ " + this.serverInfo;
    }

    public static class GsshDescriptorImpl extends BuildWrapperDescriptor {
        private final CopyOnWriteList<ServerGroup> serverGroups = new CopyOnWriteList<>();
        private final CopyOnWriteList<Server> servers = new CopyOnWriteList<>();

        public GsshDescriptorImpl() {
            super(GsshBuilderWrapper.class);
            load();
        }

        public GsshDescriptorImpl(Class<? extends BuildWrapper> clazz) {
            super(clazz);
            load();
        }

        public ServerGroup[] getServerGroups() {
            return serverGroups.toArray(new ServerGroup[0]);
        }

        public Server[] getServers() {
            return servers.toArray(new Server[0]);
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.SSHSHELL_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/plugin/ssh2easy/help.html";
        }

        @Override
        public BuildWrapper newInstance(StaplerRequest req, JSONObject formData) {
            GsshBuilderWrapper pub = new GsshBuilderWrapper();
            req.bindParameters(pub, "gssh.wrapp.");
            return pub;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) {
            serverGroups.replaceBy(req.bindParametersToList(ServerGroup.class, "gssh.sg.wrapper."));
            servers.replaceBy(req.bindParametersToList(Server.class, "gssh.s.wrapper."));
            save();
            return true;
        }

        public boolean doServerGroupSubmit(StaplerRequest req, StaplerResponse rsp) {
            serverGroups.replaceBy(req.bindParametersToList(ServerGroup.class, "gssh.sg.wrapper."));
            save();
            return true;
        }

        public boolean doServerSubmit(StaplerRequest req, StaplerResponse rsp) {
            servers.replaceBy(req.bindParametersToList(Server.class, "gssh.s.wrapper."));
            save();
            return true;
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        public ServerGroup getServerGroup(String groupName) {
            return Arrays.stream(getServerGroups())
                    .filter(serverGroup -> serverGroup.getGroupName().trim().equals(groupName.trim()))
                    .findAny()
                    .orElse(null);
        }

        public Server getServer(String ip) {
            Server[] servers = getServers();

            for (Server server : servers) {
                if (server.getIp().equals(ip))
                    return server;
            }
            return null;
        }

        public SshClient getSshClient(String groupName, String ip) {
            ServerGroup serverGroup = getServerGroup(groupName);
            return serverGroup.getSshClient(ip);
        }

        public FormValidation doCheckUsername(@QueryParameter String value) throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a name");
            if (value.length() < 2)
                return FormValidation.warning("Isn't the name too short?");
            return FormValidation.ok();
        }

        public FormValidation doCheckPort(@QueryParameter String value) throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a port");
            if (value.length() > 4)
                return FormValidation.warning("Isn't the port too large?");
            try {
                Integer.parseInt(value);
            } catch (Exception e) {
                return FormValidation.error("Please input the port as integer");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckGroupName(@Nullable @QueryParameter String value)
                throws IOException, ServletException {
            if (null == value)
                return FormValidation.error("Please input username");

            if (value.length() == 0)
                return FormValidation.error("Please input username");

            if (value.contains(Server.INFO_SPLIT)) {
                return FormValidation.error("Your input name contains '" + Server.INFO_SPLIT + "' that is forbidden");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckPassword(@QueryParameter String value) throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please input password");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckName(@QueryParameter String value) throws IOException, ServletException {
            return doCheckGroupName(value);
        }

        public FormValidation doCheckIP(@QueryParameter String value) throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please input server ip");
            }
            return FormValidation.ok();
        }
    }
}
