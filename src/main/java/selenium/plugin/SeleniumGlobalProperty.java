package selenium.plugin;

import hudson.Extension;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Computer;
import hudson.model.ManagementLink;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Extension
public class SeleniumGlobalProperty extends ManagementLink {

    private transient volatile List<String[]> cachedVersions;
    private transient volatile long lastFetchTime;
    private transient Process hubProcess;
    private transient List<String> hubRestartLogs = new ArrayList<>();
    private transient long lastHubCheckTime;

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

    public synchronized List<String> getHubRestartLogs() {
        return hubRestartLogs;
    }

    public synchronized void setHubRestartLogs(List<String> hubRestartLogs) {
        this.hubRestartLogs = hubRestartLogs;
    }

    public synchronized void addHubRestartLog(String message) {
        hubRestartLogs.add(0, new java.util.Date() + ": " + message);
        if (hubRestartLogs.size() > 25) {
            hubRestartLogs.remove(hubRestartLogs.size() - 1);
        }
        save();
    }

    @DataBoundSetter
    public void setHubActive(boolean hubShouldBeRunning) {
        this.hubActive = hubShouldBeRunning;
        save();
    }

    public synchronized void save() {
        try {
            getConfigFile().write(this);
            Jenkins.get().save();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save Selenium config", e);
        }
    }

    @RequirePOST
    public HttpResponse doSave(@QueryParameter String seleniumVersion) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        boolean versionChanged = !seleniumVersion.equals(this.seleniumVersion);
        setSeleniumVersion(seleniumVersion);

        if (versionChanged) {
            if (hubActive) {
                addHubRestartLog("Restarting Selenium Hub for version update");
                doStopHub();
                doStartHub();
                addHubRestartLog("Hub restarted with new version " + seleniumVersion);
            }
            addHubRestartLog("Restarting agents for version update");
            restartAllActiveAgents();
            addHubRestartLog("Agents restarted with new version " + seleniumVersion);
        }

