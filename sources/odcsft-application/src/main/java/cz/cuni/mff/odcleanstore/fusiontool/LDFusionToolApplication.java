package cz.cuni.mff.odcleanstore.fusiontool;

import cz.cuni.mff.odcleanstore.conflictresolution.exceptions.ConflictResolutionException;
import cz.cuni.mff.odcleanstore.core.ODCSUtils;
import cz.cuni.mff.odcleanstore.fusiontool.config.*;
import cz.cuni.mff.odcleanstore.fusiontool.conflictresolution.ResourceDescriptionConflictResolver;
import cz.cuni.mff.odcleanstore.fusiontool.exceptions.InvalidInputException;
import cz.cuni.mff.odcleanstore.fusiontool.exceptions.LDFusionToolException;
import cz.cuni.mff.odcleanstore.fusiontool.util.EnumFusionCounters;
import cz.cuni.mff.odcleanstore.fusiontool.util.MemoryProfiler;
import cz.cuni.mff.odcleanstore.fusiontool.util.ProfilingTimeCounter;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.simpleframework.xml.core.PersistenceException;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

/**
 * The main entry point of the application.
 * @author Jan Michelfeit
 */
public final class LDFusionToolApplication {

    private static String getUsage() {
        //return "Usage:\n java -jar odcs-fusion-tool-<version>.jar [--verbose] [--profile] [--only-conflicts] [--only-mapped] <xml config file>"
        //        + "\n\n  It is recommended to run java with both -Xmx and -Xms options to allocate\n  enough memory if processing large input.";
        return "Usage:\n java -jar odcs-fusion-tool-<version>.jar [-v|-vv] [--profile] [--only-mapped] <xml config file>"
                + "\n\n  It is recommended to run java with BOTH -Xmx and -Xms options to allocate\n  enough memory if processing large input.";
    }

    /**
     * Main application entry point.
     * @param args command line arguments
     */
    public static void main(String[] args) {
        ApplicationArgs parsedArgs;
        try {
            parsedArgs = parseArgs(args);
        } catch (InvalidInputException e) {
            System.err.println(getUsage());
            return;
        }

        setLogLevel(parsedArgs.getVerboseLevel());

        File configFile = new File(parsedArgs.getConfigFilePath());
        if (!configFile.isFile() || !configFile.canRead()) {
            System.err.println("Cannot read the given config file.\n");
            System.err.println(getUsage());
            return;
        }

        Config config;
        try {
            config = ConfigReader.parseConfigXml(configFile);
            ((ConfigImpl) config).setProfilingOn(parsedArgs.isProfilingOn());
            //((ConfigImpl) config).setOutputConflictsOnly(parsedArgs.getOutputConflictsOnly());
            ((ConfigImpl) config).setOutputMappedSubjectsOnly(parsedArgs.getOutputMappedSubjectsOnly());
            checkValidInput(config);
        } catch (InvalidInputException e) {
            System.err.println("Error in config file:");
            System.err.println("  " + e.getMessage());
            if (e.getCause() instanceof PersistenceException) {
                System.err.println("  " + e.getCause().getMessage());
            }
            e.printStackTrace();
            return;
        }

        long startTime = System.currentTimeMillis();
        System.out.println("Starting conflict resolution, this may take a while... \n");

        try {

            LDFusionToolComponentFactory componentFactory = new LDFusionToolComponentFactory(config);
            FusionRunner runner = new FusionRunner(componentFactory);
            runner.setProfilingOn(config.isProfilingOn());
            runner.runFusionTool();

            printProfilingInformation(config, componentFactory, runner);

        } catch (LDFusionToolException e) {
            System.err.println("Error:");
            System.err.println("  " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("  " + e.getCause().getMessage());
            }
            e.printStackTrace(System.err);
            return;
        } catch (ConflictResolutionException e) {
            System.err.println("Conflict resolution error:");
            System.err.println("  " + e.getMessage());
            e.printStackTrace(System.err);
            return;
        } catch (IOException e) {
            System.err.println("Error when writing results:");
            System.err.println("  " + e.getMessage());
            e.printStackTrace(System.err);
            return;
        }

        System.out.println("--------------------------------");
        System.out.printf("ODCS-FusionTool executed in %s\n", formatRunTime(System.currentTimeMillis() - startTime));
    }

