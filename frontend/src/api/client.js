const BASE_URL = '/api'

export async function performAction(playerId, type, params = {}) {
    const response = await fetch(`${BASE_URL}/v2/action`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerId, type, params })
    })
    return response.json()
}

export async function getSnapshot(playerId) {
    const response = await fetch(`${BASE_URL}/snapshot/${playerId}`)
    return response.json()
}
