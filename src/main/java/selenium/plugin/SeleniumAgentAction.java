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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

public class SeleniumAgentAction implements Action {

    private static final Logger LOGGER = Logger.getLogger(SeleniumAgentAction.class.getName());

    private final transient Computer computer;

    private transient Proc nodeProcess;
    private boolean nodeActive;
    private transient List<String> nodeRestartLogs = new ArrayList<>();

    public SeleniumAgentAction(Computer computer) {
        this.computer = computer;
        LOGGER.log(Level.FINE, "SeleniumAgentAction created for computer: {0}", computer.getName());
    }

    @Initializer(before = InitMilestone.SYSTEM_CONFIG_LOADED)
    public static void registerShutdownHook() {
        LOGGER.fine("registerShutdownHook: Registering Jenkins shutdown hook for Selenium nodes");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.fine("Shutdown hook triggered - stopping all Selenium nodes");
            for (Computer computer : Jenkins.get().getComputers()) {
                SeleniumAgentAction action = computer.getAction(SeleniumAgentAction.class);
                if (action != null && action.nodeProcess != null) {
                    LOGGER.log(Level.FINE, "Stopping Selenium node on: {0}", computer.getName());
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
        LOGGER.log(Level.INFO, "setNodeProcess: nodeProcess set to {0} for computer: {1}", new Object[] {
            nodeProcess != null ? "non-null" : "null", computer.getName()
        });
    }

    @DataBoundSetter
    public void setNodeActive(boolean nodeActive) {
        LOGGER.log(Level.INFO, "setNodeActive: {0} -> {1} for computer: {2}", new Object[] {
            this.nodeActive, nodeActive, computer.getName()
        });
        this.nodeActive = nodeActive;
        save();
    }

    @Override
    public String getIconFileName() {
        return "symbol-selenium-icon-solid plugin-oss-symbols-api";
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
            LOGGER.log(Level.FINE, "save: Saving config for computer: {0}, nodeActive={1}", new Object[] {
                computer.getName(), nodeActive
            });
            getConfigFile().write(this);
            Jenkins.get().save();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "save: Failed to save config for: " + computer.getName(), e);
            throw new RuntimeException("Failed to save Selenium config", e);
        }
    }

    public synchronized void load() {
        try {
            if (getConfigFile().exists()) {
                LOGGER.log(Level.INFO, "load: Loading config from file for computer: {0}", computer.getName());
                getConfigFile().unmarshal(this);
                LOGGER.log(Level.INFO, "load: Loaded nodeActive={0} for computer: {1}", new Object[] {
                    nodeActive, computer.getName()
                });
                addNodeRestartLog("Loaded saved config: nodeActive=" + nodeActive);
            } else {
                LOGGER.log(Level.INFO, "load: No config file found for computer: {0}", computer.getName());
                addNodeRestartLog("No saved config file found, using defaults");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "load: Failed to load config for: " + computer.getName(), e);
            throw new RuntimeException("Failed to load Selenium config", e);
        }
    }

    private XmlFile getConfigFile() {
        return new XmlFile(new File(Jenkins.get().getRootDir(), computer.getName() + "-selenium-config.xml"));
    }

    @RequirePOST
    public HttpResponse doStartNode() {
        LOGGER.log(Level.INFO, "doStartNode: Manual start triggered for computer: {0}", computer.getName());
        addNodeRestartLog("Manual node start triggered via UI");
        Jenkins.get().checkPermission(Jenkins.MANAGE);
        return startNodeInternal();
    }

