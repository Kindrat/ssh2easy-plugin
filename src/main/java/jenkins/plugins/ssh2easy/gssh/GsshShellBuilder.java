package jenkins.plugins.ssh2easy.gssh;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import jenkins.plugins.ssh2easy.gssh.client.SshClient;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * GSSH Builder extentation
 *
 * @author Jerry Cai
 */
public class GsshShellBuilder extends Builder {
    private boolean disable;
    private String serverInfo;
    private String groupName;
    private String ip;
    private String shell;

    public GsshShellBuilder() {
    }

    @DataBoundConstructor
    public GsshShellBuilder(boolean disable, String serverInfo, String shell) {
        this.disable = disable;
        this.serverInfo = serverInfo;
        this.shell = shell;
        this.ip = Server.parseIp(this.serverInfo);
        this.groupName = Server.parseServerGroupName(this.serverInfo);
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
			throws IOException, InterruptedException {
        LoggerDecorator logger = new LoggerDecorator(listener.getLogger());
        logger.delimiter();
        if (isDisable()) {
            logger.log("Current step is disabled, skipping execution");
            return true;
        }
        // This is where you 'build' the project.
        SshClient sshHandler = GsshBuilderWrapper.DESCRIPTOR.getSshClient(getGroupName(), getIp());

        EnvVars env = build.getEnvironment(listener);
        String enhancedShell = Util.replaceMacro(getShell(), env);
        String shell = Util.fixEmptyAndTrim(enhancedShell);
        if (shell == null) {
            return false;
        }
        int exitStatus = sshHandler.executeShell(logger, shell);
        logger.delimiter();
        return exitStatus == SshClient.STATUS_SUCCESS;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
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

    public String getShell() {
        return shell;
    }

    public void setShell(String shell) {
        this.shell = shell;
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

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

		@Nonnull
		@Override
        public String getDisplayName() {
            return Messages.SSHSHELL_DisplayName();
        }

        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
            return Objects.requireNonNull(req).bindJSON(this.clazz, formData);
        }

        public ListBoxModel doFillServerInfoItems() {
            ListBoxModel m = new ListBoxModel();
            for (Server server : GsshBuilderWrapper.DESCRIPTOR.getServers()) {
                m.add(server.getServerInfo());
            }
            return m;
        }
    }
}
