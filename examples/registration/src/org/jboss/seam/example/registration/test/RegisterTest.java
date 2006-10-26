//$Id$
package org.jboss.seam.example.registration.test;

import javax.faces.context.FacesContext;

import org.jboss.seam.mock.SeamTest;
import org.testng.annotations.Test;

public class RegisterTest extends SeamTest
{
   
   @Test
   public void testLogin() throws Exception
   {
            
      new FacesRequest("/register.jspx") {

         @Override
         protected void updateModelValues() throws Exception
         {
            setValue("#{user.username}", "1ovthafew");
            setValue("#{user.name}", "Gavin King");
            setValue("#{user.password}", "secret");
         }

         @Override
         protected void invokeApplication()
         {
            assert invokeMethod("#{register.register}").equals("/registered.jspx");
            setOutcome("/registered.jspx");
         }
         
         @Override
         protected void afterRequest()
         {
            assert isInvokeApplicationComplete();
            assert !isRenderResponseBegun();
         }
         
      }.run();
      
      new NonFacesRequest("/registered.jspx")
      {

         @Override
         protected void renderResponse()
         {
            assert getValue("#{user.username}").equals("1ovthafew");
            assert getValue("#{user.password}").equals("secret");
            assert getValue("#{user.name}").equals("Gavin King");
         }
         
      }.run();
      
      new FacesRequest("/register.jspx") {

         @Override
         protected void updateModelValues() throws Exception
         {
            setValue("#{user.username}", "1ovthafew");
            setValue("#{user.name}", "Gavin A King");
            setValue("#{user.password}", "password");
         }

         @Override
         protected void invokeApplication()
         {
            assert invokeMethod("#{register.register}")==null;
         }
         
         @Override
         protected void renderResponse() throws Exception
         {
            assert FacesContext.getCurrentInstance().getMessages().hasNext();
         }
         
         @Override
         protected void afterRequest()
         {
            assert isInvokeApplicationComplete();
            assert isRenderResponseComplete();
         }
         
      }.run();
      
   }
   
}
