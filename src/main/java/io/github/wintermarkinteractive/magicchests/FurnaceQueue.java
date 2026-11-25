package io.github.wintermarkinteractive.magicchests;

import org.bukkit.Material;

import java.util.UUID;

/** A furnace that keeps pulling one input item (and fuel) from its owner's Magic Chests. */
public final class FurnaceQueue {

    private final MagicChest.Key key;
    private final UUID owner;
    private final Material input;
    private int remaining;
    private int total;
    private boolean output;
    /** Runtime only: the owner was already told that fuel ran out. */
    boolean warnedFuel;

    public FurnaceQueue(MagicChest.Key key, UUID owner, Material input, int remaining, int total, boolean output) {
        this.key = key;
        this.owner = owner;
        this.input = input;
        this.remaining = remaining;
        this.total = total;
        this.output = output;
    }

    public MagicChest.Key key() { return key; }
    public UUID owner() { return owner; }
    public Material input() { return input; }
    /** Items still to load from the chests into the furnace. */
    public int remaining() { return remaining; }
    /** Items this queue was started with. */
    public int total() { return total; }
    public boolean output() { return output; }

    void remaining(int r) { remaining = Math.max(0, r); }
    void output(boolean o) { output = o; }
}
