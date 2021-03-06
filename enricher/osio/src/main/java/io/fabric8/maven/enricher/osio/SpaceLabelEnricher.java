/**
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.maven.enricher.osio;

import java.util.Collections;
import java.util.Map;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;

/**
 * Apply a label containing the OpenShift.io space to be associated
 * with resources generated by this plugin.
 */
public class SpaceLabelEnricher extends BaseEnricher {

    private static final String SPACE_LABEL = "space";

    enum Config implements Configs.Key {
        space;

        @Override
        public String def() {
            // No default value
            return null;
        }
    }

    public SpaceLabelEnricher(MavenEnricherContext buildContext) {
        super(buildContext, "osio-space-label");
    }

    @Override
    public void create(PlatformMode platformMode, KubernetesListBuilder builder) {
        builder.accept(new TypedVisitor<ObjectMetaBuilder>() {
            @Override
            public void visit(ObjectMetaBuilder o) {
                if(o != null) {
                    String space = getConfig(Config.space);
                    Map<String, String> labels;

                    if (o != null) {
                        labels = o.getLabels();
                        labels.put(SPACE_LABEL, space);
                    } else {
                        labels = Collections.singletonMap(SPACE_LABEL, space);
                    }
                    o.addToLabels(labels);
                    o.getLabels();
                }
            }
        });
    }
}
