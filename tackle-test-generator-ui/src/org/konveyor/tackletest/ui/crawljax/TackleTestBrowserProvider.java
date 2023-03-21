package org.konveyor.tackletest.ui.crawljax;

import com.crawljax.browser.EmbeddedBrowser;
import com.crawljax.browser.WebDriverBackedEmbeddedBrowser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.konveyor.tackletest.ui.util.DriverProvider;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Browser provider for TackleTest: creates web driver instance, sets browser configuration
 * options, and returns an embedded browser
 */
public class TackleTestBrowserProvider implements Provider<EmbeddedBrowser> {

    private final EmbeddedBrowser.BrowserType browserType;

    @Inject
    public TackleTestBrowserProvider(EmbeddedBrowser.BrowserType browserType) {
        this.browserType = browserType;
    }

    @Override
    public EmbeddedBrowser get() {
        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--ignore-certificate-errors");
        chromeOptions.addArguments("--remote-allow-origins=*");
        if (browserType == EmbeddedBrowser.BrowserType.CHROME_HEADLESS) {
            chromeOptions.addArguments("--headless");
        }
        WebDriver chromeDriver = DriverProvider.getInstance().getDriver(chromeOptions);
        chromeDriver.manage().window().setSize(new Dimension(1200, 890));
        return WebDriverBackedEmbeddedBrowser.withDriver(chromeDriver);
    }
}
