package backend_test.repository;

import backend_test.entity.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    Page<Event> findByOrganizerIdOrderByCreatedAtDesc(Long organizerId, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.eventDate >= :now AND e.availableTickets > 0 ORDER BY e.eventDate ASC")
    Page<Event> findUpcomingAvailableEvents(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE " +
            "(:keyword IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(e.location) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:afterDate IS NULL OR e.eventDate >= :afterDate) " +
            "ORDER BY e.eventDate ASC")
    Page<Event> searchEvents(@Param("keyword") String keyword, @Param("afterDate") LocalDateTime afterDate, Pageable pageable);

    /**
     * Highly optimized atomic row-level decrement to prevent overselling under extreme concurrency.
     * Returns 1 if tickets were reserved successfully, 0 if not enough available tickets.
     */
    @Modifying
    @Query("UPDATE Event e SET e.availableTickets = e.availableTickets - :quantity " +
            "WHERE e.id = :eventId AND e.availableTickets >= :quantity")
    int bookTicketsAtomic(@Param("eventId") Long eventId, @Param("quantity") int quantity);

    /**
     * Pessimistic locking support for comparison / fallback.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdWithPessimisticLock(@Param("id") Long id);
}
