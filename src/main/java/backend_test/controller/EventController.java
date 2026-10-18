package backend_test.controller;

import backend_test.common.ApiResponse;
import backend_test.dto.BookingResponse;
import backend_test.dto.CreateEventRequest;
import backend_test.dto.EventResponse;
import backend_test.dto.UpdateEventRequest;
import backend_test.security.CustomUserDetails;
import backend_test.service.BookingService;
import backend_test.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Event Management", description = "Endpoints for creating, managing, and browsing events")
public class EventController {

    private final EventService eventService;
    private final BookingService bookingService;

    @GetMapping
    @Operation(summary = "Browse all upcoming events", description = "Public endpoint to search and filter upcoming available events with pagination")
    public ResponseEntity<ApiResponse<Page<EventResponse>>> getAllEvents(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime afterDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "eventDate") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<EventResponse> events = eventService.getAllEvents(search, afterDate, pageRequest);
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get event details", description = "Public endpoint to fetch details of a specific event by ID")
    public ResponseEntity<ApiResponse<EventResponse>> getEventById(@PathVariable Long id) {
        EventResponse event = eventService.getEventById(id);
        return ResponseEntity.ok(ApiResponse.success(event));
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create an event", description = "Allowed for Event Organizers only")
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        EventResponse event = eventService.createEvent(request, userDetails.getUser());
        return new ResponseEntity<>(ApiResponse.success("Event created successfully", event), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update an event", description = "Allowed for the Event Organizer who created the event. Triggers Background Task 2 (email notification to booked customers).")
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEventRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        EventResponse event = eventService.updateEvent(id, request, userDetails.getUser());
        return ResponseEntity.ok(ApiResponse.success("Event updated successfully and update notifications dispatched to attendees", event));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete an event", description = "Allowed for the Event Organizer who created the event")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        eventService.deleteEvent(id, userDetails.getUser());
        return ResponseEntity.ok(ApiResponse.success("Event deleted successfully", null));
    }

    @GetMapping("/my-events")
    @PreAuthorize("hasRole('ORGANIZER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get organizer's events", description = "List all events created by the authenticated organizer")
    public ResponseEntity<ApiResponse<Page<EventResponse>>> getMyEvents(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<EventResponse> events = eventService.getEventsByOrganizer(userDetails.getUser(), pageRequest);
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @GetMapping("/{id}/bookings")
    @PreAuthorize("hasRole('ORGANIZER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "View event attendees / bookings", description = "Allowed for the Event Organizer who created the event")
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getEventBookings(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<BookingResponse> bookings = bookingService.getEventBookings(id, userDetails.getUser(), pageRequest);
        return ResponseEntity.ok(ApiResponse.success(bookings));
    }
}
