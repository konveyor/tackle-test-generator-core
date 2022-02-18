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

import org.junit.Assert;
import org.junit.Test;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class CrawljaxRunnerTest {

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