    private static void setLogLevel(ApplicationArgs.VerboseLevel verboseLevel) {
        Level logLevel;
        switch (verboseLevel) {
            case VERBOSE:
                logLevel = Level.DEBUG;
                break;
            case VERY_VERBOSE:
                logLevel = Level.TRACE;
                break;
            default:
                logLevel = Level.INFO;
                break;
        }
        LogManager.getLogger(LDFusionToolApplication.class.getPackage().getName()).setLevel(logLevel);
        LogManager.getLogger(ResourceDescriptionConflictResolver.class.getPackage().getName()).setLevel(logLevel);
    }

    private static ApplicationArgs parseArgs(String[] args) throws InvalidInputException {
        if (args == null) {
            throw new InvalidInputException("Missing command line arguments");
        }

        ApplicationArgs.VerboseLevel verboseLevel = ApplicationArgs.VerboseLevel.NOT_VERBOSE;
        boolean outputConflictsOnly = false;
        boolean outputMappedSubjectsOnly = false;
        boolean profile = false;
        String configFilePath = null;
        for (String arg : args) {
            switch (arg) {
                case "-v":
                    verboseLevel = ApplicationArgs.VerboseLevel.VERBOSE;
                    break;
                case "-vv":
                case "--verbose":
                    verboseLevel = ApplicationArgs.VerboseLevel.VERY_VERBOSE;
                    break;
                case "--profile":
                    profile = true;
                    break;
                case "--only-conflicts":
                    outputConflictsOnly = true;
                    break;
                case "--only-mapped":
                    outputMappedSubjectsOnly = true;
                    break;
                default:
                    configFilePath = arg;
                    break;
            }
        }
        if (configFilePath == null) {
            throw new InvalidInputException("Missing config file argument");
        }
        return new ApplicationArgs(verboseLevel, profile, configFilePath, outputConflictsOnly, outputMappedSubjectsOnly);
    }

    private static void checkValidInput(Config config) throws InvalidInputException {
        if (!ODCSUtils.isValidIRI(config.getResultDataURIPrefix())) {
            throw new InvalidInputException("Result data URI prefix must be a valid URI, '" + config.getResultDataURIPrefix()
                    + "' given");
        }
        for (Map.Entry<String, String> prefixEntry : config.getPrefixes().entrySet()) {
            if (!prefixEntry.getKey().isEmpty() && !ODCSUtils.isValidNamespacePrefix(prefixEntry.getKey())) {
                throw new InvalidInputException("Invalid namespace prefix '" + prefixEntry.getKey() + "'");
            }
            if (!prefixEntry.getValue().isEmpty() && !ODCSUtils.isValidIRI(prefixEntry.getValue())) {
                throw new InvalidInputException("Invalid namespace prefix definition for URI '" + prefixEntry.getValue() + "'");
            }
        }

        // Check data Source settings
        if (config.getDataSources() == null || config.getDataSources().isEmpty()) {
            throw new InvalidInputException("There must be at least one DataSource specified");
        }
        for (DataSourceConfig dataSourceConfig : config.getDataSources()) {
            checkDataSourceValidInput(dataSourceConfig);
        }

        for (ConstructSourceConfig sourceConfig : config.getSameAsSources()) {
            checkConstructSourceValidInput(sourceConfig);
        }
        for (ConstructSourceConfig sourceConfig : config.getMetadataSources()) {
            checkConstructSourceValidInput(sourceConfig);
        }

        // Check output settings
        for (Output output : config.getOutputs()) {
            if (output.getType() == EnumOutputType.FILE && output.getParams().get(ConfigParameters.OUTPUT_PATH) != null) {
                File fileLocation = new File(output.getParams().get(ConfigParameters.OUTPUT_PATH));
                if (fileLocation.exists() && !fileLocation.canWrite()) {
                    throw new InvalidInputException("Cannot write to output file " + fileLocation.getPath());
                }
            }
        }

        // intentionally do not check canonical URI files
    }

    private static void checkConstructSourceValidInput(ConstructSourceConfig sourceConfig) throws InvalidInputException {
        checkSourceValidInput(sourceConfig);
    }

    private static void checkDataSourceValidInput(DataSourceConfig dataSourceConfig) throws InvalidInputException {
        checkSourceValidInput(dataSourceConfig);

        if (!ODCSUtils.isValidSparqlVar(dataSourceConfig.getNamedGraphRestriction().getVar())) {
            throw new InvalidInputException(
                    "Variable name specified in source graphs restriction must be a valid SPARQL identifier, '"
                            + dataSourceConfig.getNamedGraphRestriction().getVar()
                            + "' given for data source " + dataSourceConfig
            );
        }
    }