    @RequirePOST
    public HttpResponse startNodeInternal() {
        LOGGER.log(Level.INFO, "startNodeInternal: Starting Selenium node for computer: {0}", computer.getName());
        addNodeRestartLog("startNodeInternal() called");

        if (computer.isOffline()) {
            LOGGER.log(Level.WARNING, "startNodeInternal: Computer is offline: {0}", computer.getName());
            addNodeRestartLog("ERROR: Computer is offline");
            return FormValidation.error("Computer is offline.");
        }

        SeleniumGlobalProperty globalProp = ManagementLink.all().get(SeleniumGlobalProperty.class);
        if (globalProp == null) {
            LOGGER.log(Level.WARNING, "startNodeInternal: SeleniumGlobalProperty is null");
            addNodeRestartLog("ERROR: SeleniumGlobalProperty is null");
            return FormValidation.error("Selenium Global Property not found.");
        }

        if (!globalProp.getHubActive()) {
            LOGGER.log(Level.WARNING, "startNodeInternal: Selenium Hub is not active");
            addNodeRestartLog("ERROR: Selenium Hub is not active. Please start the Hub first.");
            return FormValidation.error(Messages.SeleniumAgentAction_error_hubNotActive());
        }

        String hubUrl = globalProp.getHubUrl();
        LOGGER.log(Level.INFO, "startNodeInternal: Hub URL is: {0}", hubUrl);
        addNodeRestartLog("Hub URL: " + hubUrl);

        try {
            Node node = computer.getNode();
            if (node == null) {
                LOGGER.log(Level.WARNING, "startNodeInternal: Node is null");
                addNodeRestartLog("ERROR: Node is null");
                return FormValidation.error("No Node found.");
            }
            FilePath rootPath = node.getRootPath();
            if (rootPath == null) {
                LOGGER.log(Level.WARNING, "startNodeInternal: RootPath is null");
                addNodeRestartLog("ERROR: RootPath is null");
                return FormValidation.error("No RootPath found.");
            }
            FilePath tmp = rootPath.child("selenium-tmp");
            LOGGER.log(Level.INFO, "startNodeInternal: Using temp path: {0}", tmp.getRemote());
            addNodeRestartLog("Temp path: " + tmp.getRemote());
            tmp.mkdirs();

            killByPidFile(tmp);

            addNodeRestartLog("Checking for processes on port 5555...");
            boolean isUnix = Boolean.TRUE.equals(computer.isUnix());
            try {
                Launcher killLauncher = new Launcher.RemoteLauncher(TaskListener.NULL, computer.getChannel(), isUnix);
                if (isUnix) {
                    // On Unix/Mac: find and kill process on port 5555
                    ByteArrayOutputStream lsofOut = new ByteArrayOutputStream();
                    killLauncher
                            .launch()
                            .cmds("sh", "-c", "lsof -ti:5555 | xargs kill -9 2>/dev/null || true")
                            .stdout(lsofOut)
                            .stderr(lsofOut)
                            .join();
                    String lsofResult = lsofOut.toString(StandardCharsets.UTF_8).trim();
                    if (!lsofResult.isEmpty()) {
                        addNodeRestartLog("Killed process on port 5555: " + lsofResult);
                    } else {
                        addNodeRestartLog("No process found on port 5555");
                    }
                } else {
                    killLauncher
                            .launch()
                            .cmds(
                                    "cmd",
                                    "/c",
                                    "for /f \"tokens=5\" %a in ('netstat -aon ^| findstr :5555') do taskkill /PID %a /F 2>nul")
                            .stdout(TaskListener.NULL)
                            .join();
                    addNodeRestartLog("Attempted to kill process on port 5555 (Windows)");
                }
                Thread.sleep(1000);
            } catch (Exception e) {
                addNodeRestartLog("Warning: Could not check/kill process on port 5555: " + e.getMessage());
            }

            String version = getVersion().replaceAll("[^0-9.]", "");
            LOGGER.log(Level.INFO, "startNodeInternal: Selenium version: {0}", version);
            addNodeRestartLog("Selenium version: " + version);

            String jarUrl = "https://github.com/SeleniumHQ/selenium/releases/download/selenium-" + version
                    + "/selenium-server-" + version + ".jar";
            FilePath jar = tmp.child("selenium-" + version + ".jar");

            if (!jar.exists()) {
                LOGGER.log(Level.INFO, "startNodeInternal: Downloading Selenium JAR from: {0}", jarUrl);
                addNodeRestartLog("Downloading Selenium JAR from: " + jarUrl);
                jar.copyFrom(new URL(jarUrl));
                addNodeRestartLog("Download complete");
            } else {
                LOGGER.log(Level.INFO, "startNodeInternal: Selenium JAR already exists: {0}", jar.getRemote());
                addNodeRestartLog("Selenium JAR already exists, skipping download");
            }

            LOGGER.log(Level.INFO, "startNodeInternal: Launching Selenium node process...");
            addNodeRestartLog("Launching Selenium node with --hub " + hubUrl);

            addNodeRestartLog("System Info: isUnix=" + isUnix + ", computer=" + computer.getName());

            try {
                Launcher javaCheckLauncher =
                        new Launcher.RemoteLauncher(TaskListener.NULL, computer.getChannel(), isUnix);
                ByteArrayOutputStream javaVersionOut = new ByteArrayOutputStream();
                int javaExitCode = javaCheckLauncher
                        .launch()
                        .cmds("java", "-version")
                        .stdout(javaVersionOut)
                        .stderr(javaVersionOut)
                        .join();
                String javaVersionOutput = javaVersionOut.toString(StandardCharsets.UTF_8);
                addNodeRestartLog("Java check exit code: " + javaExitCode);
                if (!javaVersionOutput.isEmpty()) {
                    String[] lines = javaVersionOutput.split("\n");
                    for (String line : lines) {
                        if (!line.trim().isEmpty()) {
                            addNodeRestartLog("Java: " + line.trim());
                        }
                    }
                }
            } catch (Exception e) {
                addNodeRestartLog("WARNING: Could not check Java version: " + e.getMessage());
            }

            // Build command - use nohup on Unix/Mac to run as background daemon
            List<String> cmdList = new ArrayList<>();

            if (isUnix) {
                // On Unix/Mac: use nohup to detach from terminal and redirect output to file
                cmdList.add("nohup");
            }

            cmdList.add("java");
            cmdList.add("-jar");
            cmdList.add(jar.getRemote());
            cmdList.add("node");
            cmdList.add("--selenium-manager");
            cmdList.add("true");
            cmdList.add("--hub");
            cmdList.add(hubUrl);
            cmdList.add("--port");
            cmdList.add("5555");

            String[] cmdArray = cmdList.toArray(new String[0]);
            addNodeRestartLog("Command: " + String.join(" ", cmdArray));
            addNodeRestartLog("Working directory: " + tmp.getRemote());
            addNodeRestartLog("Running as background daemon (nohup on Unix/Mac)");

            Launcher launcher = new Launcher.RemoteLauncher(TaskListener.NULL, computer.getChannel(), isUnix);

            FilePath logFile = tmp.child("selenium-node.log");
            addNodeRestartLog("Log file: " + logFile.getRemote());

            Launcher.ProcStarter ps = launcher.launch().cmds(cmdArray).pwd(tmp);

            if (isUnix) {
                ps.stdout(logFile.write());
                ps.stderr(logFile.write());
            } else {
                ByteArrayOutputStream processOutput = new ByteArrayOutputStream();
                ps.stdout(processOutput);
                ps.stderr(processOutput);
            }

            addNodeRestartLog("Starting process...");
            Proc process = ps.start();
            setNodeProcess(process);
            addNodeRestartLog("Process started");

            Thread.sleep(5000);

            if (isUnix && logFile.exists()) {
                try {
                    String logContent = logFile.readToString();
                    if (!logContent.isEmpty()) {
                        String[] lines = logContent.split("\n");

                        addNodeRestartLog("=== Selenium Node Log ===");
                        int startIndex = Math.max(0, lines.length - 15);
                        for (int i = startIndex; i < lines.length; i++) {
                            String line = lines[i];
                            if (!line.trim().isEmpty()) {
                                addNodeRestartLog(line.trim());
                            }
                        }
                        addNodeRestartLog("=== End of Log ===");
                    }
                } catch (Exception e) {
                    addNodeRestartLog("Could not read log file: " + e.getMessage());
                }
            }

            try {
                boolean isAlive = process.isAlive();
                addNodeRestartLog("Process running: " + isAlive);
                if (!isAlive) {
                    addNodeRestartLog("Process died shortly after start - check log above for errors");
                    setNodeActive(false);
                    return FormValidation.error("Selenium Node process died shortly after start.");
                }
            } catch (IOException | InterruptedException e) {
                // On Unix with nohup, isAlive() may fail - that's OK
                addNodeRestartLog("Could not verify process status (normal for daemon mode)");
            }

            LOGGER.log(Level.INFO, "startNodeInternal: Selenium node started for: {0}", computer.getName());
            addNodeRestartLog("Selenium Node started successfully");

            setNodeActive(true);
            addNodeRestartLog("nodeActive set to true");

            writeNodePid(tmp, jar.getRemote());
            LOGGER.log(Level.INFO, "startNodeInternal: PID file written");
            addNodeRestartLog("PID file written");

            save();
            LOGGER.log(Level.INFO, "startNodeInternal: Config saved");
            addNodeRestartLog("Configuration saved - Node should now register with Hub");

        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Error starting Selenium Node on " + computer.getName(), e);
            addNodeRestartLog("Failed to start Selenium Node: " + e.getMessage());
            setNodeActive(false);
            return FormValidation.error("Failed to start Selenium Node: " + e.getMessage());
        }
        return new HttpRedirect(".");
    }

