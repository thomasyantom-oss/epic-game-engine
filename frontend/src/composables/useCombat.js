import { reactive } from 'vue'
import { getCombatState, submitCombatCommands, getCombatTargets } from '../api/client.js'

const combat = reactive({
    active: false,
    state: null,
    results: [],
    pendingCommands: [],
    validTargets: [],
    currentActorIndex: 0
})

export function useCombat() {
    async function enterCombat(playerId) {
        const state = await getCombatState(playerId)
        if (state) {
            combat.active = true
            combat.state = state
            combat.results = []
            combat.pendingCommands = []
            combat.currentActorIndex = 0
            await loadTargets(playerId)
        }
    }

    async function loadTargets(playerId) {
        const playerUnits = combat.state.combatants.filter(
            c => c.side === 'PLAYER' && c.alive
        )
        if (combat.currentActorIndex < playerUnits.length) {
            const actor = playerUnits[combat.currentActorIndex]
            combat.validTargets = await getCombatTargets(playerId, actor.id)
        }
    }

    function getCurrentActor() {
        if (!combat.state) return null
        const playerUnits = combat.state.combatants.filter(
            c => c.side === 'PLAYER' && c.alive
        )
        return playerUnits[combat.currentActorIndex] || null
    }

    function addCommand(actorId, type, targetId) {
        combat.pendingCommands.push({ actorId, type, targetId })
        combat.currentActorIndex++
    }

    function allCommandsIssued() {
        const playerUnits = combat.state.combatants.filter(
            c => c.side === 'PLAYER' && c.alive
        )
        return combat.pendingCommands.length >= playerUnits.length
    }

    async function executeRound(playerId) {
        const result = await submitCombatCommands(playerId, combat.pendingCommands)
        combat.results = result.results
        combat.state = result.state
        combat.pendingCommands = []
        combat.currentActorIndex = 0

        if (combat.state.phase === 'COMMAND') {
            await loadTargets(playerId)
        }
    }

    function exitCombat() {
        combat.active = false
        combat.state = null
        combat.results = []
        combat.pendingCommands = []
        combat.currentActorIndex = 0
        combat.validTargets = []
    }

    return {
        combat,
        enterCombat,
        loadTargets,
        getCurrentActor,
        addCommand,
        allCommandsIssued,
        executeRound,
        exitCombat
    }
}
