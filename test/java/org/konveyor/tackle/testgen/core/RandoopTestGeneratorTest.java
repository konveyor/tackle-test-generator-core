package org.konveyor.tackle.testgen.core;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

public class RandoopTestGeneratorTest {

    @Before
    /**
     * Delete existing randoop input files and generated test cases
     */
    public void cleanUp() {
        FileUtils.deleteQuietly(new File(RandoopTestGenerator.RANDOOP_CLASSLIST_FILE_NAME));
        FileUtils.deleteQuietly(new File(RandoopTestGenerator.RANDOOP_METHODLIST_FILE_NAME));
        FileUtils.deleteQuietly(new File("DayTrader"+RandoopTestGenerator.RANDOOP_OUTPUT_DIR_NAME_SUFFIX));
    }

    @Test
    public void testGenerateTests() throws Exception {
        String classpath = "test/data/daytrader7/monolith/bin";
        RandoopTestGenerator randoopTestgen = new RandoopTestGenerator(Collections.singletonList(classpath), "DayTrader");
        randoopTestgen.setProjectClasspath(classpath);

        // set configuration options
        Map<String, String> config = new HashMap<>();
        config.put("TIME_LIMIT", "5");
        randoopTestgen.configure(config);
        randoopTestgen.addCoverageTarget("com.ibm.websphere.samples.daytrader.TradeAction");
        randoopTestgen.generateTests();

        // assert that input/output files for randoop are created
        //assertTrue(new File(RandoopTestGenerator.RANDOOP_CLASSLIST_FILE_NAME).exists());
        assertTrue(new File(RandoopTestGenerator.RANDOOP_METHODLIST_FILE_NAME).exists());
        assertTrue(new File("DayTrader"+RandoopTestGenerator.RANDOOP_OUTPUT_DIR_NAME_SUFFIX).exists());
    }

}
