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
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;

public class SeleniumAgentAction implements Action {

    private final Computer computer;

    private Proc nodeProcess;
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
        if (ManagementLink.all().get(SeleniumGlobalProperty.class).getSeleniumVersion() == null)
            return "No Version set (Change in Selenium Global Configuration)";
        return ManagementLink.all().get(SeleniumGlobalProperty.class).getSeleniumVersion();
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
                    .cmds("java", "-jar", jar.getRemote(), "node", "--hub", "http://localhost:4444/")
                    .pwd(tmp)
                    .stdout(TaskListener.NULL);

            nodeProcess = ps.start();
            nodeActive = true;
        } catch (Exception e) {
            e.printStackTrace();
            nodeActive = false;
        }
        return new HttpRedirect(".");
    }

    public HttpResponse doStopNode() {
        if (nodeProcess != null) {
            try {
                nodeProcess.kill();
                nodeProcess.wait();
            } catch (InterruptedException | IOException ignored) {
            }
            nodeProcess = null;
        }
        nodeActive = false;
        return new HttpRedirect(".");
    }

    public boolean getNodeActive() throws IOException, InterruptedException {
        return nodeProcess != null && nodeProcess.isAlive();
    }
}
