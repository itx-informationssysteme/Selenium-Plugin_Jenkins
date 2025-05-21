package selenium.plugin;

import hudson.Extension;
import hudson.model.Node;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class SeleniumNodeProperty extends NodeProperty<Node> {
    private final boolean enabled;

    @DataBoundConstructor
    public SeleniumNodeProperty(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Extension
    public static final class DescriptorImpl extends NodePropertyDescriptor {
        @Override
        public String getDisplayName() {
            return "Selenium-Node";
        }

        @Override
        public boolean isApplicable(Class<? extends Node> nodeType) {
            return true;
        }

        public FormValidation doStartNode(@QueryParameter boolean enabled) {
            // Your logic to start the Selenium server
            return FormValidation.ok("Selenium Node wurde gestartet.");
        }
    }
}
