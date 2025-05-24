package selenium.plugin;

import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
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
        try {
            List<String> versions = fetchSeleniumVersions();
            for (String version : versions) {
                items.add(version, version);
            }
        } catch (IOException e) {
            items.add("Fehler beim Laden der Versionen", "");
        }
        return items;
    }

    public void doSave(@QueryParameter String seleniumVersion) {
        this.seleniumVersion = seleniumVersion;
    }

    private List<String> fetchSeleniumVersions() throws IOException {
        String apiUrl = "https://api.github.com/repos/SeleniumHQ/selenium/tags";
        String json = IOUtils.toString(new URL(apiUrl), "UTF-8");
        JSONArray tags = JSONArray.fromObject(json);

        return tags.stream()
                .map(obj -> ((JSONObject) obj).getString("name"))
                .limit(50)
                .sorted()
                .collect(Collectors.toList());
    }
}
