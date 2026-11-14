package com.remotemix.app

data class IrMatchCandidate(val codeId: String, val powerSignature: String, val verifySignals: Map<String, String>)

enum class IrMatchPhase { POWER, VERIFY }

data class IrMatchProbe(
    val codeId: String,
    val keyId: String,
    val phase: IrMatchPhase,
    val groupNumber: Int,
    val groupCount: Int,
    val variantNumber: Int,
    val variantCount: Int,
    val sent: Boolean,
    val verifyNumber: Int = 0,
    val verifyCount: Int = 0
)

/** Groups remotes with an identical power signal, then tests keys that distinguish the variants. */
class IrMatchSession(candidates: List<IrMatchCandidate>) {
    private data class Variant(val codeId: String, val keys: List<String>)
    private val groups = candidates.filter { it.verifySignals.isNotEmpty() }.groupBy { it.powerSignature }.values.map { group ->
        group.map { candidate -> Variant(candidate.codeId, verificationPlan(candidate, group)) }
    }
    private data class Position(val group: Int, val variant: Int, val phase: IrMatchPhase, val verify: Int)
    private val history = mutableListOf<Position>()
    private var groupIndex = 0
    private var variantIndex = 0
    private var verifyIndex = 0
    private var phase = IrMatchPhase.POWER
    private var sent = false

    val totalGroups: Int get() = groups.size

    fun current(): IrMatchProbe? {
        val group = groups.getOrNull(groupIndex) ?: return null
        val candidate = group[if (phase == IrMatchPhase.POWER) 0 else variantIndex]
        return IrMatchProbe(
            candidate.codeId,
            if (phase == IrMatchPhase.POWER) "power" else candidate.keys[verifyIndex],
            phase, groupIndex + 1, groups.size, variantIndex + 1, group.size, sent,
            verifyIndex + 1, candidate.keys.size
        )
    }

    fun markSent() {
        check(current() != null) { "没有待测试的码组" }
        sent = true
    }

    /** Returns the confirmed code ID, or null when another test is needed. */
    fun answer(responded: Boolean): String? {
        check(sent) { "请先发射测试键" }
        val probe = requireNotNull(current())
        history += Position(groupIndex, variantIndex, phase, verifyIndex)
        sent = false
        if (responded && phase == IrMatchPhase.VERIFY) {
            if (probe.verifyNumber == probe.verifyCount) return probe.codeId
            verifyIndex++
            return null
        }
        if (responded) {
            phase = IrMatchPhase.VERIFY
            variantIndex = 0
            verifyIndex = 0
        } else {
            advancePastCurrent()
        }
        return null
    }

    fun skip() {
        if (current() == null) return
        history += Position(groupIndex, variantIndex, phase, verifyIndex)
        sent = false
        advancePastCurrent()
    }

    fun back(): Boolean {
        if (history.isEmpty()) return false
        val previous = history.removeAt(history.lastIndex)
        groupIndex = previous.group
        variantIndex = previous.variant
        phase = previous.phase
        verifyIndex = previous.verify
        sent = false
        return true
    }

    fun snapshot(): IntArray = intArrayOf(groupIndex, variantIndex, phase.ordinal, verifyIndex) +
        history.flatMap { listOf(it.group, it.variant, it.phase.ordinal, it.verify) }

    fun restore(snapshot: IntArray) {
        require(snapshot.size >= 4 && snapshot.size % 4 == 0)
        val positions = snapshot.toList().chunked(4).map { (group, variant, step, verify) ->
            val phase = IrMatchPhase.entries.getOrNull(step) ?: error("无效的匹配阶段")
            require(group in 0..groups.size)
            if (group == groups.size) require(phase == IrMatchPhase.POWER && variant == 0 && verify == 0)
            else {
                require(variant in groups[group].indices)
                require(verify in groups[group][variant].keys.indices)
            }
            Position(group, variant, phase, verify)
        }
        val current = positions.first()
        groupIndex = current.group
        variantIndex = current.variant
        phase = current.phase
        verifyIndex = current.verify
        history.clear()
        history += positions.drop(1)
        sent = false // A resumed wizard asks the user to transmit again before answering.
    }

    private fun advancePastCurrent() {
        val group = groups.getOrNull(groupIndex) ?: return
        verifyIndex = 0
        if (phase == IrMatchPhase.VERIFY && variantIndex + 1 < group.size) {
            variantIndex++
        } else {
            groupIndex++
            variantIndex = 0
            phase = IrMatchPhase.POWER
        }
    }

    private fun verificationPlan(candidate: IrMatchCandidate, group: List<IrMatchCandidate>): List<String> {
        val remaining = group.filter { it.codeId != candidate.codeId }.toMutableList()
        val keys = candidate.verifySignals.keys.toMutableList()
        val plan = mutableListOf<String>()
        while (remaining.isNotEmpty() && keys.isNotEmpty()) {
            fun differs(other: IrMatchCandidate, key: String) = other.verifySignals[key] != candidate.verifySignals[key]
            val key = keys.maxBy { key -> remaining.count { differs(it, key) } }
            if (remaining.none { differs(it, key) }) break
            plan += key
            remaining.removeAll { differs(it, key) }
            keys.remove(key)
        }
        return plan.ifEmpty { listOf(candidate.verifySignals.keys.first()) }
    }
}
