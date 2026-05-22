import { reactive, ref } from 'vue'
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

const mapLog = ref([])

const TERRAIN_NAMES = { forest: '密林', grass: '草地', road: '道路', sand: '沙地', water: '水域', town: '城镇', mountain: '山地' }

function pushMoveLog(result) {
    if (!result.success || !mapState.mapData) return
    const row = mapState.mapData.terrain[result.newY]
    const ch = row.charAt(result.newX)
    const terrain = mapState.mapData.terrains.find(t => t.char === ch)
    const name = terrain ? (TERRAIN_NAMES[terrain.id] || ch) : ch
    const color = terrain?.color || '#999'
    mapLog.value.push({
        type: 'scene',
        segments: [
            { text: '你来到了', color: '#ffffff' },
            { text: name, color: color },
            { text: '。', color: '#ffffff' }
        ]
    })
    if (result.poi) {
        mapLog.value.push({
            type: 'scene',
            segments: [
                { text: '前方可以', color: '#ffffff' },
                { text: result.poi.label, color: '#ffffff' },
                { text: '。', color: '#ffffff' }
            ]
        })
    }
}

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
        if (mapState.mapData) {
            const poi = mapState.mapData.pois.find(p => p.x === mapState.playerX && p.y === mapState.playerY)
            mapState.poi = poi || null
        }
    }

    async function moveDirection(direction) {
        if (mapState.moving) return
        cancelPath()

        const result = await mapMove(state.playerId, direction)
        if (result.success) {
            mapState.playerX = result.newX
            mapState.playerY = result.newY
            mapState.poi = result.poi || null
            pushMoveLog(result)
        }
        return result
    }

    async function moveToTarget(targetX, targetY) {
        if (mapState.moving) return
        cancelPath()
        mapState.poi = null

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
                pushMoveLog(result)
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

    return { mapState, mapLog, loadMap, moveDirection, moveToTarget, cancelPath, clearPoi }
}
