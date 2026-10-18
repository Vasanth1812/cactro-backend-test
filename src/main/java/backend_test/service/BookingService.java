package backend_test.service;

import backend_test.dto.BookingResponse;
import backend_test.dto.CreateBookingRequest;
import backend_test.entity.Booking;
import backend_test.entity.BookingStatus;
import backend_test.entity.Event;
import backend_test.entity.Role;
import backend_test.entity.User;
import backend_test.exception.BadRequestException;
import backend_test.exception.ResourceNotFoundException;
import backend_test.repository.BookingRepository;
import backend_test.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final EmailNotificationService emailNotificationService;

    /**
     * Books tickets with concurrency safety using row-level atomic decrement.
     * Prevents race conditions and overselling even during high-traffic spikes.
     */
    @Transactional
    public BookingResponse bookTickets(CreateBookingRequest request, User customer) {
        if (customer.getRole() != Role.CUSTOMER) {
            throw new BadRequestException("Only Customers can book event tickets");
        }

        Long eventId = request.getEventId();
        int requestedTickets = request.getTicketCount();

        // 1. Perform atomic row-level reservation in the database
        int rowsUpdated = eventRepository.bookTicketsAtomic(eventId, requestedTickets);

        if (rowsUpdated == 0) {
            // Either event does not exist, or insufficient tickets remaining
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));

            if (event.getEventDate().isBefore(LocalDateTime.now())) {
                throw new BadRequestException("Cannot book tickets: This event has already occurred.");
            }

            throw new BadRequestException(String.format(
                    "Insufficient tickets available. Requested: %d, Currently available: %d",
                    requestedTickets, event.getAvailableTickets()
            ));
        }

        // 2. Fetch the event entity to calculate price and create booking record
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));

        BigDecimal totalAmount = event.getTicketPrice().multiply(BigDecimal.valueOf(requestedTickets));
        String referenceCode = "BKG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Booking booking = Booking.builder()
                .bookingReference(referenceCode)
                .customer(customer)
                .event(event)
                .ticketCount(requestedTickets)
                .totalAmount(totalAmount)
                .status(BookingStatus.CONFIRMED)
                .build();

        Booking savedBooking = bookingRepository.save(booking);
        log.info("Booking confirmed [Ref: {}] for customer: {} on event: {}", referenceCode, customer.getEmail(), event.getTitle());

        // 3. Trigger Background Task 1: Asynchronous Booking Confirmation Email
        emailNotificationService.sendBookingConfirmation(savedBooking);

        return mapToResponse(savedBooking);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getMyBookings(User customer, Pageable pageable) {
        return bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId(), pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getEventBookings(Long eventId, User organizer, Pageable pageable) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));

        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new BadRequestException("You are not authorized to view bookings for this event.");
        }

        return bookingRepository.findByEventIdOrderByCreatedAtDesc(eventId, pageable)
                .map(this::mapToResponse);
    }

    private BookingResponse mapToResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .bookingReference(booking.getBookingReference())
                .eventId(booking.getEvent().getId())
                .eventTitle(booking.getEvent().getTitle())
                .eventLocation(booking.getEvent().getLocation())
                .eventDate(booking.getEvent().getEventDate())
                .customerId(booking.getCustomer().getId())
                .customerName(booking.getCustomer().getName())
                .customerEmail(booking.getCustomer().getEmail())
                .ticketCount(booking.getTicketCount())
                .totalAmount(booking.getTotalAmount())
                .status(booking.getStatus())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}
