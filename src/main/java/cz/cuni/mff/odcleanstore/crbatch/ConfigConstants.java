/**
 * 
 */
package cz.cuni.mff.odcleanstore.crbatch;

/**
 * Temporary class with configuration constants.
 * 
 * @author Jan Michelfeit
 * @todo replace
 */ 
public final class ConfigConstants {
    /** Disable constructor for a utility class. */
    private ConfigConstants() {
    }

    /**
     * Coefficient used in quality computation formula. Value N means that (N+1)
     * sources with score 1 that agree on the result will increase the result
     * quality to 1.
     */
    public static final double AGREE_COEFFICIENT = 4;
    
    /**
     * Graph score used if none is given in the input.
     */
    public static final double SCORE_IF_UNKNOWN = 1;
    
    /**
     * Weight of the named graph score.
     */
    public static final double NAMED_GRAPH_SCORE_WEIGHT = 0.8;
    
    /**
     * Weight of the publisher score.
     */
    public static final double PUBLISHER_SCORE_WEIGHT = 0.2;
    
    /**
     * Difference between two dates when their distance is equal to MAX_DISTANCE in seconds.
     * 31622400 s ~ 366 days
     */
    public static final long MAX_DATE_DIFFERENCE = 31622400;
    
    /**
     * Default timeout for database queries in seconds.
     * Zero means no timeout.
     */
    public static final int DEFAULT_QUERY_TIMEOUT = 120;
}
