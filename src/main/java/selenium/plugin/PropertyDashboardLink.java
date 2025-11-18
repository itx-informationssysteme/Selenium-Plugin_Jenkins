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
import hudson.model.*;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;

@Extension
public class PropertyDashboardLink extends TransientViewActionFactory {

    private static final SeleniumGlobalProperty INSTANCE = new SeleniumGlobalProperty();

    @Override
    public List<Action> createFor(View v) {
        if (v instanceof AllView && v.getOwner() instanceof Jenkins) {
            return Collections.singletonList(INSTANCE);
        }
        return Collections.emptyList();
    }
}
