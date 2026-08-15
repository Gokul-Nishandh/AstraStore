package com.astrastore.monitoring.service;

import com.astrastore.monitoring.entity.ServiceIncident;
import com.astrastore.monitoring.repository.ServiceIncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    private static final String SERVICE = "auth";

    @Mock
    private ServiceIncidentRepository incidentRepository;

    private IncidentService incidentService;

    @BeforeEach
    void setUp() {
        incidentService = new IncidentService(incidentRepository);
        // Lenient because the no-op cases assert that nothing is ever saved,
        // and a strict stub would fail them for not using it.
        lenient().when(incidentRepository.save(any(ServiceIncident.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void open_recordsStartAndLeavesTheIncidentRunning() {
        when(incidentRepository
                .findFirstByServiceIdAndEndedAtMillisIsNullOrderByStartedAtMillisDesc(SERVICE))
                .thenReturn(Optional.empty());

        ServiceIncident incident = incidentService.open(SERVICE, 1_000L, "Connection refused");

        assertThat(incident.getServiceId()).isEqualTo(SERVICE);
        assertThat(incident.getStartedAtMillis()).isEqualTo(1_000L);
        assertThat(incident.getEndedAtMillis()).isNull();
        assertThat(incident.getDurationSeconds()).isNull();
        assertThat(incident.isOngoing()).isTrue();
        assertThat(incident.getLastError()).isEqualTo("Connection refused");
    }

    @Test
    void open_doesNotCreateASecondRowWhileOneIsAlreadyRunning() {
        ServiceIncident existing = ServiceIncident.builder()
                .id(7L).serviceId(SERVICE).startedAtMillis(1_000L).lastError("Connection refused")
                .build();
        when(incidentRepository
                .findFirstByServiceIdAndEndedAtMillisIsNullOrderByStartedAtMillisDesc(SERVICE))
                .thenReturn(Optional.of(existing));

        ServiceIncident incident = incidentService.open(SERVICE, 9_000L, "Read timed out");

        assertThat(incident.getId()).isEqualTo(7L);
        assertThat(incident.getStartedAtMillis()).isEqualTo(1_000L);
        assertThat(incident.getLastError()).isEqualTo("Read timed out");
    }

    @Test
    void close_setsEndAndDurationInSeconds() {
        ServiceIncident open = ServiceIncident.builder()
                .id(1L).serviceId(SERVICE).startedAtMillis(1_000_000L).build();
        when(incidentRepository
                .findFirstByServiceIdAndEndedAtMillisIsNullOrderByStartedAtMillisDesc(SERVICE))
                .thenReturn(Optional.of(open));

        Optional<ServiceIncident> closed = incidentService.close(SERVICE, 1_155_000L);

        assertThat(closed).isPresent();
        assertThat(closed.get().getEndedAtMillis()).isEqualTo(1_155_000L);
        assertThat(closed.get().getDurationSeconds()).isEqualTo(155L);
        assertThat(closed.get().isOngoing()).isFalse();
    }

    @Test
    void close_isANoOpWhenNothingIsOpen() {
        when(incidentRepository
                .findFirstByServiceIdAndEndedAtMillisIsNullOrderByStartedAtMillisDesc(SERVICE))
                .thenReturn(Optional.empty());

        assertThat(incidentService.close(SERVICE, 5_000L)).isEmpty();
        verify(incidentRepository, never()).save(any(ServiceIncident.class));
    }

    @Test
    void close_neverRecordsANegativeDuration() {
        ServiceIncident open = ServiceIncident.builder()
                .id(1L).serviceId(SERVICE).startedAtMillis(5_000L).build();
        when(incidentRepository
                .findFirstByServiceIdAndEndedAtMillisIsNullOrderByStartedAtMillisDesc(SERVICE))
                .thenReturn(Optional.of(open));

        ServiceIncident closed = incidentService.close(SERVICE, 1_000L).orElseThrow();

        assertThat(closed.getDurationSeconds()).isZero();
        assertThat(closed.getEndedAtMillis()).isEqualTo(5_000L);
    }

    @Test
    void ongoingIncidentIsMeasuredAgainstNowUntilItCloses() {
        ServiceIncident open = ServiceIncident.builder()
                .serviceId(SERVICE).startedAtMillis(1_000_000L).build();

        assertThat(open.durationSeconds(1_090_000L)).isEqualTo(90L);

        open.setEndedAtMillis(1_060_000L);
        open.setDurationSeconds(60L);

        assertThat(open.durationSeconds(1_090_000L)).isEqualTo(60L);
    }

    @Test
    void lastErrorOnAnOpenIncidentIsTruncatedToTheColumnWidth() {
        ServiceIncident open = ServiceIncident.builder()
                .id(1L).serviceId(SERVICE).startedAtMillis(1_000L).build();
        when(incidentRepository
                .findFirstByServiceIdAndEndedAtMillisIsNullOrderByStartedAtMillisDesc(SERVICE))
                .thenReturn(Optional.of(open));

        incidentService.updateOpenIncidentError(SERVICE, "x".repeat(2_000));

        ArgumentCaptor<ServiceIncident> captor = ArgumentCaptor.forClass(ServiceIncident.class);
        verify(incidentRepository).save(captor.capture());
        assertThat(captor.getValue().getLastError()).hasSize(512);
    }

    @Test
    void updatingTheErrorIsANoOpWhenNothingIsOpen() {
        when(incidentRepository
                .findFirstByServiceIdAndEndedAtMillisIsNullOrderByStartedAtMillisDesc(anyString()))
                .thenReturn(Optional.empty());

        incidentService.updateOpenIncidentError(SERVICE, "Connection refused");

        verify(incidentRepository, never()).save(any(ServiceIncident.class));
    }
}
