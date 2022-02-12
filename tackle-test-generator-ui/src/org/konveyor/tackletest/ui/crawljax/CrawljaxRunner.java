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
import com.crawljax.core.configuration.*;
import com.crawljax.core.state.Identification;
import com.crawljax.forms.FormInput;
import com.crawljax.plugins.crawloverview.CrawlOverview;
import com.crawljax.plugins.testcasegenerator.TestConfiguration;
import com.crawljax.plugins.testcasegenerator.TestSuiteGenerator;
import com.crawljax.stateabstractions.dom.RTEDStateVertexFactory;
import com.crawljax.stateabstractions.hybrid.HybridStateVertexFactory;
import org.apache.commons.cli.*;
import org.konveyor.tackletest.ui.util.TackleTestLogger;
import org.tomlj.Toml;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

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
     * @return browser configuration object
     */
    private static BrowserConfiguration createBrowserConfiguration(String browser, int pixelDensity) {
        if (browser == null || browser.isEmpty()) {
            throw new RuntimeException("Browser type not specified: "+browser);
        }
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
            throw new RuntimeException("Unsupported browser: "+browser+
                "\n  must be one of [chrome, chrome_headless, firefox, firefox_headless]");
        }
        return new BrowserConfiguration(browserType, pixelDensity);
    }

    /**
     * Creates and returns a test configuration for the given state equivalence assertion type.
     * @param stateAssertion
     * @return test configuration object
     */
    private static TestConfiguration createTestConfiguration(String stateAssertion) {
        if (stateAssertion == null || stateAssertion.isEmpty()) {
            throw new RuntimeException("Assertion type not specified: "+stateAssertion);
        }
        TestConfiguration.StateEquivalenceAssertionMode stateAssertionMode;
        if (stateAssertion.equals("none")) {
            stateAssertionMode = TestConfiguration.StateEquivalenceAssertionMode.NONE;
        } else if (stateAssertion.equals("dom")) {
            stateAssertionMode = TestConfiguration.StateEquivalenceAssertionMode.DOM;
        } else if (stateAssertion.equals("visual")) {
            stateAssertionMode = TestConfiguration.StateEquivalenceAssertionMode.VISUAL;
        } else if (stateAssertion.equals("both")) {
            stateAssertionMode = TestConfiguration.StateEquivalenceAssertionMode.BOTH;
        } else if (stateAssertion.equals("hybrid")) {
            stateAssertionMode = TestConfiguration.StateEquivalenceAssertionMode.HYBRID;
        } else {
            throw new RuntimeException("Unknown assertion type: "+stateAssertion+
                "\n  must be one of [dom, visual, both, hybrid]");
        }
        return new TestConfiguration(stateAssertionMode);
    }

    /**
     * Returns Crawljax's form fill mode based on the specified form fill mode
     * @param ffMode
     * @return
     */
    private static CrawlRules.FormFillMode getFormFillMode(String ffMode) {
        if (ffMode == null || ffMode.isEmpty()) {
            throw new RuntimeException("Form fill mode not specified: "+ffMode);
        }
        CrawlRules.FormFillMode formFillMode;
        if (ffMode.equals("random")) {
            formFillMode = CrawlRules.FormFillMode.RANDOM;
        } else if (ffMode.equals("normal")) {
            formFillMode = CrawlRules.FormFillMode.NORMAL;
        } else if (ffMode.equals("training")) {
            formFillMode = CrawlRules.FormFillMode.TRAINING;
        } else if (ffMode.equals("xpath_training")) {
            formFillMode = CrawlRules.FormFillMode.XPATH_TRAINING;
        } else {
            throw new RuntimeException("Unknown form fill mode: "+ffMode+
                "\n  must be one of [random, normal, training, xpath_training]");
        }
        return formFillMode;
    }

    /**
     * Returns Crawljax's form fill order based on the specified form fill order
     * @param ffOrder
     * @return
     */
    private static CrawlRules.FormFillOrder getFormFillOrder(String ffOrder) {
        if (ffOrder == null || ffOrder.isEmpty()) {
            throw new RuntimeException("Form fill order not specified: "+ffOrder);
        }
        CrawlRules.FormFillOrder formFillOrder;
        if (ffOrder.equals("normal")) {
            formFillOrder = CrawlRules.FormFillOrder.NORMAL;
        } else if (ffOrder.equals("dom")) {
            formFillOrder = CrawlRules.FormFillOrder.DOM;
        } else if (ffOrder.equals("visual")) {
            formFillOrder = CrawlRules.FormFillOrder.VISUAL;
        } else {
            throw new RuntimeException("Unknown form fill order: "+ffOrder+
                "\n  must be one of [normal, dom, visual]");
        }
        return formFillOrder;

    }

    private static int getIntTypeOption(TomlTable optionSpec, String optionName) {
        Long optionVal = optionSpec.getLong(optionName);
        if (optionVal == null) {
            throw new RuntimeException("Unspecified configuration option: "+optionName);
        }
        return optionVal.intValue();
    }

    private static long getLongTypeOption(TomlTable optionSpec, String optionName) {
        Long optionVal = optionSpec.getLong(optionName);
        if (optionVal == null) {
            throw new RuntimeException("Unspecified configuration option: "+optionName);
        }
        return optionVal.longValue();
    }

    private static double getDoubleTypeOption(TomlTable optionSpec, String optionName) {
        Double optionVal = optionSpec.getDouble(optionName);
        if (optionVal == null) {
            throw new RuntimeException("Unspecified configuration option: "+optionName);
        }
        return optionVal.doubleValue();
    }

    private static boolean getBooleanTypeOption(TomlTable optionSpec, String optionName) {
        Boolean optionVal = optionSpec.getBoolean(optionName);
        if (optionVal == null) {
            throw new RuntimeException("Unspecified configuration option: "+optionName);
        }
        return optionVal.booleanValue();
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
     * Computes and returns crawljax identification object for a form field.
     * @param fieldIdent
     * @return
     */
    private static Identification getIdentificationForFormField(TomlTable fieldIdent) {
        if (fieldIdent == null) {
            return null;
        }
        String how = fieldIdent.getString("how");
        String value = fieldIdent.getString("value");
        if (how == null || how.isEmpty() || value == null || value.isEmpty()) {
            return null;
        }
        Identification.How identHow;
        if (how.equals("name")) {
            identHow = Identification.How.name;
        } else if (how.equals("id")) {
            identHow = Identification.How.id;
        } else if (how.equals("tag")) {
            identHow = Identification.How.tag;
        } else if (how.equals("text")) {
            identHow = Identification.How.text;
        } else if (how.equals("partial_text")) {
            identHow = Identification.How.partialText;
        } else if (how.equals("xpath")) {
            identHow = Identification.How.xpath;
        } else {
            throw new RuntimeException("Unknown identification type for form field: "+how+
                "\n  must be onne of [name, id, tag, text, partial_text, xpath]");
        }
        return new Identification(identHow, value);
    }

    /**
     * Computes and returns crawljax input types for the given string type from a form data spec.
     * @param type
     * @return
     */
    private static FormInput.InputType getInputTypeForFormField(String type) {
        if (type == null || type.isEmpty()) {
            return null;
        }
        FormInput.InputType inputType;
        if (type.equals("text")) {
            inputType = FormInput.InputType.TEXT;
        } else if (type.equals("select")) {
            inputType = FormInput.InputType.SELECT;
        } else if (type.equals("checkbox")) {
            inputType = FormInput.InputType.CHECKBOX;
        } else if (type.equals("radio")) {
            inputType = FormInput.InputType.RADIO;
        } else if (type.equals("email")) {
            inputType = FormInput.InputType.EMAIL;
        } else if (type.equals("textarea")) {
            inputType = FormInput.InputType.TEXTAREA;
        } else if (type.equals("password")) {
            inputType = FormInput.InputType.PASSWORD;
        } else if (type.equals("number")) {
            inputType = FormInput.InputType.NUMBER;
        } else {
            throw new RuntimeException("Unknown input type for form field: "+type+
                "\n  must be one of [text, select, checkbox, radio, email, textarea, password, number]");
        }
        return inputType;
    }

    /**
     * Creates and returns form input specification by processing the content of the given form
     * data spec file.
     * @param formDataSpecFile
     * @return
     */
    private static InputSpecification getFormInputSpecification(String formDataSpecFile) throws IOException {
        // parse the toml spec
        TomlParseResult parsedConfig = Toml.parse(Paths.get(formDataSpecFile));
        if (parsedConfig.hasErrors()) {
            // print parse errors and exit
            String errMsg = "Error parsing "+formDataSpecFile+":\n";
            for (TomlParseError parseError : parsedConfig.errors()) {
                errMsg += "  - "+parseError.toString()+"\n";
            }
            throw new RuntimeException(errMsg);
        }

        // create input specification
        InputSpecification inputSpec = new InputSpecification();

        // iterate over specified forms and set values for all form fields
        TomlTable forms = parsedConfig.getTable("forms");
        for (String formName: forms.keySet()) {
            TomlTable formInfo = forms.getTable(formName);
            if (formInfo == null || formInfo.isEmpty()) {
                logger.warning("Skipping empty form specification: "+formName);
                continue;
            }
            Form form = new Form();
            TomlTable[] formInputFields = formInfo.getArray("input_fields").toList()
                .toArray(new TomlTable[0]);

            // process all form fields
            for (TomlTable inputField : formInputFields) {
                FormInput.InputType inputType = getInputTypeForFormField(inputField.getString("input_type"));
                if (inputType == null) {
                    logger.warning("Skipping empty input type for form field: "+formName);
                    continue;
                }
                Identification identification = getIdentificationForFormField(inputField.getTable("identification"));
                if (identification == null) {
                    logger.warning("Skipping empty identification for form field: "+formName);
                    continue;
                }
                String inputValue = inputField.getString("input_value");
                if (inputValue == null) {
                    logger.warning("Skipping null input value for form field: "+formName);
                    continue;
                }
                form.inputField(inputType, identification).inputValues(inputValue);
            }

            // process before click event spec
            TomlTable formBeforeEvent = formInfo.getTable("before_click");
            String beforeTag = formBeforeEvent.getString("tag_name");
            if (formBeforeEvent.contains("with_text")) {
                String withText = formBeforeEvent.getString("with_text");
                if (withText != null && !withText.isEmpty()) {
                    inputSpec.setValuesInForm(form).beforeClickElement(beforeTag).withText(withText);
                }
            } else if (formBeforeEvent.contains("under_xpath")) {
                String underXpath = formBeforeEvent.getString("under_xpath");
                if (underXpath != null && !underXpath.isEmpty()) {
                    inputSpec.setValuesInForm(form).beforeClickElement(beforeTag).underXPath(underXpath);
                }
            } else if (formBeforeEvent.contains("with_attribute")) {
                TomlTable withAttribute = formBeforeEvent.getTable("with_attribute");
                String attrName = withAttribute.getString("attr_name");
                String attrValue = withAttribute.getString("attr_value");
                if (attrName != null && !attrName.isEmpty() && attrValue != null && !attrValue.isEmpty()) {
                    inputSpec.setValuesInForm(form).beforeClickElement(beforeTag).withAttribute(attrName, attrValue);
                }
            }
        }
        return inputSpec;
    }

    /**
     * Creates a crawljax configuration from the given generate options read from the toml
     * configuration file.
     * @param appUrl
     * @param generateOptions
     * @return
     */
    private static CrawljaxConfiguration createCrawljaxConfiguration(String appUrl, String testDir,
                                                                     TomlTable generateOptions)
        throws IOException {
        CrawljaxConfiguration.CrawljaxConfigurationBuilder builder = CrawljaxConfiguration.builderFor(appUrl);

        // set browser
        builder.setBrowserConfig(createBrowserConfiguration(
            generateOptions.getString("browser"),
            getIntTypeOption(generateOptions, "browser_pixel_density")
        ));

        // set max runtime
        long timeLimit = getLongTypeOption(generateOptions, "time_limit");
        builder.setMaximumRunTime(timeLimit, TimeUnit.MINUTES);

        // set max states
        int maxStates = getIntTypeOption(generateOptions, "max_states");
        if (maxStates == 0) {
            builder.setUnlimitedStates();
        }
        else {
            builder.setMaximumStates(maxStates);
        }

        // set max depth
        builder.setMaximumDepth(getIntTypeOption(generateOptions, "max_depth"));

        // set click rules
        builder.crawlRules().clickOnce(Boolean.TRUE.equals(
            generateOptions.getBoolean("click_once")));
        builder.crawlRules().clickElementsInRandomOrder(Boolean.TRUE.equals(
            generateOptions.getBoolean("click_randomly")));

        // set wait times
        builder.crawlRules().waitAfterEvent(getLongTypeOption(generateOptions, "wait_after_event"),
            TimeUnit.MILLISECONDS);
        builder.crawlRules().waitAfterReloadUrl(getLongTypeOption(generateOptions, "wait_after_reload"),
            TimeUnit.MILLISECONDS);

        // crawl hidden anchors
        builder.crawlRules().crawlHiddenAnchors(Boolean.TRUE.equals(
            generateOptions.getBoolean("crawl_hidden_anchors")));

        // click default elements
        if (getBooleanTypeOption(generateOptions," click_default_elements")) {
            builder.crawlRules().clickDefaultElements();
        }

        // handle include_iframes option: if specified use RTED state abstraction function; otherwise
        // use fragment-based state abstraction
        if (getBooleanTypeOption(generateOptions, "include_iframes")) {
            double rtedSimilarityThreshold = getDoubleTypeOption(generateOptions, "rted_similarity_threshold");
            builder.setStateVertexFactory(new RTEDStateVertexFactory(rtedSimilarityThreshold));
            builder.crawlRules().crawlFrames(true);
        }
        else {
            builder.setStateVertexFactory(new HybridStateVertexFactory(0, builder, false));
            builder.crawlRules().crawlFrames(false);
        }

        // form fill mode
        CrawlRules.FormFillMode formFillMode = getFormFillMode(generateOptions.getString("form_fill_mode"));
        builder.crawlRules().setFormFillMode(formFillMode);

        // form fill order
        CrawlRules.FormFillOrder formFillOrder = getFormFillOrder(generateOptions.getString("form_fill_order"));
        builder.crawlRules().setFormFillOrder(formFillOrder);

        // set click and don't-click rules
        String clickablesSpecFile = generateOptions.getString("clickables_spec_file");
        if (clickablesSpecFile != null && !clickablesSpecFile.isEmpty()) {
            updateClickablesConfiguration(clickablesSpecFile, builder);
        }

        // set form input specification
        InputSpecification inputSpec = getFormInputSpecification(generateOptions.getString("form_data_spec_file"));
        builder.crawlRules().setInputSpec(inputSpec);

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
            crawljaxConfig = createCrawljaxConfiguration(appUrl, testDir, generateOptions);
            logger.info("Crawljax configuration created: " + crawljaxConfig.toString());
        }
        catch (Exception re) {
            logger.severe("Error creating Crawljax configuration: "+re.getMessage());
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
