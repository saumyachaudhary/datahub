package com.linkedin.datahub.graphql.types.datajob;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import com.linkedin.common.urn.CorpuserUrn;
import com.linkedin.common.urn.DataJobUrn;
import com.linkedin.common.urn.Urn;
import com.linkedin.data.template.StringArray;
import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.datahub.graphql.authorization.AuthorizationUtils;
import com.linkedin.datahub.graphql.authorization.ConjunctivePrivilegeGroup;
import com.linkedin.datahub.graphql.authorization.DisjunctivePrivilegeGroup;
import com.linkedin.metadata.authorization.PoliciesConfig;
import com.linkedin.datahub.graphql.exception.AuthorizationException;
import com.linkedin.datahub.graphql.generated.AutoCompleteResults;
import com.linkedin.datahub.graphql.generated.BrowsePath;
import com.linkedin.datahub.graphql.generated.BrowseResults;
import com.linkedin.datahub.graphql.generated.DataJob;
import com.linkedin.datahub.graphql.generated.EntityType;
import com.linkedin.datahub.graphql.generated.FacetFilterInput;
import com.linkedin.datahub.graphql.generated.SearchResults;
import com.linkedin.datahub.graphql.resolvers.ResolverUtils;
import com.linkedin.datahub.graphql.types.BrowsableEntityType;
import com.linkedin.datahub.graphql.types.SearchableEntityType;
import com.linkedin.datahub.graphql.types.datajob.mappers.DataJobSnapshotMapper;
import com.linkedin.datahub.graphql.types.datajob.mappers.DataJobUpdateInputSnapshotMapper;
import com.linkedin.datahub.graphql.types.mappers.AutoCompleteResultsMapper;
import com.linkedin.datahub.graphql.generated.DataJobUpdateInput;
import com.linkedin.datahub.graphql.types.MutableType;
import com.linkedin.datahub.graphql.types.mappers.BrowsePathsMapper;
import com.linkedin.datahub.graphql.types.mappers.BrowseResultMapper;
import com.linkedin.datahub.graphql.types.mappers.UrnSearchResultsMapper;
import com.linkedin.entity.client.EntityClient;
import com.linkedin.entity.Entity;
import com.linkedin.metadata.extractor.AspectExtractor;
import com.linkedin.metadata.browse.BrowseResult;
import com.linkedin.metadata.query.AutoCompleteResult;
import com.linkedin.metadata.query.SearchResult;
import com.linkedin.metadata.snapshot.DataJobSnapshot;
import com.linkedin.metadata.snapshot.Snapshot;
import graphql.execution.DataFetcherResult;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.linkedin.r2.RemoteInvocationException;

import static com.linkedin.datahub.graphql.Constants.BROWSE_PATH_DELIMITER;


public class DataJobType implements SearchableEntityType<DataJob>, BrowsableEntityType<DataJob>, MutableType<DataJobUpdateInput> {

    private static final Set<String> FACET_FIELDS = ImmutableSet.of("flow");
    private final EntityClient _dataJobsClient;

    public DataJobType(final EntityClient dataJobsClient) {
        _dataJobsClient = dataJobsClient;
    }

    @Override
    public EntityType type() {
        return EntityType.DATA_JOB;
    }

    @Override
    public Class<DataJob> objectClass() {
        return DataJob.class;
    }

    @Override
    public Class<DataJobUpdateInput> inputClass() {
        return DataJobUpdateInput.class;
    }

