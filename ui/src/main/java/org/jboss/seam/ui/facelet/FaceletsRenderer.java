package org.jboss.seam.ui.facelet;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;

import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;

import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.AutoCreate;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.intercept.BypassInterceptors;
import org.jboss.seam.core.ResourceLoader;
import org.jboss.seam.faces.Renderer;
import org.jboss.seam.mock.MockExternalContext;
import org.jboss.seam.mock.MockFacesContext;
import org.jboss.seam.ui.util.JSF;

import com.sun.facelets.Facelet;
import com.sun.facelets.compiler.SAXCompiler;
import com.sun.facelets.impl.DefaultFaceletFactory;
import com.sun.facelets.impl.DefaultResourceResolver;

/**
 * 
 * Implementation of Renderer using Facelets
 * 
 * Especially useful for sending email using Seam Mail
 * 
 * @author Pete Muir
 * @author Norman Richards
 *
 */

@Scope(ScopeType.STATELESS)
@BypassInterceptors
@Name("org.jboss.seam.faces.renderer")
@AutoCreate
@Install(value = true, precedence = Install.BUILT_IN, classDependencies="com.sun.facelets.Facelet")
public class FaceletsRenderer extends Renderer
{
   /**
    * Sets up and tears down the rendering (FacesContext, ExternalContext) using 
    * Seam's mock contexts if JSF is not currently running.  Also switches out the
    * ViewRoot for the duration.  Any output written to the ResponseWriter is
    * buffered.
    * 
    * @author Pete Muir
    *
    */
   protected abstract class RenderingContext 
   {
      
      public RenderingContext(String viewId)
      {
         this.viewId = viewId;
      }
      
      private String viewId;
      
      private ClassLoader originalClassLoader;
      private ResponseWriter originalResponseWriter;
      private UIViewRoot originalViewRoot;
      private StringWriter writer = new StringWriter();
      
      /**
       * Call to execute the process method inside the RenderingContext
       */
      public void run() 
      {
         try
         {
            init();
            process();
         }
         finally
         {
            cleanup();
         }
      }
      
      private void init()
      {
         // Make sure we are using the correct ClassLoader
         originalClassLoader = Thread.currentThread().getContextClassLoader();
         Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
         
         // If a FacesContext isn't available, set one up
         if (FacesContext.getCurrentInstance() == null)
         {
            MockFacesContext mockFacesContext = new MockFacesContext(new MockExternalContext())
                     .setCurrent();
            mockFacesContext.createViewRoot();
         }
         
         FacesContext facesContext = FacesContext.getCurrentInstance();
         
         // Wrap the ResponseWriter
         originalResponseWriter = facesContext.getResponseWriter();
         facesContext.setResponseWriter(facesContext.getRenderKit().createResponseWriter(writer,
                  null, null));
         
         // Create a new UIViewRoot
         originalViewRoot = facesContext.getViewRoot();
         UIViewRoot viewRoot = new UIViewRoot();
         viewRoot.setRenderKitId(facesContext.getApplication().getViewHandler().calculateRenderKitId(facesContext));
         viewRoot.setViewId(viewId);
         viewRoot.setLocale(originalViewRoot.getLocale());
         facesContext.setViewRoot(viewRoot);
      }
      
      private void cleanup()
      {
         FacesContext facesContext = FacesContext.getCurrentInstance();
         if (originalResponseWriter != null)
         {
            facesContext.setResponseWriter(originalResponseWriter);
         }
         if (originalViewRoot != null)
         {
            facesContext.setViewRoot(originalViewRoot);
         }
         Thread.currentThread().setContextClassLoader(originalClassLoader);       
      }
      
      /**
       * Return any output written to the ResponseWriter as a String
       */
      public String getOutput() 
      {
         return writer.getBuffer().toString(); 
      }
      
      /**
       * Override to execute within a RenderingContext
       */
      protected abstract void process();
      
   }
   
   /**
    * Render the viewId, anything written to the JSF ResponseWriter is
    * returned
    */
   @Override
   public String render(final String viewId)
   {
      RenderingContext context = new RenderingContext(viewId) {
         
         @Override
         public void process()
         {
            FacesContext facesContext = FacesContext.getCurrentInstance();
            try
            {
               renderFacelet(facesContext, faceletForURL(resourceURL(viewId)));
            }
            catch (IOException e)
            {
               throw new RuntimeException("Error rendering view", e);
            }
         }
         
      };
      context.run();
      
      return context.getOutput();
   }

   /**
    * Get a viewId as a URL
    */
   protected URL resourceURL(String viewId)
   {

      URL url = ResourceLoader.instance().getResource(viewId);

      if (url == null)
      {
         throw new IllegalArgumentException("resource doesn't exist: " + viewId);
      }

      return url;
   }

   /**
    * Get a Facelet for a URL
    */
   protected Facelet faceletForURL(URL url) throws IOException
   {
      return new DefaultFaceletFactory(new SAXCompiler(), new DefaultResourceResolver())
               .getFacelet(url);
   }

   /**
    * Render a Facelet
    */
   protected void renderFacelet(FacesContext facesContext, Facelet facelet) throws IOException
   {
      UIViewRoot root = facesContext.getViewRoot();
      facelet.apply(facesContext, root);
      JSF.renderChildren(facesContext, root);  
   }
   
}
