package net.thucydides.core.steps;

import com.sun.jdi.event.StepEvent;
import net.thucydides.core.annotations.Pending;
import net.thucydides.core.annotations.Step;
import net.thucydides.core.annotations.StepGroup;
import net.thucydides.core.annotations.Steps;
import net.thucydides.core.csv.Person;
import net.thucydides.core.model.Story;
import net.thucydides.core.pages.Pages;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;

public class WhenUsingTheStepEventBus {

    static class SimpleTestScenarioSteps extends ScenarioSteps {

        public SimpleTestScenarioSteps(Pages pages) {
            super(pages);
        }

        @Step
        public void step1(){
            getDriver().get("step_one");
        }

        @Step
        public void step2(){
            getDriver().get("step_two");
        }

        @Step
        public void step3(){
            getDriver().get("step_three");
        }

        @Step
        public void step4(){
            step5();
            step6();
        }

        @Step
        public void step5(){
            getDriver().get("step_five");
        }

        @Step
        public void step6(){
            getDriver().get("step_six");
        }

        @Step
        public void step7(){
            step1();
            failingStep();
            step2();
        }

        @Step
        public void step8(){
            step1();
            failingStep();
            step4();
        }

        @Step
        public void failingStep(){
            getDriver().get("failing_step");
            assertThat(true, is(false));
        }

        @Step
        public SimpleTestScenarioSteps stepThatReturnsAStep() {
            return this;
        }

        @Step
        public SimpleTestScenarioSteps stepThatFailsAndReturnsAStep() {
            assertThat(true, is(false));
            return this;
        }

        @StepGroup
        public void legacyStepGroup() {
            step1();
            step2();
            step3();

        }
    }

    static class SampleTestScenario {

        @Steps
        SimpleTestScenarioSteps steps;

        public void sampleTest() {
            steps.step1();
            steps.step2();
            steps.step3();
        }
    }

    @Mock
    WebDriver driver;

    @Mock
    StepListener listener;

    SampleStepListener sampleStepListener;

    private StepFactory factory;

    @Before
    public void initMocks() {
        MockitoAnnotations.initMocks(this);

        factory = new StepFactory(new Pages(driver));

        sampleStepListener = new SampleStepListener();

        StepEventBus.getEventBus().registerListener(listener);
        StepEventBus.getEventBus().registerListener(sampleStepListener);

    }

    @After
    public void clearListener() {
        StepEventBus.getEventBus().dropListener(sampleStepListener);
        StepEventBus.getEventBus().dropListener(listener);
    }

    @Test
    public void should_execute_steps_transparently() {
        SimpleTestScenarioSteps steps = factory.getStepLibraryFor(SimpleTestScenarioSteps.class);

        steps.step1();
        steps.step2();
        steps.step3();

        verify(driver).get("step_one");
        verify(driver).get("step_two");
        verify(driver).get("step_three");
    }

    @Test
    public void the_step_event_bus_can_be_used_to_sent_notification_events_about_steps() {
        SimpleTestScenarioSteps steps = factory.getStepLibraryFor(SimpleTestScenarioSteps.class);

        StepEventBus.getEventBus().stepStarted(ExecutedStepDescription.withTitle("a step"));

        verify(listener).stepStarted(any(ExecutedStepDescription.class));
    }

    @Test
    public void should_notify_listeners_when_a_step_starts() {
        SimpleTestScenarioSteps steps = factory.getStepLibraryFor(SimpleTestScenarioSteps.class);

        steps.step1();

        ArgumentCaptor<ExecutedStepDescription> argument = ArgumentCaptor.forClass(ExecutedStepDescription.class);

        verify(listener).stepStarted(argument.capture());

        assertThat(argument.getValue().getName(), is("step1"));
    }

    @Test
    public void should_record_when_a_test_starts_and_finishes() {
        SimpleTestScenarioSteps steps = factory.getStepLibraryFor(SimpleTestScenarioSteps.class);

        StepEventBus.getEventBus().testStarted("a_test");
        steps.step1();
        StepEventBus.getEventBus().testFinished();

        String expectedSteps =
                  "TEST a_test\n"
                + "-step1\n"
                + "-step1 done\n"
                + "TEST DONE\n";
        assertThat(sampleStepListener.toString(), is(expectedSteps));
    }


    @Test
    public void should_record_nested_test_steps() {
        SimpleTestScenarioSteps steps = factory.getStepLibraryFor(SimpleTestScenarioSteps.class);

        StepEventBus.getEventBus().testStarted("a_test");
        steps.step4();
        StepEventBus.getEventBus().testFinished();

        String expectedSteps =
                  "TEST a_test\n"
                          + "-step4\n"
                          + "--step5\n"
                          + "--step5 done\n"
                          + "--step6\n"
                          + "--step6 done\n"
                          + "-step4 done\n"
                          + "TEST DONE\n";
        assertThat(sampleStepListener.toString(), is(expectedSteps));
    }

    @Test
    public void should_record_groups_as_nested_test_steps() {
        SimpleTestScenarioSteps steps = factory.getStepLibraryFor(SimpleTestScenarioSteps.class);

        StepEventBus.getEventBus().testStarted("a_test");
        steps.legacyStepGroup();
        StepEventBus.getEventBus().testFinished();

        String expectedSteps =
                  "TEST a_test\n"
                          + "-legacyStepGroup\n"
                          + "--step1\n"
                          + "--step1 done\n"
                          + "--step2\n"
                          + "--step2 done\n"
                          + "--step3\n"
                          + "--step3 done\n"
                          + "-legacyStepGroup done\n"
                          + "TEST DONE\n";
        assertThat(sampleStepListener.toString(), is(expectedSteps));
    }

