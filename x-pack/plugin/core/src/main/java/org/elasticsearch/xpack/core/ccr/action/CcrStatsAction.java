/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.core.ccr.action;

import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.FailedNodeException;
import org.elasticsearch.action.IndicesRequest;
import org.elasticsearch.action.TaskOperationFailure;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.tasks.BaseTasksRequest;
import org.elasticsearch.action.support.tasks.BaseTasksResponse;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.xpack.core.ccr.ShardFollowNodeTaskStatus;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class CcrStatsAction extends Action<CcrStatsAction.StatsRequest, CcrStatsAction.StatsResponses, CcrStatsAction.StatsRequestBuilder> {

    public static final String NAME = "cluster:monitor/ccr/stats";

    public static final CcrStatsAction INSTANCE = new CcrStatsAction();

    private CcrStatsAction() {
        super(NAME);
    }

    @Override
    public StatsResponses newResponse() {
        return new StatsResponses();
    }

    public static class StatsResponses extends BaseTasksResponse implements ToXContentObject {

        private List<StatsResponse> statsResponse;

        public List<StatsResponse> getStatsResponses() {
            return statsResponse;
        }

        public StatsResponses() {
            this(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        public StatsResponses(
                final List<TaskOperationFailure> taskFailures,
                final List<? extends FailedNodeException> nodeFailures,
                final List<StatsResponse> statsResponse) {
            super(taskFailures, nodeFailures);
            this.statsResponse = statsResponse;
        }

        @Override
        public XContentBuilder toXContent(final XContentBuilder builder, final Params params) throws IOException {
            // sort by index name, then shard ID
            final Map<String, Map<Integer, StatsResponse>> taskResponsesByIndex = new TreeMap<>();
            for (final StatsResponse statsResponse : statsResponse) {
                taskResponsesByIndex.computeIfAbsent(
                        statsResponse.status().followerIndex(),
                        k -> new TreeMap<>()).put(statsResponse.status().getShardId(), statsResponse);
            }
            builder.startObject();
            {
                for (final Map.Entry<String, Map<Integer, StatsResponse>> index : taskResponsesByIndex.entrySet()) {
                    builder.startArray(index.getKey());
                    {
                        for (final Map.Entry<Integer, StatsResponse> shard : index.getValue().entrySet()) {
                            shard.getValue().status().toXContent(builder, params);
                        }
                    }
                    builder.endArray();
                }
            }
            builder.endObject();
            return builder;
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            super.readFrom(in);
            statsResponse = in.readList(StatsResponse::new);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeList(statsResponse);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StatsResponses that = (StatsResponses) o;
            return Objects.equals(statsResponse, that.statsResponse);
        }

        @Override
        public int hashCode() {
            return Objects.hash(statsResponse);
        }
    }

    public static class StatsRequest extends BaseTasksRequest<StatsRequest> implements IndicesRequest {

        private String[] indices;

        @Override
        public String[] indices() {
            return indices;
        }

        public void setIndices(final String[] indices) {
            this.indices = indices;
        }

        @Override
        public IndicesOptions indicesOptions() {
            return IndicesOptions.strictExpand();
        }

        @Override
        public boolean match(final Task task) {
            /*
             * This is a limitation of the current tasks API. When the transport action is executed, the tasks API invokes this match method
             * to find the tasks on which to execute the task-level operation (see TransportTasksAction#nodeOperation and
             * TransportTasksAction#processTasks). If we do the matching here, then we can not match index patterns. Therefore, we override
             * TransportTasksAction#processTasks (see TransportCcrStatsAction#processTasks) and do the matching there. We should never see
             * this method invoked and since we can not support matching a task on the basis of the request here, we throw that this
             * operation is unsupported.
             */
            throw new UnsupportedOperationException();
        }

        @Override
        public ActionRequestValidationException validate() {
            return null;
        }

        @Override
        public void readFrom(final StreamInput in) throws IOException {
            super.readFrom(in);
            indices = in.readOptionalStringArray();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeOptionalStringArray(indices);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StatsRequest that = (StatsRequest) o;
            return Arrays.equals(indices, that.indices);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(indices);
        }
    }

    public static class StatsResponse implements Writeable {

        private final ShardFollowNodeTaskStatus status;

        public ShardFollowNodeTaskStatus status() {
            return status;
        }

        public StatsResponse(final ShardFollowNodeTaskStatus status) {
            this.status = status;
        }

        public StatsResponse(final StreamInput in) throws IOException {
            this.status = new ShardFollowNodeTaskStatus(in);
        }

        @Override
        public void writeTo(final StreamOutput out) throws IOException {
            status.writeTo(out);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StatsResponse that = (StatsResponse) o;
            return Objects.equals(status, that.status);
        }

        @Override
        public int hashCode() {
            return Objects.hash(status);
        }
    }

    public static class StatsRequestBuilder extends ActionRequestBuilder<StatsRequest, StatsResponses, StatsRequestBuilder> {

        public StatsRequestBuilder(
                final ElasticsearchClient client,
                final Action<StatsRequest, StatsResponses, StatsRequestBuilder> action) {
            super(client, action, new StatsRequest());
        }

    }

    @Override
    public StatsRequestBuilder newRequestBuilder(final ElasticsearchClient client) {
        return new StatsRequestBuilder(client, this);
    }

}
