package backend_test;

import backend_test.dto.*;
import backend_test.entity.Role;
import backend_test.entity.User;
import backend_test.repository.BookingRepository;
import backend_test.repository.EventRepository;
import backend_test.repository.UserRepository;
import backend_test.service.AuthService;
import backend_test.service.BookingService;
import backend_test.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EventBookingIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private EventService eventService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private User organizer;
    private User customer;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();

        // Create test organizer
        AuthResponse orgAuth = authService.register(RegisterRequest.builder()
                .name("Test Organizer")
                .email("org.test@cactro.com")
                .password("Password@123")
                .role(Role.ORGANIZER)
                .build());
        organizer = userRepository.findById(orgAuth.getUserId()).orElseThrow();

        // Create test customer
        AuthResponse custAuth = authService.register(RegisterRequest.builder()
                .name("Test Customer")
                .email("cust.test@cactro.com")
                .password("Password@123")
                .role(Role.CUSTOMER)
                .build());
        customer = userRepository.findById(custAuth.getUserId()).orElseThrow();
    }

    @Test
    @DisplayName("Should create event, allow booking, and properly decrement available tickets")
    void testCreateEventAndBookTickets() {
        EventResponse createdEvent = eventService.createEvent(CreateEventRequest.builder()
                .title("Spring Boot Meetup")
                .description("Discussion on Spring Boot 3.4 and Java 21")
                .location("Online")
                .eventDate(LocalDateTime.now().plusDays(5))
                .totalTickets(50)
                .ticketPrice(new BigDecimal("15.00"))
                .build(), organizer);

        assertNotNull(createdEvent.getId());
        assertEquals(50, createdEvent.getAvailableTickets());

        // Book 2 tickets
        BookingResponse booking = bookingService.bookTickets(CreateBookingRequest.builder()
                .eventId(createdEvent.getId())
                .ticketCount(2)
                .build(), customer);

        assertNotNull(booking.getBookingReference());
        assertTrue(booking.getBookingReference().startsWith("BKG-"));
        assertEquals(new BigDecimal("30.00"), booking.getTotalAmount());

        // Verify available tickets decremented to 48
        EventResponse updatedEvent = eventService.getEventById(createdEvent.getId());
        assertEquals(48, updatedEvent.getAvailableTickets());
    }

    @Test
    @DisplayName("Concurrency Test: 20 simultaneous booking threads for 10 tickets must result in 10 successes, 10 rejections, and 0 overselling")
    void testConcurrentBookingOversellingProtection() throws InterruptedException {
        int initialTickets = 10;
        int totalAttemptingUsers = 20;

        EventResponse event = eventService.createEvent(CreateEventRequest.builder()
                .title("High Demand Flash Sale Concert")
                .description("Limited seats available")
                .location("Arena Stadium")
                .eventDate(LocalDateTime.now().plusDays(10))
                .totalTickets(initialTickets)
                .ticketPrice(new BigDecimal("100.00"))
                .build(), organizer);

        Long eventId = event.getId();

        ExecutorService executorService = Executors.newFixedThreadPool(totalAttemptingUsers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalAttemptingUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < totalAttemptingUsers; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Synchronize all threads to fire simultaneously

                    // Create separate customer per thread
                    User concurrentCustomer = User.builder()
                            .name("User " + index)
                            .email("concurrent" + index + "@test.com")
                            .password("secret")
                            .role(Role.CUSTOMER)
                            .build();
                    concurrentCustomer = userRepository.save(concurrentCustomer);

                    bookingService.bookTickets(CreateBookingRequest.builder()
                            .eventId(eventId)
                            .ticketCount(1)
                            .build(), concurrentCustomer);

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // FIRE ALL THREADS SIMULTANEOUSLY
        boolean finished = finishLatch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(finished, "Concurrent booking test timed out");
        assertEquals(initialTickets, successCount.get(), "Exactly " + initialTickets + " bookings must succeed");
        assertEquals(totalAttemptingUsers - initialTickets, failureCount.get(), "Over-limit bookings must fail gracefully");

        EventResponse finalEventState = eventService.getEventById(eventId);
        assertEquals(0, finalEventState.getAvailableTickets(), "Available tickets must be exactly 0 (no overselling!)");
    }
}
