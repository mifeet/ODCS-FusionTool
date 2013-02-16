package cz.cuni.mff.odcleanstore.crbatch.loaders;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.cuni.mff.odcleanstore.connection.WrappedResultSet;
import cz.cuni.mff.odcleanstore.connection.exceptions.DatabaseException;
import cz.cuni.mff.odcleanstore.connection.exceptions.QueryException;
import cz.cuni.mff.odcleanstore.crbatch.ConfigConstants;
import cz.cuni.mff.odcleanstore.crbatch.ConnectionFactory;
import cz.cuni.mff.odcleanstore.crbatch.exceptions.CRBatchErrorCodes;
import cz.cuni.mff.odcleanstore.crbatch.exceptions.CRBatchException;
import cz.cuni.mff.odcleanstore.crbatch.urimapping.AlternativeURINavigator;
import cz.cuni.mff.odcleanstore.queryexecution.impl.QueryExecutionHelper;
import cz.cuni.mff.odcleanstore.vocabulary.ODCS;
import de.fuberlin.wiwiss.ng4j.Quad;

/**
 * Loads triples containing statements about a given URI resource (having the URI as their subject)
 * from payload graphs matching the given named graph constraint pattern, taking into consideration
 * given owl:sameAs alternatives.
 * @author Jan Michelfeit
 */
public class QuadLoader extends DatabaseLoaderBase {
    private static final Logger LOG = LoggerFactory.getLogger(QuadLoader.class);

    /**
     * SPARQL query that gets all quads having the given uri as their subject from
     * from relevant payload graph and attached graphs.
     * Variable ?{@value ConfigConstants#NG_CONSTRAINT_VAR} represents a relevant payload graph.
     * This query is to be used when there are no owl:sameAs alternatives for the given URI.
     * 
     * Must be formatted with arguments:
     * (1) named graph constraint pattern
     * (2) graph name prefix filter
     * (3) searched uri
     */
    private static final String QUADS_QUERY_SIMPLE = "SPARQL"
            + "\n SELECT ?" + VAR_PREFIX + "g  <%3$s> AS ?" + VAR_PREFIX + "s ?" + VAR_PREFIX + "p ?" + VAR_PREFIX + "o"
            + "\n WHERE {"
            + "\n   {"
            + "\n     SELECT DISTINCT "
            + "\n       ?" + ConfigConstants.NG_CONSTRAINT_VAR + " AS ?" + VAR_PREFIX + "g"
            + "\n       ?" + VAR_PREFIX + "p ?" + VAR_PREFIX + "o"
            + "\n     WHERE {"
            + "\n       GRAPH ?" + ConfigConstants.NG_CONSTRAINT_VAR + " {"
            + "\n         <%3$s> ?" + VAR_PREFIX + "p ?" + VAR_PREFIX + "o"
            + "\n       }"
            + "\n       %1$s"
            + "\n       ?" + ConfigConstants.NG_CONSTRAINT_VAR + " <" + ODCS.metadataGraph + "> ?" + VAR_PREFIX + "metadataGraph."
            + "\n       %2$s"
            + "\n     }"
            + "\n   }"
            + "\n   UNION"
            + "\n   {"
            + "\n     SELECT DISTINCT"
            + "\n       ?" + VAR_PREFIX + "attachedGraph AS ?" + VAR_PREFIX + "g ?" + VAR_PREFIX + "p ?" + VAR_PREFIX + "o"
            + "\n     WHERE {"
            + "\n       %1$s"
            + "\n       ?" + ConfigConstants.NG_CONSTRAINT_VAR + " <" + ODCS.metadataGraph + "> ?" + VAR_PREFIX + "metadataGraph."
            + "\n       ?" + ConfigConstants.NG_CONSTRAINT_VAR + " <" + ODCS.attachedGraph + "> ?" + VAR_PREFIX + "attachedGraph."
            + "\n       %2$s"
            + "\n       GRAPH ?" + VAR_PREFIX + "attachedGraph {"
            + "\n         <%3$s> ?" + VAR_PREFIX + "p ?" + VAR_PREFIX + "o"
            + "\n       }"
            + "\n     }"
            + "\n   }"
            + "\n }";

