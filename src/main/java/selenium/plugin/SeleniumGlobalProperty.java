package selenium.plugin;

import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.QueryParameter;

@Extension
public class SeleniumGlobalProperty extends ManagementLink {

    private String seleniumVersion;

    public String getSeleniumVersion() {
        return seleniumVersion;
    }

    public void setSeleniumVersion(String seleniumVersion) {
        this.seleniumVersion = seleniumVersion;
    }

    @Override
    public String getDisplayName() {
        return "Selenium verwalten";
    }

    @Override
    public String getDescription() {
        return "Globale Selenium-Einstellungen konfigurieren";
    }

    @Override
    public String getUrlName() {
        return "selenium-settings";
    }

    @Override
    public String getIconFileName() {
        return "/plugin/selenium-plugin/images/selenium-icon.png";
    }

    public ListBoxModel doFillSeleniumVersionItems() {
        ListBoxModel items = new ListBoxModel();
        items.add("4.10.0", "4.10.0");
        items.add("4.9.0", "4.9.0");
        items.add("4.8.0", "4.8.0");
        // Weitere Versionen...
        return items;
    }

    public void doSave(@QueryParameter String seleniumVersion) {
        this.seleniumVersion = seleniumVersion;
    }
}
