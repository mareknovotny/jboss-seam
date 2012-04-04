//$Id: BookingTest.java 6505 2007-10-12 11:24:54Z pmuir $
package org.jboss.seam.example.jpa.test;

import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.model.DataModel;
import javax.faces.model.ListDataModel;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OverProtocol;
import org.jboss.seam.contexts.Contexts;
import org.jboss.seam.core.Manager;
import org.jboss.seam.example.jpa.Booking;
import org.jboss.seam.example.jpa.Hotel;
import org.jboss.seam.example.jpa.HotelBookingAction;
import org.jboss.seam.example.jpa.User;
import org.jboss.seam.mock.JUnitSeamTest;
import org.jboss.seam.mock.SeamTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

public class BookingTest extends JUnitSeamTest
{
   @Deployment(name="BookingTest")
   @OverProtocol("Servlet 3.0")
   public static Archive<?> createDeployment()
   {
      WebArchive web = Deployments.jpaDeployment();

      web.addClasses(BookingTest.class);

      return web;
   }
   
   @Test
   public void testBookHotel() throws Exception
   {
      
      new FacesRequest() {
         
         @Override
         protected void invokeApplication() throws Exception
         {
            Contexts.getSessionContext().set("user", new User("Gavin King", "foobar", "gavin"));
            setValue("#{identity.username}", "gavin");
            setValue("#{identity.password}", "foobar");            
            invokeAction("#{identity.login}");
         }
         
      }.run();
      
      new FacesRequest("/main.xhtml") {

         @Override
         protected void updateModelValues() throws Exception
         {
            setValue("#{hotelSearch.searchString}", "Union Square");
         }

         @Override
         protected void invokeApplication()
         {
            assert invokeAction("#{hotelSearch.find}")==null;
         }

         @Override
         protected void renderResponse()
         {
            DataModel hotels = (DataModel) Contexts.getSessionContext().get("hotels");
            assert hotels.getRowCount()==1;
            assert ( (Hotel) hotels.getRowData() ).getCity().equals("NY");
            assert getValue("#{hotelSearch.searchString}").equals("Union Square");
            assert !Manager.instance().isLongRunningConversation();
         }
         
      }.run();
      
      String id = new FacesRequest("/main.xhtml") {
         
         @Override
         protected void invokeApplication() throws Exception {
            HotelBookingAction hotelBooking = (HotelBookingAction) getInstance("hotelBooking");
            DataModel hotels = (DataModel) Contexts.getSessionContext().get("hotels");
            assert hotels.getRowCount()==1;
            hotelBooking.selectHotel( (Hotel) hotels.getRowData() );
         }

         @Override
         protected void renderResponse()
         {
            Hotel hotel = (Hotel) Contexts.getConversationContext().get("hotel");
            assert hotel.getCity().equals("NY");
            assert hotel.getZip().equals("10011");
            assert Manager.instance().isLongRunningConversation();
         }
         
      }.run();
      
      id = new FacesRequest("/hotel.xhtml", id) {

         @Override
         protected void invokeApplication()
         {
            invokeAction("#{hotelBooking.bookHotel}");
         }

         @Override
         protected void renderResponse()
         {
            assert getValue("#{booking.user}")!=null;
            assert getValue("#{booking.hotel}")!=null;
            assert getValue("#{booking.creditCard}")==null;
            assert getValue("#{booking.creditCardName}")==null;
            Booking booking = (Booking) Contexts.getConversationContext().get("booking");
            assert booking.getHotel()==Contexts.getConversationContext().get("hotel");
            assert booking.getUser()==Contexts.getSessionContext().get("user");
            assert Manager.instance().isLongRunningConversation();
         }
         
      }.run();
      
      new FacesRequest("/book.xhtml", id) {

         @Override
         protected void processValidations() throws Exception
         {
            validateValue("#{booking.creditCard}", "123");
            assert isValidationFailure();
         }

         @Override
         protected void renderResponse()
         {
            Iterator messages = FacesContext.getCurrentInstance().getMessages();
            assert messages.hasNext();
            assert ( (FacesMessage) messages.next() ).getSummary().equals("Credit card number must 16 digits long");
            assert !messages.hasNext();
            assert Manager.instance().isLongRunningConversation();
         }
         
         @Override
         protected void afterRequest()
         {
            assert !isInvokeApplicationBegun();
         }
         
      }.run();
      
      new FacesRequest("/book.xhtml", id) {

         @Override
         protected void processValidations() throws Exception
         {
            validateValue("#{booking.creditCardName}", "");
            assert isValidationFailure();
         }

         @Override
         protected void renderResponse()
         {
            Iterator messages = FacesContext.getCurrentInstance().getMessages();
            assert messages.hasNext();
            assert ( (FacesMessage) messages.next() ).getSummary().equals("Credit card name is required");
            assert !messages.hasNext();
            assert Manager.instance().isLongRunningConversation();
         }
         
         @Override
         protected void afterRequest()
         {
            assert !isInvokeApplicationBegun();
         }
         
      }.run();
      
      new FacesRequest("/book.xhtml", id) {
         
         @Override @SuppressWarnings("deprecation")
         protected void updateModelValues() throws Exception
         {  
            setValue("#{booking.creditCard}", "1234567891021234");
            setValue("#{booking.creditCardName}", "GAVIN KING");
            setValue("#{booking.beds}", 2);
            Date now = new Date();
            setValue("#{booking.checkinDate}", now);
            setValue("#{booking.checkoutDate}", now);
         }

         @Override
         protected void invokeApplication()
         {
            assert invokeAction("#{hotelBooking.setBookingDetails}")==null;
         }

         @Override
         protected void renderResponse()
         {
            Iterator messages = FacesContext.getCurrentInstance().getMessages();
            assert messages.hasNext();
            FacesMessage message = (FacesMessage) messages.next();
            assert message.getSummary().equals("Check out date must be later than check in date");
            assert !messages.hasNext();
            assert Manager.instance().isLongRunningConversation();
         }
         
         @Override
         protected void afterRequest()
         {
            assert isInvokeApplicationComplete();
         }
         
      }.run();
      
      new FacesRequest("/book.xhtml", id) {
         
         @Override @SuppressWarnings("deprecation")
         protected void updateModelValues() throws Exception
         {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, 2);
            setValue("#{booking.checkoutDate}", cal.getTime() );
         }

         @Override
         protected void invokeApplication()
         {
            invokeAction("#{hotelBooking.setBookingDetails}");
         }

         @Override
         protected void renderResponse()
         {
            assert Manager.instance().isLongRunningConversation();
         }
         
         @Override
         protected void afterRequest()
         {
            assert isInvokeApplicationComplete();
         }
         
      }.run();
      