        return new HttpRedirect(".");
    }

    private void restartAllActiveAgents() {
        for (Computer computer : Jenkins.get().getComputers()) {
            if (computer.getName().equals("") || computer.getSearchName().equals("Jenkins")) {
                continue;
            }

            SeleniumAgentAction agentAction = computer.getAction(SeleniumAgentAction.class);
            try {
                if (agentAction != null && agentAction.getNodeActive()) {
                    agentAction.addNodeRestartLog("Restarting agent for version update");
                    agentAction.doStopNode();
                    agentAction.doStartNode();
                    agentAction.addNodeRestartLog("Agent restarted with new version " + seleniumVersion);
                }
            } catch (Exception e) {
                agentAction.addNodeRestartLog("Error during agent update: " + e.getMessage());
                addHubRestartLog("Error during agent update of " + computer.getName() + ": " + e.getMessage());
            }
        }
    }

    @Initializer(after = InitMilestone.JOB_LOADED)
    public static void initAfterStartup() {
        SeleniumGlobalProperty instance = ManagementLink.all().get(SeleniumGlobalProperty.class);
        if (instance != null) {
            instance.load();
            instance.checkAndRestartHubIfNeeded();

            new java.util.Timer()
                    .scheduleAtFixedRate(
                            new java.util.TimerTask() {
                                public void run() {
                                    instance.checkAndRestartHubIfNeeded();
                                }
                            },
                            300000,
                            300000); // check hub every 5 minutes
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
        return "/plugin/selenium-plugin/48x48/selenium.png";
    }

    @RequirePOST
    public ListBoxModel doFillSeleniumVersionItems() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        ListBoxModel items = new ListBoxModel();
        List<String[]> versions = fetchSeleniumVersions();
        for (String[] version : versions) {
            items.add(version[0], version[1]);
        }
        return items;
    }

    @RequirePOST
    public HttpResponse doStartHub() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        if (this.seleniumVersion == null || this.seleniumVersion.isEmpty()) {
            return FormValidation.error(
                    "Bitte wählen Sie eine Selenium-Version aus und speichern Sie die Konfiguration.");
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
            addHubRestartLog("Started Selenium Hub");
            save();

            int retries = 0;
            while (!isHubReachable() && retries < 10) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                retries++;
            }

            return new HttpRedirect(".");

        } catch (IOException e) {
            addHubRestartLog("Fehler beim Starten des Hubs: " + e.getMessage());
            return FormValidation.error("Fehler beim Starten des Selenium Hubs: " + e.getMessage());
        }
    }

    @RequirePOST
    public HttpResponse doStopHub() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        if (hubProcess == null) {
            return FormValidation.error("Kein Prozess vorhanden.");
        }
        if (!hubProcess.isAlive()) {
            return FormValidation.ok("Selenium Hub ist bereits gestoppt.");
        }
        try {
            hubProcess.destroy();
            hubProcess.waitFor();
            hubProcess = null;
            this.hubActive = false;
            addHubRestartLog("Stopped Selenium Hub");
            save();
            return new HttpRedirect(".");
        } catch (InterruptedException e) {
            addHubRestartLog("Error stopping Selenium Hub: " + e.getMessage());
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
        if (cachedVersions != null && System.currentTimeMillis() - lastFetchTime < 86400000) {
            return cachedVersions;
        }

        String apiUrl = "https://api.github.com/repos/SeleniumHQ/selenium/tags";
        String json = IOUtils.toString(new URL(apiUrl), StandardCharsets.UTF_8);
        JSONArray tags = JSONArray.fromObject(json);

        List<String[]> versions = tags.stream()
                .map(obj -> ((JSONObject) obj).getString("name"))
                .filter(version -> version.matches("^selenium-\\d+\\.\\d+\\.\\d+$"))
                .limit(15)
                .sorted(Comparator.reverseOrder())
                .map(version -> new String[] {version, version.replace("selenium-", "")})
                .collect(Collectors.toList());

        cachedVersions = versions;
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
            URL statusUrl = new URL(getHubUrl() + "/status");
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
            URL statusUrl = new URL(getHubUrl() + "/status");
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
        if (seleniumVersion == null) {
            return "Bitte setzen Sie die Selenium Version und speichern Sie die Konfiguration.";
        } else if (!isHubReachable()) {
            return "Hub nicht im Betrieb";
        } else if (!isHubReady()) {
            return "Hub gestartet, aber keine Nodes registriert (Url: " + getHubUrl() + "/ui/)";
        } else {
            return "Hub läuft unter " + getHubUrl() + "/ui/";
        }
    }

    public boolean getHubRunning() {
        return isHubReachable();
    }

    public String getHubUrl() {
        String jenkinsUrl = Jenkins.get().getRootUrl();
        if (jenkinsUrl == null) {
            return "http://localhost:4444";
        }

        try {
            URL url = new URL(jenkinsUrl);
            return new URL(url.getProtocol(), url.getHost(), 4444, "").toString();
        } catch (Exception e) {
            return "http://localhost:4444";
        }
    }

    public List<Computer> getAgents() {
        return Arrays.stream(Jenkins.get().getComputers())
                .filter(c -> !"Jenkins".equals(c.getDisplayName())) // Master/Controller ausschließen
                .collect(Collectors.toList());
    }

    public boolean hasSeleniumServer(Computer computer) throws IOException, InterruptedException {
        if (computer.getName().equals("") || computer.getSearchName().equals("Jenkins")) {
            return isHubReachable();
        }
        SeleniumAgentAction action = computer.getAction(SeleniumAgentAction.class);
        return action != null && action.getNodeActive();
    }

    public String getAgentUrl(Computer computer) {
        if (computer.getName().equals("") || computer.getSearchName().equals("Jenkins")) {
            return Jenkins.get().getRootUrl() + "computer/(built-in)/selenium-settings";
        }
        return Jenkins.get().getRootUrl() + "computer/" + computer.getName() + "/selenium";
    }

    public JSONObject getGridStatus() {
        try {
            URL statusUrl = new URL(getHubUrl() + "/status");
            try (InputStream in = statusUrl.openStream()) {
                String response = IOUtils.toString(in, "UTF-8");
                return JSONObject.fromObject(response);
            }
        } catch (IOException e) {
            JSONObject errorStatus = new JSONObject();
            JSONObject value = new JSONObject();
            value.put("ready", false);
            value.put("message", "Hub nicht erreichbar: " + e.getMessage());
            value.put("nodes", new JSONArray());
            errorStatus.put("value", value);
            return errorStatus;
        }
    }

    public void checkAndRestartHubIfNeeded() {
        if (hubActive && (!isHubReachable() || hubProcess == null)) {
            addHubRestartLog("Trigger automatic restart of Selenium Hub (Hub not reachable or stopped)");
            doStartHub();
        }
    }

    private XmlFile getConfigFile() {
        return new XmlFile(new File(Jenkins.get().getRootDir(), "selenium-config.xml"));
    }

    public synchronized void load() {
        try {
            if (getConfigFile().exists()) {
                getConfigFile().unmarshal(this);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Selenium config", e);
        }
    }
}
