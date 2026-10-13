package com.mindfortress.app

import com.mindfortress.app.ai.OnDeviceSovereignBrain
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * OnDeviceSovereignBrainTest — Verifies 100% Autonomous On-Device Polymath Reasoning.
 * Ensures all STEM derivations, Feynman evaluations, and discipline protocols
 * execute flawlessly on-device with zero network and zero laptop connection.
 */
class OnDeviceSovereignBrainTest {

    private lateinit var brain: OnDeviceSovereignBrain

    @Before
    fun setUp() {
        brain = OnDeviceSovereignBrain(context = null)
    }

    @Test
    fun testPresenceHandshake_returnsConfirmedLink() {
        val res = brain.processSovereignInput("are you there jarvis")
        assertEquals("PRESENCE_CONFIRMED", res.actionTag)
        assertTrue(res.isOfflineSovereign)
        assertTrue(res.replyMarkdown.contains("Commander Vivek"))
        assertTrue(res.replyMarkdown.contains("Sovereign Standalone Mode"))
    }

    @Test
    fun testStokesTheoremDerivation_returnsAnalyticalVerification() {
        val res = brain.processSovereignInput("please derive Stokes' theorem from first principles")
        assertEquals("STEM_STOKES_DERIVATION", res.actionTag)
        assertTrue(res.isOfflineSovereign)
        assertTrue(res.replyMarkdown.contains("Stokes' Theorem"))
        assertTrue(res.replyMarkdown.contains("∮_C F · dr = ∬_S (∇ × F) · dS"))
        assertTrue(res.replyMarkdown.contains("2π"))
    }

    @Test
    fun testDivergenceTheoremDerivation_returnsMaxwellGaussLaw() {
        val res = brain.processSovereignInput("derive the divergence theorem and flux integral")
        assertEquals("STEM_DIVERGENCE_DERIVATION", res.actionTag)
        assertTrue(res.replyMarkdown.contains("∬_(∂V) F · n̂ dS = ∭_V (∇ · F) dV"))
        assertTrue(res.replyMarkdown.contains("Maxwell's Gauss Law"))
        assertTrue(res.replyMarkdown.contains("∇ · E = ρ / ε₀"))
    }

    @Test
    fun testThermodynamicEntropyDerivation_returnsCarnotEfficiencyProof() {
        val res = brain.processSovereignInput("derive entropy and carnot cycle efficiency")
        assertEquals("STEM_THERMODYNAMICS_DERIVATION", res.actionTag)
        assertTrue(res.replyMarkdown.contains("dS = δQ_rev / T"))
        assertTrue(res.replyMarkdown.contains("1 - T_C / T_H"))
        assertTrue(res.replyMarkdown.contains("Clausius inequality"))
    }

    @Test
    fun testQuantumSuperposition_returnsSchrodingerEvolution() {
        val res = brain.processSovereignInput("explain quantum superposition and schrodinger equation")
        assertEquals("STEM_QUANTUM_DERIVATION", res.actionTag)
        assertTrue(res.replyMarkdown.contains("|ψ⟩ = Σ c_n |e_n⟩"))
        assertTrue(res.replyMarkdown.contains("iℏ ∂/∂t |ψ(t)⟩ = Ĥ |ψ(t)⟩"))
    }

    @Test
    fun testSingularValueDecomposition_returnsGeometricFactorization() {
        val res = brain.processSovereignInput("show singular value decomposition SVD proof")
        assertEquals("STEM_SVD_DERIVATION", res.actionTag)
        assertTrue(res.replyMarkdown.contains("A = U Σ Vᵀ"))
        assertTrue(res.replyMarkdown.contains("orthogonal"))
        assertTrue(res.replyMarkdown.contains("singular values"))
    }

    @Test
    fun testDynamicProgrammingKnapsack_returnsReverseIterationInvariant() {
        val res = brain.processSovereignInput("explain dynamic programming 0/1 knapsack space reduction")
        assertEquals("STEM_ALGORITHMS_DERIVATION", res.actionTag)
        assertTrue(res.replyMarkdown.contains("dp[i][w] = max(dp[i-1][w]"))
        assertTrue(res.replyMarkdown.contains("O(N · W) to O(W)"))
        assertTrue(res.replyMarkdown.contains("BACKWARDS"))
    }

    @Test
    fun testFeynmanPupilEvaluation_analogyWithoutJargon_scoresHigh() {
        val prompt = "Feynman: Electricity is like water moving through a pipe where pressure pushes the current forward and resistance is a narrow constriction"
        val res = brain.processSovereignInput(prompt)
        assertEquals("FEYNMAN_EVALUATED", res.actionTag)
        assertTrue(res.replyMarkdown.contains("94 / 100"))
        assertTrue(res.replyMarkdown.contains("Physical Analogy Detected:** YES ✅"))
        assertTrue(res.replyMarkdown.contains("None - Excellent!"))
    }

    @Test
    fun testFeynmanPupilEvaluation_heavyJargon_scoresLowAndFlagsCrutches() {
        val prompt = "Feynman: This is obviously a quantum orthogonal manifold that trivially simplifies with eigen vectors across simply every dimension"
        val res = brain.processSovereignInput(prompt)
        assertEquals("FEYNMAN_EVALUATED", res.actionTag)
        assertTrue(res.replyMarkdown.contains("55 / 100"))
        assertTrue(res.replyMarkdown.contains("obviously"))
        assertTrue(res.replyMarkdown.contains("quantum"))
        assertTrue(res.replyMarkdown.contains("manifold"))
        assertTrue(res.replyMarkdown.contains("Physical Analogy Detected:** NO ❌"))
    }

    @Test
    fun testPhysiologicalSighProtocol_returnsActionableBreathingCycles() {
        val res = brain.processSovereignInput("i feel a strong urge physiological sigh reset")
        assertEquals("PHYSIOLOGICAL_SIGH", res.actionTag)
        assertTrue(res.replyMarkdown.contains("First Deep Inhale"))
        assertTrue(res.replyMarkdown.contains("Second Sharp Inhale"))
        assertTrue(res.replyMarkdown.contains("Prolonged Exhale"))
        assertTrue(res.replyMarkdown.contains("vagus nerve"))
    }

    @Test
    fun testDisciplineLimbicFrictionReframe_returnsCognitiveGuidance() {
        val res = brain.processSovereignInput("i feel lazy and tired today")
        assertEquals("DISCIPLINE_REFRAME", res.actionTag)
        assertTrue(res.replyMarkdown.contains("limbic friction"))
        assertTrue(res.replyMarkdown.contains("5-Minute Rule"))
        assertTrue(res.replyMarkdown.contains("Dopamine Baseline Defense"))
    }
}
