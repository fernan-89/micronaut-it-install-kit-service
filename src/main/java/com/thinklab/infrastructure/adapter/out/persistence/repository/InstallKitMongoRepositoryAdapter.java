package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.exception.DuplicateInstallKitException;
import com.thinklab.domain.exception.InvalidInstallKitStatusException;
import com.thinklab.domain.model.InstallKit;
import com.thinklab.domain.model.InstallKit.InstallKitAuditEntry;
import com.thinklab.domain.model.InstallKit.InstallKitStatus;
import com.thinklab.domain.repository.InstallKitRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.InstallKitDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.InstallKitDocument.AuditEntryDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.InstallKitDocument.InstallKitPersistenceMapper;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * MongoDB Reactive Repository Adapter for the InstallKit aggregate, raw reactive-streams driver. Every change is one atomic update guarded by the
 * status loaded (ADR-033) that also appends the audit entry; a name unique per organisation is the atomic backstop of a duplicate.
 */
@Singleton
public class InstallKitMongoRepositoryAdapter implements InstallKitRepository {

    private static final Logger log = LoggerFactory.getLogger(InstallKitMongoRepositoryAdapter.class);
    static final String DEFAULT_DATABASE = "thinklab_it_install_kit_db";
    static final String COLLECTION_NAME = "install_kits";
    static final int DUPLICATE_KEY = 11000;
    private static final String FIELD_ID = "_id";
    private static final String FIELD_ORGANISATION = "organisationId";
    private static final String FIELD_STATUS = "status";

    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;
    private final String database;

    public InstallKitMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : DEFAULT_DATABASE;
    }

    private MongoCollection<InstallKitDocument> getCollection() {
        return mongoClient.getDatabase(database).getCollection(COLLECTION_NAME, InstallKitDocument.class).withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<InstallKit> create(InstallKit kit) {
        log.debug("[PERSISTENCE] Monolithic create for InstallKit Aggregate: {}", kit.getId());
        return Mono.from(getCollection().insertOne(InstallKitPersistenceMapper.toDocument(kit)))
                .map(result -> kit)
                .onErrorMap(MongoWriteException.class, error -> duplicate(error, kit));
    }

    @Override
    public Mono<InstallKit> findById(UUID id, UUID organisationId) {
        return Mono.from(getCollection().find(Filters.and(Filters.eq(FIELD_ID, id), Filters.eq(FIELD_ORGANISATION, organisationId))).first())
                .map(InstallKitPersistenceMapper::toDomain);
    }

    @Override
    public Flux<InstallKit> findAll(UUID organisationId) {
        return Flux.from(getCollection().find(Filters.eq(FIELD_ORGANISATION, organisationId)).sort(Sorts.ascending("createdAt"))).map(InstallKitPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> save(InstallKit kit, InstallKitStatus expectedStatus, InstallKitAuditEntry auditEntry) {
        Bson guard = Filters.and(Filters.eq(FIELD_ID, kit.getId()), Filters.eq(FIELD_ORGANISATION, kit.getOrganisationId()), Filters.eq(FIELD_STATUS, expectedStatus.name()));
        Bson update = Updates.combine(
                Updates.set("name", kit.getName()),
                Updates.set("family", kit.getFamily().name()),
                Updates.set("role", kit.getRole().name()),
                Updates.set("language", kit.getLanguage().name()),
                Updates.set("systemId", kit.getSystemId()),
                Updates.set("machineClass", kit.getMachineClass()),
                Updates.set("revision", kit.getRevision()),
                Updates.set(FIELD_STATUS, kit.getStatus().name()),
                Updates.set("updatedAt", Instant.now()),
                Updates.push("auditTrail", AuditEntryDocument.fromDomain(auditEntry))
        );
        return Mono.from(getCollection().updateOne(guard, update))
                .flatMap(result -> result.getMatchedCount() == 0
                        ? Mono.error(new InvalidInstallKitStatusException("InstallKit was changed by someone else while this change was being recorded; read it again and retry."))
                        : Mono.<Void>empty())
                .onErrorMap(MongoWriteException.class, error -> duplicate(error, kit));
    }

    private static Throwable duplicate(MongoWriteException error, InstallKit kit) {
        return error.getError().getCode() == DUPLICATE_KEY
                ? new DuplicateInstallKitException("A kit named '" + kit.getName() + "' already exists in this organisation.") : error;
    }
}
