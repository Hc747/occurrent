package se.haleby.occurrent.changestreamer.mongodb.spring.blocking;

import com.mongodb.client.model.changestream.ChangeStreamDocument;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import org.bson.BsonValue;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.ChangeStreamOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.messaging.ChangeStreamRequest;
import org.springframework.data.mongodb.core.messaging.ChangeStreamRequest.ChangeStreamRequestOptions;
import org.springframework.data.mongodb.core.messaging.MessageListener;
import org.springframework.data.mongodb.core.messaging.MessageListenerContainer;
import org.springframework.data.mongodb.core.messaging.Subscription;
import org.springframework.data.mongodb.core.query.Update;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static se.haleby.occurrent.changestreamer.mongodb.common.MongoDBCloudEventsToJsonDeserializer.*;

public class SpringBlockingChangeStreamerForMongoDB {
    private static final Logger log = LoggerFactory.getLogger(SpringBlockingChangeStreamerForMongoDB.class);

    private final MongoTemplate mongoTemplate;
    private final String eventCollection;
    private final String resumeTokenCollection;
    private final MessageListenerContainer messageListenerContainer;
    private final ConcurrentMap<String, Subscription> subscriptions;
    private final EventFormat cloudEventSerializer;

    public SpringBlockingChangeStreamerForMongoDB(MongoTemplate mongoTemplate, String eventCollection, String resumeTokenCollection, MessageListenerContainer messageListenerContainer) {
        requireNonNull(mongoTemplate, "Mongo template cannot be null");
        requireNonNull(mongoTemplate, "eventCollection cannot be null");
        requireNonNull(mongoTemplate, "resumeTokenCollection cannot be null");
        requireNonNull(mongoTemplate, "messageListenerContainer cannot be null");

        this.mongoTemplate = mongoTemplate;
        this.eventCollection = eventCollection;
        this.resumeTokenCollection = resumeTokenCollection;
        this.subscriptions = new ConcurrentHashMap<>();
        this.cloudEventSerializer = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE);
        this.messageListenerContainer = messageListenerContainer;
        this.messageListenerContainer.start();
    }

    public Subscription subscribe(String subscriptionId, Consumer<List<CloudEvent>> action) {
        Document document = mongoTemplate.findOne(query(where(ID).is(subscriptionId)), Document.class, resumeTokenCollection);

        final ChangeStreamOptions changeStreamOptions;
        if (document == null) {
            log.info("Couldn't find resume token for subscription {}, will start subscribing to events at this moment in time.", subscriptionId);
            changeStreamOptions = ChangeStreamOptions.empty();
        } else {
            ResumeToken resumeToken = extractResumeTokenFromPersistedResumeTokenDocument(document);
            log.info("Found resume token {} for subscription {}, will resume stream.", resumeToken.asString(), subscriptionId);
            changeStreamOptions = ChangeStreamOptions.builder().startAfter(resumeToken.asBsonDocument()).build();
        }


        MessageListener<ChangeStreamDocument<Document>, Document> listener = change -> {
            ChangeStreamDocument<Document> raw = change.getRaw();
            List<CloudEvent> cloudEvents = deserializeToCloudEvents(requireNonNull(cloudEventSerializer), raw);
            action.accept(cloudEvents);
            persistResumeToken(subscriptionId, requireNonNull(raw).getResumeToken());
        };

        ChangeStreamRequestOptions options = new ChangeStreamRequestOptions(null, eventCollection, changeStreamOptions);
        final Subscription subscription = messageListenerContainer.register(new ChangeStreamRequest<>(listener, options), Document.class);
        subscriptions.put(subscriptionId, subscription);
        return subscription;
    }

    void pauseSubscription(String subscriptionId) {
        Subscription subscription = subscriptions.remove(subscriptionId);
        if (subscription != null) {
            messageListenerContainer.remove(subscription);
        }
    }

    public void cancelSubscription(String subscriptionId) {
        pauseSubscription(subscriptionId);
        mongoTemplate.remove(query(where(ID).is(subscriptionId)), resumeTokenCollection);
    }


    @PreDestroy
    void closeSubscribers() {
        subscriptions.clear();
        messageListenerContainer.stop();
    }

    private void persistResumeToken(String subscriptionId, BsonValue resumeToken) {
        mongoTemplate.upsert(query(where(ID).is(subscriptionId)),
                Update.fromDocument(generateResumeTokenDocument(subscriptionId, resumeToken)),
                resumeTokenCollection);
    }
}