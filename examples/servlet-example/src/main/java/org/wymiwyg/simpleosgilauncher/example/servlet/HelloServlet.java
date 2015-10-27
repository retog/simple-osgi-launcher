package org.wymiwyg.simpleosgilauncher.example.servlet;

import java.io.IOException;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.osgi.service.component.annotations.Component;


@Component(enabled = true, immediate = true, service = Servlet.class, property = "alias=/hello")
public class HelloServlet extends HttpServlet {
    
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    resp.getWriter().write("Hello World");      
  }
}

