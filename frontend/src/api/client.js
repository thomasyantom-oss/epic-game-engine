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

let onError = null

export function setErrorHandler(handler) {
    onError = handler
}

export async function performAction(type, params = {}) {
    try {
        const response = await fetch(`${BASE_URL}/action`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify({ type, params })
        })
        const data = await response.json()
        if (!response.ok || !data.phase) {
            const msg = data.message || data.error || '操作失败'
            if (onError) onError(msg)
            return null
        }
        saveToken(data)
        return data
    } catch (e) {
        if (onError) onError('网络错误: ' + e.message)
        return null
    }
}

export async function getSnapshot() {
    const response = await fetch(`${BASE_URL}/snapshot`, {
        headers: getHeaders()
    })
    const data = await response.json()
    saveToken(data)
    return data
}
