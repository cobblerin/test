package kr.cobblerin.pokedex.client;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class DexModels {
    private final Map<Model, ModelWidget> models = new HashMap<>();
    private final Set<Model> unavailable = new HashSet<>();

    boolean render(DrawContext draw, int dex, Set<String> aspects, int x, int y, int size, float delta) {
        var key = new Model(dex, aspects);
        if (unavailable.contains(key)) {
            return false;
        }
        var species = PokemonSpecies.getByPokedexNumber(dex, "cobblemon");
        if (species == null || !species.getImplemented() && aspects.isEmpty()) {
            return false;
        }
        var matrices = draw.getMatrices();
        var transform = matrices.peek().getPositionMatrix();
        var start = transform.transformPosition(new Vector3f(x, y, 0));
        var end = transform.transformPosition(new Vector3f(x + size, y + size, 0));
        int screenX = Math.round(start.x);
        int screenY = Math.round(start.y);
        int pixels = Math.max(1, Math.round(Math.min(end.x - start.x, end.y - start.y)));
        try {
            var widget = models.computeIfAbsent(key, ignored -> new ModelWidget(screenX, screenY, pixels, pixels,
                    new RenderablePokemon(species, aspects, ItemStack.EMPTY), pixels / 45f, 325f,
                    -pixels * 0.15, false, false, 15));
            widget.setDimensions(pixels, pixels);
            widget.setX(screenX);
            widget.setY(screenY);
            widget.setBaseScale(pixels / 45f);
            widget.setOffsetY(-pixels * 0.15);
            draw.draw();
            matrices.push();
            try {
                matrices.loadIdentity();
                widget.render(draw, -1, -1, delta);
                draw.draw();
            } finally {
                matrices.pop();
            }
            return true;
        } catch (RuntimeException exception) {
            unavailable.add(key);
            DexClient.LOG.debug("Could not render Pokédex model {}", dex, exception);
            return false;
        }
    }

    private record Model(int dex, Set<String> aspects) {}
}
