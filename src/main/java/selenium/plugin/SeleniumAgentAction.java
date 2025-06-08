package selenium.plugin;

import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.ManagementLink;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;

public class SeleniumAgentAction implements Action {

    private final Computer computer;

    private boolean nodeActive;

    public SeleniumAgentAction(Computer computer) {
        this.computer = computer;
    }

    @Override
    public String getIconFileName() {
        return "icon.png";
    }

    @Override
    public String getDisplayName() {
        return "Selenium";
    }

    @Override
    public String getUrlName() {
        return "selenium";
    }

    public Computer getComputer() {
        return computer;
    }

    public String getVersion() {
        if (ManagementLink.all().get(SeleniumGlobalProperty.class).getSeleniumVersion() == null) return "No Version set (Change in Selenium Global Configuration)";
        return ManagementLink.all().get(SeleniumGlobalProperty.class).getSeleniumVersion();
    }

    public HttpResponse doStartNode() {
        nodeActive = true;
        return new HttpRedirect(".");
    }

    public HttpResponse doStopNode() {
        nodeActive = false;
        return new HttpRedirect(".");
    }

    public boolean getNodeActive() {
        return nodeActive;
    }

}
