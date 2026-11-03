/*
 * The MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.jenkins.plugins.advancedinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlPage;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import hudson.model.Job;
import jenkins.model.Jenkins;

public class InputStepSkipTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule j = new JenkinsRule();

    /** When no skip caption is set, the step is not skippable and defaults are unchanged. */
    @Test public void notSkippableByDefault() throws Exception {
        InputStep s = new InputStep("proceed?");
        assertFalse(s.isSkippable());
        assertNull(s.getSkip());
        // skipValue has a default even when unused
        assertEquals("SKIPPED", s.getSkipValue());
    }

    /** Setting a skip caption makes the step skippable; skipValue defaults to SKIPPED. */
    @Test public void skippableWhenCaptionSet() throws Exception {
        InputStep s = new InputStep("proceed?");
        s.setSkip("Skip apply");
        assertTrue(s.isSkippable());
        assertEquals("Skip apply", s.getSkip());
        assertEquals("SKIPPED", s.getSkipValue());
        s.setSkipValue("SKIP");
        assertEquals("SKIP", s.getSkipValue());
    }

    @Test public void configRoundTrip() throws Exception {
        InputStep s1 = new InputStep("hello world");
        s1.setSkip("Skip apply");
        s1.setSkipValue("SKIP");
        InputStep s2 = new StepConfigTester(j).configRoundTrip(s1);
        assertEquals(s1.getSkip(), s2.getSkip());
        assertEquals(s1.getSkipValue(), s2.getSkipValue());
        assertTrue(s2.isSkippable());
    }

    /**
     * The parameterless console path renders three hyperlinks when skippable, and the
     * skip endpoint resumes the pipeline with the configured skip value.
     */
    @Test public void skipReturnsSkipValue() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "def r = advancedInput id: 'InputX', message: 'Apply?', ok: 'Apply', skip: 'Skip apply', skipValue: 'SKIP', cancel: 'Abort'\n"
                + "echo \"result=${r}\"", true));

        QueueTaskFuture<WorkflowRun> q = p.scheduleBuild2(0);
        WorkflowRun b = q.getStartCondition().get();
        CpsFlowExecution exec = (CpsFlowExecution) b.getExecutionPromise().get();
        while (b.getAction(InputAction.class) == null) {
            exec.waitForSuspension();
        }
        // The three-way console prompt lists all three captions.
        j.waitForMessage("Apply, Skip apply or Abort", b);

        InputAction action = b.getAction(InputAction.class);
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getPage(new WebRequest(
                wc.createCrumbedUrl(b.getUrl() + action.getUrlName() + "/InputX/skip"), HttpMethod.POST));

        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("result=SKIP", b);
        assertEquals(0, action.getExecutions().size());
    }

    /** Skip via the form submit button (parameterized input) resumes with the skip value. */
    @Test public void skipViaFormButton() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "def r = advancedInput id: 'InputX', message: 'Apply?', ok: 'Apply', skip: 'Skip apply', skipValue: 'SKIP',\n"
                + "  parameters: [booleanParam(name: 'flag', defaultValue: false)]\n"
                + "echo \"result=${r}\"", true));

        QueueTaskFuture<WorkflowRun> q = p.scheduleBuild2(0);
        WorkflowRun b = q.getStartCondition().get();
        CpsFlowExecution exec = (CpsFlowExecution) b.getExecutionPromise().get();
        while (b.getAction(InputAction.class) == null) {
            exec.waitForSuspension();
        }
        j.waitForMessage("Input requested", b);

        InputAction action = b.getAction(InputAction.class);
        InputStepExecution is = action.getExecution("InputX");
        HtmlPage page = j.createWebClient().getPage(b, action.getUrlName());
        j.submit(page.getFormByName(is.getId()), "skip");

        j.assertBuildStatusSuccess(j.waitForCompletion(b));
        j.assertLogContains("result=SKIP", b);
        assertEquals(0, action.getExecutions().size());
    }

    /** Hitting the skip endpoint on a non-skippable input is rejected and does not resume. */
    @Test public void skipRejectedWhenNotSkippable() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "advancedInput id: 'InputX', message: 'Apply?', ok: 'Apply'", true));

        QueueTaskFuture<WorkflowRun> q = p.scheduleBuild2(0);
        WorkflowRun b = q.getStartCondition().get();
        CpsFlowExecution exec = (CpsFlowExecution) b.getExecutionPromise().get();
        while (b.getAction(InputAction.class) == null) {
            exec.waitForSuspension();
        }

        InputAction action = b.getAction(InputAction.class);
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        wc.getPage(new WebRequest(
                wc.createCrumbedUrl(b.getUrl() + action.getUrlName() + "/InputX/skip"), HttpMethod.POST));

        // The input is still pending: the skip was refused, not honored.
        assertEquals(1, action.getExecutions().size());
        b.doStop();
        j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b));
    }

    /**
     * Skip is a proceed-class action: a user with only Job/CANCEL (but not Job/BUILD)
     * cannot skip. Mirrors the submitter/permission model used for proceed.
     */
    @Test public void skipRequiresBuildPermission() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Job.READ, Job.CANCEL).everywhere().to("charlie"));

        WorkflowJob p = j.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "advancedInput id: 'InputX', message: 'Apply?', ok: 'Apply', skip: 'Skip apply'", true));

        QueueTaskFuture<WorkflowRun> q = p.scheduleBuild2(0);
        WorkflowRun b = q.getStartCondition().get();
        CpsFlowExecution exec = (CpsFlowExecution) b.getExecutionPromise().get();
        while (b.getAction(InputAction.class) == null) {
            exec.waitForSuspension();
        }

        InputAction action = b.getAction(InputAction.class);
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("charlie");
        wc.setThrowExceptionOnFailingStatusCode(false);
        wc.getPage(new WebRequest(
                wc.createCrumbedUrl(b.getUrl() + action.getUrlName() + "/InputX/skip"), HttpMethod.POST));

        // charlie lacks Job/BUILD, so skip is refused and the input remains pending.
        assertEquals(1, action.getExecutions().size());
        b.doStop();
        j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b));
    }
}
