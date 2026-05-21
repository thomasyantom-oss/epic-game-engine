const BASE_URL = '/api'

export async function fetchScene(sceneId) {
    const response = await fetch(`${BASE_URL}/scene/${sceneId}`)
    if (!response.ok) return null
    return response.json()
}

export async function performAction(playerId, type, params) {
    const response = await fetch(`${BASE_URL}/action`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerId, type, params })
    })
    return response.json()
}

export async function getPlayerState(playerId) {
    const response = await fetch(`${BASE_URL}/save/${playerId}`)
    return response.json()
}

export async function getCombatState(playerId) {
    const response = await fetch(`${BASE_URL}/combat/${playerId}`)
    if (!response.ok) return null
    return response.json()
}

export async function submitCombatCommands(playerId, commands) {
    const response = await fetch(`${BASE_URL}/combat/${playerId}/commands`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(commands)
    })
    return response.json()
}

export async function getCombatTargets(playerId, combatantId) {
    const response = await fetch(`${BASE_URL}/combat/${playerId}/targets/${combatantId}`)
    if (!response.ok) return []
    return response.json()
}
