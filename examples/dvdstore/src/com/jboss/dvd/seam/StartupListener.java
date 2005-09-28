package com.jboss.dvd.seam;

import org.jboss.seam.microcontainer.JbpmFactory;
import org.jboss.logging.Logger;

import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;

/**
 * @author <a href="mailto:steve@hibernate.org">Steve Ebersole </a>
 */
public class StartupListener 
    implements ServletContextListener
{
    private static final Logger log = Logger.getLogger( StartupListener.class );
    
//     private String[] PROCESS_LIST = {
//         "jbpm-ordermanagement.xml"
//     };
    
    private JbpmFactory jbpmFactory;
    
    public void contextInitialized(ServletContextEvent servletContextEvent)
    {
        System.out.println("!!!!!!!!!!!!!!! STARTUP LISTENER");
        jbpmFactory = new JbpmFactory();
        jbpmFactory.setJndiName("/JbpmSessionFactory");
        //jbpmFactory.setProcessDefinitionResources(PROCESS_LIST);

        try {
            jbpmFactory.initialize();
        } catch (Throwable t) {
            log.error("was unable to bootstrap jBPM", t);
        }
    }
    
    public void contextDestroyed(ServletContextEvent servletContextEvent)
    {
        jbpmFactory.cleanup();
        jbpmFactory = null;
    }
}
