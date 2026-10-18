package backend_test.service;

import backend_test.entity.Booking;
import backend_test.entity.Event;
import backend_test.entity.User;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:no-reply@cactro-events.com}")
    private String fromEmail;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy 'at' hh:mm a");

    /**
     * Background Task 1: Real Booking Confirmation Email
     * Triggered asynchronously when a customer books tickets.
     */
    @Async("notificationTaskExecutor")
    public void sendBookingConfirmation(Booking booking) {
        String recipientEmail = booking.getCustomer().getEmail();
        String customerName = booking.getCustomer().getName();
        String eventTitle = booking.getEvent().getTitle();
        String eventLocation = booking.getEvent().getLocation();
        String formattedDate = booking.getEvent().getEventDate().format(DATE_FORMATTER);

        String subject = "Booking Confirmed: " + eventTitle + " [Ref: " + booking.getBookingReference() + "]";

        String htmlContent = String.format("""
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                    <h2 style="color: #2563eb;">🎟️ Booking Confirmation</h2>
                    <p>Dear <strong>%s</strong>,</p>
                    <p>Your tickets for <strong>%s</strong> have been successfully booked!</p>
                    <div style="background-color: #f8fafc; padding: 15px; border-radius: 6px; margin: 20px 0;">
                        <p style="margin: 5px 0;"><strong>Booking Reference:</strong> %s</p>
                        <p style="margin: 5px 0;"><strong>Event:</strong> %s</p>
                        <p style="margin: 5px 0;"><strong>Date & Time:</strong> %s</p>
                        <p style="margin: 5px 0;"><strong>Location:</strong> %s</p>
                        <p style="margin: 5px 0;"><strong>Tickets Booked:</strong> %d</p>
                        <p style="margin: 5px 0;"><strong>Total Paid:</strong> $%s</p>
                    </div>
                    <p style="color: #64748b; font-size: 14px;">Please present this confirmation reference upon arrival at the venue.</p>
                    <hr style="border: 0; border-top: 1px solid #e2e8f0; margin: 20px 0;" />
                    <p style="color: #94a3b8; font-size: 12px;">Cactro Event Booking System &bull; Automated Notification</p>
                </div>
                """,
                customerName,
                eventTitle,
                booking.getBookingReference(),
                eventTitle,
                formattedDate,
                eventLocation,
                booking.getTicketCount(),
                booking.getTotalAmount().toPlainString()
        );

        sendEmail(recipientEmail, subject, htmlContent);
    }

    /**
     * Background Task 2: Real Event Update Notification Email
     * Triggered asynchronously when an organizer updates an event.
     * Notifies all customers who have booked tickets for this event.
     */
    @Async("notificationTaskExecutor")
    public void sendEventUpdateNotifications(Event event, List<User> customers, String changeSummary) {
        if (customers == null || customers.isEmpty()) {
            log.info("No booked customers found for updated event id: {}", event.getId());
            return;
        }

        String eventTitle = event.getTitle();
        String formattedDate = event.getEventDate().format(DATE_FORMATTER);
        String subject = "Important Update: " + eventTitle;

        log.info("Dispatching asynchronous event update notification emails to {} customers", customers.size());

        for (User customer : customers) {
            String htmlContent = String.format("""
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px; border: 1px solid #fef08a; border-radius: 8px;">
                        <h2 style="color: #d97706;">📢 Important Event Update</h2>
                        <p>Dear <strong>%s</strong>,</p>
                        <p>The organizer has posted an update regarding your upcoming event <strong>%s</strong>.</p>
                        <div style="background-color: #fffbeb; padding: 15px; border-radius: 6px; margin: 20px 0; border-left: 4px solid #f59e0b;">
                            <h4 style="margin: 0 0 10px 0; color: #b45309;">Updated Event Details:</h4>
                            <p style="margin: 5px 0;"><strong>Date & Time:</strong> %s</p>
                            <p style="margin: 5px 0;"><strong>Venue Location:</strong> %s</p>
                            %s
                        </div>
                        <p style="color: #64748b; font-size: 14px;">Your existing tickets and booking references remain valid.</p>
                        <hr style="border: 0; border-top: 1px solid #e2e8f0; margin: 20px 0;" />
                        <p style="color: #94a3b8; font-size: 12px;">Cactro Event Booking System &bull; Automated Notification</p>
                    </div>
                    """,
                    customer.getName(),
                    eventTitle,
                    formattedDate,
                    event.getLocation(),
                    changeSummary != null && !changeSummary.isBlank() ? "<p style=\"margin: 5px 0;\"><strong>Notes:</strong> " + changeSummary + "</p>" : ""
            );

            sendEmail(customer.getEmail(), subject, htmlContent);
        }
    }

    private void sendEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Real email notification successfully dispatched to: {}", to);
        } catch (Exception ex) {
            log.error("Failed to send email to {}: {}. Ensure SMTP credentials are configured.", to, ex.getMessage());
        }
    }
}
