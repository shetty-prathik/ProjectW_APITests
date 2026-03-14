package com.projectw.utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Singleton that owns the ExtentReports instance for the entire test run.
 *
 * - One HTML report is created per run, saved to target/extent-reports/
 * - The report is flushed (written to disk) automatically via a JVM shutdown hook,
 *   so it is always produced even when tests fail or the suite is interrupted.
 * - ExtentTest nodes are stored in a ThreadLocal so parallel test execution is safe.
 */
public class ExtentReportManager {

    private static ExtentReports extent;
    private static final ThreadLocal<ExtentTest> testThread = new ThreadLocal<>();

    private ExtentReportManager() {}

    public static synchronized ExtentReports getInstance() {
        if (extent == null) {
            extent = createInstance();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (extent != null) {
                    extent.flush();
                }
            }));
        }
        return extent;
    }

    private static ExtentReports createInstance() {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String reportDir  = "target/extent-reports/";
        String reportPath = reportDir + "ProjectW_TestReport_" + timestamp + ".html";

        new File(reportDir).mkdirs();

        ExtentSparkReporter spark = new ExtentSparkReporter(reportPath);
        spark.config().setTheme(Theme.DARK);
        spark.config().setDocumentTitle("Project W API Test Report");
        spark.config().setReportName("Project W — API Automation");
        spark.config().setTimeStampFormat("dd MMM yyyy HH:mm:ss");
        spark.config().setEncoding("UTF-8");

        ExtentReports er = new ExtentReports();
        er.attachReporter(spark);
        er.setSystemInfo("Application", "Project W (Manufacturing ERP)");
        er.setSystemInfo("Environment", ConfigManager.getBaseUrl());
        er.setSystemInfo("EID", ConfigManager.getTestEid());
        er.setSystemInfo("Tester", System.getProperty("user.name"));
        er.setSystemInfo("Java", System.getProperty("java.version"));

        return er;
    }

    /** Called by the listener when a test method starts. */
    public static ExtentTest createTest(String testName, String description) {
        ExtentTest test = getInstance().createTest(testName, description);
        testThread.set(test);
        return test;
    }

    /** Returns the ExtentTest node for the currently running test thread. */
    public static ExtentTest getTest() {
        return testThread.get();
    }

    /** Removes the thread-local entry after the test finishes. */
    public static void removeTest() {
        testThread.remove();
    }

    /** Explicitly flush — call at the end of the suite for immediate write. */
    public static void flush() {
        if (extent != null) {
            extent.flush();
        }
    }
}
