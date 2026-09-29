package com.nordicframtiden.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/** The job is a thin clock: queue on the ready date, then drain the pending queue. */
@ExtendWith(MockitoExtension.class)
class PayslipDeliveryJobTest {

    @Mock private PayslipDeliveryService deliveryService;
    @InjectMocks private PayslipDeliveryJob job;

    @Test
    void run_queuesForToday_andDrainsPendingDeliveries() {
        job.run();

        verify(deliveryService).queueIfReady(LocalDate.now(java.time.ZoneId.of(PayslipDeliveryJob.ZONE)));
        verify(deliveryService).deliverPending();
        verifyNoMoreInteractions(deliveryService);
    }
}
