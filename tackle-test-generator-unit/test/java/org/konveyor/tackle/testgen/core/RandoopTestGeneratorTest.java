/*
Copyright IBM Corporation 2021

Licensed under the Eclipse Public License 2.0, Version 2.0 (the "License");
you may not use this file except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

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
        RandoopTestGenerator randoopTestgen = new RandoopTestGenerator(Collections.singletonList(classpath), 
        		"DayTrader", System.getProperty("java.home"));
        randoopTestgen.setProjectClasspath(classpath);

        // set configuration options
        Map<String, String> config = new HashMap<>();
        config.put("TIME_LIMIT", "5");
        randoopTestgen.configure(config);
        randoopTestgen.addCoverageTarget("com.ibm.websphere.samples.daytrader.TradeAction");
        randoopTestgen.generateTests();

        // assert that input/output files for randoop are created
        //assertTrue(new File(RandoopTestGenerator.RANDOOP_CLASSLIST_FILE_NAME).exists());
        //assertTrue(new File(RandoopTestGenerator.RANDOOP_METHODLIST_FILE_NAME).exists());
        assertTrue(new File("DayTrader"+RandoopTestGenerator.RANDOOP_OUTPUT_DIR_NAME_SUFFIX).exists());
    }

}
