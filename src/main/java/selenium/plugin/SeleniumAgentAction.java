package selenium.plugin;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.ManagementLink;
import hudson.model.TaskListener;
import java.io.IOException;
import java.net.URL;

import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;

public class SeleniumAgentAction implements Action {

    private final Computer computer;

    private Proc nodeProcess;
    private boolean nodeActive;

    public SeleniumAgentAction(Computer computer) {
        this.computer = computer;
    }

    @DataBoundSetter
    public void setNodeProcess(Proc nodeProcess) {
        this.nodeProcess = nodeProcess;
        save();
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
        if (ManagementLink.all().get(SeleniumGlobalProperty.class).getSeleniumVersion() == null)
            return "No Version set (Change in Selenium Global Configuration)";
        return ManagementLink.all().get(SeleniumGlobalProperty.class).getSeleniumVersion();
    }

    private void save() {
        try {
            Jenkins.get().save();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save configuration", e);
        }
    }

    public HttpResponse doStartNode() {
        if (!ManagementLink.all().get(SeleniumGlobalProperty.class).getHubActive()) {
            return FormValidation.error(
                    "Selenium-Hub ist nicht aktiv. Bitte starten sie das Hub und versuchen Sie es erneut.");
        }
        try {
            FilePath tmp = computer.getNode().getRootPath().child("selenium-tmp");
            tmp.mkdirs();

            String version = getVersion().replaceAll("[^0-9.]", "");
            String jarUrl = "https://github.com/SeleniumHQ/selenium/releases/download/selenium-" + version
                    + "/selenium-server-" + version + ".jar";
            FilePath jar = tmp.child("selenium-" + version + ".jar");

            if (!jar.exists()) {
                jar.copyFrom(new URL(jarUrl));
            }

            Launcher launcher = new Launcher.RemoteLauncher(TaskListener.NULL, computer.getChannel(), Boolean.TRUE.equals(computer.isUnix()));
            Launcher.ProcStarter ps = launcher.launch()
                    .cmds("java", "-jar", jar.getRemote(), "node", "--selenium-manager", "true", "--hub", ManagementLink.all().get(SeleniumGlobalProperty.class).getHubUrl())
                    .pwd(tmp)
                    .stdout(TaskListener.NULL);

            setNodeProcess(ps.start());
            nodeActive = true;
        } catch (Exception e) {
            e.printStackTrace();
            nodeActive = false;
        }
        return new HttpRedirect(".");
    }

    public HttpResponse doStopNode() {
        synchronized (this) { // oder synchronized (nodeProcess), wenn nodeProcess nicht null ist
            if (nodeProcess != null) {
                try {
                    nodeProcess.kill();
                } catch (IOException | InterruptedException e) {
                    return FormValidation.error("Fehler beim Stoppen des Selenium Nodes: " + e.getMessage());
                }
                setNodeProcess(null);
            }
        }
        nodeActive = false;
        return new HttpRedirect(".");
    }

    public boolean getNodeActive() throws IOException, InterruptedException {
        return nodeProcess != null && nodeProcess.isAlive();
    }
}
