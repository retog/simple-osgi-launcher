/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wymiwyg.inheritance.test;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component
public class Configurator {

    ConfigurationAdmin configurationAdmin;

    @Activate
    public void start() throws IOException {
        Configuration configuration = configurationAdmin.getConfiguration("com.eclipsesource.jaxrs.connector", null);
        Dictionary props = configuration.getProperties();
        if (props == null) {
            props = new Hashtable();
        }
        props.put("root", "/jaxrs");
        configuration.update(props);
    }

    @Reference
    protected void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    protected void unsetConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = null;
    }

}
