package com.heliosphere.planets.weapon.armor;

/**
 * The armour sets besides the space suits. Netherite is 3/8/6/3 (20 points), toughness 3. The combat uniforms
 * sit just under or at netherite and stop bullets better; the heavy suits go a little beyond it and pay for it
 * (the bomb suit is slow); the light suits protect little but do one thing well.
 */
public enum ArmorSet {
    //          id              dur  helm chest legs boots tough  kb    ench sound       special
    US_OCP     ("us_ocp",        30,  3,   7,    6,   3,   2.5f, 0.05f, 12, Sound.IRON, Special.BALLISTIC),
    RU_RATNIK  ("ru_ratnik",     32,  3,   8,    6,   3,   2.5f, 0.05f, 10, Sound.IRON, Special.BALLISTIC),
    PLA_STARRY ("pla_starry",    32,  3,   8,    6,   3,   2.5f, 0.05f, 12, Sound.IRON, Special.BALLISTIC),
    SWAT       ("swat",          35,  3,   8,    6,   3,   3.0f, 0.10f, 10, Sound.NETHERITE, Special.BALLISTIC),
    EOD        ("eod",           40,  4,   9,    8,   4,   4.0f, 0.40f,  6, Sound.NETHERITE, Special.BLAST),
    HAZMAT     ("hazmat",        22,  2,   5,    4,   2,   0.0f, 0.00f, 10, Sound.LEATHER, Special.HAZMAT),
    GHILLIE    ("ghillie",       18,  2,   5,    4,   2,   0.0f, 0.00f, 14, Sound.LEATHER, Special.STEALTH),
    EXO        ("exo",           45,  4,   9,    7,   4,   5.0f, 0.30f, 12, Sound.NETHERITE, Special.EXO),
    ALIEN      ("alien",         50,  4,   9,    7,   4,   4.0f, 0.20f, 18, Sound.NETHERITE, Special.ENERGY);

    public enum Sound { LEATHER, IRON, NETHERITE }

    /** What a set is good at; the effect grows with every piece worn (see ArmorEffects). */
    public enum Special { BALLISTIC, BLAST, HAZMAT, STEALTH, EXO, ENERGY }

    public final String id;
    public final int durability, helmet, chest, legs, boots, enchant;
    public final float toughness, knockback;
    public final Sound sound;
    public final Special special;

    ArmorSet(String id, int durability, int helmet, int chest, int legs, int boots, float toughness, float knockback, int enchant, Sound sound, Special special) {
        this.id = id; this.durability = durability; this.helmet = helmet; this.chest = chest; this.legs = legs; this.boots = boots;
        this.toughness = toughness; this.knockback = knockback; this.enchant = enchant; this.sound = sound; this.special = special;
    }
}
