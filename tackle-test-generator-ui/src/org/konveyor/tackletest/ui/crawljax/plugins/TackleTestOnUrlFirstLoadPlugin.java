package org.konveyor.tackletest.ui.crawljax.plugins;

import com.bastiaanjansen.otp.HMACAlgorithm;
import com.bastiaanjansen.otp.SecretGenerator;
import com.bastiaanjansen.otp.TOTP;
import com.crawljax.core.CrawlerContext;
import com.crawljax.core.plugin.OnUrlFirstLoadPlugin;
import org.konveyor.tackletest.ui.util.TackleTestLogger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.tomlj.TomlTable;

import java.time.Duration;
import java.util.logging.Logger;

/**
 * Plugin class for sequence of actions that execute on first load of the AUT URL prior to
 * crawling; implemented via Crawljax plugin interface.
 */
public class TackleTestOnUrlFirstLoadPlugin implements OnUrlFirstLoadPlugin {

    private static final Logger logger = TackleTestLogger.getLogger(TackleTestOnUrlFirstLoadPlugin.class);
    private TomlTable[] preCrawlActions;

    private static final int MAX_SUBMIT_TOTP_ATTEMPTS = 3;

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
        logger.info("Performing "+this.preCrawlActions.length+" pre-crawl actions");
        for (TomlTable action : this.preCrawlActions) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // get action type for pre-crawl action
            String actionType = action.getString("action_type");

