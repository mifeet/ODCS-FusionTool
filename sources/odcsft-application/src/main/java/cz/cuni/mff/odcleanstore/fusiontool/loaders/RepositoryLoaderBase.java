/**
 * 
 */
package cz.cuni.mff.odcleanstore.fusiontool.loaders;

import cz.cuni.mff.odcleanstore.fusiontool.config.LDFTConfigConstants;
import cz.cuni.mff.odcleanstore.fusiontool.config.SparqlRestriction;
import cz.cuni.mff.odcleanstore.fusiontool.config.SparqlRestrictionImpl;
import cz.cuni.mff.odcleanstore.fusiontool.source.Source;
import cz.cuni.mff.odcleanstore.fusiontool.util.LDFusionToolUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Jan Michelfeit
 */
public abstract class RepositoryLoaderBase {
    /**
     * SPARQL query BASE declaration.
     */
    private static final Pattern BASE_PATTERN = Pattern.compile("^\\s*BASE\\s+<[^>]+>"); // TODO: replace with query parser

    /**
     * Maximum number of values in a generated argument for the "?var IN (...)" SPARQL construct .
     */
    protected static final int MAX_QUERY_LIST_LENGTH = LDFTConfigConstants.MAX_QUERY_LIST_LENGTH;

    /**
     * A random prefix for variables used in SPARQL queries so that they don't conflict
     * with variables used in named graph constraint pattern.
     */
    protected static final String VAR_PREFIX = "afdc1ea803_";
    
    /**
     * An empty restriction. 
     * To be used when no other restriction is given. 
     * Variable name is a random string to avoid conflicts.
     */
    protected static final SparqlRestriction EMPTY_RESTRICTION = new SparqlRestrictionImpl("", "308ae1cdfa_x");

    /** RDF data source. */
    protected final Source source;
    
    /** Cached value returned by {@link #getPrefixDecl()}. */
    private String cachedPrefixDecl;

    /**
     * Creates a new instance.
     * @param source an initialized data source
     */
    protected RepositoryLoaderBase(Source source) {
        this.source = source;
    }
    
    /**
     * Returns a SPARQL snippet with namespace prefix declarations.
     * @return SPARQL query snippet
     */
    protected String getPrefixDecl() {
        if (cachedPrefixDecl == null) {
            cachedPrefixDecl = LDFusionToolUtils.buildPrefixDecl(source.getPrefixes());
        }
        return cachedPrefixDecl;
    }

    /**
     * Adds prefix declarations to the given SPARQL query.
     * @param query query to add declarations to
     * @return input query with added prefix declarations
     */
    protected String addPrefixDecl(String query) {
        String prefixDecl = getPrefixDecl();

        Matcher baseMatcher = BASE_PATTERN.matcher(query);
        if (baseMatcher.find()) {
            String base = baseMatcher.group();
            String subQuery = query.substring(base.length());
            return base + prefixDecl + subQuery;
        } else {
            return prefixDecl + query;
        }
    }
}
