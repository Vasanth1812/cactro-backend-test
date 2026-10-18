package backend_test.config;

import backend_test.entity.Event;
import backend_test.entity.Role;
import backend_test.entity.User;
import backend_test.repository.EventRepository;
import backend_test.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            log.info("Seeding initial demo users and events...");

            // 1. Create Demo Organizer
            User organizer = User.builder()
                    .name("Sarah Connor (Organizer)")
                    .email("organizer@cactro.com")
                    .password(passwordEncoder.encode("Password@123"))
                    .role(Role.ORGANIZER)
                    .build();
            organizer = userRepository.save(organizer);

            // 2. Create Demo Customers
            User customer1 = User.builder()
                    .name("John Doe (Customer)")
                    .email("customer1@cactro.com")
                    .password(passwordEncoder.encode("Password@123"))
                    .role(Role.CUSTOMER)
                    .build();
            userRepository.save(customer1);

            User customer2 = User.builder()
                    .name("Alice Smith (Customer)")
                    .email("customer2@cactro.com")
                    .password(passwordEncoder.encode("Password@123"))
                    .role(Role.CUSTOMER)
                    .build();
            userRepository.save(customer2);

            // 3. Create Sample Events
            Event event1 = Event.builder()
                    .title("Cloud Native & AI Developer Summit 2026")
                    .description("Explore the latest advancements in Distributed Systems, LLMs, and Cloud Native architectures.")
                    .location("San Francisco Tech Center, CA & Online")
                    .eventDate(LocalDateTime.now().plusDays(14))
                    .totalTickets(100)
                    .availableTickets(100)
                    .ticketPrice(new BigDecimal("49.99"))
                    .organizer(organizer)
                    .build();
            eventRepository.save(event1);

            Event event2 = Event.builder()
                    .title("Spring Boot & Reactive Systems Masterclass")
                    .description("Deep dive into high-throughput concurrency, Virtual Threads, and distributed databases.")
                    .location("Seattle Convention Center, WA")
                    .eventDate(LocalDateTime.now().plusDays(30))
                    .totalTickets(250)
                    .availableTickets(250)
                    .ticketPrice(new BigDecimal("29.00"))
                    .organizer(organizer)
                    .build();
            eventRepository.save(event2);

            log.info("Demo data successfully initialized:");
            log.info("-> Organizer: organizer@cactro.com / Password@123");
            log.info("-> Customer:  customer1@cactro.com / Password@123");
        }
    }
}
