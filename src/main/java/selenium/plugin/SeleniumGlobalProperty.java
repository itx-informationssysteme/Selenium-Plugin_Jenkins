package selenium.plugin;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
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
    private transient Process hubProcess;

    private String seleniumVersion;
    private boolean hubActive;

    // Getter für die Version
    public String getSeleniumVersion() {
        return seleniumVersion;
    }

    @DataBoundSetter
    public void setSeleniumVersion(String seleniumVersion) {
        this.seleniumVersion = seleniumVersion;
        save();
    }

    public boolean getHubActive() {
        return hubActive;
    }

    @DataBoundSetter
    public void setHubActive(boolean hubShouldBeRunning) {
        this.hubActive = hubShouldBeRunning;
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

    @Initializer(after = InitMilestone.JOB_LOADED)
    public static void initAfterStartup() {
        SeleniumGlobalProperty instance = ManagementLink.all().get(SeleniumGlobalProperty.class);
        if (instance != null && instance.seleniumVersion != null && instance.hubActive) {
            if (!instance.isHubReachable()) {
                instance.doStartHub();
            }
        }
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
            this.hubProcess = pb.start();
            this.hubActive = true;
            save();

            return FormValidation.ok("Selenium Hub wurde erfolgreich gestartet.");
        } catch (IOException e) {
            return FormValidation.error("Fehler beim Starten des Selenium Hubs: " + e.getMessage());
        }
    }

    public FormValidation doStopHub() {
        if (hubProcess == null) {
            return FormValidation.ok("Kein Prozess vorhanden.");
        }
        if (!hubProcess.isAlive()) {
            return FormValidation.ok("Selenium Hub ist bereits gestoppt.");
        }
        try {
            hubProcess.destroy();
            hubProcess.waitFor();
            hubProcess = null;
            this.hubActive = false;
            save();
            return FormValidation.ok("Selenium Hub wurde gestoppt.");
        } catch (InterruptedException e) {
            return FormValidation.error("Fehler beim Stoppen des Selenium Hubs: " + e.getMessage());
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
        // Cache für 24 Stunden
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
            return "Hub gestartet, aber keine Nodes registriert (Url: " + getHubUrl() + ")";
        } else {
            return "Hub läuft unter " + getHubUrl();
        }
    }

    public boolean getHubRunning() {
        return isHubReachable();
    }

    public String getHubUrl() {
        return "http://localhost:4444/ui";
    }
}