            try {
                // alert accept action
                if (actionType.equals("alert_accept")) {
                    driver.switchTo().alert().accept();
                    continue;
                }

                // "submit TOTP" action; the action also generates TOTP code
                if (actionType.equals("submit_totp")) {
                    submitTOTP(action, driverWait);
                    continue;
                }

                // compute selenium locator
                By locator = getLocatorForAction(action);
                if (locator == null) {
                    continue;
                }

                // perform the specified action type (click, enter) if locator is non-null, or alert accept
                if (actionType.equals("click")) {
                    driverWait.until(ExpectedConditions.elementToBeClickable(locator)).click();
                } else if (actionType.equals("enter")) {
                    String inputValue = null;
                    if (action.contains("input_value")) {
                        inputValue = action.getString("input_value");
                    }
                    // read input value from environment variable
                    else if (action.contains("input_value_env_var")) {
                        inputValue = System.getenv(action.getString("input_value_env_var"));
                    }
                    if (inputValue != null) {
                        WebElement element = driverWait.until(ExpectedConditions.presenceOfElementLocated(locator));
                        element.clear();
                        element.sendKeys(inputValue);
                    }
                } else {
                    logger.warning("Ignoring unsupported pre-crawl action");
                    throw new RuntimeException("Unsupported pre-crawl action type: " + actionType +
                        "\n  must be one of [click, enter, submit_totp, alert_accept]");
                }
            } catch (RuntimeException re) {
                // ignore exception if action is marked as optional
                if (action.contains("optional") && action.getBoolean("optional")) {
                    logger.info("Ignoring optional pre-crawl action: "+action.toMap()+
                        " on exception\n"+re.getMessage());
                    continue;
                }
                logger.severe("Error executing pre-crawl action: "+action.toMap()+
                    " on exception\n"+re.getMessage()+"\n... exiting");
                driver.quit();
                System.exit(0);
            }
        }
        logger.info("Precrawl actions execution done");
    }

    /**
     * Creates selenium locator for the given pre-crawl action.
     * @param action pre-crawl action to create locator for
     * @return Selenium By locator for action
     */
    private By getLocatorForAction(TomlTable action) {
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
        else {
            throw new RuntimeException("None of the expected locator specification found: "+
                "[by_id, by_name, under_xpath, with_text]");
        }
        return locator;
    }

    /**
     * Performs the "submit TOTP" action using the given action specification and driver instance.
     * It generates the TOTP code based on the specified secret (via environment variable) or a
     * generated secret, and then enters the code into the specified textbox and clicks the submit
     * button. Performs TOTP submission and generation repeatedly until it succeeds or max attempts
     * (default 3) is reached.
     * @param action pre-crawl action specification for TOTP submission
     * @param driverWait web driver instance
     */
    private void submitTOTP(TomlTable action, WebDriverWait driverWait) {
        if (!action.contains("enter") || !action.contains("click")) {
            throw new RuntimeException("submit_otp pre-crawl action must have \"enter\" and \"click\" specifications");
        }
        byte[] totpSecret = null;
        // get seed secret for totp generation from env var if specified
        if (action.contains("totp_secret_env_var")) {
            String seedSecret = System.getenv(action.getString("totp_secret_env_var"));
            totpSecret = seedSecret.getBytes();
        }

        // locate the TOTP textbox for the first submission attempt; if this fails, TOTP submission
        // action is aborted
        int maxTotpAttempts = MAX_SUBMIT_TOTP_ATTEMPTS;
        if (action.contains("max_attempts")) {
            maxTotpAttempts = action.getLong("max_attempts").intValue();
        }
        By enterLocator = getLocatorForAction(action.getTable("enter"));
        driverWait.until(ExpectedConditions.presenceOfElementLocated(enterLocator));

        // generate OTP and submit until it succeeds (i.e., totp textbox cannot be located) or
        // max attempts is reached
        for (int i = 0; true; i++) {
            // if max attempts reached, throw exception to indicate failure
            if (i == maxTotpAttempts) {
                throw new RuntimeException("Max TOTP submission attempts reached without success");
            }
            // generate TOTP and enter in text box
            String inputValue = generateOTP(totpSecret);
            if (inputValue == null) {
                // generated TOTP could not be verified; make another attempt
                continue;
            }
            sendKeys(enterLocator, inputValue, driverWait);

            // click submit button
            By clickLocator = getLocatorForAction(action.getTable("click"));
            driverWait.until(ExpectedConditions.elementToBeClickable(clickLocator)).click();

            // attempt to locate the TOTP textbox; if it is not present, TOTP submission succeeded
            // otherwise, attempt OTP submission again until max attempts is reached
            try {
                Thread.sleep(1000);
                driverWait.until(ExpectedConditions.presenceOfElementLocated(enterLocator));
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (WebDriverException wde) {
                logger.info("TOTP submission succeeded");
                break;
            }
        }
    }

    /**
     * Locates the target web element using the given locator, clears the element, and enters the
     * given input value in the element via the sendkeys event
     * @param locator Selenium locator for target web element
     * @param inputValue input value to be used for sendkeys
     * @param driverWait web driver instance
     */
    private void sendKeys(By locator, String inputValue, WebDriverWait driverWait) {
        WebElement element = driverWait.until(ExpectedConditions.presenceOfElementLocated(locator));
        element.clear();
        element.sendKeys(inputValue);
    }

    /**
     * Generates and returns TOTP code based on the given secret if specified or a generated secret.
     * The code is valid for 30 seconds.
     * @param secret secret to be used for TOTP generation (optional)
     * @return
     */
    private static String generateOTP(byte[] secret) {
        // secret not specified, generate it
        if (secret == null || secret.length == 0) {
            secret = SecretGenerator.generate();
        }
        TOTP.Builder builder = new TOTP.Builder(secret);
        builder.withPasswordLength(6)
            .withPeriod(Duration.ofSeconds(30))
            .withAlgorithm(HMACAlgorithm.SHA1);
        TOTP totp = builder.build();
        String code = totp.now();
        if (!totp.verify(code)) {
            logger.warning("Unable to verify TOTP");
            return null;
        }
        return code;
    }

    public static void main(String[] args) {
        String seedSecret = System.getenv("TKLTESTUI_TOTP_SECRET");
        String otp = generateOTP(seedSecret.getBytes());
        System.out.println(otp);
    }

}