    @Override
    public List<DataFetcherResult<DataJob>> batchLoad(final List<String> urns, final QueryContext context) throws Exception {
        final List<DataJobUrn> dataJobUrns = urns.stream()
            .map(this::getDataJobUrn)
            .collect(Collectors.toList());

        try {
            final Map<Urn, Entity> dataJobMap = _dataJobsClient.batchGet(dataJobUrns
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()),
            context.getActor());

            final List<Entity> gmsResults = dataJobUrns.stream()
                .map(jobUrn -> dataJobMap.getOrDefault(jobUrn, null)).collect(Collectors.toList());

            return gmsResults.stream()
                .map(gmsDataJob -> gmsDataJob == null ? null
                    : DataFetcherResult.<DataJob>newResult()
                        .data(DataJobSnapshotMapper.map(gmsDataJob.getValue().getDataJobSnapshot()))
                        .localContext(AspectExtractor.extractAspects(gmsDataJob.getValue().getDataJobSnapshot()))
                        .build())
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to batch load DataJobs", e);
        }
    }

    @Override
    public SearchResults search(@Nonnull String query,
                                @Nullable List<FacetFilterInput> filters,
                                int start,
                                int count,
                                @Nonnull final QueryContext context) throws Exception {
        final Map<String, String> facetFilters = ResolverUtils.buildFacetFilters(filters, FACET_FIELDS);
        final SearchResult searchResult = _dataJobsClient.search(
            "dataJob", query, facetFilters, start, count, context.getActor());
        return UrnSearchResultsMapper.map(searchResult);
    }

    @Override
    public AutoCompleteResults autoComplete(@Nonnull String query,
                                            @Nullable String field,
                                            @Nullable List<FacetFilterInput> filters,
                                            int limit,
                                            @Nonnull final QueryContext context) throws Exception {
        final Map<String, String> facetFilters = ResolverUtils.buildFacetFilters(filters, FACET_FIELDS);
        final AutoCompleteResult result = _dataJobsClient.autoComplete("dataJob", query, facetFilters, limit, context.getActor());
        return AutoCompleteResultsMapper.map(result);
    }

    private DataJobUrn getDataJobUrn(String urnStr) {
        try {
            return DataJobUrn.createFromString(urnStr);
        } catch (URISyntaxException e) {
            throw new RuntimeException(String.format("Failed to retrieve datajob with urn %s, invalid urn", urnStr));
        }
    }

    @Override
    public BrowseResults browse(@Nonnull List<String> path, @Nullable List<FacetFilterInput> filters, int start,
        int count, @Nonnull QueryContext context) throws Exception {
                final Map<String, String> facetFilters = ResolverUtils.buildFacetFilters(filters, FACET_FIELDS);
        final String pathStr = path.size() > 0 ? BROWSE_PATH_DELIMITER + String.join(BROWSE_PATH_DELIMITER, path) : "";
        final BrowseResult result = _dataJobsClient.browse(
            "dataJob",
                pathStr,
                facetFilters,
                start,
                count,
            context.getActor());
        return BrowseResultMapper.map(result);
    }

    @Override
    public List<BrowsePath> browsePaths(@Nonnull String urn, @Nonnull QueryContext context) throws Exception {
        final StringArray result = _dataJobsClient.getBrowsePaths(DataJobUrn.createFromString(urn), context.getActor());
        return BrowsePathsMapper.map(result);
    }

    @Override
    public DataJob update(@Nonnull DataJobUpdateInput input, @Nonnull QueryContext context) throws Exception {
        if (isAuthorized(input, context)) {
            final CorpuserUrn actor = CorpuserUrn.createFromString(context.getActor());
            final DataJobSnapshot dataJobSnapshot = DataJobUpdateInputSnapshotMapper.map(input, actor);
            final Snapshot snapshot = Snapshot.create(dataJobSnapshot);

            try {
                Entity entity = new Entity();
                entity.setValue(snapshot);
                _dataJobsClient.update(entity, context.getActor());
            } catch (RemoteInvocationException e) {
                throw new RuntimeException(String.format("Failed to write entity with urn %s", input.getUrn()), e);
            }

            return load(input.getUrn(), context).getData();
        }
        throw new AuthorizationException("Unauthorized to perform this action. Please contact your DataHub administrator.");
    }

    private boolean isAuthorized(@Nonnull DataJobUpdateInput update, @Nonnull QueryContext context) {
        // Decide whether the current principal should be allowed to update the Dataset.
        final DisjunctivePrivilegeGroup orPrivilegeGroups = getAuthorizedPrivileges(update);
        return AuthorizationUtils.isAuthorized(
            context.getAuthorizer(),
            context.getActor(),
            PoliciesConfig.DATA_JOB_PRIVILEGES.getResourceType(),
            update.getUrn(),
            orPrivilegeGroups);
    }

    private DisjunctivePrivilegeGroup getAuthorizedPrivileges(final DataJobUpdateInput updateInput) {

        final ConjunctivePrivilegeGroup allPrivilegesGroup = new ConjunctivePrivilegeGroup(ImmutableList.of(
            PoliciesConfig.EDIT_ENTITY_PRIVILEGE.getType()
        ));

        List<String> specificPrivileges = new ArrayList<>();
        if (updateInput.getOwnership() != null) {
            specificPrivileges.add(PoliciesConfig.EDIT_ENTITY_OWNERS_PRIVILEGE.getType());
        }
        if (updateInput.getEditableProperties() != null) {
            specificPrivileges.add(PoliciesConfig.EDIT_ENTITY_DOCS_PRIVILEGE.getType());
        }
        if (updateInput.getGlobalTags() != null) {
            specificPrivileges.add(PoliciesConfig.EDIT_ENTITY_TAGS_PRIVILEGE.getType());
        }
        final ConjunctivePrivilegeGroup specificPrivilegeGroup = new ConjunctivePrivilegeGroup(specificPrivileges);

        // If you either have all entity privileges, or have the specific privileges required, you are authorized.
        return new DisjunctivePrivilegeGroup(ImmutableList.of(
            allPrivilegesGroup,
            specificPrivilegeGroup
        ));
    }
}