    @RequirePOST
    public HttpResponse doStopNode() {
        LOGGER.log(Level.INFO, "doStopNode: Manual stop triggered for computer: {0}", computer.getName());
        addNodeRestartLog("Manual node stop triggered via UI");
        setNodeActive(false);
        return stopNode();
    }

    public HttpResponse stopNode() {
        synchronized (this) {
            LOGGER.log(Level.INFO, "stopNode: Stopping Selenium node for computer: {0}", computer.getName());
            addNodeRestartLog("stopNode() called");

            Jenkins.get().checkPermission(Jenkins.MANAGE);
            if (nodeProcess != null) {
                try {
                    LOGGER.log(Level.INFO, "stopNode: Killing node process");
                    addNodeRestartLog("Killing node process...");
                    nodeProcess.kill();
                    LOGGER.log(Level.INFO, "stopNode: Node process killed successfully");
                    addNodeRestartLog("Node process killed successfully");
                } catch (IOException | InterruptedException e) {
                    LOGGER.log(Level.SEVERE, "stopNode: Error stopping Selenium Node", e);
                    addNodeRestartLog("ERROR stopping Selenium Node: " + e.getMessage());
                    return FormValidation.error("Error stopping Selenium Node: " + e.getMessage());
                }
                setNodeProcess(null);
                addNodeRestartLog("nodeProcess set to null");
            } else {
                LOGGER.log(Level.INFO, "stopNode: nodeProcess is already null, nothing to stop");
                addNodeRestartLog("nodeProcess is already null, nothing to stop");
            }
        }
        return new HttpRedirect(".");
    }

