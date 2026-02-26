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

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

@Extension
public class InboundAgentWatcher extends ComputerListener {

    private static final Logger LOGGER = Logger.getLogger(InboundAgentWatcher.class.getName());

    @Override
    public void onOnline(Computer c, TaskListener listener) {
        LOGGER.log(Level.FINE, "Agent online: {0}", c.getName());
        SeleniumAgentAction action = c.getAction(SeleniumAgentAction.class);
        if (action != null) {
            action.addNodeRestartLog("Agent came online: " + c.getName());
        }
        checkAndStartSeleniumNode(c);
    }

    @Override
    public void onOffline(Computer c, hudson.slaves.OfflineCause cause) {
        LOGGER.log(Level.FINE, "Agent offline: {0}, cause: {1}", new Object[] {c.getName(), cause});
        SeleniumAgentAction action = c.getAction(SeleniumAgentAction.class);
        if (action != null) {
            action.addNodeRestartLog("Agent went offline: " + c.getName() + " (cause: " + cause + ")");
        }
    }

    @Override
    public void onConfigurationChange() {
        LOGGER.fine("Node configuration changed, re-checking agent state");
        runWhenAgentsAreConnected();
    }

    private boolean isController(Computer c) {
        return c instanceof jenkins.model.Jenkins.MasterComputer;
    }

    private void checkAndStartSeleniumNode(Computer c) {
        if (c == null || isController(c)) {
            return;
        }

        if (!c.isOnline() || !c.isIdle()) {
            return;
        }

        SeleniumAgentAction action = c.getAction(SeleniumAgentAction.class);
        if (action == null) {
            LOGGER.log(Level.FINE, "No SeleniumAgentAction found for: {0}", c.getDisplayName());
            return;
        }

        if (action.isNodeActiveConfigured()) {
            LOGGER.log(Level.FINE, "Starting Selenium node on: {0}", c.getDisplayName());
            action.checkAndRestartNodeIfNeeded();
        }
    }

    private void runWhenAgentsAreConnected() {
        Jenkins j = Jenkins.get();
        List<Computer> inboundComputers = j.getNodes().stream()
                .map(Node::toComputer)
                .filter(Objects::nonNull)
                .filter(c -> !isController(c))
                .toList();

        LOGGER.log(Level.FINE, "runWhenAgentsAreConnected: Found {0} inbound computers", inboundComputers.size());

        if (inboundComputers.isEmpty()) {
            LOGGER.fine("runWhenAgentsAreConnected: No inbound computers found, skipping");
            return;
        }

        List<Computer> allOnlineAndIdleComputers = inboundComputers.stream()
                .filter(Computer::isOnline)
                .filter(Computer::isIdle)
                .toList();

        LOGGER.log(
                Level.FINE,
                "runWhenAgentsAreConnected: {0} online and idle computers",
                allOnlineAndIdleComputers.size());

        if (!allOnlineAndIdleComputers.isEmpty()) {
            runPostAgentStartupLogic(allOnlineAndIdleComputers);
        }
    }

    private void runPostAgentStartupLogic(List<Computer> allOnlineComputers) {
        for (Computer computer : allOnlineComputers) {
            String display = computer.getDisplayName();
            LOGGER.log(Level.FINE, "runPostAgentStartupLogic: Processing computer: {0}", display);

            if (isController(computer)) {
                LOGGER.log(Level.FINE, "runPostAgentStartupLogic: Skipping controller node");
                continue;
            }

            SeleniumAgentAction action = computer.getAction(SeleniumAgentAction.class);
            if (action == null) {
                LOGGER.log(Level.FINE, "runPostAgentStartupLogic: No SeleniumAgentAction found for: {0}", display);
                continue;
            }

            Node node = computer.getNode();
            if (node == null) {
                LOGGER.log(Level.WARNING, "runPostAgentStartupLogic: No Node found for: {0}", display);
                action.addNodeRestartLog("No Node object found for computer: " + display);
                continue;
            }

            if (!node.isAcceptingTasks()) {
                LOGGER.log(Level.INFO, "runPostAgentStartupLogic: Node not accepting tasks: {0}", display);
                action.addNodeRestartLog("Node not accepting tasks, skipping Selenium Node start");
                continue;
            }

            action.addNodeRestartLog("Starting delayed Selenium Node check (5s delay)...");

            // Delay the restart to ensure the agent channel is fully established
            new Thread(
                            () -> {
                                try {
                                    LOGGER.log(
                                            Level.INFO,
                                            "SeleniumNodeStarter: Waiting 5s for agent to be fully ready: {0}",
                                            computer.getName());
                                    Thread.sleep(5000);

                                    // Verify agent is still online after delay
                                    if (!computer.isOnline()) {
                                        LOGGER.log(
                                                Level.WARNING,
                                                "SeleniumNodeStarter: Agent went offline during wait: {0}",
                                                computer.getName());
                                        action.addNodeRestartLog(
                                                "Agent went offline during 5s wait, aborting Selenium Node start");
                                        return;
                                    }

                                    if (computer.getChannel() == null) {
                                        LOGGER.log(
                                                Level.WARNING,
                                                "SeleniumNodeStarter: Agent channel is null after wait: {0}",
                                                computer.getName());
                                        action.addNodeRestartLog(
                                                "Agent channel is null after 5s wait, aborting Selenium Node start");
                                        return;
                                    }

                                    LOGGER.log(
                                            Level.INFO,
                                            "SeleniumNodeStarter: Agent ready, triggering checkAndRestartNodeIfNeeded: {0}",
                                            computer.getName());
                                    action.addNodeRestartLog(
                                            "Post-Agent-Startup Trigger (InboundAgentWatcher) - Agent ready after 5s delay");
                                    action.checkAndRestartNodeIfNeeded();

                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    LOGGER.log(
                                            Level.WARNING,
                                            "SeleniumNodeStarter: Interrupted while waiting for agent startup: "
                                                    + computer.getName(),
                                            e);
                                    action.addNodeRestartLog(
                                            "Interrupted while waiting for agent startup: " + e.getMessage());
                                } catch (Exception e) {
                                    LOGGER.log(
                                            Level.WARNING,
                                            "SeleniumNodeStarter: Error while Post-Agent-Startup-Logic for "
                                                    + computer.getName(),
                                            e);
                                    action.addNodeRestartLog("Error in Post-Agent-Startup-Logic: " + e.getMessage());
                                }
                            },
                            "SeleniumNodeStarter-" + computer.getName())
                    .start();
        }
    }
}