    @Test
    public void should_record_step_failures() {
        SimpleTestScenarioSteps steps = factory.getStepLibraryFor(SimpleTestScenarioSteps.class);

        StepEventBus.getEventBus().testStarted("a_test");
        steps.step1();
        steps.failingStep();
        StepEventBus.getEventBus().testFinished();

        String expectedSteps =
                  "TEST a_test\n"
                          + "-step1\n"
                          + "-step1 done\n"
                          + "-failingStep\n"
                          + "---> STEP FAILED\n"
                          + "TEST DONE\n";
        assertThat(sampleStepListener.toString(), is(expectedSteps));
    }

    @Test
    public void should_record_nested_step_failures() {
        SimpleTestScenarioSteps steps = factory.getStepLibraryFor(SimpleTestScenarioSteps.class);

        StepEventBus.getEventBus().testStarted("a_test");
        steps.step1();
        steps.step8();
        StepEventBus.getEventBus().testFinished();

        String expectedSteps =
                  "TEST a_test\n"
                          + "-step1\n"
                          + "-step1 done\n"
                          + "-step8\n"
                          + "--step1\n"
                          + "--step1 done\n"
                          + "--failingStep\n"
                          + "----> STEP FAILED\n"
                          + "--step4\n"
                          + "---step5\n"
                          + "-----> STEP IGNORED\n"
                          + "---step6\n"
                          + "-----> STEP IGNORED\n"
                          + "----> STEP IGNORED\n"
                          + "-step8 done\n"
                          + "TEST DONE\n";

        System.out.println(sampleStepListener.toString());
        assertThat(sampleStepListener.toString(), is(expectedSteps));


    }

    @Test
    public void should_skip_steps_after_a_step_failure() {
        SimpleTestScenarioSteps steps = factory.getStepLibraryFor(SimpleTestScenarioSteps.class);

        StepEventBus.getEventBus().testStarted("a_test");
        steps.step1();
        steps.step7();
        StepEventBus.getEventBus().testFinished();

        String expectedSteps =
                  "TEST a_test\n"
                          + "-step1\n"
                          + "-step1 done\n"
                          + "-step7\n"
                          + "--step1\n"
                          + "--step1 done\n"
                          + "--failingStep\n"
                          + "----> STEP FAILED\n"
                          + "--step2\n"
                          + "----> STEP IGNORED\n"
                          + "-step7 done\n"
                          + "TEST DONE\n";
        assertThat(sampleStepListener.toString(), is(expectedSteps));
    }

    @Test
    public void should_skip_nested_steps_after_a_step_failure() {
        SimpleTestScenarioSteps steps = factory.getStepLibraryFor(SimpleTestScenarioSteps.class);

        StepEventBus.getEventBus().testStarted("a_test");
        steps.step1();
        steps.step7();
        StepEventBus.getEventBus().testFinished();

        String expectedSteps =
                  "TEST a_test\n"
                          + "-step1\n"
                          + "-step1 done\n"
                          + "-step7\n"
                          + "--step1\n"
                          + "--step1 done\n"
                          + "--failingStep\n"
                          + "----> STEP FAILED\n"
                          + "--step2\n"
                          + "----> STEP IGNORED\n"
                          + "-step7 done\n"
                          + "TEST DONE\n";
        assertThat(sampleStepListener.toString(), is(expectedSteps));
    }

    @Test
    public void should_not_use_the_browser() {
        SimpleTestScenarioSteps steps = factory.getStepLibraryFor(SimpleTestScenarioSteps.class);

        StepEventBus.getEventBus().testStarted("a_test");
        steps.step1();
        steps.step7();
        StepEventBus.getEventBus().testFinished();

        String expectedSteps =
                  "TEST a_test\n"
                          + "-step1\n"
                          + "-step1 done\n"
                          + "-step7\n"
                          + "--step1\n"
                          + "--step1 done\n"
                          + "--failingStep\n"
                          + "----> STEP FAILED\n"
                          + "--step2\n"
                          + "----> STEP IGNORED\n"
                          + "-step7 done\n"
                          + "TEST DONE\n";
        assertThat(sampleStepListener.toString(), is(expectedSteps));
    }

    @Test
    public void a_step_can_return_a_step_object() {
        SimpleTestScenarioSteps steps = factory.getStepLibraryFor(SimpleTestScenarioSteps.class);

        StepEventBus.getEventBus().testStarted("a_test");
        steps.stepThatReturnsAStep().stepThatReturnsAStep().stepThatReturnsAStep();
        StepEventBus.getEventBus().testFinished();

        String expectedSteps =
                  "TEST a_test\n"
                          + "-stepThatReturnsAStep\n"
                          + "-stepThatReturnsAStep done\n"
                          + "-stepThatReturnsAStep\n"
                          + "-stepThatReturnsAStep done\n"
                          + "-stepThatReturnsAStep\n"
                          + "-stepThatReturnsAStep done\n"
                          + "TEST DONE\n";
        assertThat(sampleStepListener.toString(), is(expectedSteps));
    }

    @Test
    public void a_step_can_return_a_step_object_if_a_failure_occurs() {
        SimpleTestScenarioSteps steps = factory.getStepLibraryFor(SimpleTestScenarioSteps.class);

        StepEventBus.getEventBus().testStarted("a_test");
        steps.stepThatFailsAndReturnsAStep().stepThatReturnsAStep();
        StepEventBus.getEventBus().testFinished();

        String expectedSteps =
                  "TEST a_test\n"
                          + "-stepThatFailsAndReturnsAStep\n"
                          + "---> STEP FAILED\n"
                          + "-stepThatReturnsAStep\n"
                          + "---> STEP IGNORED\n"
                          + "TEST DONE\n";
        assertThat(sampleStepListener.toString(), is(expectedSteps));
    }

}
