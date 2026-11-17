package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.model.InstallReport;
import com.thinklab.domain.repository.InstallReportRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.InstallReportDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.InstallReportDocument.InstallReportPersistenceMapper;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

/** MongoDB Reactive Repository Adapter for the InstallReport aggregate, raw reactive-streams driver. A report is only inserted, never updated. */
@Singleton
public class InstallReportMongoRepositoryAdapter implements InstallReportRepository {

    private static final Logger log = LoggerFactory.getLogger(InstallReportMongoRepositoryAdapter.class);
    static final String COLLECTION_NAME = "install_reports";
    private static final String FIELD_ID = "_id";
    private static final String FIELD_ORGANISATION = "organisationId";

    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;
    private final String database;

    public InstallReportMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : InstallKitMongoRepositoryAdapter.DEFAULT_DATABASE;
    }

    private MongoCollection<InstallReportDocument> getCollection() {
        return mongoClient.getDatabase(database).getCollection(COLLECTION_NAME, InstallReportDocument.class).withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<InstallReport> create(InstallReport report) {
        log.debug("[PERSISTENCE] Insert for InstallReport Aggregate: {}", report.getId());
        return Mono.from(getCollection().insertOne(InstallReportPersistenceMapper.toDocument(report))).map(result -> report);
    }

    @Override
    public Mono<InstallReport> findById(UUID id, UUID organisationId) {
        return Mono.from(getCollection().find(Filters.and(Filters.eq(FIELD_ID, id), Filters.eq(FIELD_ORGANISATION, organisationId))).first())
                .map(InstallReportPersistenceMapper::toDomain);
    }

    @Override
    public Flux<InstallReport> findAll(UUID organisationId) {
        return Flux.from(getCollection().find(Filters.eq(FIELD_ORGANISATION, organisationId)).sort(Sorts.descending("occurredAt")))
                .map(InstallReportPersistenceMapper::toDomain);
    }
}
