package jenkins.plugins.ssh2easy.gssh;

import hudson.*;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.util.Optional;
import java.util.logging.Logger;

import jenkins.plugins.ssh2easy.gssh.client.SshClient;
import net.sf.json.JSONObject;

import org.apache.tools.ant.util.FileUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;

/**
 * GSSH FTP Builder extentation
 *
 * @author Jerry Cai
 */
public class GsshFtpDownloadBuilder extends Builder {
	private boolean disable;
	private String serverInfo;
	private String groupName;
	private String ip;
	private String remoteFile;
	private String localFolder;
	private String fileName;

	public GsshFtpDownloadBuilder() {
	}

	@DataBoundConstructor
	public GsshFtpDownloadBuilder(boolean disable ,String serverInfo, String remoteFile, String localFolder,
			String fileName) {
		this.disable = disable;
		this.serverInfo = serverInfo;
		this.ip = Server.parseIp(this.serverInfo);
		this.groupName = Server.parseServerGroupName(this.serverInfo);
		this.remoteFile = remoteFile;
		this.localFolder = localFolder;
		this.fileName = Util.fixEmptyAndTrim(fileName);
	}

	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
		LoggerDecorator logger = new LoggerDecorator(listener.getLogger());
		logger.delimiter();
		if(isDisable()){
			logger.log("Current step is disabled, skipping execution");
			return true;
		}
		logger.log("Running on server -- " + getServerInfo());
		// This is where you 'build' the project.
		SshClient sshClient = GsshBuilderWrapper.DESCRIPTOR.getSshClient(getGroupName(), getIp());
		try {
			EnvVars env = build.getEnvironment(listener);
			String filePath = Util.replaceMacro(getLocalFolder(), env);
			String remoteFile = Util.replaceMacro(getRemoteFile(), env);
			FilePath buildWorkspace = build.getWorkspace();

			if (filePath == null) {
				return false;
			}
			if (remoteFile == null) {
				return false;
			}
			if (buildWorkspace == null) {
				return false;
			}
			String fileName = Optional.ofNullable(this.fileName).orElse(new File(remoteFile).getName());
			FilePath localFilePath;
			if (buildWorkspace.isRemote()) {
				String fp = String.format("%s/%s/%s", buildWorkspace.getRemote(), localFolder, fileName);
				localFilePath = new FilePath(buildWorkspace.getChannel(), fp);
			} else {
				localFilePath = new FilePath(new File(new File(buildWorkspace.toURI()), filePath));
			}
			logger.log("Going to load file into: %s", localFilePath.toURI());
			int exitStatus = sshClient.downloadFile(logger, remoteFile, localFilePath);
			logger.delimiter();
			return exitStatus == SshClient.STATUS_SUCCESS;
		} catch (Exception e) {
			return false;
		}
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

	public String getRemoteFile() {
		return remoteFile;
	}

	public void setRemoteFile(String remoteFile) {
		this.remoteFile = remoteFile;
	}

	public String getLocalFolder() {
		return localFolder;
	}

	public void setLocalFolder(String localFolder) {
		this.localFolder = localFolder;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
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
			return Messages.SSHFTPDOWNLOAD_DisplayName();
		}

		@Override
		public Builder newInstance(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
			return req.bindJSON(this.clazz, formData);
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
