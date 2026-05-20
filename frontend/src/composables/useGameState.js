import { reactive } from 'vue'
import { getPlayerState, fetchScene, performAction } from '../api/client.js'

const state = reactive({
    playerId: 'player1',
    currentScene: null,
    loading: false
})

export function useGameState() {
    async function initialize() {
        state.loading = true
        const playerState = await getPlayerState(state.playerId)
        const scene = await fetchScene(playerState.currentScene)
        state.currentScene = scene
        state.loading = false
    }

    async function doAction(type, params) {
        state.loading = true
        const response = await performAction(state.playerId, type, params)
        return response
    }

    async function refreshScene(sceneId) {
        const scene = await fetchScene(sceneId)
        state.currentScene = scene
    }

    return { state, initialize, doAction, refreshScene }
}
