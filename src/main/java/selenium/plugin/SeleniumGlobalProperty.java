package selenium.plugin;

import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
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
            List<String[]> versions = fetchSeleniumVersions();
            for (String[] version : versions) {
                items.add(version[0], version[1]);
            }
        } catch (IOException e) {
            items.add("Fehler beim Laden der Versionen", "");
        }
        return items;
    }

    public FormValidation doStartHub(@QueryParameter String seleniumVersion) {
        this.seleniumVersion = seleniumVersion;
        if (seleniumVersion == null || seleniumVersion.isEmpty()) {
            return FormValidation.error("Bitte wählen Sie eine Selenium-Version aus.");
        }

        try {
            String downloadUrl = String.format(
                    "https://github.com/SeleniumHQ/selenium/releases/download/selenium-%s/selenium-server-%s.jar",
                    seleniumVersion, seleniumVersion);

            File destFile = new File(Jenkins.get().getRootDir(), "selenium-hub.jar");

            try (InputStream in = new URL(downloadUrl).openStream();
                    FileOutputStream out = new FileOutputStream(destFile)) {
                IOUtils.copy(in, out);
            }

            ProcessBuilder pb = new ProcessBuilder("java", "-jar", destFile.getAbsolutePath(), "hub");
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb.start();

            return FormValidation.ok("Selenium Hub wurde erfolgreich gestartet.");
        } catch (IOException e) {
            return FormValidation.error("Fehler beim Starten des Selenium Hubs: " + e.getMessage());
        }
    }

    public FormValidation doSave(@QueryParameter String seleniumVersion) {
        this.seleniumVersion = seleniumVersion;
        //        Jenkins.get().save();
        return FormValidation.ok("Gespeichert");
    }

    private List<String[]> fetchSeleniumVersions() throws IOException {
        String apiUrl = "https://api.github.com/repos/SeleniumHQ/selenium/tags";
        String json = IOUtils.toString(new URL(apiUrl), "UTF-8");
        JSONArray tags = JSONArray.fromObject(json);

        return tags.stream()
                .map(obj -> ((JSONObject) obj).getString("name"))
                .limit(50)
                .sorted()
                .map(version -> new String[] {version, version.replace("selenium-", "")})
                .collect(Collectors.toList());
    }
}
