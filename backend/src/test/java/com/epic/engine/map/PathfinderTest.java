package com.epic.engine.map;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PathfinderTest {

    @Test
    void directPathNoObstacles() {
        boolean[][] passable = {
                {true, true, true},
                {true, true, true},
                {true, true, true}
        };
        List<int[]> path = Pathfinder.findPath(passable, 0, 0, 2, 2);
        assertThat(path).isNotNull();
        assertThat(path.get(0)).containsExactly(0, 0);
        assertThat(path.get(path.size() - 1)).containsExactly(2, 2);
        assertThat(path).hasSize(5);
    }

    @Test
    void pathAroundObstacle() {
        boolean[][] passable = {
                {true,  true,  true,  true},
                {true,  false, false, true},
                {true,  false, false, true},
                {true,  true,  true,  true}
        };
        List<int[]> path = Pathfinder.findPath(passable, 0, 0, 3, 3);
        assertThat(path).isNotNull();
        assertThat(path.get(path.size() - 1)).containsExactly(3, 3);
        for (int[] pos : path) {
            assertThat(passable[pos[1]][pos[0]]).isTrue();
        }
    }

    @Test
    void noPathWhenBlocked() {
        boolean[][] passable = {
                {true,  true,  true},
                {true,  false, true},
                {true,  true,  true}
        };
        List<int[]> path = Pathfinder.findPath(passable, 0, 0, 1, 1);
        assertThat(path).isNull();
    }

    @Test
    void sameStartAndEnd() {
        boolean[][] passable = {{true, true}, {true, true}};
        List<int[]> path = Pathfinder.findPath(passable, 0, 0, 0, 0);
        assertThat(path).hasSize(1);
        assertThat(path.get(0)).containsExactly(0, 0);
    }

    @Test
    void adjacentMove() {
        boolean[][] passable = {{true, true}, {true, true}};
        List<int[]> path = Pathfinder.findPath(passable, 0, 0, 1, 0);
        assertThat(path).hasSize(2);
    }
}
