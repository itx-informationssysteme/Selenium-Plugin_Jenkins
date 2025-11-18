/*
 * Copyright 2025 itx. informationssysteme gmbh
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
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import jenkins.model.Jenkins;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Extension
public class InboundAgentWatcher extends ComputerListener {

    private static final Logger LOGGER = Logger.getLogger(InboundAgentWatcher.class.getName());
    private static final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public void onOnline(Computer c, TaskListener listener) {
        LOGGER.log(Level.INFO, "Agent online: {0}", c.getName());
        maybeRunAfterAllAgentsOnline();
    }

    @Override
    public void onConfigurationChange() {
        LOGGER.info("Node configuration changed, re-checking agent state");
        maybeRunAfterAllAgentsOnline();
    }

    private void maybeRunAfterAllAgentsOnline() {
        if (started.get()) {
            return;
        }

        Jenkins j = Jenkins.get();
        List<Computer> inboundComputers = j.getNodes().stream()
                .map(node -> node.toComputer())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (inboundComputers.isEmpty()) {
            return;
        }

        List<Computer> allOnlineComputers = inboundComputers.stream().filter(Computer::isOnline).toList();

        if (!allOnlineComputers.isEmpty()) {
            runPostAgentStartupLogic(allOnlineComputers);
        }
    }

    private void runPostAgentStartupLogic(List<Computer> allOnlineComputers) {
        if(!started.compareAndSet(false, true)) {
            return;
        }
        for (Computer computer : allOnlineComputers) {
            String display = computer.getDisplayName();
            if ("Jenkins".equals(display) || "(built-in)".equals(display)) {
                continue;
            }
            SeleniumAgentAction action = computer.getAction(SeleniumAgentAction.class);
            if (action != null && computer.isConnected()) {
                try {
                    action.addNodeRestartLog("Post-Agent-Startup Trigger (InboundAgentWatcher)");
                    action.checkAndRestartNodeIfNeeded();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error while Post-Agent-Startup-Logic for " + computer.getName(), e);
                }
            }
        }
    }
}
