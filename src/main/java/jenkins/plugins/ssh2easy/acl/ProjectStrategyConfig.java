package jenkins.plugins.ssh2easy.acl;

import hudson.Extension;
import hudson.model.Descriptor.FormException;
import hudson.model.ManagementLink;
import hudson.security.AuthorizationStrategy;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

@Extension
public class ProjectStrategyConfig extends ManagementLink {

    @Override
    public String getIconFileName() {
        String icon = null;
        if (Jenkins.get().getAuthorizationStrategy() instanceof CloudCIAuthorizationStrategy) {
            icon = "cloud_ci_logo.png";
        }
        return icon;
    }

    @Override
    public String getUrlName() {
        return "projects-members";
    }

    public String getDisplayName() {
        return Messages.CloudCIAuthorizationStrategy_ManageAndAssign();
    }

    public String getAssignProjectsName() {
        return Messages.CloudCIAuthorizationStrategy_Assign();
    }

    public String getManageProjectsName() {
        return Messages.CloudCIAuthorizationStrategy_Manage();
    }

    @Override
    public String getDescription() {
        return Messages.CloudCIAuthorizationStrategy_Description();
    }

    public AuthorizationStrategy getStrategy() {
        AuthorizationStrategy strategy = Jenkins.get().getAuthorizationStrategy();
        if (strategy instanceof CloudCIAuthorizationStrategy) {
            return strategy;
        } else {
            return null;
        }
    }

    public void doProjectsSubmit(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, FormException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        CloudCIAuthorizationStrategy.DESCRIPTOR.doProjectsSubmit(req, rsp);
        rsp.sendRedirect(".");
    }

    public void doAssignSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        CloudCIAuthorizationStrategy.DESCRIPTOR.doAssignSubmit(req, rsp);
        rsp.sendRedirect(".");
    }

}
