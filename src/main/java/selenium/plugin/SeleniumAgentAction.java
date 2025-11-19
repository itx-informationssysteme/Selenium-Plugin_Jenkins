/*
 * Copyright 2025 it.x informationssysteme gmbh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package selenium.plugin;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.*;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

public class SeleniumAgentAction implements Action {

    private final transient Computer computer;

    private transient Proc nodeProcess;
    private boolean nodeActive;
    private transient List<String> nodeRestartLogs = new ArrayList<>();
    private transient long lastNodeCheckTime;

    public SeleniumAgentAction(Computer computer) {
        this.computer = computer;
    }

    @Initializer(after = InitMilestone.JOB_LOADED)
    public static void initAfterStartup() {
        for (Computer computer : Jenkins.get().getComputers()) {
            SeleniumAgentAction nodeAction = computer.getAction(SeleniumAgentAction.class);
            if (nodeAction != null) {
                nodeAction.load();
                nodeAction.checkAndRestartNodeIfNeeded();

                new java.util.Timer()
                        .scheduleAtFixedRate(
                                new java.util.TimerTask() {
                                    public void run() {
                                        nodeAction.checkAndRestartNodeIfNeeded();
                                    }
                                },
                                300000,
                                300000); // check node every 5 minutes
            }
        }
    }

    @Initializer(before = InitMilestone.SYSTEM_CONFIG_LOADED)
    public static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Computer computer : Jenkins.get().getComputers()) {
                SeleniumAgentAction action = computer.getAction(SeleniumAgentAction.class);
                if (action != null && action.nodeProcess != null) {
                    action.stopNode();
                }
            }
        }));
    }

    public synchronized List<String> getNodeRestartLogs() {
        return new ArrayList<>(nodeRestartLogs);
    }

    public synchronized void setNodeRestartLogs(List<String> nodeRestartLogs) {
        this.nodeRestartLogs = new ArrayList<>(nodeRestartLogs);
    }

    public void setNodeProcess(Proc nodeProcess) {
        this.nodeProcess = nodeProcess;
    }

    @DataBoundSetter
    public void setNodeActive(boolean nodeActive) {
        this.nodeActive = nodeActive;
        save();
    }

    @Override
    public String getIconFileName() {
        return "/plugin/selenium-plugin/48x48/selenium.png";
    }

    @Override
    public String getDisplayName() {
        return Messages.SeleniumAgentAction_title();
    }

    @Override
    public String getUrlName() {
        return "selenium";
    }

    public Computer getComputer() {
        return computer;
    }

    public String getVersion() {
        SeleniumGlobalProperty globalProp = ManagementLink.all().get(SeleniumGlobalProperty.class);
        if (globalProp == null || globalProp.getSeleniumVersion() == null) {
            return "No Version set (Change in Selenium Global Configuration)";
        }
        return globalProp.getSeleniumVersion();
    }

    public synchronized void save() {
        try {
            getConfigFile().write(this);
            Jenkins.get().save();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save Selenium config", e);
        }
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

    private XmlFile getConfigFile() {
        return new XmlFile(new File(Jenkins.get().getRootDir(), computer.getName() + "-selenium-config.xml"));
    }

    @RequirePOST
    public HttpResponse doStartNode() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return startNodeInternal();
    }

    @RequirePOST
    public HttpResponse startNodeInternal() {
        if (computer == null || computer.isOffline()) return FormValidation.error("Computer ist offline.");
        SeleniumGlobalProperty globalProp = ManagementLink.all().get(SeleniumGlobalProperty.class);
        if (globalProp == null || !globalProp.getHubActive()) {
            addNodeRestartLog("Selenium Hub is not active. Please start the Hub and try again.");
            return FormValidation.error(Messages.SeleniumAgentAction_error_hubNotActive());
        }
        try {
            FilePath tmp = null;
            if (computer.getNode() != null) {
                Node node = computer.getNode();
                if (node == null) {
                    return FormValidation.error("No Node found.");
                }
                FilePath rootPath = node.getRootPath();
                if (rootPath == null) {
                    return FormValidation.error("No RootPath found.");
                }
                tmp = rootPath.child("selenium-tmp");
            }
            if (tmp == null) {
                return FormValidation.error("No TempPath found.");
            }
            tmp.mkdirs();

            killByPidFile(tmp);

            String version = getVersion().replaceAll("[^0-9.]", "");
            String jarUrl = "https://github.com/SeleniumHQ/selenium/releases/download/selenium-" + version
                    + "/selenium-server-" + version + ".jar";
            FilePath jar = tmp.child("selenium-" + version + ".jar");

            if (!jar.exists()) {
                jar.copyFrom(new URL(jarUrl));
            }

            Launcher launcher = new Launcher.RemoteLauncher(
                    TaskListener.NULL, computer.getChannel(), Boolean.TRUE.equals(computer.isUnix()));
            Launcher.ProcStarter ps = launcher.launch()
                    .cmds(
                            "java",
                            "-jar",
                            jar.getRemote(),
                            "node",
                            "--selenium-manager",
                            "true",
                            "--hub",
                            ManagementLink.all().get(SeleniumGlobalProperty.class).getHubUrl())
                    .pwd(tmp)
                    .stdout(TaskListener.NULL);

            setNodeProcess(ps.start());
            setNodeActive(true);
            addNodeRestartLog("Started Selenium Node");

            writeNodePid(tmp, jar.getRemote());

            save();
        } catch (Exception e) {
            setNodeActive(false);
            addNodeRestartLog("Error starting Selenium Node: " + e.getMessage());
            return FormValidation.error("An error occurred while starting Selenium Node: " + e.getMessage());
        }
        return new HttpRedirect(".");
    }

    @RequirePOST
    public HttpResponse doStopNode() {
        setNodeActive(false);
        return stopNode();
    }

    public HttpResponse stopNode() {
        synchronized (this) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (nodeProcess != null) {
                try {
                    nodeProcess.kill();
                } catch (IOException | InterruptedException e) {
                    addNodeRestartLog("Error stopping Selenium Node: " + e.getMessage());
                    return FormValidation.error("Error stopping Selenium Node: " + e.getMessage());
                }
                setNodeProcess(null);
                addNodeRestartLog("Stopped Selenium Node");
            }
        }
        return new HttpRedirect(".");
    }

    public boolean getNodeActive() throws IOException, InterruptedException {
        return nodeProcess != null && nodeProcess.isAlive();
    }

    public synchronized void addNodeRestartLog(String message) {
        nodeRestartLogs.add(0, new java.util.Date() + ": " + message);
        if (nodeRestartLogs.size() > 25) {
            nodeRestartLogs.remove(nodeRestartLogs.size() - 1);
        }
        save();
    }

    public void checkAndRestartNodeIfNeeded() {
        try {
            if (nodeActive && (!getNodeActive() || nodeProcess == null)) {
                addNodeRestartLog("Trigger automatic restart of Selenium Node (Node not reachable or stopped)");
                this.startNodeInternal();
            }
        } catch (IOException | InterruptedException e) {
            addNodeRestartLog("Error checking Selenium Node: " + e.getMessage());
        }
    }

    private FilePath getPidFile(FilePath tmp) {
        return tmp.child("selenium-node.pid");
    }

    private String readPidFromFile(FilePath f) throws IOException, InterruptedException {
        try (var is = f.read()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    private void killByPidFile(FilePath tmp) {
        try {
            FilePath pidFile = getPidFile(tmp);
            if (pidFile.exists()) {
                String pid = readPidFromFile(pidFile);
                if (!pid.isEmpty() && !pid.equals("0") && !pid.equals("1") && pid.matches("\\d+")) {
                    boolean isUnix = Boolean.TRUE.equals(computer.isUnix());
                    Launcher launcher = new Launcher.RemoteLauncher(TaskListener.NULL, computer.getChannel(), isUnix);
                    if (isUnix) {
                        launcher.launch().cmds("sh", "-c", "kill -9 " + pid + " || true").stdout(TaskListener.NULL).join();
                    } else {
                        launcher.launch().cmds("cmd", "/c", "taskkill /PID " + pid + " /F 2>nul || ver > nul").stdout(TaskListener.NULL).join();
                    }
                    addNodeRestartLog("Killed Node by PID file (PID=" + pid + ")");
                }
                pidFile.delete();
            }
        } catch (Exception e) {
            addNodeRestartLog("killByPidFile failed: " + e.getMessage());
        }
    }

    private void writeNodePid(FilePath tmp, String jarRemote) {
        try {
            boolean isUnix = Boolean.TRUE.equals(computer.isUnix());
            FilePath pidFile = getPidFile(tmp);
            Launcher launcher = new Launcher.RemoteLauncher(TaskListener.NULL, computer.getChannel(), isUnix);
            if (isUnix) {
                if (!jarRemote.matches("^[\\w\\-./]+$")) {
                    throw new IllegalArgumentException("Invalid jarRemote value");
                }
                // Use `pgrep` and correctly read stdout (macOS otherwise only returns exit code)
                ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                int exitCode = launcher.launch()
                        .cmds("pgrep", "-f", "-n", jarRemote)
                        .stdout(out)
                        .join();
                if (exitCode == 0) {
                    String pid = out.toString(StandardCharsets.UTF_8).trim();
                    if (pid.matches("\\d+") && !(pid.equals("0") || pid.equals("1"))) {
                        pidFile.write(pid, StandardCharsets.UTF_8.name());
                    }
                } else {
                    addNodeRestartLog("No process found for jarRemote: " + jarRemote + " (pgrep exit code: " + exitCode + ")");
                }
            } else {
                String escaped = jarRemote.replace("'", "''");
                String ps = "$p=(Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match [regex]::Escape('" + escaped + "') -and $_.CommandLine -match ' node' } | Select-Object -First 1 -ExpandProperty ProcessId); if ($p) { Set-Content -Path '" + pidFile.getRemote().replace("\\", "/") + "' -Value $p }";
                launcher.launch().cmds("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", ps).stdout(TaskListener.NULL).join();
            }
            if (pidFile.exists()) {
                addNodeRestartLog("Wrote Node PID file: " + pidFile.getRemote());
            } else {
                addNodeRestartLog("Node PID file not created (process search may have failed)");
            }
        } catch (Exception e) {
            addNodeRestartLog("writeNodePid failed: " + e.getMessage());
        }
    }
}
