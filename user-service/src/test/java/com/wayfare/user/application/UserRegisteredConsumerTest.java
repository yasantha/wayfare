package com.wayfare.user.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayfare.user.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegisteredConsumerTest {

    @Mock
    UserService userService;
    @Mock
    ProcessedEventRepository processedEvents;

    ObjectMapper objectMapper = new ObjectMapper();

    private UserRegisteredConsumer consumer() {
        return new UserRegisteredConsumer(userService, processedEvents, objectMapper);
    }

    @Test
    void newEvent_createsProfileAndRecordsEventId() throws Exception {
        UUID userId = UUID.randomUUID();
        String msg = "{\"eventId\":\"e1\",\"userId\":\"" + userId + "\"}";
        when(processedEvents.existsById("e1")).thenReturn(false);

        consumer().onUserRegistered(msg);

        verify(userService).ensureProfile(userId);
        verify(processedEvents).save(any());
    }

    @Test
    void duplicateEvent_isSkipped() throws Exception {
        String msg = "{\"eventId\":\"e1\",\"userId\":\"" + UUID.randomUUID() + "\"}";
        when(processedEvents.existsById("e1")).thenReturn(true);

        consumer().onUserRegistered(msg);

        verify(userService, never()).ensureProfile(any());
        verify(processedEvents, never()).save(any());
    }
}
