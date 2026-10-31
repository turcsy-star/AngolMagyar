package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class PlatinumWorkflowTest {
    private fun accepted(outcome: PlatinumWorkflow.Outcome): PlatinumWorkflow.Run =
        (outcome as PlatinumWorkflow.Outcome.Accepted).run

    @Test fun offlineTemplateHasDeterministicRunnableSteps() {
        val run = accepted(PlatinumWorkflow.create("run-1", PlatinumWorkflow.textTranslationTemplate, true))
        assertEquals("prepare", PlatinumWorkflow.nextRunnable(run, emptySet(), false))
        val started = accepted(PlatinumWorkflow.start(run, "prepare", emptySet(), false))
        val completed = accepted(PlatinumWorkflow.complete(started, "prepare"))
        assertEquals("translate_local", PlatinumWorkflow.nextRunnable(completed, setOf("offline_translation"), false))
    }

    @Test fun offlineRunRejectsNetworkRequiredStep() {
        val definition = PlatinumWorkflow.Definition("network", PlatinumWorkflow.Kind.TEXT_TRANSLATION, listOf(
            PlatinumWorkflow.StepDefinition("cloud", "Felhőfordítás", requiresNetwork = true)
        ))
        val run = accepted(PlatinumWorkflow.create("run-2", definition, true))
        val result = PlatinumWorkflow.start(run, "cloud", emptySet(), true)
        assertTrue(result is PlatinumWorkflow.Outcome.Rejected)
        assertEquals(PlatinumWorkflow.StepStatus.PENDING, run.steps.single().status)
    }

    @Test fun dependencyMustSucceedBeforeNextStepCanStart() {
        val run = accepted(PlatinumWorkflow.create("run-3", PlatinumWorkflow.textTranslationTemplate, true))
        assertTrue(PlatinumWorkflow.start(run, "translate_local", setOf("offline_translation"), false) is PlatinumWorkflow.Outcome.Rejected)
    }

    @Test fun failedStepCanBeRetriedWithinAttemptLimit() {
        var run = accepted(PlatinumWorkflow.create("run-4", PlatinumWorkflow.textTranslationTemplate, true))
        run = accepted(PlatinumWorkflow.start(run, "prepare", emptySet(), false))
        run = accepted(PlatinumWorkflow.fail(run, "prepare", "  IO error  "))
        assertEquals(PlatinumWorkflow.RunStatus.FAILED, run.status)
        run = accepted(PlatinumWorkflow.retry(run, "prepare"))
        assertEquals(PlatinumWorkflow.StepStatus.PENDING, run.steps.first().status)
        assertEquals(1, run.steps.first().attempts)
    }

    @Test fun invalidDependencyCycleIsRejected() {
        val definition = PlatinumWorkflow.Definition("cycle", PlatinumWorkflow.Kind.TEXT_TRANSLATION, listOf(
            PlatinumWorkflow.StepDefinition("a", "A", setOf("b")),
            PlatinumWorkflow.StepDefinition("b", "B", setOf("a"))
        ))
        assertTrue(PlatinumWorkflow.create("run-5", definition, true) is PlatinumWorkflow.Outcome.Rejected)
    }

    @Test fun completionAndCancellationAreTerminal() {
        var run = accepted(PlatinumWorkflow.create("run-6", PlatinumWorkflow.Definition("one", PlatinumWorkflow.Kind.TEXT_TRANSLATION,
            listOf(PlatinumWorkflow.StepDefinition("only", "Egyetlen lépés"))), true))
        run = accepted(PlatinumWorkflow.start(run, "only", emptySet(), false))
        run = accepted(PlatinumWorkflow.complete(run, "only"))
        assertEquals(PlatinumWorkflow.RunStatus.COMPLETED, run.status)
        assertTrue(PlatinumWorkflow.cancel(run) is PlatinumWorkflow.Outcome.Rejected)
    }
}
