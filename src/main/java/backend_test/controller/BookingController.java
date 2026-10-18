package backend_test.controller;

import backend_test.common.ApiResponse;
import backend_test.dto.BookingResponse;
import backend_test.dto.CreateBookingRequest;
import backend_test.security.CustomUserDetails;
import backend_test.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking Management", description = "Endpoints for booking tickets and viewing booking history")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Book event tickets", description = "Allowed for Customers only. Atomic inventory reservation to prevent overselling. Triggers Background Task 1 (confirmation email).")
    public ResponseEntity<ApiResponse<BookingResponse>> bookTickets(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        BookingResponse booking = bookingService.bookTickets(request, userDetails.getUser());
        return new ResponseEntity<>(ApiResponse.success("Tickets booked successfully. Confirmation email sent.", booking), HttpStatus.CREATED);
    }

    @GetMapping("/my-bookings")
    @PreAuthorize("hasRole('CUSTOMER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get customer's booking history", description = "List all ticket bookings made by the authenticated customer")
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getMyBookings(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<BookingResponse> bookings = bookingService.getMyBookings(userDetails.getUser(), pageRequest);
        return ResponseEntity.ok(ApiResponse.success(bookings));
    }
}
