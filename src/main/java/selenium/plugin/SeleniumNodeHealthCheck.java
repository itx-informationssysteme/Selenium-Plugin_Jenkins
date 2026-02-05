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
import hudson.model.AsyncPeriodicWork;
import hudson.model.Computer;
import hudson.model.TaskListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * Periodic work that checks all Selenium nodes and restarts them if needed.
 * Runs every 5 minutes and handles dynamically added/removed agents.
 */
@Extension
public class SeleniumNodeHealthCheck extends AsyncPeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(SeleniumNodeHealthCheck.class.getName());

    private static final long RECURRENCE_PERIOD = 5 * 60 * 1000L;

    public SeleniumNodeHealthCheck() {
        super("Selenium Node Health Check");
    }

    @Override
    public long getRecurrencePeriod() {
        return RECURRENCE_PERIOD;
    }

    @Override
    protected void execute(TaskListener listener) {
        LOGGER.log(Level.FINE, "Starting Selenium node health check for all agents");

        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return;
        }

        for (Computer computer : jenkins.getComputers()) {
            if (computer instanceof Jenkins.MasterComputer) {
                continue;
            }

            SeleniumAgentAction action = computer.getAction(SeleniumAgentAction.class);
            if (action != null) {
                try {
                    action.checkAndRestartNodeIfNeeded();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error checking Selenium node on " + computer.getName(), e);
                }
            }
        }

        LOGGER.log(Level.FINE, "Completed Selenium node health check");
    }
}
