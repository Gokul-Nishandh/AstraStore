package com.astrastore.monitoring.repository;

import com.astrastore.monitoring.entity.ServiceIncident;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceIncidentRepository extends JpaRepository<ServiceIncident, Long> {

    Optional<ServiceIncident> findFirstByServiceIdAndEndedAtMillisIsNullOrderByStartedAtMillisDesc(
            String serviceId);

    /** The newest transition of any kind, used for {@code lastStateChange}. */
    Optional<ServiceIncident> findFirstByServiceIdOrderByStartedAtMillisDesc(String serviceId);

    List<ServiceIncident> findByEndedAtMillisIsNull();

    /**
     * Incidents that overlap the window — including one that started before it
     * and one that has not ended. Both contribute downtime inside the window
     * and both would be missed by a plain {@code startedAt between} filter.
     */
    @Query("""
            select i from ServiceIncident i
             where i.startedAtMillis <= :toMillis
               and (i.endedAtMillis is null or i.endedAtMillis >= :fromMillis)
             order by i.startedAtMillis desc
            """)
    List<ServiceIncident> findOverlapping(@Param("fromMillis") long fromMillis,
                                          @Param("toMillis") long toMillis);

    @Query("""
            select i from ServiceIncident i
             where i.startedAtMillis <= :toMillis
               and (i.endedAtMillis is null or i.endedAtMillis >= :fromMillis)
             order by i.startedAtMillis desc
            """)
    List<ServiceIncident> findOverlapping(@Param("fromMillis") long fromMillis,
                                          @Param("toMillis") long toMillis,
                                          Pageable pageable);
}
