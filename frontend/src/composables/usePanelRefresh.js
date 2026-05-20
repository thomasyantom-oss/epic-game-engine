import { useGameState } from './useGameState.js'
import { getPlayerState } from '../api/client.js'

export function usePanelRefresh() {
    const { state, refreshScene } = useGameState()

    async function handleRefresh(refreshPanels) {
        for (const panel of refreshPanels) {
            if (panel === 'SCENE') {
                const playerState = await getPlayerState(state.playerId)
                await refreshScene(playerState.currentScene)
            }
        }
        state.loading = false
    }

    return { handleRefresh }
}
