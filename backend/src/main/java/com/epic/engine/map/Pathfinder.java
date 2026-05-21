package com.epic.engine.map;

import java.util.*;

public class Pathfinder {

    private static final int[][] DIRECTIONS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    public static List<int[]> findPath(boolean[][] passable, int startX, int startY, int endX, int endY) {
        int height = passable.length;
        int width = passable[0].length;

        if (endX < 0 || endX >= width || endY < 0 || endY >= height || !passable[endY][endX]) {
            return null;
        }

        if (startX == endX && startY == endY) {
            return List.of(new int[]{startX, startY});
        }

        PriorityQueue<int[]> open = new PriorityQueue<>(Comparator.comparingInt(a -> a[2]));
        open.add(new int[]{startX, startY, 0});
        Map<Long, Long> cameFrom = new HashMap<>();
        Map<Long, Integer> gScore = new HashMap<>();
        long startKey = key(startX, startY, width);
        gScore.put(startKey, 0);

        while (!open.isEmpty()) {
            int[] current = open.poll();
            int cx = current[0], cy = current[1];

            if (cx == endX && cy == endY) {
                return reconstructPath(cameFrom, cx, cy, startX, startY, width);
            }

            for (int[] dir : DIRECTIONS) {
                int nx = cx + dir[0], ny = cy + dir[1];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height || !passable[ny][nx]) {
                    continue;
                }
                long nKey = key(nx, ny, width);
                int tentativeG = gScore.getOrDefault(key(cx, cy, width), Integer.MAX_VALUE) + 1;
                if (tentativeG < gScore.getOrDefault(nKey, Integer.MAX_VALUE)) {
                    cameFrom.put(nKey, key(cx, cy, width));
                    gScore.put(nKey, tentativeG);
                    int h = Math.abs(nx - endX) + Math.abs(ny - endY);
                    open.add(new int[]{nx, ny, tentativeG + h});
                }
            }
        }

        return null;
    }

    private static long key(int x, int y, int width) {
        return (long) y * width + x;
    }

    private static List<int[]> reconstructPath(Map<Long, Long> cameFrom, int endX, int endY, int startX, int startY, int width) {
        List<int[]> path = new ArrayList<>();
        long current = key(endX, endY, width);
        long startKey = key(startX, startY, width);
        path.add(new int[]{endX, endY});

        while (current != startKey) {
            current = cameFrom.get(current);
            int x = (int) (current % width);
            int y = (int) (current / width);
            path.add(new int[]{x, y});
        }

        Collections.reverse(path);
        return path;
    }
}
