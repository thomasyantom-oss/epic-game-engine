import { reactive } from 'vue'
import { fetchMap, mapMove, mapMoveTo, getMapPosition } from '../api/client.js'
import { useGameState } from './useGameState.js'

const mapState = reactive({
    mapData: null,
    playerX: 0,
    playerY: 0,
    moving: false,
    currentPath: [],
    poi: null
})

let pathTimer = null

export function useMap() {
    const { state } = useGameState()

    async function loadMap() {
        const pos = await getMapPosition(state.playerId)
        if (pos) {
            mapState.playerX = pos.x
            mapState.playerY = pos.y
        }
        mapState.mapData = await fetchMap(pos?.mapId || 'world_map')
    }

    async function moveDirection(direction) {
        if (mapState.moving) return
        cancelPath()

        const result = await mapMove(state.playerId, direction)
        if (result.success) {
            mapState.playerX = result.newX
            mapState.playerY = result.newY
            mapState.poi = result.poi || null
        }
        return result
    }

    async function moveToTarget(targetX, targetY) {
        if (mapState.moving) return
        cancelPath()

        const result = await mapMoveTo(state.playerId, targetX, targetY)
        if (!result.success || !result.path || result.path.length <= 1) return result

        mapState.moving = true
        mapState.currentPath = result.path
        animatePath(result.path, 1)
        return result
    }

    function animatePath(path, index) {
        if (index >= path.length || !mapState.moving) {
            mapState.moving = false
            mapState.currentPath = []
            return
        }

        pathTimer = setTimeout(async () => {
            const step = path[index]
            const dx = step.x - mapState.playerX
            const dy = step.y - mapState.playerY
            const dir = dx > 0 ? 'RIGHT' : dx < 0 ? 'LEFT' : dy > 0 ? 'DOWN' : 'UP'

            const result = await mapMove(state.playerId, dir)
            if (result.success) {
                mapState.playerX = result.newX
                mapState.playerY = result.newY
                if (result.poi) {
                    mapState.poi = result.poi
                    mapState.moving = false
                    mapState.currentPath = []
                    return
                }
            } else {
                mapState.moving = false
                mapState.currentPath = []
                return
            }

            animatePath(path, index + 1)
        }, 200)
    }

    function cancelPath() {
        if (pathTimer) {
            clearTimeout(pathTimer)
            pathTimer = null
        }
        mapState.moving = false
        mapState.currentPath = []
    }

    function clearPoi() {
        mapState.poi = null
    }

    return { mapState, loadMap, moveDirection, moveToTarget, cancelPath, clearPoi }
}
