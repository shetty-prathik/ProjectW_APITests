package com.projectw.listeners;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.markuputils.ExtentColor;
import com.aventstack.extentreports.markuputils.MarkupHelper;
import com.projectw.utils.ExtentReportManager;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * TestNG listener that populates the ExtentReports HTML report automatically.
 *
 * Registered in testng.xml — no annotation changes needed in test classes.
 *
 * What it captures per test:
 *  - Test name + description (from @Test annotation)
 *  - PASS / FAIL / SKIP status with colour-coded labels
 *  - Full exception stack trace on failure
 *  - Skip reason when a test is skipped
 *  - Suite-level flush at the end so the file is always written
 */
public class ExtentReportListener implements ITestListener {

    // ─── Suite lifecycle ──────────────────────────────────────────────────────

    @Override
    public void onStart(ITestContext context) {
        ExtentReportManager.getInstance();
    }

    @Override
    public void onFinish(ITestContext context) {
        ExtentReportManager.flush();
    }

    // ─── Test lifecycle ───────────────────────────────────────────────────────

    @Override
    public void onTestStart(ITestResult result) {
        String testName   = result.getMethod().getMethodName();
        String description = result.getMethod().getDescription();
        if (description == null || description.isBlank()) {
            description = testName;
        }

        ExtentTest test = ExtentReportManager.createTest(testName, description);

        // Attach class name as a category tag
        test.assignCategory(result.getTestClass().getRealClass().getSimpleName());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        ExtentTest test = ExtentReportManager.getTest();
        if (test != null) {
            test.pass(MarkupHelper.createLabel("PASSED", ExtentColor.GREEN));
        }
        ExtentReportManager.removeTest();
    }

    @Override
    public void onTestFailure(ITestResult result) {
        ExtentTest test = ExtentReportManager.getTest();
        if (test != null) {
            test.fail(MarkupHelper.createLabel("FAILED", ExtentColor.RED));
            Throwable cause = result.getThrowable();
            if (cause != null) {
                test.fail(cause);
            }
        }
        ExtentReportManager.removeTest();
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        ExtentTest test = ExtentReportManager.getTest();
        if (test == null) {
            // Test was skipped before onTestStart fired (e.g. dependency failure)
            test = ExtentReportManager.createTest(
                    result.getMethod().getMethodName(),
                    result.getMethod().getDescription()
            );
            test.assignCategory(result.getTestClass().getRealClass().getSimpleName());
        }
        test.skip(MarkupHelper.createLabel("SKIPPED", ExtentColor.ORANGE));
        Throwable cause = result.getThrowable();
        if (cause != null) {
            test.skip("Skip reason: " + cause.getMessage());
        }
        ExtentReportManager.removeTest();
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        ExtentTest test = ExtentReportManager.getTest();
        if (test != null) {
            test.log(Status.WARNING, "Test failed but within success percentage");
        }
        ExtentReportManager.removeTest();
    }
}
