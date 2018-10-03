/*
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
package io.fabric8.maven.enricher.fabric8;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;

public class ServiceDiscoveryEnricher extends BaseEnricher {
    static final String ENRICHER_NAME        = "f8-service-discovery";
    private boolean tried = false;

    //Default Prefix
    static final String PREFIX    = "discovery.3scale.net";
    //Service Annotations
    static final String DISCOVERY_VERSION = "discovery-version";
    static final String SCHEME            = "scheme";
    static final String PATH              = "path";
    static final String PORT              = "port";
    static final String DESCRIPTION_PATH  = "description-path";
    //Service Labels
    static final String DISCOVERABLE      = "discoverable";

    private File springConfigDir;
    private String path             = "/";
    private String port             = "80";
    private String scheme           = "http";
    private String descriptionPath  = null;
    private String discoverable     = null;
    private String discoveryVersion = "v1";

    private enum Config implements Configs.Key {
        descriptionPath,
        discoverable,
        discoveryVersion,
        path,
        port,
        scheme,
        springDir;

        public String def() { return d; } protected String d;
    }
    
    public ServiceDiscoveryEnricher(EnricherContext buildContext) {
        super(buildContext, ENRICHER_NAME);

        String baseDir  = getProject().getBasedir().getAbsolutePath();
        springConfigDir = new File(getConfig(Config.springDir, baseDir + "/src/main/resources/spring"));
        discoverable    = getConfig(Config.discoverable, null);
    }

    @Override
    public Map<String, String> getAnnotations(Kind kind) {

        if (kind == Kind.SERVICE) {

            if (!tried) {
                tryCamelDSLProject();
                //try more project types in future, last match wins
                tried = true;
            }

            if (discoverable != null) {
                Map<String, String> annotations = new HashMap<>();
                annotations.put(PREFIX + "/" + DISCOVERY_VERSION, getConfig(Config.discoveryVersion, discoveryVersion));
                annotations.put(PREFIX + "/" + SCHEME           , getConfig(Config.scheme, scheme));
                if (getConfig(Config.path, path) != null) {
                    annotations.put(PREFIX + "/" + PATH             , getConfig(Config.path, path));
                }
                annotations.put(PREFIX + "/" + PORT           , getConfig(Config.port, port));
                if (getConfig(Config.descriptionPath, descriptionPath) != null) {
                    annotations.put(PREFIX + "/" + DESCRIPTION_PATH , getConfig(Config.descriptionPath, descriptionPath));
                }
                for (String annotationName : annotations.keySet()) {
                    log.info("Add %s annotation: \"%s\" : \"%s\"", PREFIX, 
                            annotationName, annotations.get(annotationName));
                }
                return annotations;
            }
        }

        return super.getAnnotations(kind);
    }

    @Override
    public Map<String, String> getLabels(Kind kind) {

        if (kind == Kind.SERVICE) {

            if (!tried) {
                tryCamelDSLProject();
                //try more project types in future, last match wins
                tried = true;
            }

            if (discoverable != null) {
                Map<String, String> labels = new HashMap<>();
                String labelName = PREFIX;
                labels.put(labelName, getConfig(Config.discoverable, discoverable));
                log.info("Add %s label: \"%s\" : \"%s\"", PREFIX, 
                        labelName, labels.get(labelName));
                return labels;
            }
        }
        return super.getLabels(kind);
    }

    @Override
    public void addMissingResources(final KubernetesListBuilder listBuilder) {
        listBuilder.accept(new TypedVisitor<ServiceBuilder>() {

            @Override
            public void visit(ServiceBuilder serviceBuilder) {
                
                List<ServicePort> ports = serviceBuilder.buildSpec().getPorts();
                if (! ports.isEmpty()) {
                    ServicePort firstServicePort = ports.iterator().next();
                    port = firstServicePort.getPort().toString();
                    log.info("Using first mentioned service port '%s' " , port);
                }
            }

        });
    }
    
    public void tryCamelDSLProject(){
        File camelContextXmlFile = new File(springConfigDir.getAbsoluteFile() + "/camel-context.xml");
        if (camelContextXmlFile.exists()) {
            try {
                Document doc = DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder().parse(camelContextXmlFile);
                XPath xPath = XPathFactory.newInstance().newXPath();
                Node nl = (Node) xPath.evaluate(
                        "/beans/camelContext/restConfiguration", 
                        doc, 
                        XPathConstants.NODE);
                if (nl != null) {
                    discoverable = "true";
                    if (nl.getAttributes().getNamedItem("scheme")!=null) {
                        scheme = nl.getAttributes().getNamedItem("scheme").getNodeValue();
                        log.verbose("Obtained scheme '%s' from camel-context.xml " , scheme);
                    }
                    if (nl.getAttributes().getNamedItem("contextPath")!=null) {
                        path = nl.getAttributes().getNamedItem("contextPath").getNodeValue();
                        log.verbose("Obtained path '%s' from camel-context.xml " , path);
                    }
                    if (nl.getAttributes().getNamedItem("apiContextPath")!=null) {
                        descriptionPath = nl.getAttributes().getNamedItem("apiContextPath").getNodeValue();
                        log.verbose("Obtained descriptionPath '%s' from camel-context.xml " , descriptionPath);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load camel context file: %s", e);
            }
        }
    }
}
