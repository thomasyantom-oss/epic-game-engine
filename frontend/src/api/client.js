const BASE_URL = '/api'

let sessionToken = localStorage.getItem('epic_session_token')

function getHeaders() {
    const headers = { 'Content-Type': 'application/json' }
    if (sessionToken) {
        headers['X-Session-Token'] = sessionToken
    }
    return headers
}

function saveToken(snapshot) {
    if (snapshot && snapshot.sessionToken) {
        sessionToken = snapshot.sessionToken
        localStorage.setItem('epic_session_token', sessionToken)
    }
}

export async function performAction(type, params = {}) {
    const response = await fetch(`${BASE_URL}/action`, {
        method: 'POST',
        headers: getHeaders(),
        body: JSON.stringify({ type, params })
    })
    const data = await response.json()
    if (!response.ok || !data.phase) {
        console.error('Action failed:', type, data)
        return null
    }
    saveToken(data)
    return data
}

export async function getSnapshot() {
    const response = await fetch(`${BASE_URL}/snapshot`, {
        headers: getHeaders()
    })
    const data = await response.json()
    saveToken(data)
    return data
}
