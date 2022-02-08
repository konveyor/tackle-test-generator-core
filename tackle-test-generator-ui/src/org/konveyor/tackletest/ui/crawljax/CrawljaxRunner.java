/*
Licensed under the Eclipse Public License 2.0, Version 2.0 (the "License");
you may not use this file except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.konveyor.tackletest.ui.crawljax;

import com.crawljax.browser.EmbeddedBrowser;
import com.crawljax.core.configuration.BrowserConfiguration;
import com.crawljax.core.configuration.CrawlRules;
import com.crawljax.core.configuration.CrawljaxConfiguration;
import com.crawljax.core.configuration.InputSpecification;
import com.crawljax.plugins.crawloverview.CrawlOverview;
import com.crawljax.plugins.testcasegenerator.TestConfiguration;
import com.crawljax.plugins.testcasegenerator.TestSuiteGenerator;
import com.crawljax.stateabstractions.dom.RTEDStateVertexFactory;
import com.crawljax.stateabstractions.hybrid.HybridStateVertexFactory;
import org.apache.commons.cli.*;
import org.konveyor.tackletest.ui.util.TackleTestLogger;
import org.tomlj.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class CrawljaxRunner {

    private static final Logger logger = TackleTestLogger.getLogger(CrawljaxRunner.class);

    /**
     * Creates and returns browser configuration given the browser type and pixel density.
     * @param browser
     * @param pixelDensity
     * @return
     */
    private static BrowserConfiguration createBrowserConfiguration(String browser, int pixelDensity) {
        EmbeddedBrowser.BrowserType browserType;
        if (browser.equals("chrome")) {
            browserType = EmbeddedBrowser.BrowserType.CHROME;
        } else if (browser.equals("chrome_headless")) {
            browserType = EmbeddedBrowser.BrowserType.CHROME_HEADLESS;
        } else if (browser.equals("firefox")) {
            browserType = EmbeddedBrowser.BrowserType.FIREFOX;
        } else if (browser.equals("firefox_headless")) {
            browserType = EmbeddedBrowser.BrowserType.FIREFOX_HEADLESS;
        } else {
            throw new RuntimeException("Unsupported browser: "+browser);
        }
        return new BrowserConfiguration(browserType, pixelDensity);
    }

    /**
     * Creates and returns a test configuration for the given state equivalence assertion type.
     * TODO: NONE mode
     * @param stateAssertion
     * @return
     */
    private static TestConfiguration createTestConfiguration(String stateAssertion) {
        TestConfiguration.StateEquivalenceAssertionMode stateAssertionMode = TestConfiguration
            .StateEquivalenceAssertionMode.DOM;
        if (stateAssertion.equals("visual")) {
            stateAssertionMode = TestConfiguration.StateEquivalenceAssertionMode.VISUAL;
        } else if (stateAssertion.equals("both")) {
            stateAssertionMode = TestConfiguration.StateEquivalenceAssertionMode.BOTH;
        } else if (stateAssertion.equals("hybrid")) {
            stateAssertionMode = TestConfiguration.StateEquivalenceAssertionMode.HYBRID;
        }
        return new TestConfiguration(stateAssertionMode);
    }

    /**
     * Reads the clickables specifination from the given toml file, and updates crawl rules in
     * the given Crawljax configuration builder with included and excluded web elements to be
     * crawled.
     * @param clickablesSpecFile
     * @param builder
     * @throws IOException
     */
    private static void updateClickablesConfiguration(String clickablesSpecFile,
                                                      CrawljaxConfiguration.CrawljaxConfigurationBuilder builder) throws IOException {
        TomlParseResult clickableSpec = Toml.parse(Paths.get(clickablesSpecFile));

        // process click spec, consisting of a list of tags to be treated as clickables
        String[] clickTags = clickableSpec.getArray("click").toList().toArray(new String[0]);
        logger.info("adding clickables: "+ Arrays.toString(clickTags));
        builder.crawlRules().click(clickTags);

        // process don't click element spec
        TomlTable[] dontclickElementSpec = clickableSpec.getArray("dont_click.element")
            .toList()
            .toArray(new TomlTable[0]);
        for (TomlTable dontClickElem : dontclickElementSpec) {
            String tagName = dontClickElem.getString("tag_name");
            if (dontClickElem.contains("with_text")) {
                String withText = dontClickElem.getString("with_text");
                if (withText != null && !withText.isEmpty()) {
                    builder.crawlRules().dontClick(tagName).withText(withText);
                }
            } else if (dontClickElem.contains("under_xpath")) {
                String underXpath = dontClickElem.getString("under_xpath");
                if (underXpath != null && !underXpath.isEmpty()) {
                    builder.crawlRules().dontClick(tagName).underXPath(underXpath);
                }
            } else if (dontClickElem.contains("with_attribute")) {
                TomlTable withAttribute = dontClickElem.getTable("with_attribute");
                String attrName = withAttribute.getString("attr_name");
                String attrValue = withAttribute.getString("attr_value");
                if (attrName != null && !attrName.isEmpty() && attrValue != null && !attrValue.isEmpty()) {
                    builder.crawlRules().dontClick(tagName).withAttribute(attrName, attrValue);
                }
            }
        }
        logger.info("Done processing dont_click.element spec");

        // process don't click children_of spec
        TomlTable[] dontclickChildrenofSpec = clickableSpec.getArray("dont_click.children_of")
            .toList()
            .toArray(new TomlTable[0]);
        for (TomlTable dontClickElem : dontclickChildrenofSpec) {
            String tagName = dontClickElem.getString("tag_name");
            if (dontClickElem.contains("with_class")) {
                String withClass = dontClickElem.getString("with_class");
                if (withClass != null && !withClass.isEmpty()) {
                    builder.crawlRules().dontClickChildrenOf(tagName).withClass(withClass);
                }
            } else if (dontClickElem.contains("with_id")) {
                String withId = dontClickElem.getString("with_id");
                if (withId != null && !withId.isEmpty()) {
                    builder.crawlRules().dontClickChildrenOf(tagName).withId(withId);
                }
            }
        }
        logger.info("Done processing dont_click.children_of spec");
    }

    /**
     * Creates a crawljax configuration from the given generate options read from the toml
     * configuration file.
     * @param url
     * @param generateOptions
     * @return
     */
    private static CrawljaxConfiguration createCrawljaxConfiguration(String appName, String url,
                                                                     String testDir,
                                                                     TomlTable generateOptions)
        throws IOException {
        CrawljaxConfiguration.CrawljaxConfigurationBuilder builder = CrawljaxConfiguration.builderFor(url);

        // set browser
        builder.setBrowserConfig(createBrowserConfiguration(
            generateOptions.getString("browser"),
            generateOptions.getLong("browser_pixel_density").intValue()
        ));

        // set max runtime
        long timeLimit = generateOptions.getLong("time_limit");
        builder.setMaximumRunTime(timeLimit, TimeUnit.MINUTES);

        // set max states
        int maxStates = generateOptions.getLong("max_states").intValue();
        if (maxStates == 0) {
            builder.setUnlimitedStates();
        }
        else {
            builder.setMaximumStates(maxStates);
        }

        // set max depth
        builder.setMaximumDepth(generateOptions.getLong("max_depth").intValue());

        // set click rules
        builder.crawlRules().clickOnce(Boolean.TRUE.equals(
            generateOptions.getBoolean("click_once")));
        builder.crawlRules().clickElementsInRandomOrder(Boolean.TRUE.equals(
            generateOptions.getBoolean("click_randomly")));

        // set wait times
        builder.crawlRules().waitAfterEvent(generateOptions.getLong("wait_after_event"), TimeUnit.MILLISECONDS);
        builder.crawlRules().waitAfterReloadUrl(generateOptions.getLong("wait_after_reload"), TimeUnit.MILLISECONDS);

        // crawl hidden anchors
        builder.crawlRules().crawlHiddenAnchors(Boolean.TRUE.equals(
            generateOptions.getBoolean("crawl_hidden_anchors")));

        // click default elements
        if (generateOptions.getBoolean("click_default_elements")) {
            builder.crawlRules().clickDefaultElements();
        }

        // handle include_iframes option: if specified use RTED state abstraction function; otherwise
        // use fragment-based state abstraction
        if (generateOptions.getBoolean("include_iframes")) {
            double rtedSimilarityThreshold = generateOptions.getDouble("rted_similarity_threshold");
            builder.setStateVertexFactory(new RTEDStateVertexFactory(rtedSimilarityThreshold));
            builder.crawlRules().crawlFrames(true);
        }
        else {
            builder.setStateVertexFactory(new HybridStateVertexFactory(0, builder, false));
            builder.crawlRules().crawlFrames(false);
        }

        // form fill mode
        String ffMode = generateOptions.getString("form_fill_mode");
        CrawlRules.FormFillMode formFillMode = CrawlRules.FormFillMode.RANDOM;
        if (ffMode.equals("normal")) {
            formFillMode = CrawlRules.FormFillMode.NORMAL;
        } else if (ffMode.equals("training")) {
            formFillMode = CrawlRules.FormFillMode.TRAINING;
        } else if (ffMode.equals("xpath_training")) {
            formFillMode = CrawlRules.FormFillMode.XPATH_TRAINING;
        }
        builder.crawlRules().setFormFillMode(formFillMode);

        // form fill order
        String ffOrder = generateOptions.getString("form_fill_order");
        CrawlRules.FormFillOrder formFillOrder = CrawlRules.FormFillOrder.NORMAL;
        if (ffOrder.equals("dom")) {
            formFillOrder = CrawlRules.FormFillOrder.DOM;
        } else if (ffOrder.equals("visual")) {
            formFillOrder = CrawlRules.FormFillOrder.VISUAL;
        }
        builder.crawlRules().setFormFillOrder(formFillOrder);

        // set click and don't-click rules
        String clickablesSpecFile = generateOptions.getString("clickables_spec_file");
        if (clickablesSpecFile != null && !clickablesSpecFile.isEmpty()) {
            updateClickablesConfiguration(clickablesSpecFile, builder);
        }

        // TODO: set form data specification

        // add crawl-overview and test-generator plugins
        builder.addPlugin(new CrawlOverview());
        builder.addPlugin(new TestSuiteGenerator(
            createTestConfiguration(generateOptions.getString("add_state_diff_assertions"))));

        // set output directory
        builder.setOutputDirectory(new File(testDir));

        return builder.build();
    }

    /**
     * Creates and returns name of the output dir path.
     * @param appName
     * @param appUri
     * @param timeLimit
     * @return
     */
    private static String createOutputDirectoryName(String appName, URI appUri, long timeLimit) {
        return "tkltest-output-ui-" + appName + File.separator + appName + "_" +
            appUri.getHost() + "_" + timeLimit + "mins";
    }

    /**
     * Reorganizes Crawljax's default directory structure by moving the crawl0 directory up one
     * level to output directory name (removing the intermediate directory named for the app url's
     * host component).
     * @param outputDir
     * @param appUri
     * @throws IOException
     */
    private static void moveDirectory(String outputDir, URI appUri) throws IOException {
        // source output directory
        Path srcCrawlPath = Paths.get(outputDir, appUri.getHost(), "crawl0");

        // create the target output directory by finding the highest crawl index
        Path targetCrawlPath;
        int i = 0;
        do {
            targetCrawlPath = Paths.get(outputDir, "crawl" + i);
            i++;
        } while (Files.exists(targetCrawlPath));

        // move the output directory and remove the intermediate url host directory
        Files.move(srcCrawlPath, targetCrawlPath);
        Files.delete(Paths.get(outputDir, appUri.getHost()));
    }

    /**
     * Parses command-line options and returns parsed command-line object. If help
     * option is specified or a parse exception occurs, prints the help message and
     * returns null.
     *
     * @param args
     * @return
     */
    private static CommandLine parseCommandLineOptions(String[] args) {
        Options options = new Options();

        // option for specifying the toml config file
        options.addOption(Option.builder("cf").longOpt("config-file").hasArg()
            .desc("Name of TOML file containing UI test-generation configuration").type(String.class).build());

        // help option
        options.addOption(Option.builder("h").longOpt("help").desc("Print this help message").build());

        HelpFormatter formatter = new HelpFormatter();

        // parse command line options
        CommandLineParser argParser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = argParser.parse(options, args);
            // if help option specified, print help message and return null
            if (cmd.hasOption("h")) {
                formatter.printHelp("CrawljaxRunner", options, true);
                return null;
            }
        } catch (ParseException e) {
            logger.warning(e.getMessage());
            formatter.printHelp("CrawljaxRunner", options, true);
            return null;
        }

        // check whether required options are specified
        if (!cmd.hasOption("cf")) {
            formatter.printHelp("CrawljaxRunner", options, true);
            return null;
        }
        return cmd;

    }

    /**
     * Parses the configuration options specified in the given tackle-test toml config file,
     * creates crawljax configuration, and invokes the crawljax runner.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException, URISyntaxException {
        // parse command-line options
        CommandLine cmd = parseCommandLineOptions(args);

        // if parser command-line is empty (which occurs if the help option is specified, a parse
        // exception occurs, or any required option is not provided, exit
        if (cmd == null) {
            System.exit(0);
        }

        // parse toml file and check for errors
        String configFilename = cmd.getOptionValue("cf");
        logger.info("Testgen config plan file: " + configFilename);
        TomlParseResult parsedConfig = Toml.parse(Paths.get(configFilename));
        if (parsedConfig.hasErrors()) {
            // print parse errors and exit
            System.out.println("Error parsing "+configFilename+":");
            for (TomlParseError parseError : parsedConfig.errors()) {
                System.out.println("  - "+parseError.toString());
            }
            System.exit(1);
        }

        String appName = parsedConfig.getString("general.app_name");
        String appUrl = parsedConfig.getString("general.app_url");
        String testDir = parsedConfig.getString("general.test_directory");
        TomlTable generateOptions = parsedConfig.getTable("generate");

        // set output directory if not specified in config
        URI appUri = URI.create(appUrl);
        if (testDir == null || testDir.isEmpty()) {
            testDir = createOutputDirectoryName(appName, appUri, generateOptions.getLong("time_limit"));
        }
        logger.info("app_name="+appName+" app_url="+appUrl+" test_directory="+testDir);
        logger.info("generate options: "+generateOptions.keySet());

        // create crawljax configuration
        CrawljaxConfiguration crawljaxConfig = null;
        try {
            crawljaxConfig = createCrawljaxConfiguration(appName, appUrl, testDir, generateOptions);
            logger.info("Crawljax configuration created: " + crawljaxConfig.toString());
        }
        catch (RuntimeException re) {
            System.out.println("Error creating Crawljax configuration: "+re.getMessage());
            re.printStackTrace();
            System.exit(1);
        }

        // run crawljax
        com.crawljax.core.CrawljaxRunner crawljaxRunner = new com.crawljax.core.CrawljaxRunner(crawljaxConfig);
        crawljaxRunner.call();

        // move directory
        moveDirectory(testDir, appUri);

    }
}
