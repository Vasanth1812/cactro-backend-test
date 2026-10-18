package backend_test.service;

import backend_test.dto.CreateEventRequest;
import backend_test.dto.EventResponse;
import backend_test.dto.UpdateEventRequest;
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

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;
    private final EmailNotificationService emailNotificationService;

    @Transactional
    public EventResponse createEvent(CreateEventRequest request, User organizer) {
        if (organizer.getRole() != Role.ORGANIZER) {
            throw new BadRequestException("Only Event Organizers can create events");
        }

        Event event = Event.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .location(request.getLocation().trim())
                .eventDate(request.getEventDate())
                .totalTickets(request.getTotalTickets())
                .availableTickets(request.getTotalTickets())
                .ticketPrice(request.getTicketPrice())
                .organizer(organizer)
                .build();

        Event savedEvent = eventRepository.save(event);
        log.info("Event created successfully with ID: {} by organizer: {}", savedEvent.getId(), organizer.getEmail());
        return mapToResponse(savedEvent);
    }

    @Transactional(readOnly = true)
    public EventResponse getEventById(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));
        return mapToResponse(event);
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> getAllEvents(String keyword, LocalDateTime afterDate, Pageable pageable) {
        LocalDateTime dateFilter = afterDate != null ? afterDate : LocalDateTime.now();
        Page<Event> eventPage;
        if (keyword != null && !keyword.isBlank()) {
            eventPage = eventRepository.searchEventsWithKeywordAndDate(keyword.trim(), dateFilter, pageable);
        } else {
            eventPage = eventRepository.findByEventDateGreaterThanEqualOrderByEventDateAsc(dateFilter, pageable);
        }
        return eventPage.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> getEventsByOrganizer(User organizer, Pageable pageable) {
        return eventRepository.findByOrganizerIdOrderByCreatedAtDesc(organizer.getId(), pageable)
                .map(this::mapToResponse);
    }

    @Transactional
    public EventResponse updateEvent(Long eventId, UpdateEventRequest request, User organizer) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));

        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new BadRequestException("You are not authorized to update this event. Only the creator can modify it.");
        }

        StringBuilder changes = new StringBuilder();

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            event.setTitle(request.getTitle().trim());
            changes.append("Title updated. ");
        }
        if (request.getDescription() != null) {
            event.setDescription(request.getDescription());
        }
        if (request.getLocation() != null && !request.getLocation().isBlank()) {
            event.setLocation(request.getLocation().trim());
            changes.append("Location changed to: ").append(request.getLocation().trim()).append(". ");
        }
        if (request.getEventDate() != null) {
            event.setEventDate(request.getEventDate());
            changes.append("New date: ").append(request.getEventDate()).append(". ");
        }
        if (request.getTicketPrice() != null) {
            event.setTicketPrice(request.getTicketPrice());
        }
        if (request.getTotalTickets() != null) {
            int bookedCount = event.getTotalTickets() - event.getAvailableTickets();
            if (request.getTotalTickets() < bookedCount) {
                throw new BadRequestException("Cannot reduce total tickets below already booked tickets (" + bookedCount + ")");
            }
            int diff = request.getTotalTickets() - event.getTotalTickets();
            event.setTotalTickets(request.getTotalTickets());
            event.setAvailableTickets(event.getAvailableTickets() + diff);
            changes.append("Capacity updated to ").append(request.getTotalTickets()).append(". ");
        }

        Event updatedEvent = eventRepository.save(event);
        log.info("Event ID {} updated by organizer {}. Changes: {}", eventId, organizer.getEmail(), changes);

        // Background Task 2: Notify all booked customers asynchronously
        List<User> bookedCustomers = bookingRepository.findDistinctCustomersByEventId(eventId);
        if (!bookedCustomers.isEmpty()) {
            log.info("Triggering Background Task 2: Sending event update notifications to {} booked customers", bookedCustomers.size());
            emailNotificationService.sendEventUpdateNotifications(updatedEvent, bookedCustomers, changes.toString());
        }

        return mapToResponse(updatedEvent);
    }

    @Transactional
    public void deleteEvent(Long eventId, User organizer) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", "id", eventId));

        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new BadRequestException("You are not authorized to delete this event.");
        }

        long activeBookings = bookingRepository.countConfirmedTicketsByEventId(eventId);
        if (activeBookings > 0) {
            throw new BadRequestException("Cannot delete event with " + activeBookings + " confirmed tickets booked. Cancel the event or notify attendees first.");
        }

        eventRepository.delete(event);
        log.info("Event ID {} deleted by organizer {}", eventId, organizer.getEmail());
    }

    public EventResponse mapToResponse(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .location(event.getLocation())
                .eventDate(event.getEventDate())
                .totalTickets(event.getTotalTickets())
                .availableTickets(event.getAvailableTickets())
                .ticketPrice(event.getTicketPrice())
                .organizerId(event.getOrganizer().getId())
                .organizerName(event.getOrganizer().getName())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
