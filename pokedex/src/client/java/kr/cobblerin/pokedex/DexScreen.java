package kr.cobblerin.pokedex;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static kr.cobblerin.pokedex.DexProtocol.*;

final class DexScreen extends Screen {
    private static final int WIDTH = 660;
    private static final int HEIGHT = 384;
    private static final int TEXT = 0xfff4f4f4;
    private static final int MUTED = 0xffc4c7c9;
    private static final int GOLD = 0xffffdf93;
    private static final int GREEN = 0xff99d4a4;
    private static final int FRAME = 0xff34383b;
    private static final int RECESS = 0xff303437;
    private static final int TRIM = 0xff797e81;
    private static final Box CLOSE = new Box(580, 15, 60, 22);
    private static final Box PREVIOUS = new Box(514, 350, 32, 22);
    private static final Box NEXT = new Box(608, 350, 32, 22);
    private final List<Control> controls = new ArrayList<>();
    private final DexModels models = new DexModels();
    private Overview overview;
    private CandidatePage candidates;
    private Preview preview;
    private Mode mode = Mode.CATALOGUE;
    private Filter filter = Filter.ALL;
    private int generation = 1;
    private int page;
    private int waiting;
    private int ticksWaiting;
    private String waitingAction = "";
    private String message;
    private float scale = 1;
    private int originX;
    private int originY;
    private double mouseX;
    private double mouseY;

