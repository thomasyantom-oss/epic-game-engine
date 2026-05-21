package com.epic.engine.combat.model;

public class Combatant {

    private final String id;
    private final String name;
    private final Side side;
    private final Position position;
    private int maxHp;
    private int hp;
    private int attack;
    private int defense;
    private int speed;
    private boolean defending;

    public Combatant(String id, String name, Side side, Position position,
                     int maxHp, int attack, int defense, int speed) {
        this.id = id;
        this.name = name;
        this.side = side;
        this.position = position;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.attack = attack;
        this.defense = defense;
        this.speed = speed;
        this.defending = false;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Side getSide() { return side; }
    public Position getPosition() { return position; }
    public int getMaxHp() { return maxHp; }
    public int getHp() { return hp; }
    public int getAttack() { return attack; }
    public int getDefense() { return defense; }
    public int getSpeed() { return speed; }
    public boolean isDefending() { return defending; }
    public boolean isAlive() { return hp > 0; }

    public void takeDamage(int amount) {
        this.hp = Math.max(0, this.hp - amount);
    }

    public void setDefending(boolean defending) {
        this.defending = defending;
    }
}
