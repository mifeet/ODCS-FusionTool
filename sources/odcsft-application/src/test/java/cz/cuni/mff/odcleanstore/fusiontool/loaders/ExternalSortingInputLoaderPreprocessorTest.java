package cz.cuni.mff.odcleanstore.fusiontool.loaders;

import com.google.common.collect.ImmutableSet;
import cz.cuni.mff.odcleanstore.fusiontool.conflictresolution.urimapping.UriMappingIterableImpl;
import cz.cuni.mff.odcleanstore.fusiontool.loaders.extsort.ExternalSortingInputLoaderPreprocessor;
import org.junit.Ignore;
import org.junit.Test;
import org.openrdf.model.Statement;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.helpers.StatementCollector;

import java.util.ArrayList;

import static cz.cuni.mff.odcleanstore.fusiontool.testutil.ContextAwareStatementIsEqual.contextAwareStatementIsEqual;
import static cz.cuni.mff.odcleanstore.fusiontool.testutil.LDFusionToolTestUtils.createHttpStatement;
import static cz.cuni.mff.odcleanstore.fusiontool.testutil.LDFusionToolTestUtils.createHttpUri;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class ExternalSortingInputLoaderPreprocessorTest {
    public static final ValueFactoryImpl VF = ValueFactoryImpl.getInstance();

    @Test
    public void respectsSetContext() throws Exception {
        // Act
        ArrayList<Statement> result = new ArrayList<>();
        ExternalSortingInputLoaderPreprocessor preprocessor = new ExternalSortingInputLoaderPreprocessor(
                new StatementCollector(result),
                ValueFactoryImpl.getInstance()
        );
        preprocessor.startRDF();

        Statement statement1 = VF.createStatement(createHttpUri("a"), createHttpUri("b"), createHttpUri("c"));
        preprocessor.setDefaultContext(createHttpUri("g1"));
        preprocessor.handleStatement(statement1);

        Statement statement2 = VF.createStatement(createHttpUri("x"), createHttpUri("y"), createHttpUri("z"));
        preprocessor.setDefaultContext(createHttpUri("g2"));
        preprocessor.handleStatement(statement2);

        preprocessor.endRDF();

        // Assert
        assertThat(result.size(), equalTo(2));
        assertThat(result.get(0), contextAwareStatementIsEqual(createHttpStatement("a", "b", "c", "g1")));
        assertThat(result.get(1), contextAwareStatementIsEqual(createHttpStatement("x", "y", "z", "g2")));
    }

    @Ignore // TODO
    @Test
    public void filtersUnmappedSubjectsWhenOutputMappedSubjectsOnlyIsTrue() throws Exception {
        // Arrange
        ArrayList<Statement> statements = new ArrayList<>();
        statements.add(createHttpStatement("s1", "p1", "o1", "g1"));
        statements.add(createHttpStatement("s3", "p1", "o1", "g1"));
        statements.add(createHttpStatement("s2", "p1", "o1", "g1"));
        statements.add(createHttpStatement("s4", "p1", "o1", "g1"));

        UriMappingIterableImpl uriMapping = new UriMappingIterableImpl(ImmutableSet.of(
                createHttpUri("s1").toString(), createHttpUri("s2").toString()));
        uriMapping.addLink(createHttpUri("s1").toString(), createHttpUri("sx").toString());
        uriMapping.addLink(createHttpUri("s2").toString(), createHttpUri("sy").toString());

        // Act
        ArrayList<Statement> result = collectResultsFromPreprocessor(statements);

        // Assert
        assertThat(result.size(), equalTo(2));
        assertThat(result.get(0), contextAwareStatementIsEqual(createHttpStatement("s1", "p1", "o1", "g1")));
        assertThat(result.get(1), contextAwareStatementIsEqual(createHttpStatement("s2", "p1", "o1", "g1")));
    }

    private ArrayList<Statement> collectResultsFromPreprocessor(ArrayList<Statement> statements) throws RDFHandlerException {

        ArrayList<Statement> result = new ArrayList<>();
        ExternalSortingInputLoaderPreprocessor preprocessor = new ExternalSortingInputLoaderPreprocessor(
                new StatementCollector(result),
                VF
        );
        preprocessor.startRDF();
        for (Statement statement : statements) {
            preprocessor.handleStatement(statement);
        }
        preprocessor.endRDF();
        return result;
    }
}