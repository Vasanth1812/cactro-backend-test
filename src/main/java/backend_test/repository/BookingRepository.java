package backend_test.repository;

import backend_test.entity.Booking;
import backend_test.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingReference(String bookingReference);

    Page<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Page<Booking> findByEventIdOrderByCreatedAtDesc(Long eventId, Pageable pageable);

    /**
     * Finds all unique customers who have confirmed bookings for a specific event.
     * Used for Background Task 2: Event Update Notifications.
     */
    @Query("SELECT DISTINCT b.customer FROM Booking b WHERE b.event.id = :eventId AND b.status = 'CONFIRMED'")
    List<User> findDistinctCustomersByEventId(@Param("eventId") Long eventId);

    @Query("SELECT COALESCE(SUM(b.ticketCount), 0) FROM Booking b WHERE b.event.id = :eventId AND b.status = 'CONFIRMED'")
    long countConfirmedTicketsByEventId(@Param("eventId") Long eventId);
}
