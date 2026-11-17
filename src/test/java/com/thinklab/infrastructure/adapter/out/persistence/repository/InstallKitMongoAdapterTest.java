package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.exception.DuplicateInstallKitException;
import com.thinklab.domain.exception.InvalidInstallKitStatusException;
import com.thinklab.domain.model.InstallKit;
import com.thinklab.domain.model.InstallKit.InstallKitStatus;
import com.thinklab.domain.model.InstallKit.Family;
import com.thinklab.domain.model.InstallKit.Language;
import com.thinklab.domain.model.InstallKit.Role;
import com.thinklab.infrastructure.adapter.out.persistence.entity.InstallKitDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.InstallKitDocument.InstallKitPersistenceMapper;
import org.bson.BsonDocument;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class InstallKitMongoAdapterTest {

    private static final String URI = "mongodb://localhost:27017/vnd_test";

    private final UUID org = UUID.randomUUID();
    private MongoClient client;
    private MongoCollection<InstallKitDocument> collection;

    @BeforeEach
    void setUp() {
        client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        collection = mock(MongoCollection.class);
        when(client.getDatabase("vnd_test")).thenReturn(database);
        when(client.getDatabase("thinklab_it_install_kit_db")).thenReturn(database);
        when(database.getCollection("install_kits", InstallKitDocument.class)).thenReturn(collection);
        when(collection.withCodecRegistry(any())).thenReturn(collection);
    }

    private InstallKit kit() {
        InstallKit kit = InstallKit.createNew(UUID.randomUUID(), org, "Dell", Family.WINDOWS, Role.WORKSTATION, Language.PT, "W11", "BASE", "op");
        kit.retire("op");
        return kit;
    }

    private static MongoWriteException writeError(int code) {
        return new MongoWriteException(new WriteError(code, "write error", new BsonDocument()), new ServerAddress());
    }

    private void finds(InstallKit found) {
        FindPublisher<InstallKitDocument> publisher = mock(FindPublisher.class);
        when(collection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(publisher);
        when(publisher.sort(any(Bson.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<InstallKitDocument> subscriber = invocation.getArgument(0);
            Flux.just(InstallKitPersistenceMapper.toDocument(found)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());
    }

    @Test
    @DisplayName("kit survives the round trip, with its status and its audit trail")
    void roundTrip() {
        InstallKit kit = kit();

        InstallKitDocument document = InstallKitPersistenceMapper.toDocument(kit);
        InstallKit back = InstallKitPersistenceMapper.toDomain(document);

        assertEquals(InstallKitStatus.RETIRED, back.getStatus());
        assertEquals(2, back.getAuditTrail().size());
        assertEquals(null, back.getAuditTrail().get(0).fromStatus());
        assertEquals(InstallKitStatus.ACTIVE, back.getAuditTrail().get(1).fromStatus());
        assertEquals("RETIRED", document.getStatus());
        assertEquals(org, document.getOrganisationId());
        assertEquals(kit.getId(), document.getId());
        assertEquals(kit.getCreatedAt(), document.getCreatedAt());
        assertEquals(kit.getUpdatedAt(), document.getUpdatedAt());
        assertEquals(2, document.getAuditTrail().size());
        assertEquals("Dell", document.getName());
        assertEquals("WINDOWS", document.getFamily());
        assertEquals(Family.WINDOWS, back.getFamily());
        assertEquals("Dell", back.getName());
        assertEquals(Role.WORKSTATION, back.getRole());
        assertEquals(Language.PT, back.getLanguage());
        assertEquals("W11", back.getSystemId());
        assertEquals("BASE", back.getMachineClass());
        assertEquals(1, back.getRevision());
        assertEquals("PT", document.getLanguage());
        assertEquals("WORKSTATION", document.getRole());
        assertEquals("W11", document.getSystemId());
        assertEquals("BASE", document.getMachineClass());
        assertEquals(1, document.getRevision());
    }

    @Test
    @DisplayName("the document setters are plain, and the persistence mapper is a utility class")
    void plainDocument() throws ReflectiveOperationException {
        InstallKitDocument document = new InstallKitDocument();
        document.setAuditTrail(new ArrayList<>());
        assertTrue(document.getAuditTrail().isEmpty());

        Constructor<InstallKitPersistenceMapper> constructor = InstallKitPersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    @DisplayName("create and the lookups are scoped to the organisation, the list oldest first")
    void lookups() {
        InstallKit kit = kit();
        when(collection.insertOne(any(InstallKitDocument.class))).thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));
        finds(kit);
        InstallKitMongoRepositoryAdapter adapter = new InstallKitMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.create(kit)).expectNext(kit).verifyComplete();
        StepVerifier.create(adapter.findById(kit.getId(), org)).assertNext(found -> assertEquals(kit.getId(), found.getId())).verifyComplete();
        StepVerifier.create(adapter.findAll(org)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).find(filter.capture());
        assertTrue(filter.getAllValues().get(0).toString().contains("organisationId"));
    }

    @Test
    @DisplayName("a duplicate is a 409 of the domain, on create or on a change; any other write error passes through; the URI may omit the database")
    void duplicates() {
        InstallKit kit = kit();
        when(collection.insertOne(any(InstallKitDocument.class))).thenReturn(Mono.error(writeError(11000))).thenReturn(Mono.error(writeError(1)));
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.error(writeError(11000))).thenReturn(Mono.error(writeError(2)));
        InstallKitMongoRepositoryAdapter adapter = new InstallKitMongoRepositoryAdapter(client, "mongodb://localhost:27017");

        StepVerifier.create(adapter.create(kit)).expectError(DuplicateInstallKitException.class).verify();
        StepVerifier.create(adapter.create(kit)).expectError(MongoWriteException.class).verify();
        StepVerifier.create(adapter.save(kit, InstallKitStatus.ACTIVE, kit.getAuditTrail().get(1))).expectError(DuplicateInstallKitException.class).verify();
        StepVerifier.create(adapter.save(kit, InstallKitStatus.ACTIVE, kit.getAuditTrail().get(1))).expectError(MongoWriteException.class).verify();
        assertThrows(NullPointerException.class, () -> new InstallKitMongoRepositoryAdapter(client, null));
    }

    @Test
    @DisplayName("save is guarded by the status loaded and pushes the audit entry; a lost race is a conflict")
    void save() {
        InstallKit kit = kit();
        when(collection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));
        InstallKitMongoRepositoryAdapter adapter = new InstallKitMongoRepositoryAdapter(client, URI);

        StepVerifier.create(adapter.save(kit, InstallKitStatus.ACTIVE, kit.getAuditTrail().get(1))).verifyComplete();
        StepVerifier.create(adapter.save(kit, InstallKitStatus.ACTIVE, kit.getAuditTrail().get(1))).expectError(InvalidInstallKitStatusException.class).verify();

        ArgumentCaptor<Bson> guard = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(collection, times(2)).updateOne(guard.capture(), update.capture());
        assertTrue(guard.getAllValues().get(0).toString().contains("ACTIVE") && guard.getAllValues().get(0).toString().contains("organisationId"));
        String text = update.getAllValues().get(0).toString();
        assertTrue(text.contains("auditTrail"));
        for (String field : new String[]{"name", "family", "role", "language", "systemId", "machineClass", "revision"}) {
            assertTrue(text.contains(field), field);
        }
    }
}