    private static void checkSourceValidInput(SourceConfig sourceConfig) throws InvalidInputException {
        switch (sourceConfig.getType()) {
            case VIRTUOSO:
                checkRequiredDataSourceParam(sourceConfig,
                        ConfigParameters.DATA_SOURCE_VIRTUOSO_HOST,
                        ConfigParameters.DATA_SOURCE_VIRTUOSO_PORT,
                        ConfigParameters.DATA_SOURCE_VIRTUOSO_USERNAME,
                        ConfigParameters.DATA_SOURCE_VIRTUOSO_PASSWORD);
                break;
            case SPARQL:
                checkRequiredDataSourceParam(sourceConfig, ConfigParameters.DATA_SOURCE_SPARQL_ENDPOINT);
                break;
            case FILE:
                checkRequiredDataSourceParam(sourceConfig, ConfigParameters.DATA_SOURCE_FILE_PATH);
                File file = new File(sourceConfig.getParams().get(ConfigParameters.DATA_SOURCE_FILE_PATH));
                if (!file.isFile() || !file.canRead()) {
                    throw new InvalidInputException("Cannot read input file " + sourceConfig.getParams().get(ConfigParameters.DATA_SOURCE_FILE_PATH));
                }
                break;
            default:
                throw new InvalidInputException("Unsupported type of data source: " + sourceConfig.getType());
        }
    }

    private static void checkRequiredDataSourceParam(SourceConfig dataSourceConfig, String... requiredParams)
            throws InvalidInputException {

        for (String requiredParam : requiredParams) {
            if (dataSourceConfig.getParams().get(requiredParam) == null) {
                throw new InvalidInputException("Missing required parameter '" + requiredParam
                        + "' for data source " + dataSourceConfig);
            }
        }
    }

    private static String formatRunTime(long runTime) {
        final long hourMs = ODCSUtils.MILLISECONDS * ODCSUtils.TIME_UNIT_60 * ODCSUtils.TIME_UNIT_60;
        DateFormat timeFormat = new SimpleDateFormat("mm:ss.SSS");
        timeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return String.format("%d:%s",
                runTime / hourMs,
                timeFormat.format(new Date(runTime)));
    }

    private static void printProfilingInformation(Config config, LDFusionToolComponentFactory componentFactory, FusionRunner runner) {
        if (config.isProfilingOn()) {
            ProfilingTimeCounter<EnumFusionCounters> timeProfiler = runner.getTimeProfiler();
            timeProfiler.addProfilingTimeCounter(componentFactory.getExecutorTimeProfiler());
            MemoryProfiler memoryProfiler = componentFactory.getExecutorMemoryProfiler();

            System.out.println("-- Profiling information --------");
            System.out.println("Initialization time:              " + timeProfiler.formatCounter(EnumFusionCounters.INITIALIZATION));
            System.out.println("Reading metadata & sameAs links:  " + timeProfiler.formatCounter(EnumFusionCounters.META_INITIALIZATION));
            System.out.println("Data preparation time:            " + timeProfiler.formatCounter(EnumFusionCounters.DATA_INITIALIZATION));
            System.out.println("Quad loading time:                " + timeProfiler.formatCounter(EnumFusionCounters.QUAD_LOADING));
            System.out.println("Input filtering time:             " + timeProfiler.formatCounter(EnumFusionCounters.INPUT_FILTERING));
            System.out.println("Buffering time:                   " + timeProfiler.formatCounter(EnumFusionCounters.BUFFERING));
            System.out.println("Conflict resolution time:         " + timeProfiler.formatCounter(EnumFusionCounters.CONFLICT_RESOLUTION));
            System.out.println("Output writing time:              " + timeProfiler.formatCounter(EnumFusionCounters.OUTPUT_WRITING));
            System.out.println("Maximum recorded total memory:    " + MemoryProfiler.formatMemoryBytes(memoryProfiler.getMaxTotalMemory()));
            System.out.println("Maximum recorded used memory:     " + MemoryProfiler.formatMemoryBytes(memoryProfiler.getMaxUsedMemory()));
            System.out.println("Minimum recorded free memory:     " + MemoryProfiler.formatMemoryBytes(memoryProfiler.getMinFreeMemory()));
        }
    }

    /** Disable constructor. */
    private LDFusionToolApplication() {
    }
}
