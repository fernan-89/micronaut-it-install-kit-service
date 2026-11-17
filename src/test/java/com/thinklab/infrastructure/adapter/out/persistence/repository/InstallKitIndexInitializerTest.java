package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.micronaut.context.event.StartupEvent;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class InstallKitIndexInitializerTest {

    private final MongoClient client = mock(MongoClient.class);

    @Test
    @DisplayName("startup creates the unique kit name, the status list index and the two report indexes (by kit and by time)")
    void indexes() {
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<Document> kits = mock(MongoCollection.class);
        MongoCollection<Document> reports = mock(MongoCollection.class);
        when(client.getDatabase("tenant_vnd")).thenReturn(database);
        when(database.getCollection("install_kits")).thenReturn(kits);
        when(database.getCollection("install_reports")).thenReturn(reports);
        when(kits.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));
        when(reports.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));

        new InstallKitIndexInitializer(client, "mongodb://mongo:27017/tenant_vnd").onApplicationEvent(mock(StartupEvent.class));

        ArgumentCaptor<IndexOptions> kitOptions = ArgumentCaptor.forClass(IndexOptions.class);
        verify(kits, times(2)).createIndex(any(), kitOptions.capture());
        assertTrue(kitOptions.getAllValues().get(0).isUnique());
        assertEquals("organisationId_1_name_1", kitOptions.getAllValues().get(0).getName());
        assertFalse(kitOptions.getAllValues().get(1).isUnique());
        assertEquals("organisationId_1_status_1", kitOptions.getAllValues().get(1).getName());
        ArgumentCaptor<IndexOptions> reportOptions = ArgumentCaptor.forClass(IndexOptions.class);
        verify(reports, times(2)).createIndex(any(), reportOptions.capture());
        assertFalse(reportOptions.getAllValues().get(0).isUnique());
        assertEquals("organisationId_1_kitId_1_occurredAt_-1", reportOptions.getAllValues().get(0).getName());
        assertFalse(reportOptions.getAllValues().get(1).isUnique());
        assertEquals("organisationId_1_occurredAt_-1", reportOptions.getAllValues().get(1).getName());
    }

    @Test
    @DisplayName("fail-open: an unreachable server or a rejected index is logged, never propagated; the URI may omit the database; arguments are null-checked")
    void failOpen() {
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(client.getDatabase("thinklab_it_install_kit_db")).thenReturn(database);
        when(database.getCollection(any(String.class))).thenReturn(collection);
        when(collection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.error(new MongoTimeoutException("no server")))
                .thenReturn(Mono.error(new IllegalStateException("rejected"))).thenReturn(Mono.just("ok"));
        var initializer = new InstallKitIndexInitializer(client, "mongodb://mongo:27017");

        initializer.onApplicationEvent(mock(StartupEvent.class));

        verify(collection, times(4)).createIndex(any(), any(IndexOptions.class));
        assertThrows(NullPointerException.class, () -> initializer.onApplicationEvent(null));
        assertThrows(NullPointerException.class, () -> new InstallKitIndexInitializer(null, "mongodb://mongo:27017"));
        assertThrows(NullPointerException.class, () -> new InstallKitIndexInitializer(client, null));
    }
}
