package com.moviebooking.moviebookingsystem;

import com.moviebooking.moviebookingsystem.service.SupportEmailNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SupportEmailNotificationServiceTests {

    @Test
    void sendsTicketUpdateToCustomerUsingConfiguredMailSender() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SupportEmailNotificationService service = new SupportEmailNotificationService(sender);
        ReflectionTestUtils.setField(service, "enabled", true);

        service.sendTicketUpdate(
                "customer@example.com", "Ticket updated", "Ticket #42 has a new reply.");

        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertEquals("customer@example.com", message.getValue().getTo()[0]);
        assertEquals("Ticket updated", message.getValue().getSubject());
        assertEquals("Ticket #42 has a new reply.", message.getValue().getText());
    }

    @Test
    void doesNotAttemptDeliveryWhenSupportEmailIsDisabled() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SupportEmailNotificationService service = new SupportEmailNotificationService(sender);
        ReflectionTestUtils.setField(service, "enabled", false);

        service.sendTicketUpdate("customer@example.com", "Ticket updated", "New reply");

        verifyNoInteractions(sender);
    }
}
