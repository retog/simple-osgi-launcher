/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wymiwyg.inheritance.test;


import javax.ws.rs.GET;
import javax.ws.rs.Path;
import org.osgi.service.component.annotations.Component;


@Component(enabled = true, immediate = true, service = Object.class)
@Path( "/foo" )
public class TestRes {
    
    @GET
    public String foo() {
        return "foo";
    }
    
}
