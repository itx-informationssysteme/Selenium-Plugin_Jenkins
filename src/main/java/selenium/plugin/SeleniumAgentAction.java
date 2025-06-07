package selenium.plugin;

import hudson.model.Action;
import hudson.model.Computer;

public class SeleniumAgentAction implements Action {

    private final Computer computer;

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

    public boolean isEnabled() {
        return true;
    }

    public void doEnable() {
        // Settings
    }
}