    DexScreen() {
        super(Text.literal("포켓몬 납부 도감"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    void refresh() {
        request(OVERVIEW, 0, 0, "");
    }

    private void request(String action, int species, int page, String value) {
        if (waiting != 0) {
            return;
        }
        waiting = DexClient.request(action, species, page, value);
        waitingAction = action;
        ticksWaiting = 0;
        message = waiting == 0 ? "서버에 연결할 수 없습니다." : null;
    }

    void accept(int sequence, View view) {
        if (sequence != 0 && sequence != waiting) {
            return;
        }
        controls.clear();
        String action = sequence == 0 ? OVERVIEW : waitingAction;
        waiting = 0;
        waitingAction = "";
        message = view.message();
        if (view.overview() != null) {
            overview = view.overview();
            if (action.equals(DONATE) || sequence == 0) {
                mode = Mode.CATALOGUE;
                candidates = null;
                preview = null;
            }
        }
        if (view.candidates() != null) {
            candidates = view.candidates();
            mode = Mode.CANDIDATES;
        }
        if (view.preview() != null) {
            preview = view.preview();
        } else if (action.equals(DONATE)) {
            preview = null;
        }
    }

    @Override
    public void tick() {
        if (waiting != 0 && ++ticksWaiting >= 200) {
            if (waitingAction.equals(DONATE)) {
                preview = null;
                candidates = null;
                mode = Mode.CATALOGUE;
            }
            waiting = 0;
            waitingAction = "";
            message = "서버 응답이 지연됩니다. 새로고침해 주세요.";
        }
    }

    @Override
    public void render(DrawContext draw, int mx, int my, float delta) {
        scale = Math.max(0.1f, Math.min(1, Math.min((width - 12f) / WIDTH, (height - 12f) / HEIGHT)));
        originX = Math.round((width - WIDTH * scale) / 2);
        originY = Math.round((height - HEIGHT * scale) / 2);
        mouseX = (mx - originX) / scale;
        mouseY = (my - originY) / scale;
        controls.clear();
        draw.fill(0, 0, width, height, 0x99000000);
        var matrices = draw.getMatrices();
        matrices.push();
        try {
            matrices.translate(originX, originY, 0);
            matrices.scale(scale, scale, 1);
            frame(draw);
            if (preview != null) {
                renderPreview(draw, delta);
            } else {
                button(draw, CLOSE, "닫기", false, true, this::close);
                switch (mode) {
                    case CATALOGUE -> renderCatalogue(draw, delta);
                    case CANDIDATES -> renderCandidates(draw, delta);
                    case REWARDS -> renderRewards(draw);
                }
            }
            String footer = waiting != 0 ? "서버 응답을 기다리는 중…" : message;
            if (footer != null) {
                label(draw, footer, 20, 356, message == null ? MUTED : GOLD, 480);
            } else if (mode != Mode.REWARDS && preview == null) {
                label(draw, "휠로 페이지 이동", 20, 356, MUTED, 220);
            }
        } finally {
            matrices.pop();
        }
    }

    private void frame(DrawContext draw) {
        draw.fill(3, 4, WIDTH + 3, HEIGHT + 4, 0xc5121517);
        draw.fill(0, 0, WIDTH, HEIGHT, FRAME);
        draw.drawBorder(0, 0, WIDTH, HEIGHT, TRIM);
        draw.fill(2, 2, WIDTH - 2, 45, 0xff43484c);
        draw.fill(2, 44, WIDTH - 2, 45, 0xff222629);
        draw.fill(20, 17, 23, 35, GOLD);
        String heading = switch (mode) {
            case CATALOGUE -> "포켓몬 납부 도감";
            case CANDIDATES -> "납부 개체 선택";
            case REWARDS -> "도감 완성 보상";
        };
        label(draw, heading, 31, 22, TEXT, 240);
    }

    private void renderCatalogue(DrawContext draw, float delta) {
        int total = overview == null ? 0 : overview.registered().size();
        int first = ENDS[generation - 1] + 1;
        int last = ENDS[generation];
        int count = overview == null ? 0 : (int) overview.registered().stream()
                .filter(number -> number >= first && number <= last).count();
        stat(draw, 20, "전체 등록", total, TOTAL, true);
        stat(draw, 221, generation + "세대 등록", count, last - first + 1, false);
        button(draw, new Box(422, 52, 124, 36), "완성 보상", false, overview != null && idle(), () -> {
            mode = Mode.REWARDS;
            message = null;
        });
        button(draw, new Box(554, 52, 86, 36), "새로고침", false, idle(), this::refresh);
        for (int gen = 1; gen <= 9; gen++) {
            int target = gen;
            button(draw, new Box(20 + (gen - 1) * 69, 98, 68, 22), gen + "세대",
                    gen == generation, idle(), () -> { generation = target; page = 0; });
        }
        for (Filter option : Filter.values()) {
            button(draw, new Box(20 + option.ordinal() * 94, 128, 88, 20), option.label,
                    filter == option, idle(), () -> { filter = option; page = 0; });
        }

        List<Integer> entries = catalogue();
        int pages = pageCount(entries.size());
        page = Math.min(page, pages - 1);
        if (overview != null && entries.isEmpty()) {
            label(draw, "이 조건에 해당하는 포켓몬이 없습니다.", 220, 237, MUTED, 265);
        }
        for (int i = page * PAGE_SIZE; i < Math.min((page + 1) * PAGE_SIZE, entries.size()); i++) {
            int dex = entries.get(i);
            boolean registered = overview.registered().contains(dex);
            int available = overview.available().getOrDefault(dex, 0);
            boolean ready = !registered && available > 0;
            String status = registered ? "등록 완료" : ready ? "납부 가능 · " + available + "마리" : "미등록";
            Box box = card(i % PAGE_SIZE);
            cardBase(draw, box, ready && idle() ? () -> candidatePage(dex, 0) : null);
            portrait(draw, box.x + 10, box.y + 12, 64, dex, Set.of(), delta);
            label(draw, dexNumber(dex), box.x + 83, box.y + 13, MUTED, 106);
            label(draw, speciesName(dex), box.x + 83, box.y + 32, TEXT, 106);
            label(draw, status, box.x + 83, box.y + 55, registered ? GREEN : ready ? GOLD : MUTED, 106);
        }
        pagination(draw, page, pages, () -> page--, () -> page++);
    }

    private void renderCandidates(DrawContext draw, float delta) {
        int dex = candidates.species();
        button(draw, new Box(20, 53, 87, 25), "‹ 도감", false, idle(), this::back);
        label(draw, dexNumber(dex) + "  " + speciesName(dex), 119, 61, TEXT, 343);
        button(draw, new Box(539, 53, 101, 25), "새로고침", false, idle(), () -> candidatePage(dex, candidates.page()));
        label(draw, "파티와 PC에서 납부할 개체를 선택하세요.", 20, 98, MUTED, 500);
        if (candidates.entries().isEmpty()) {
            label(draw, "이 페이지에서 납부할 수 있는 개체가 없습니다.", 195, 237, MUTED, 330);
        }
        for (int i = 0; i < candidates.entries().size(); i++) {
            Candidate candidate = candidates.entries().get(i);
            Box box = card(i);
            cardBase(draw, box, idle() ? () -> request(PREVIEW, dex, 0, candidate.id()) : null);
            portrait(draw, box.x + 9, box.y + 12, 60, dex, candidate.aspects(), delta);
            String name = candidate.nickname().isEmpty() ? speciesName(dex) : candidate.nickname();
            label(draw, name, box.x + 75, box.y + 12, TEXT, 116);
            label(draw, candidate.location() + " · Lv." + candidate.level() + (candidate.shiny() ? " · 이로치" : ""),
                    box.x + 75, box.y + 32, candidate.shiny() ? GOLD : MUTED, 116);
            label(draw, ivs(candidate),
                    box.x + 75, box.y + 52, MUTED, 116);
            label(draw, "HP/공/방/특공/특방/스", box.x + 75, box.y + 68, MUTED, 116);
        }
        pagination(draw, candidates.page(), candidates.pages(),
                () -> candidatePage(dex, candidates.page() - 1), () -> candidatePage(dex, candidates.page() + 1));
    }

    private void renderPreview(DrawContext draw, float delta) {
        Candidate candidate = preview.candidate();
        int dex = preview.species();
        draw.fill(98, 75, 570, 318, 0xff202426);
        draw.fill(94, 71, 566, 314, 0xff454b4f);
        draw.drawBorder(94, 71, 472, 243, TRIM);
        label(draw, "포켓몬 납부 확인", 115, 93, GOLD, 340);
        portrait(draw, 115, 116, 92, dex, candidate.aspects(), delta);
        String name = candidate.nickname().isEmpty() ? speciesName(dex)
                : candidate.nickname() + " (" + speciesName(dex) + ")";
        label(draw, dexNumber(dex) + "  " + name, 224, 125, TEXT, 304);
        label(draw, candidate.location() + " · Lv." + candidate.level() + (candidate.shiny() ? " · 이로치" : ""),
                224, 146, MUTED, 304);
        label(draw, "개체값 HP/공/방/특공/특방/스  " +
                ivs(candidate),
                224, 167, MUTED, 304);
        label(draw, "개체 ID  " + candidate.id(), 224, 188, MUTED, 304);
        label(draw, "확정하면 선택한 개체 한 마리가 소모되고 이 종이 영구 등록됩니다.",
                115, 228, GOLD, 422);
        button(draw, new Box(340, 270, 92, 24), "취소", false, idle(), this::back);
        button(draw, new Box(444, 270, 101, 24), "납부 확정", true, idle(),
                () -> request(DONATE, 0, 0, preview.token()));
    }

    private void renderRewards(DrawContext draw) {
        button(draw, new Box(20, 53, 87, 25), "‹ 도감", false, idle(), this::back);
        label(draw, "등록 수를 채우면 보상을 한 번씩 받을 수 있습니다.", 119, 61, MUTED, 446);
        for (int i = 0; i < overview.rewards().size(); i++) {
            RewardView reward = overview.rewards().get(i);
            int y = 89 + i * 50;
            surface(draw, new Box(20, y, 620, 44), false);
            label(draw, reward.label(), 31, y + 8, TEXT, 139);
            label(draw, reward.current() + " / " + reward.required(), 31, y + 25, MUTED, 139);
            bar(draw, 162, y + 19, 148, reward.current(), reward.required());
            String contents = "보상 미설정";
            Identifier id = Identifier.tryParse(reward.item());
            if (reward.count() > 0 && id != null && Registries.ITEM.containsId(id)) {
                var stack = new ItemStack(Registries.ITEM.get(id), reward.count());
                draw.drawItem(stack, 324, y + 13);
                contents = stack.getName().getString() + " × " + reward.count();
            }
            label(draw, contents, 348, y + 18, MUTED, 170);
            String status = reward.claimed() ? "수령 완료" : reward.claimable() ? "수령하기"
                    : reward.current() < reward.required() ? "진행 중" : "보상 미설정";
            button(draw, new Box(532, y + 10, 96, 24), status, reward.claimable(),
                    idle() && reward.claimable(), () -> request(CLAIM, 0, 0, reward.id()));
        }
    }

    private void stat(DrawContext draw, int x, String title, int count, int total, boolean percent) {
        Box box = new Box(x, 52, 193, 36);
        surface(draw, box, false);
        label(draw, title, x + 8, 59, MUTED, 78);
        String value = count + " / " + total + (percent ? " · " + count * 100 / total + "%" : "");
        int textWidth = textRenderer.getWidth(value);
        label(draw, value, x + 185 - textWidth, 59, TEXT, textWidth);
        bar(draw, x + 8, 76, 177, count, total);
    }

    private void bar(DrawContext draw, int x, int y, int width, int count, int total) {
        draw.fill(x, y, x + width, y + 5, 0xff24282a);
        int fill = width * Math.min(count, total) / total;
        if (fill > 0) {
            draw.fill(x, y, x + fill, y + 5, GOLD);
        }
    }

    private void pagination(DrawContext draw, int current, int total, Runnable previous, Runnable next) {
        button(draw, PREVIOUS, "‹", false, idle() && current > 0, previous);
        button(draw, NEXT, "›", false, idle() && current + 1 < total, next);
        label(draw, (current + 1) + " / " + total, 554, 357, TEXT, 50);
    }

    private void portrait(DrawContext draw, int x, int y, int size, int dex, Set<String> aspects, float delta) {
        draw.fill(x, y, x + size, y + size, 0xff33373a);
        draw.drawBorder(x, y, size, size, 0xff676c70);
        if (!models.render(draw, dex, aspects, x + 2, y + 2, size - 4, delta)) {
            label(draw, "?", x + size / 2 - 3, y + size / 2 - 4, MUTED, 10);
        }
    }

    private void cardBase(DrawContext draw, Box box, Runnable click) {
        boolean hover = click != null && box.contains(mouseX, mouseY);
        surface(draw, box, hover);
        if (click != null) {
            controls.add(new Control(box, click));
        }
    }

    private void surface(DrawContext draw, Box box, boolean hover) {
        draw.fill(box.x + 2, box.y + 2, box.x + box.w + 2, box.y + box.h + 2, RECESS);
        draw.fill(box.x, box.y, box.x + box.w, box.y + box.h, hover ? 0xff606568 : 0xff4b5054);
        draw.drawBorder(box.x, box.y, box.w, box.h, hover ? GOLD : 0xff686e72);
    }

    private void button(DrawContext draw, Box box, String value, boolean active, boolean enabled, Runnable click) {
        boolean hover = enabled && box.contains(mouseX, mouseY);
        int background = active ? 0xff656055 : hover ? 0xff686e72 : 0xff4b5054;
        draw.fill(box.x, box.y, box.x + box.w, box.y + box.h, background);
        draw.drawBorder(box.x, box.y, box.w, box.h, hover || active ? GOLD : TRIM);
        int textWidth = Math.min(box.w - 8, textRenderer.getWidth(value));
        label(draw, value, box.x + (box.w - textWidth) / 2, box.y + (box.h - 8) / 2,
                enabled ? active ? GOLD : TEXT : 0xffa2a5a7, box.w - 8);
        if (enabled) {
            controls.add(new Control(box, click));
        }
    }

    private void label(DrawContext draw, String value, int x, int y, int color, int maxWidth) {
        if (textRenderer.getWidth(value) > maxWidth) {
            value = textRenderer.trimToWidth(value, Math.max(0, maxWidth - textRenderer.getWidth("…"))) + "…";
        }
        draw.drawText(textRenderer, value, x, y, color, false);
    }

    private static Box card(int index) {
        return new Box(20 + index % 3 * 210, 157 + index / 3 * 94, 200, 86);
    }

    private List<Integer> catalogue() {
        if (overview == null) {
            return List.of();
        }
        return IntStream.rangeClosed(ENDS[generation - 1] + 1, ENDS[generation])
                .filter(dex -> switch (filter) {
                    case ALL -> true;
                    case AVAILABLE -> overview.available().containsKey(dex);
                    case REGISTERED -> overview.registered().contains(dex);
                }).boxed().toList();
    }

    private static int pageCount(int size) {
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static String ivs(Candidate candidate) {
        return candidate.ivs().stream().map(String::valueOf).collect(Collectors.joining("/"));
    }

    private static String dexNumber(int dex) {
        return String.format(Locale.ROOT, "#%04d", dex);
    }

    private static String speciesName(int dex) {
        var species = PokemonSpecies.getByPokedexNumber(dex, "cobblemon");
        return species == null ? "포켓몬" : species.getTranslatedName().getString();
    }

    private void candidatePage(int dex, int page) {
        request(CANDIDATES, dex, page, "");
    }

    private boolean idle() {
        return waiting == 0;
    }

    private void back() {
        if (!idle()) {
            return;
        }
        message = null;
        if (preview != null) {
            preview = null;
        } else if (mode != Mode.CATALOGUE) {
            mode = Mode.CATALOGUE;
            candidates = null;
        } else {
            close();
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            for (Control control : controls) {
                if (control.box.contains((x - originX) / scale, (y - originY) / scale)) {
                    controls.clear();
                    control.click.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0 || preview != null || mode == Mode.REWARDS || !idle()) {
            return false;
        }
        int step = vertical > 0 ? -1 : 1;
        if (mode == Mode.CATALOGUE) {
            page = Math.clamp(page + step, 0, pageCount(catalogue().size()) - 1);
        } else {
            int target = Math.clamp(candidates.page() + step, 0, candidates.pages() - 1);
            if (target != candidates.page()) {
                candidatePage(candidates.species(), target);
            }
        }
        controls.clear();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && (preview != null || mode != Mode.CATALOGUE)) {
            back();
            controls.clear();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private enum Mode { CATALOGUE, CANDIDATES, REWARDS }

    private enum Filter {
        ALL("전체"), AVAILABLE("납부 가능"), REGISTERED("등록 완료");
        private final String label;

        Filter(String label) {
            this.label = label;
        }
    }

    private record Control(Box box, Runnable click) {}

    private record Box(int x, int y, int w, int h) {
        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }
}
