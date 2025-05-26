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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

@Extension
public class SeleniumGlobalProperty extends ManagementLink {

    private transient volatile List<String[]> cachedVersions;
    private transient volatile long lastFetchTime;

    private String seleniumVersion;

    // Getter für die Version
    public String getSeleniumVersion() {
        return seleniumVersion;
    }

    @DataBoundSetter
    public void setSeleniumVersion(String seleniumVersion) {
        this.seleniumVersion = seleniumVersion;
        save();
    }

    // Methode zum Speichern der Konfiguration
    private void save() {
        try {
            Jenkins.get().save();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save configuration", e);
        }
    }

    public FormValidation doSave(@QueryParameter String seleniumVersion) {
        setSeleniumVersion(seleniumVersion);
        return FormValidation.ok("Selenium Version " + seleniumVersion + " wurde gespeichert");
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
        List<String[]> versions = fetchSeleniumVersions();
        for (String[] version : versions) {
            items.add(version[0], version[1]);
        }
        return items;
    }

    public FormValidation doStartHub() {
        if (this.seleniumVersion == null || this.seleniumVersion.isEmpty()) {
            return FormValidation.error("Bitte wählen Sie eine Selenium-Version aus.");
        }

        try {
            String downloadUrl = String.format(
                    "https://github.com/SeleniumHQ/selenium/releases/download/selenium-%s/selenium-server-%s.jar",
                    this.seleniumVersion, this.seleniumVersion);

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

    private List<String[]> fetchSeleniumVersions() {
        try {
            return tryFetchFromApi();
        } catch (IOException e) {
            return getCachedOrDefaultVersions();
        }
    }

    private List<String[]> tryFetchFromApi() throws IOException {
        // Cache für 24 Stunden (86.400.000 Millisekunden)
        if (cachedVersions != null && System.currentTimeMillis() - lastFetchTime < 86400000) {
            return cachedVersions;
        }

        String apiUrl = "https://api.github.com/repos/SeleniumHQ/selenium/tags";
        String json = IOUtils.toString(new URL(apiUrl), "UTF-8");
        JSONArray tags = JSONArray.fromObject(json);

        List<String[]> versions = tags.stream()
                .map(obj -> ((JSONObject) obj).getString("name"))
                .limit(50)
                .sorted()
                .map(version -> new String[] {version, version.replace("selenium-", "")})
                .collect(Collectors.toList());

        cachedVersions = tags.stream()
                .map(obj -> ((JSONObject) obj).getString("name"))
                .limit(50)
                .sorted()
                .map(version ->
                        new String[] {version.replace("selenium-", "cached-selenium-"), version.replace("selenium-", "")
                        })
                .collect(Collectors.toList());

        //        cachedVersions = versions;
        lastFetchTime = System.currentTimeMillis();
        return versions;
    }

    private List<String[]> getCachedOrDefaultVersions() {
        if (cachedVersions != null) return cachedVersions;
        return Arrays.asList(
                new String[] {"selenium-4.33.0-default", "4.33.0"},
                new String[] {"selenium-4.32.0-default", "4.32.0"},
                new String[] {"selenium-4.31.0-default", "4.31.0"},
                new String[] {"selenium-4.30.0-default", "4.30.0"});
    }

    public boolean isHubReachable() {
        try {
            URL statusUrl = new URL("http://localhost:4444/status");
            try (InputStream in = statusUrl.openStream()) {
                IOUtils.toString(in, "UTF-8"); // nur aufrufbar = erreichbar
                return true;
            }
        } catch (IOException e) {
            return false;
        }
    }

    public Boolean isHubReady() {
        try {
            URL statusUrl = new URL("http://localhost:4444/status");
            try (InputStream in = statusUrl.openStream()) {
                String response = IOUtils.toString(in, "UTF-8");
                JSONObject status = JSONObject.fromObject(response);
                return status.getJSONObject("value").getBoolean("ready");
            }
        } catch (IOException e) {
            return false;
        }
    }

    public String getHubStatusText() {
        if (!isHubReachable()) {
            return "Hub nicht im Betrieb";
        } else if (!isHubReady()) {
            return "Hub gestartet, aber keine Nodes registriert";
        } else {
            return "Hub läuft unter <a href=\"" + getHubUrl() + "\" target=\"_blank\">" + getHubUrl() + "</a>";
        }
    }

    public boolean getHubRunning() {
        return isHubReachable();
    }

    public String getHubUrl() {
        return "http://localhost:4444/ui";
    }
}
