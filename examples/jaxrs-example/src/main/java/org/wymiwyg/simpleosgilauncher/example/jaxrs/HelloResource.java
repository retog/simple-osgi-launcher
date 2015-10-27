/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wymiwyg.simpleosgilauncher.example.jaxrs;


import javax.ws.rs.GET;
import javax.ws.rs.Path;
import org.osgi.service.component.annotations.Component;


@Component(service = Object.class)
@Path( "hello" )
public class HelloResource {
    
    @GET
    public String sayIt() {
        return "Hello OSGi JaxRs World";
    }
    
}