    /**
     * SPARQL query that gets all quads having one of the given URIs as their subject from
     * from relevant payload graph and attached graphs.
     * Variable ?{@value ConfigConstants#NG_CONSTRAINT_VAR} represents a relevant payload graph.
     * This query is to be used when there are multiple owl:sameAs alternatives.
     * 
     * Must be formatted with arguments:
     * (1) named graph constraint pattern
     * (2) graph name prefix filter
     * (3) list of searched URIs (e.g. "<uri1>,<uri2>,<uri3>")
     */
    private static final String QUADS_QUERY_ALTERNATIVE = "SPARQL"
            + "\n SELECT ?" + VAR_PREFIX + "g ?" + VAR_PREFIX + "s ?" + VAR_PREFIX + "p ?" + VAR_PREFIX + "o"
            + "\n WHERE {"
            + "\n   {"
            + "\n     SELECT DISTINCT "
            + "\n       ?" + ConfigConstants.NG_CONSTRAINT_VAR + " AS ?" + VAR_PREFIX + "g"
            + "\n       ?" + VAR_PREFIX + "s ?" + VAR_PREFIX + "p ?" + VAR_PREFIX + "o"
            + "\n     WHERE {"
            + "\n       %1$s"
            + "\n       ?" + ConfigConstants.NG_CONSTRAINT_VAR + " <" + ODCS.metadataGraph + "> ?" + VAR_PREFIX + "metadataGraph."
            + "\n       %2$s"
            + "\n       GRAPH ?" + ConfigConstants.NG_CONSTRAINT_VAR + " {"
            + "\n         ?" + VAR_PREFIX + "s ?" + VAR_PREFIX + "p ?" + VAR_PREFIX + "o"
            + "\n         FILTER (?" + VAR_PREFIX + "s IN (%3$s))"
            + "\n       }"
            + "\n     }"
            + "\n   }"
            + "\n   UNION"
            + "\n   {"
            + "\n     SELECT DISTINCT"
            + "\n       ?" + VAR_PREFIX + "attachedGraph AS ?" + VAR_PREFIX + "g"
            + "\n       ?" + VAR_PREFIX + "s ?" + VAR_PREFIX + "p ?" + VAR_PREFIX + "o"
            + "\n     WHERE {"
            + "\n       %1$s"
            + "\n       ?" + ConfigConstants.NG_CONSTRAINT_VAR + " <" + ODCS.metadataGraph + "> ?" + VAR_PREFIX + "metadataGraph."
            + "\n       ?" + ConfigConstants.NG_CONSTRAINT_VAR + " <" + ODCS.attachedGraph + "> ?" + VAR_PREFIX + "attachedGraph."
            + "\n       %2$s"
            + "\n       GRAPH ?" + VAR_PREFIX + "attachedGraph {"
            + "\n         ?" + VAR_PREFIX + "s ?" + VAR_PREFIX + "p ?" + VAR_PREFIX + "o"
            + "\n         FILTER (?" + VAR_PREFIX + "s IN (%3$s))"
            + "\n       }"
            + "\n     }"
            + "\n   }"
            + "\n }";

    private final String namedGraphConstraintPattern;

    private final AlternativeURINavigator alternativeURINavigator;

    /**
     * Creates a new instance.
     * @param connectionFactory factory for database connection
     * @param namedGraphConstraintPattern SPARQL group graph pattern limiting source payload named graphs
     *        (where ?{@value ConfigConstants#NG_CONSTRAINT_VAR} represents the payload graph)
     * @param alternativeURINavigator container of alternative owl:sameAs variants for URIs
     */
    public QuadLoader(ConnectionFactory connFactory, String namedGraphConstraintPattern,
            AlternativeURINavigator alternativeURINavigator) {
        super(connFactory);
        this.namedGraphConstraintPattern = namedGraphConstraintPattern;
        this.alternativeURINavigator = alternativeURINavigator;
    }

    /**
     * Returns quads having the given uri or one of its owl:sameAs alternatives as their subject.
     * Triples are loaded from payload graphs matching the given named graph constraint pattern
     * and from their attached graphs.
     * @return collection of quads having uri as their subject
     */
    public Collection<Quad> getQuadsForURI(String uri) throws CRBatchException {
        long startTime = System.currentTimeMillis();
        ArrayList<Quad> result = new ArrayList<Quad>();
        try {

            List<String> alternativeURIs = alternativeURINavigator.listAlternativeURIs(uri);
            if (alternativeURIs.size() <= 1) {
                String query = String.format(Locale.ROOT, QUADS_QUERY_SIMPLE,
                        namedGraphConstraintPattern,
                        LoaderUtils.getGraphPrefixFilter(ConfigConstants.NG_CONSTRAINT_VAR), 
                        uri);
                addQuadsFromQuery(query, result);
            } else {
                Iterable<CharSequence> limitedURIListBuilder = QueryExecutionHelper.getLimitedURIListBuilder(alternativeURIs,
                        MAX_QUERY_LIST_LENGTH);
                for (CharSequence uriList : limitedURIListBuilder) {
                    String query = String.format(Locale.ROOT, QUADS_QUERY_ALTERNATIVE,
                            namedGraphConstraintPattern,
                            LoaderUtils.getGraphPrefixFilter(ConfigConstants.NG_CONSTRAINT_VAR),
                            uriList);
                    addQuadsFromQuery(query, result);
                }
            }

        } catch (DatabaseException e) {
            throw new CRBatchException(CRBatchErrorCodes.QUERY_QUADS, "Database error", e);
        } finally {
            closeConnectionQuietly();
        }

        LOG.trace("CR-batch: Loaded quads for URI {} in {} ms", uri, System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * Execute the given SPARQL SELECT and constructs a collection of quads from the result.
     * The query must contain four variables in the result, exactly in this order: named graph, subject,
     * property, object
     * @param sparqlQuery a SPARQL SELECT query with four variables in the result: named graph, subject,
     *        property, object (exactly in this order).
     * @param quads collection where the retrieved quads are added
     * @throws DatabaseException database error
     */
    private void addQuadsFromQuery(String sparqlQuery, Collection<Quad> quads) throws DatabaseException {
        long startTime = System.currentTimeMillis();
        WrappedResultSet resultSet = getConnection().executeSelect(sparqlQuery);
        LOG.trace("CR-batch: Quads query took {} ms", System.currentTimeMillis() - startTime);
        try {
            while (resultSet.next()) {
                // CHECKSTYLE:OFF
                Quad quad = new Quad(
                        resultSet.getNode(1),
                        resultSet.getNode(2),
                        resultSet.getNode(3),
                        resultSet.getNode(4));
                quads.add(quad);
                // CHECKSTYLE:ON
            }
        } catch (SQLException e) {
            throw new QueryException(e);
        } finally {
            resultSet.closeQuietly();
        }
    }
}