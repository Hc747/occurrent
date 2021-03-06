/*
 * Copyright 2020 Johan Haleby
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.occurrent.eventstore.api.reactor;

import io.cloudevents.CloudEvent;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.function.Function;

/**
 * Additional operations that may be supported by an {@link EventStore} implementation that is not typically part of a
 * "transactional" use case.
 */
public interface EventStoreOperations {

    /**
     * Delete all events and metadata associated with a event stream
     *
     * @param streamId The id of the stream to delete
     */
    Mono<Void> deleteEventStream(String streamId);

    /**
     * Delete a specific cloud event from an event stream
     *
     * @param cloudEventId     The id of the cloud event (see {@link CloudEvent#getId()})
     * @param cloudEventSource The source of the cloud event (see {@link CloudEvent#getSource()})
     */
    Mono<Void> deleteEvent(String cloudEventId, URI cloudEventSource);

    /**
     * Update a unique cloud event. This is mainly useful as a strategy for complying with e.g. GDPR if you need to
     * remove some attributes that are sensitive.
     *
     * @param cloudEventId     The id of the cloud event (see {@link CloudEvent#getId()})
     * @param cloudEventSource The source of the cloud event (see {@link CloudEvent#getSource()})
     * @param updateFunction   A function that takes the existing cloud event and you're expected to return an
     *                         updated cloud event (cannot be <code>null</code>). If the a cloud event is not found
     *                         for the given <code>cloudEventId</code> and <code>cloudEventSource</code> then the <code>fn</code>
     *                         function will not be called and an empty <code>Optional</code> will be returned.
     * @return The updated cloud event or an empty <code>Optional</code> if no cloud event was found matching the <code>cloudEventId</code> and <code>cloudEventSource</code>.
     */
    Mono<CloudEvent> updateEvent(String cloudEventId, URI cloudEventSource, Function<CloudEvent, CloudEvent> updateFunction);

    // TODO Implement generic delete method with a Condition
}