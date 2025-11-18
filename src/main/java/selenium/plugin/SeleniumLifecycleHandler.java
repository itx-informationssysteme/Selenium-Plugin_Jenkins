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
import hudson.model.RestartListener;
import java.io.IOException;
import jenkins.model.Jenkins;

@Extension
public class SeleniumLifecycleHandler extends RestartListener {

    @Override
    public boolean isReadyToRestart() throws IOException, InterruptedException {
        return true;
    }

    @Override
    public void onRestart() {
        stopAllNodes();
    }

    private static void stopAllNodes() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) return;

        for (Computer computer : jenkins.getComputers()) {
            SeleniumAgentAction action = computer.getAction(SeleniumAgentAction.class);
            try {
                if (action != null && action.getNodeActive()) {
                    action.stopNode();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
