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
