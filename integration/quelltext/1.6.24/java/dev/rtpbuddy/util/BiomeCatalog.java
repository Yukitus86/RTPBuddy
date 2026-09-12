package dev.rtpbuddy.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Every biome that can be searched for, grouped the way the search order asks
 * for them.
 *
 * <p>A family is a colour band - eighteen of them across some fifty biomes -
 * and that is the right grain for a map legend. It is the wrong grain for a
 * search order: nobody sets a run going to find "rare", they set it going to
 * find the Pale Garden. So the picker needs the individual biomes, and it needs
 * the ones the player has <em>never</em> landed in, which is exactly the set the
 * recorded landings cannot supply.
 *
 * <p>The server's own biome registry can. It holds every biome the current world
 * knows, vanilla or datapack, whether or not it has ever been visited. Landings
 * are folded in on top for the case where a biome was recorded on one server and
 * the player is now on another - a recorded name should never vanish from the
 * list that produced it.
 *
 * <p>Nothing here is cached. It runs when a menu opens, never per frame, and a
 * cache would only introduce a way for the list to go stale on a world change.
 */
public final class BiomeCatalog {

    private BiomeCatalog() {
    }

    /**
     * The biomes of one family, named in the player's language and sorted by
     * that name.
     *
     * @param extra biome ids to include beyond the registry, normally the ones
     *              the recorded landings hold
     */
    public static List<String> in(Biomes.Family family, Collection<String> extra) {
        List<String> found = new ArrayList<>();
        for (String id : all(extra)) {
            if (Biomes.family(id) == family) {
                found.add(id);
            }
        }
        found.sort(Comparator.comparing(Biomes::label, String.CASE_INSENSITIVE_ORDER));
        return found;
    }

    /** Registry ids plus whatever the caller passes, deduplicated. */
    private static Set<String> all(Collection<String> extra) {
        Set<String> ids = new LinkedHashSet<>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.world != null) {
            try {
                client.world.getRegistryManager().getOptional(RegistryKeys.BIOME)
                        .map(Registry::getIds)
                        .ifPresent(keys -> keys.forEach(id -> ids.add(id.toString())));
            } catch (RuntimeException ignored) {
                // No world registry yet; the recorded ids below still stand.
            }
        }
        if (extra != null) {
            for (String id : extra) {
                if (id != null && !id.isBlank()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }
}