      new FacesRequest("/confirm.xhtml", id) {

         @Override
         protected void invokeApplication()
         {
            invokeAction("#{hotelBooking.confirm}");
         }
         
         @Override
         protected void afterRequest()
         {
            assert isInvokeApplicationComplete();
         }
         
      }.run();
      
      new NonFacesRequest("/main.xhtml") {

         @Override
         protected void renderResponse()
         {
            ListDataModel bookings = (ListDataModel) getInstance("bookings");
            assert bookings.getRowCount()==1;
            bookings.setRowIndex(0);
            Booking booking = (Booking) bookings.getRowData();
            assert booking.getHotel().getCity().equals("NY");
            assert booking.getUser().getUsername().equals("gavin");
            assert !Manager.instance().isLongRunningConversation();
         }
         
      }.run();
      
      new FacesRequest("/main.xhtml") {
         
         @Override
         protected void invokeApplication()
         {
            ListDataModel bookings = (ListDataModel) Contexts.getSessionContext().get("bookings");
            bookings.setRowIndex(0);
            invokeAction("#{bookingList.cancel}");
         }

         @Override
         protected void renderResponse()
         {
            ListDataModel bookings = (ListDataModel) Contexts.getSessionContext().get("bookings");
            assert bookings.getRowCount()==0;
            assert !Manager.instance().isLongRunningConversation();
         }
         
      }.run();
      
   }
   
}
