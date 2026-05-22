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

export async function endCombat(playerId) {
    await fetch(`${BASE_URL}/combat/${playerId}/end`, { method: 'POST' })
}

export async function fetchMap(mapId) {
    const response = await fetch(`${BASE_URL}/map/${mapId}`)
    if (!response.ok) return null
    return response.json()
}

export async function mapMove(playerId, direction) {
    const response = await fetch(`${BASE_URL}/map/move`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerId, direction })
    })
    return response.json()
}

export async function mapMoveTo(playerId, targetX, targetY) {
    const response = await fetch(`${BASE_URL}/map/moveTo`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerId, targetX, targetY })
    })
    return response.json()
}

export async function getMapPosition(playerId) {
    const response = await fetch(`${BASE_URL}/map/position/${playerId}`)
    if (!response.ok) return null
    return response.json()
}
