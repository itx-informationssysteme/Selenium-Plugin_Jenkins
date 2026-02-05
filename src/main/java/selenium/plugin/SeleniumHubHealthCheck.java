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
import hudson.model.ManagementLink;
import hudson.model.TaskListener;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Periodic work that checks the Selenium Hub and restarts it if needed.
 * Runs every 5 minutes.
 */
@Extension
public class SeleniumHubHealthCheck extends AsyncPeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(SeleniumHubHealthCheck.class.getName());

    private static final long RECURRENCE_PERIOD = 5 * 60 * 1000L;

    public SeleniumHubHealthCheck() {
        super("Selenium Hub Health Check");
    }

    @Override
    public long getRecurrencePeriod() {
        return RECURRENCE_PERIOD;
    }

    @Override
    protected void execute(TaskListener listener) {
        LOGGER.log(Level.FINE, "Starting Selenium Hub health check");

        SeleniumGlobalProperty globalProp = ManagementLink.all().get(SeleniumGlobalProperty.class);
        if (globalProp != null) {
            try {
                globalProp.checkAndRestartHubIfNeeded();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error checking Selenium Hub", e);
            }
        }

        LOGGER.log(Level.FINE, "Completed Selenium Hub health check");
    }
}
