package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Creates the indexes at startup. The UNIQUE one is the atomic backstop (ADR-033): a kit name per organisation. The list indexes serve the real
 * queries (kits by status, reports by kit and by time). Fail-open: {@code createIndex} is idempotent, a failure is logged and the
 * application still starts. Turn it off with {@code thinklab.mongo.create-indexes=false}.
 */
@Singleton
@Requires(property = "thinklab.mongo.create-indexes", notEquals = "false")
public class InstallKitIndexInitializer implements ApplicationEventListener<StartupEvent> {

    static final String KIT_NAME_INDEX = "organisationId_1_name_1";
    static final String KIT_STATUS_INDEX = "organisationId_1_status_1";
    static final String REPORT_KIT_INDEX = "organisationId_1_kitId_1_occurredAt_-1";
    static final String REPORT_TIME_INDEX = "organisationId_1_occurredAt_-1";

    private static final Logger log = LoggerFactory.getLogger(InstallKitIndexInitializer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final MongoClient mongoClient;
    private final String database;
    private final Duration timeout;

    @Inject
    public InstallKitIndexInitializer(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this(mongoClient, mongoUri, TIMEOUT);
    }

    InstallKitIndexInitializer(MongoClient mongoClient, String mongoUri, Duration timeout) {
        this.mongoClient = Objects.requireNonNull(mongoClient, "Infrastructure constraint violated: MongoClient cannot be null.");
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : InstallKitMongoRepositoryAdapter.DEFAULT_DATABASE;
        this.timeout = timeout;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        Objects.requireNonNull(event, "Application constraint violated: StartupEvent cannot be null.");
        String kits = InstallKitMongoRepositoryAdapter.COLLECTION_NAME;
        ensureIndex(kits, KIT_NAME_INDEX, new Document("organisationId", 1).append("name", 1), new IndexOptions().unique(true));
        ensureIndex(kits, KIT_STATUS_INDEX, new Document("organisationId", 1).append("status", 1), new IndexOptions());
        String reports = InstallReportMongoRepositoryAdapter.COLLECTION_NAME;
        ensureIndex(reports, REPORT_KIT_INDEX, new Document("organisationId", 1).append("kitId", 1).append("occurredAt", -1), new IndexOptions());
        ensureIndex(reports, REPORT_TIME_INDEX, new Document("organisationId", 1).append("occurredAt", -1), new IndexOptions());
    }

    private void ensureIndex(String collection, String indexName, Document keys, IndexOptions options) {
        try {
            Mono.from(mongoClient.getDatabase(database).getCollection(collection).createIndex(keys, options.name(indexName))).block(timeout);
            log.info("[MONGO_INDEXES] Ensured index [{}] on [{}.{}]", indexName, database, collection);
        } catch (MongoTimeoutException e) {
            log.error("[MONGO_INDEXES] MongoDB unreachable; index [{}] was not created. Reason: {}", indexName, e.getMessage());
        } catch (RuntimeException e) {
            log.error("[MONGO_INDEXES] Could not create index [{}] on [{}.{}]: {}", indexName, database, collection, e.getMessage());
        }
    }
}
