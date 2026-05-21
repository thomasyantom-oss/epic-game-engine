package com.epic.engine.combat.model;

public record Position(Row row, int slot) {

    public enum Row {
        FRONT(0),
        MID(1),
        BACK(2);

        private final int distance;

        Row(int distance) {
            this.distance = distance;
        }

        public int distance() {
            return distance;
        }
    }
}
