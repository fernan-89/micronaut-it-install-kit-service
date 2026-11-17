package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.client.result.InsertOneResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.model.InstallReport;
import com.thinklab.domain.model.InstallReport.Outcome;
import com.thinklab.infrastructure.adapter.out.persistence.entity.InstallReportDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.InstallReportDocument.InstallReportPersistenceMapper;
import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class InstallReportMongoAdapterTest {

    private static final String URI = "mongodb://localhost:27017/ikt_test";

    private final UUID org = UUID.randomUUID();
    private MongoClient client;
    private MongoCollection<InstallReportDocument> collection;

    @BeforeEach
    void setUp() {
        client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        collection = mock(MongoCollection.class);
        when(client.getDatabase("ikt_test")).thenReturn(database);
        when(client.getDatabase("thinklab_it_install_kit_db")).thenReturn(database);
        when(database.getCollection("install_reports", InstallReportDocument.class)).thenReturn(collection);
        when(collection.withCodecRegistry(any())).thenReturn(collection);
    }

    private InstallReport report(UUID asset) {
        return InstallReport.createNew(UUID.randomUUID(), org, UUID.randomUUID(), 2, "1.2.2", Outcome.PARTIAL, 3, asset, Instant.parse("2026-11-17T12:00:00Z"), "op");
    }

    private void finds(InstallReport found) {
        FindPublisher<InstallReportDocument> publisher = mock(FindPublisher.class);
        when(collection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(publisher);
        when(publisher.sort(any(Bson.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<InstallReportDocument> subscriber = invocation.getArgument(0);
            Flux.just(InstallReportPersistenceMapper.toDocument(found)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());
    }

    @Test
    @DisplayName("a report survives the round trip with and without an asset")
    void roundTrip() {
        UUID asset = UUID.randomUUID();
        InstallReport report = report(asset);

        InstallReportDocument document = InstallReportPersistenceMapper.toDocument(report);
        InstallReport back = InstallReportPersistenceMapper.toDomain(document);

        assertEquals(report.getId(), document.getId());
        assertEquals(org, document.getOrganisationId());
        assertEquals(report.getKitId(), document.getKitId());
        assertEquals(2, document.getKitRevision());
        assertEquals("1.2.2", document.getAddOnVersion());
        assertEquals("PARTIAL", document.getOutcome());
        assertEquals(3, document.getWarnings());
        assertEquals(asset, document.getAssetId());
        assertEquals(report.getOccurredAt(), document.getOccurredAt());
        assertEquals(report.getCreatedAt(), document.getCreatedAt());
        assertEquals("op", document.getReportedBy());
        assertEquals(Outcome.PARTIAL, back.getOutcome());
        assertEquals(asset, back.getAssetId());
        assertEquals(report.getCreatedAt(), back.getCreatedAt());
        assertNull(InstallReportPersistenceMapper.toDomain(InstallReportPersistenceMapper.toDocument(report(null))).getAssetId());
    }

    @Test
    @DisplayName("the persistence mapper is a utility class")
    void utilityClass() throws ReflectiveOperationException {
        Constructor<InstallReportPersistenceMapper> constructor = InstallReportPersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    @DisplayName("create inserts and the lookups are scoped to the organisation; the URI may omit the database")
    void lookups() {
        InstallReport report = report(null);
        when(collection.insertOne(any(InstallReportDocument.class))).thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));
        finds(report);
        InstallReportMongoRepositoryAdapter adapter = new InstallReportMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.create(report)).expectNext(report).verifyComplete();
        StepVerifier.create(adapter.findById(report.getId(), org)).assertNext(found -> assertEquals(report.getId(), found.getId())).verifyComplete();
        StepVerifier.create(adapter.findAll(org)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).find(filter.capture());
        assertTrue(filter.getAllValues().get(0).toString().contains("organisationId"));
        new InstallReportMongoRepositoryAdapter(client, "mongodb://localhost:27017");
        assertThrows(NullPointerException.class, () -> new InstallReportMongoRepositoryAdapter(client, null));
    }
}
