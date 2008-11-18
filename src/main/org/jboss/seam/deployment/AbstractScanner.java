package org.jboss.seam.deployment;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.Map.Entry;

import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;

import org.jboss.seam.log.LogProvider;
import org.jboss.seam.log.Logging;

/**
 * Abstract base class for {@link Scanner} providing common functionality
 * 
 * @author Pete Muir
 *
 */
public abstract class AbstractScanner implements Scanner
{
   
   private static class Handler
   {
      
      // Cache descriptors for performance
      private ClassFile classFile;
      private ClassDescriptor classDescriptor;
      private FileDescriptor fileDescriptor;
      
      private Set<Entry<String, DeploymentHandler>> deploymentHandlers;
      private ClassLoader classLoader;
      private String name;
      
      public Handler(String name, Set<Entry<String, DeploymentHandler>> deploymentHandlers, ClassLoader classLoader)
      {
         this.deploymentHandlers = deploymentHandlers;
         this.name = name;
         this.classLoader = classLoader;
      }
      
      /**
       * Return true if the file was handled (false if it was ignored)
       */
      protected boolean handle(DeploymentHandler deploymentHandler)
      {
         boolean handled = false;
         if (deploymentHandler instanceof ClassDeploymentHandler) 
         {
            if (name.endsWith(".class"))
            {
               ClassDeploymentHandler classDeploymentHandler = (ClassDeploymentHandler) deploymentHandler;
               if (hasAnnotations(getClassFile(), classDeploymentHandler.getMetadata().getClassAnnotatedWith()))
               {
                  log.trace("adding class to deployable list " + name + " for deployment handler " + deploymentHandler.getName());
                  if (getClassDescriptor().getClazz() != null)
                  {
                     classDeploymentHandler.getClasses().add(getClassDescriptor());
                     handled = true;
                  }
               }
            }
         }
         else
         {
            if (name.endsWith(deploymentHandler.getMetadata().getFileNameSuffix()))
            {
               deploymentHandler.getResources().add(getFileDescriptor());
               handled = true;
            }
         }
         return handled;
      }
      
      protected boolean handle()
      {
         log.trace("found " + name);
         boolean handled = false;
         for (Entry<String, DeploymentHandler> entry: deploymentHandlers)
         {
            if (handle(entry.getValue()))
            {
               handled = true;
            }
         }
         return handled;
      }
      
      private ClassFile getClassFile()
      {
         if (classFile == null)
         {
            try
            {
               classFile = loadClassFile(name, classLoader);
            } 
            catch (IOException e)
            {
               throw new RuntimeException("Error loading class file " + name, e);
            }
         }
         return classFile;
      }
      
      private ClassDescriptor getClassDescriptor()
      {
         if (classDescriptor == null)
         {
            classDescriptor = new ClassDescriptor(name, classLoader);
         }
         return classDescriptor;
      }
      
      private FileDescriptor getFileDescriptor()
      {
         if (fileDescriptor == null)
         {
            fileDescriptor = new FileDescriptor(name, classLoader);
         }
         return fileDescriptor;
      }
   }
   
   private static final LogProvider log = Logging.getLogProvider(Scanner.class);
   
   private DeploymentStrategy deploymentStrategy;
   
   public AbstractScanner(DeploymentStrategy deploymentStrategy)
   {
      this.deploymentStrategy = deploymentStrategy;
      ClassFile.class.getPackage(); //to force loading of javassist, throwing an exception if it is missing
   }
   
   protected static boolean hasAnnotations(ClassFile classFile, Set<Class<? extends Annotation>> annotationTypes)
   {
      if (annotationTypes.size() > 0)
      {
         AnnotationsAttribute visible = (AnnotationsAttribute) classFile.getAttribute( AnnotationsAttribute.visibleTag );
         if ( visible != null ) 
         {
            for (Class<? extends Annotation> annotationType : annotationTypes)
            {
               if (visible.getAnnotation(annotationType.getName()) != null)
               {
                  return true;
               }
            }
         }
      }
      return false;
   }
   
   /**
    * Get a Javassist {@link ClassFile} for a given class name from the classLoader
    */
   protected static ClassFile loadClassFile(String name, ClassLoader classLoader) throws IOException 
   {
      if (name == null)
      {
         throw new NullPointerException("name cannot be null");
      }
      InputStream stream = classLoader.getResourceAsStream(name);
      if (stream == null)
      {
         throw new IllegalStateException("Cannot load " + name + " from " + classLoader + " (using getResourceAsStream() returned null)");
      }
      DataInputStream dstream = new DataInputStream(stream);

      try 
      { 
         return new ClassFile(dstream); 
      } 
      finally 
      { 
         dstream.close(); 
         stream.close(); 
      }
   }
   
   public DeploymentStrategy getDeploymentStrategy()
   {
      return deploymentStrategy;
   }
   
   public long getTimestamp()
   {
      return Long.MAX_VALUE;
   }
   
   protected boolean handleItem(String name)
   {
      return new Handler(name, deploymentStrategy.getDeploymentHandlers().entrySet(), deploymentStrategy.getClassLoader()).handle();
   }

}
