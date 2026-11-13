package com.remotemix.app

data class IrMatchCandidate(val codeId: String, val powerSignature: String, val verifyKey: String)

enum class IrMatchPhase { POWER, VERIFY }

data class IrMatchProbe(
    val codeId: String,
    val keyId: String,
    val phase: IrMatchPhase,
    val groupNumber: Int,
    val groupCount: Int,
    val variantNumber: Int,
    val variantCount: Int,
    val sent: Boolean
)

/** Groups remotes with an identical power signal, then verifies a second key. */
class IrMatchSession(candidates: List<IrMatchCandidate>) {
    private val groups = candidates.groupByTo(linkedMapOf()) { it.powerSignature }.values.toList()
    private data class Position(val group: Int, val variant: Int, val phase: IrMatchPhase)
    private val history = mutableListOf<Position>()
    private var groupIndex = 0
    private var variantIndex = 0
    private var phase = IrMatchPhase.POWER
    private var sent = false

    val totalGroups: Int get() = groups.size

    fun current(): IrMatchProbe? {
        val group = groups.getOrNull(groupIndex) ?: return null
        val candidate = group[if (phase == IrMatchPhase.POWER) 0 else variantIndex]
        return IrMatchProbe(
            candidate.codeId,
            if (phase == IrMatchPhase.POWER) "power" else candidate.verifyKey,
            phase, groupIndex + 1, groups.size, variantIndex + 1, group.size, sent
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
        history += Position(groupIndex, variantIndex, phase)
        sent = false
        if (responded && phase == IrMatchPhase.VERIFY) return probe.codeId
        if (responded) {
            phase = IrMatchPhase.VERIFY
            variantIndex = 0
        } else {
            advancePastCurrent()
        }
        return null
    }

    fun skip() {
        if (current() == null) return
        history += Position(groupIndex, variantIndex, phase)
        sent = false
        advancePastCurrent()
    }

    fun back(): Boolean {
        if (history.isEmpty()) return false
        val previous = history.removeAt(history.lastIndex)
        groupIndex = previous.group
        variantIndex = previous.variant
        phase = previous.phase
        sent = false
        return true
    }

    fun snapshot(): IntArray = intArrayOf(groupIndex, variantIndex, phase.ordinal, history.size) +
        history.flatMap { listOf(it.group, it.variant, it.phase.ordinal) }

    fun restore(snapshot: IntArray) {
        require(snapshot.size >= 4 && snapshot[3] >= 0 && snapshot.size == 4 + snapshot[3] * 3)
        val newGroup = snapshot[0]
        val newVariant = snapshot[1]
        val newPhase = IrMatchPhase.entries.getOrNull(snapshot[2]) ?: error("无效的匹配阶段")
        require(newGroup in 0..groups.size)
        require(newGroup == groups.size || newVariant in groups[newGroup].indices)
        groupIndex = newGroup
        variantIndex = newVariant
        phase = newPhase
        history.clear()
        for (offset in 4 until snapshot.size step 3) {
            val oldPhase = IrMatchPhase.entries.getOrNull(snapshot[offset + 2]) ?: error("无效的历史阶段")
            history += Position(snapshot[offset], snapshot[offset + 1], oldPhase)
        }
        sent = false // A resumed wizard asks the user to transmit again before answering.
    }

    private fun advancePastCurrent() {
        val group = groups.getOrNull(groupIndex) ?: return
        if (phase == IrMatchPhase.VERIFY && variantIndex + 1 < group.size) {
            variantIndex++
        } else {
            groupIndex++
            variantIndex = 0
            phase = IrMatchPhase.POWER
        }
    }
}