    public boolean getNodeActive() throws IOException, InterruptedException {
        boolean isAlive = nodeProcess != null && nodeProcess.isAlive();
        LOGGER.log(Level.FINE, "getNodeActive: nodeProcess={0}, isAlive={1}, computer={2}", new Object[] {
            nodeProcess != null ? "non-null" : "null", isAlive, computer.getName()
        });
        return isAlive;
    }

    public boolean isNodeActiveConfigured() {
        return nodeActive;
    }

    public synchronized void addNodeRestartLog(String message) {
        String logEntry = new java.util.Date() + ": " + message;
        LOGGER.log(Level.INFO, "NodeLog [{0}]: {1}", new Object[] {computer.getName(), message});
        nodeRestartLogs.add(0, logEntry);
        if (nodeRestartLogs.size() > 50) { // Increased from 25 to 50 for more history
            nodeRestartLogs.remove(nodeRestartLogs.size() - 1);
        }
    }

    public void checkAndRestartNodeIfNeeded() {
        LOGGER.log(
                Level.INFO, "checkAndRestartNodeIfNeeded: Checking node status for computer: {0}", computer.getName());
        addNodeRestartLog("checkAndRestartNodeIfNeeded() called");

        try {
            // Check if agent is reachable before attempting to check node status

            if (computer.isOffline()) {
                LOGGER.log(Level.INFO, "checkAndRestartNodeIfNeeded: Computer is offline: {0}", computer.getName());
                addNodeRestartLog("Computer is offline, skipping check");
                return;
            }

            if (computer.getChannel() == null) {
                LOGGER.log(
                        Level.WARNING,
                        "checkAndRestartNodeIfNeeded: Computer channel is null: {0}",
                        computer.getName());
                addNodeRestartLog("Computer channel is null, skipping check");
                return;
            }

            LOGGER.log(Level.INFO, "checkAndRestartNodeIfNeeded: nodeActive={0}, nodeProcess={1}", new Object[] {
                nodeActive, nodeProcess != null ? "non-null" : "null"
            });
            addNodeRestartLog("Status check: nodeActive=" + nodeActive + ", nodeProcess="
                    + (nodeProcess != null ? "exists" : "null"));

            // If nodeActive is true (node SHOULD be running), ensure the node IS running
            if (nodeActive) {
                boolean nodeRunning = nodeProcess != null && getNodeActive();
                LOGGER.log(Level.INFO, "checkAndRestartNodeIfNeeded: nodeActive=true, nodeRunning={0}", nodeRunning);
                addNodeRestartLog("nodeActive=true, checking if process is alive: " + nodeRunning);

                if (!nodeRunning) {
                    LOGGER.log(
                            Level.INFO, "checkAndRestartNodeIfNeeded: Node should be running but isn't, restarting...");
                    addNodeRestartLog("Node should be running but process is not alive - triggering restart");
                    this.startNodeInternal();
                } else {
                    addNodeRestartLog("Node is running as expected, no action needed");
                }
            } else {
                LOGGER.log(Level.INFO, "checkAndRestartNodeIfNeeded: nodeActive=false, no restart needed");
                addNodeRestartLog("nodeActive=false, no restart needed");
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "checkAndRestartNodeIfNeeded: Error checking Selenium Node", e);
            addNodeRestartLog("ERROR checking Selenium Node: " + e.getClass().getSimpleName() + ": " + e.getMessage());
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
                        launcher.launch()
                                .cmds("sh", "-c", "kill -9 " + pid + " || true")
                                .stdout(TaskListener.NULL)
                                .join();
                    } else {
                        launcher.launch()
                                .cmds("cmd", "/c", "taskkill /PID " + pid + " /F 2>nul || ver > nul")
                                .stdout(TaskListener.NULL)
                                .join();
                    }
                    addNodeRestartLog("Killed Node by PID file (PID=" + pid + ")");
                }
                pidFile.delete();
            }
        } catch (Exception e) {
            addNodeRestartLog("killByPidFile failed: " + e.getMessage());
        }
    }

    private void writeNodePid(FilePath tmp, String jarRemote) throws IOException, InterruptedException {
        boolean isUnix = Boolean.TRUE.equals(computer.isUnix());
        FilePath pidFile = getPidFile(tmp);
        Launcher launcher = new Launcher.RemoteLauncher(TaskListener.NULL, computer.getChannel(), isUnix);
        if (isUnix) {
            if (!jarRemote.matches("^[\\w\\-./]+$")) {
                throw new IllegalArgumentException("Invalid jarRemote value");
            }
            // Use `pgrep` and correctly read stdout (macOS otherwise only returns exit code)
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int exitCode = launcher.launch()
                    .cmds("pgrep", "-f", "-n", jarRemote + ".* node")
                    .stdout(out)
                    .start()
                    .joinWithTimeout(5000, TimeUnit.MILLISECONDS, TaskListener.NULL);
            if (exitCode == 0) {
                String pid = out.toString(StandardCharsets.UTF_8).trim();
                if (pid.matches("\\d+") && !(pid.equals("0") || pid.equals("1"))) {
                    pidFile.write(pid, StandardCharsets.UTF_8.name());
                } else if (pid.isEmpty()) {
                    addNodeRestartLog(
                            "pgrep succeeded (exit code 0) but output was empty or only whitespace for jarRemote: "
                                    + jarRemote);
                }
            } else {
                addNodeRestartLog(
                        "No process found for jarRemote: " + jarRemote + " (pgrep exit code: " + exitCode + ")");
            }
        } else {
            String escaped = jarRemote.replace("'", "''");
            String ps = "$p=(Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match [regex]::Escape('"
                    + escaped
                    + "') -and $_.CommandLine -match ' node' } | Select-Object -First 1 -ExpandProperty ProcessId); if ($p) { Set-Content -Path '"
                    + pidFile.getRemote().replace("\\", "/") + "' -Value $p }";
            launcher.launch()
                    .cmds("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", ps)
                    .stdout(TaskListener.NULL)
                    .join();
        }
        if (pidFile.exists()) {
            addNodeRestartLog("Wrote Node PID file: " + pidFile.getRemote());
        } else {
            addNodeRestartLog("Node PID file not created (process search may have failed)");
        }
    }
}
