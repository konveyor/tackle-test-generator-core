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

import com.crawljax.core.configuration.CrawlRules;
import com.crawljax.core.configuration.CrawljaxConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class CrawljaxRunnerTest {

    @Test
    public void testCreateCrawljaxConfigurationSample() throws IOException {
        // sample app config
        String configFile = "./test/data/sample/tkltest_ui_config.toml";
        TomlParseResult parsedConfig = Toml.parse(Paths.get(configFile));
        String appUrl = parsedConfig.getString("general.app_url");
        String testDir = parsedConfig.getString("general.test_directory");
        TomlTable generateOptions = parsedConfig.getTable("generate");

        // call method for creating crawljax config
        CrawljaxConfiguration crawljaxConfig = CrawljaxRunner.createCrawljaxConfiguration(appUrl, testDir, generateOptions);

        // assert on created config
        Assert.assertNotNull(crawljaxConfig);
        Assert.assertEquals(appUrl, crawljaxConfig.getUrl().toString());
        Assert.assertTrue(crawljaxConfig.getOutputDir().toString().endsWith(
            testDir+File.separator+"localhost"+File.separator+"crawl0"));
        Assert.assertEquals(0, crawljaxConfig.getMaximumStates());
        Assert.assertEquals(2, crawljaxConfig.getCrawlRules().getMaxRepeatExploredActions());
        Assert.assertEquals(2, crawljaxConfig.getMaximumDepth());
        CrawlRules crawlRules = crawljaxConfig.getCrawlRules();
        Assert.assertEquals(9, crawlRules.getPreCrawlConfig().getIncludedElements().size());
        Assert.assertEquals(6, crawlRules.getPreCrawlConfig().getExcludedElements().size());
        Assert.assertEquals(500, crawlRules.getWaitAfterEvent());
        Assert.assertEquals(500, crawlRules.getWaitAfterReloadUrl());
    }

    @Test
    public void testUpdateClickablesConfigurationSample() {
        String newLine = System.getProperty("line.separator");
        String clickablesSpec = String.join(newLine,
            "[[click.element]]", "  tag_name = [\"div\"]",
            "[[dont_click.element]]", "  tag_name = [\"a\"]",
            "[[dont_click.element]]", "  tag_name = [\"tag1\"]", "  with_text = \"some text\""
        );
        CrawljaxConfiguration.CrawljaxConfigurationBuilder builder = CrawljaxConfiguration.builderFor("http://localhost:8080");
        CrawljaxRunner.updateClickablesConfiguration(Toml.parse(clickablesSpec), builder);
        CrawlRules crawlRules = builder.build().getCrawlRules();
        Assert.assertEquals(1, crawlRules.getPreCrawlConfig().getIncludedElements().size());
        Assert.assertEquals(2, crawlRules.getPreCrawlConfig().getExcludedElements().size());

        // click specified, dont_click not specified
        clickablesSpec = String.join(newLine,
            "[[click.element]]", "  tag_name = [\"div\"]"
        );
        builder = CrawljaxConfiguration.builderFor("http://localhost:8080");
        CrawljaxRunner.updateClickablesConfiguration(Toml.parse(clickablesSpec), builder);
        crawlRules = builder.build().getCrawlRules();
        Assert.assertEquals(1, crawlRules.getPreCrawlConfig().getIncludedElements().size());
        Assert.assertEquals(0, crawlRules.getPreCrawlConfig().getExcludedElements().size());

        // dont_click specified, click not specified
        clickablesSpec = String.join(newLine,
            "[[dont_click.element]]", "  tag_name = [\"a\"]"
        );
        builder = CrawljaxConfiguration.builderFor("http://localhost:8080");
        CrawljaxRunner.updateClickablesConfiguration(Toml.parse(clickablesSpec), builder);
        crawlRules = builder.build().getCrawlRules();
        Assert.assertEquals(4, crawlRules.getPreCrawlConfig().getIncludedElements().size());
        Assert.assertEquals(1, crawlRules.getPreCrawlConfig().getExcludedElements().size());

        // neither click nor dont_click specified
        clickablesSpec = "";
        builder = CrawljaxConfiguration.builderFor("http://localhost:8080");
        CrawljaxRunner.updateClickablesConfiguration(Toml.parse(clickablesSpec), builder);
        crawlRules = builder.build().getCrawlRules();
        Assert.assertEquals(4, crawlRules.getPreCrawlConfig().getIncludedElements().size());
        Assert.assertEquals(0, crawlRules.getPreCrawlConfig().getExcludedElements().size());
    }

    @Test
    public void testUpdateClickablesConfigurationClickSpecExcpSample() {
        // click spec with no tag_name property
        final String clickablesSpec = "[[click.element]]";
        final CrawljaxConfiguration.CrawljaxConfigurationBuilder builder = CrawljaxConfiguration.builderFor("http://localhost:8080");
        Assert.assertThrows(RuntimeException.class, new ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                CrawljaxRunner.updateClickablesConfiguration(Toml.parse(clickablesSpec), builder);
            }
        });
    }

    @Test
    public void testUpdateClickablesConfigurationDontclickSpecExcpSample() {
        // dont_click spec with no tag_name property
        final String clickablesSpec = "[[dont_click.element]]";
        final CrawljaxConfiguration.CrawljaxConfigurationBuilder builder = CrawljaxConfiguration.builderFor("http://localhost:8080");
        Assert.assertThrows(RuntimeException.class, new ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                CrawljaxRunner.updateClickablesConfiguration(Toml.parse(clickablesSpec), builder);
            }
        });
    }

    @Test
    public void testCrawljaxRunnerPetclinic() throws IOException, URISyntaxException {
        String configFile = "./test/data/petclinic/tkltest_ui_config.toml";
        String[] args = {
            "--config-file", configFile
        };
        // run crawljax on app
        CrawljaxRunner.main(args);

        // assert that the output directory is created
        String outDir = getOutputDirectoryName(configFile);
        Assert.assertTrue(Files.exists(Paths.get(outDir)));
    }

    @Test
    public void testCrawljaxRunnerAddressbook() throws IOException, URISyntaxException {
        String configFile = "./test/data/addressbook/tkltest_ui_config.toml";
        String[] args = {
            "--config-file", configFile
        };
        // run crawljax on app
        CrawljaxRunner.main(args);

        // assert that the output directory is created
        String outDir = getOutputDirectoryName(configFile);
        Assert.assertTrue(Files.exists(Paths.get(outDir)));
    }

    private String getOutputDirectoryName(String configFile) throws IOException {
        TomlParseResult parsedConfig = parseConfig(configFile);
        String appName = parsedConfig.getString("general.app_name");
        URI appUri = URI.create(parsedConfig.getString("general.app_url"));
        int timeLimit = parsedConfig.getLong("generate.time_limit").intValue();
        return "tkltest-output-ui-" + appName + File.separator + appName + "_" +
            appUri.getHost() + "_" + timeLimit + "mins";
    }

    private TomlParseResult parseConfig(String configFile) throws IOException {
        return Toml.parse(Paths.get(configFile));
    }

}
