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
import com.crawljax.plugins.crawloverview.CrawlOverview;
import com.crawljax.plugins.testcasegenerator.TestConfiguration;
import com.crawljax.plugins.testcasegenerator.TestSuiteGenerator;
import org.apache.commons.cli.*;
import org.konveyor.tackletest.ui.util.TackleTestLogger;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import com.crawljax.core.configuration.CrawljaxConfiguration;

import java.io.IOException;
import java.nio.file.Paths;
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
        EmbeddedBrowser.BrowserType browserType = EmbeddedBrowser.BrowserType.CHROME_HEADLESS;
        if (browser.equals("chrome")) {
            browserType = EmbeddedBrowser.BrowserType.CHROME;
        } else if (browser.equals("firefox")) {
            browserType = EmbeddedBrowser.BrowserType.FIREFOX;
        } else if (browser.equals("firefox_headless")) {
            browserType = EmbeddedBrowser.BrowserType.FIREFOX_HEADLESS;
        } else if (browser.equals("phantomjs")) {
            browserType = EmbeddedBrowser.BrowserType.PHANTOMJS;
        }
        return new BrowserConfiguration(browserType, pixelDensity);
    }

    // TODO: NONE mode

    /**
     * Creates and returns a test configuration for the given state equivalence assertion type.
     * @param stateAssertion
     * @return
     */
    private static TestConfiguration createTestConfiguration(String stateAssertion) {
        TestConfiguration.StateEquivalenceAssertionMode stateAssertionMode = TestConfiguration.StateEquivalenceAssertionMode.DOM;
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
     * Creates a crawljax configuration from the given generate options read from the toml
     * configuration file.
     * @param url
     * @param generateOptions
     * @return
     */
    private static CrawljaxConfiguration createCrawljaxConfiguration(String url, TomlTable generateOptions) {
        CrawljaxConfiguration.CrawljaxConfigurationBuilder builder = CrawljaxConfiguration.builderFor(url);

        // set browser
        builder.setBrowserConfig(createBrowserConfiguration(
            generateOptions.getString("browser"),
            generateOptions.getLong("browser_pixel_density").intValue()
        ));

        // set max runtime
        builder.setMaximumRunTime(generateOptions.getLong("time_limit"), TimeUnit.MINUTES);

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

        // TODO: handle include_iframes option
        builder.crawlRules().crawlFrames(false);

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

        // TODO: set clickables and dont click rules

        // TODO: set form data specification

        // add crawl-overview and test-generator plugins
        builder.addPlugin(new CrawlOverview());
        builder.addPlugin(new TestSuiteGenerator(
            createTestConfiguration(generateOptions.getString("add_state_diff_assertions"))));

        return builder.build();
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
    public static void main(String[] args) throws IOException {
        // parse command-line options
        CommandLine cmd = parseCommandLineOptions(args);

        // if parser command-line is empty (which occurs if the help option is specified, a parse
        // exception occurs, or any required option is not provided, exit
        if (cmd == null) {
            System.exit(0);
        }

        // parse toml file
        String configFilename = cmd.getOptionValue("cf");
        logger.info("Testgen config plan file: " + configFilename);
        TomlParseResult parsedConfig = Toml.parse(Paths.get(configFilename));
        String appUrl = parsedConfig.getString("general.app_url");
        TomlTable generateOptions = parsedConfig.getTable("generate");
        logger.info("app_url: "+appUrl);
        logger.info("generate options: "+generateOptions.keySet());

        // create crawljax configuration
        CrawljaxConfiguration crawljaxConfig = createCrawljaxConfiguration(appUrl, generateOptions);
        logger.info("crawljax configuration created: "+crawljaxConfig.toString());

        // run crawljax
        com.crawljax.core.CrawljaxRunner crawljaxRunner = new com.crawljax.core.CrawljaxRunner(crawljaxConfig);
//        crawljaxRunner.call();

    }
}
