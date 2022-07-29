package org.konveyor.tackletest.ui.crawljax.plugins;

import com.crawljax.core.CrawlerContext;
import com.crawljax.core.plugin.OnUrlFirstLoadPlugin;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.tomlj.TomlTable;

import java.time.Duration;

/**
 * Plugin class for sequence of actions that execute on first load of the AUT URL prior to
 * crawling; implemented via Crawljax plugin interface.
 */
public class TackleTestOnUrlFirstLoadPlugin implements OnUrlFirstLoadPlugin {

    private TomlTable[] preCrawlActions;

    /**
     * Construct plugin instance with the specified pre-crawl actions.
     * @param preCrawlActions
     */
    public TackleTestOnUrlFirstLoadPlugin(TomlTable[] preCrawlActions) {
        this.preCrawlActions = preCrawlActions;
    }

    /**
     * Perform pre-crawl actions specified in the clickables spec on first load of URL
     * @param crawlerContext
     */
    @Override
    public void onUrlFirstLoad(CrawlerContext crawlerContext) {
        // get web driver instance and driver wait instance with the configired wait-after-event duration
        WebDriver driver = crawlerContext.getBrowser().getWebDriver();
        long waitAfterEvent = crawlerContext.getConfig().getCrawlRules().getWaitAfterEvent();
        WebDriverWait driverWait = new WebDriverWait(driver, Duration.ofMillis(waitAfterEvent));

        // execute specified pre-crawl actions
        for (TomlTable action : this.preCrawlActions) {

            // compute selenium locator
            By locator = null;
            if (action.contains("by_id")) {
                String elemId = action.getString("by_id");
                if (!elemId.isEmpty()) {
                    locator = By.id(elemId);
                }
            }
            else if (action.contains("by_name")) {
                String elemName = action.getString("by_name");
                if (!elemName.isEmpty()) {
                    locator = By.name(elemName);
                }
            }
            else if (action.contains("under_xpath")) {
                String elemXpath = action.getString("under_xpath");
                if (!elemXpath.isEmpty()) {
                    locator = By.xpath(elemXpath);
                }
            }
            else if (action.contains("with_text")) {
                String elemText = action.getString("with_text");
                if (!elemText.isEmpty()) {
                    locator = By.linkText(elemText);
                }
            }
            else if (action.contains("by_css_selector")) {
                String elemCssSelector = action.getString("by_css_selector");
                if (!elemCssSelector.isEmpty()) {
                    locator = By.cssSelector(elemCssSelector);
                }
            }
            // perform the specified action type (click or enter) if locator is non-null
            String actionType = action.getString("action_type");
            if (locator != null) {
                if (actionType.equals("click")) {
                    driverWait.until(ExpectedConditions.elementToBeClickable(locator)).click();
                }
                else if (actionType.equals("enter")) {
                    String inputValue = null;
                    if (action.contains("input_value")) {
                        inputValue = action.getString("input_value");
                    }
                    // read input value from environment variable
                    else if (action.contains("input_value_env_var")) {
                        inputValue = System.getenv(action.getString("input_value_env_var"));
                    }
                    if (inputValue != null) {
                        driverWait.until(ExpectedConditions.presenceOfElementLocated(locator))
                            .sendKeys(inputValue);
                    }
                }
                else {
                    throw new RuntimeException("Unsupported pre-crawl action type: "+actionType+
                        "\n  must be one of [click, enter]");
                }
            }
        }
    }

}
