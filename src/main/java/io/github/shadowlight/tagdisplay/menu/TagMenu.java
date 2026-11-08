package io.github.shadowlight.tagdisplay.menu;

import io.github.shadowlight.tagdisplay.access.AccessResult;
import io.github.shadowlight.tagdisplay.access.TagAccessService;
import io.github.shadowlight.tagdisplay.config.TagCatalog;
import io.github.shadowlight.tagdisplay.display.TagDisplayModule;
import io.github.shadowlight.tagdisplay.model.TagDefinition;
import io.github.shadowlight.tagdisplay.model.TagType;
import io.github.shadowlight.tagdisplay.storage.PlayerSelectionStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TagMenu implements Listener {
    private static final int MENU_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int CLEAR_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final JavaPlugin plugin;
    private final Map<TagType, TagCatalog> catalogs;
    private final Map<TagType, TagDisplayModule> displayModules;
    private final PlayerSelectionStore selections;
    private final TagAccessService access;

    public TagMenu(JavaPlugin plugin, Map<TagType, TagCatalog> catalogs,
                   Map<TagType, TagDisplayModule> displayModules,
                   PlayerSelectionStore selections, TagAccessService access) {
        this.plugin = plugin;
        this.catalogs = catalogs;
        this.displayModules = displayModules;
        this.selections = selections;
        this.access = access;
    }

    public void open(Player player, TagType type, int requestedPage) {
        List<TagDefinition> available = new ArrayList<>();
        boolean checkingDiscord = false;
        for (TagDefinition tag : catalogs.get(type).all()) {
            AccessResult result = access.check(player, tag);
            if (result == AccessResult.ALLOWED) {
                available.add(tag);
            } else if (result == AccessResult.PENDING) {
                checkingDiscord = true;
            }
        }

        int pageCount = Math.max(1, (available.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        MenuHolder holder = new MenuHolder(type, page);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE,
                Component.text(type.displayName() + " Selection"));
        holder.setInventory(inventory);

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int slot = 45; slot < MENU_SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, available.size());
        for (int index = start; index < end; index++) {
            TagDefinition tag = available.get(index);
            int slot = index - start;
            boolean active = tag.id().equals(selections.get(player.getUniqueId(), type));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Click to select this " + type.displayName().toLowerCase() + "."));
            lore.add(Component.text(active ? "Currently selected" : " "));
            inventory.setItem(slot, item(tag.icon(), tag.display(), lore));
            holder.addChoice(slot, tag);
        }

        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW, Component.text("Previous page"), List.of()));
        }
        inventory.setItem(CLEAR_SLOT, item(Material.BARRIER, Component.text("Clear selected " + type.displayName().toLowerCase()),
                List.of(Component.text("Show no " + type.displayName().toLowerCase() + "."))));
        if (page + 1 < pageCount) {
            inventory.setItem(NEXT_SLOT, item(Material.ARROW, Component.text("Next page"), List.of()));
        }
        inventory.setItem(48, item(Material.PAPER, Component.text("Page " + (page + 1) + " of " + pageCount),
                List.of(Component.text(available.size() + " available tag(s)"))));

        if (available.isEmpty()) {
            inventory.setItem(22, item(checkingDiscord ? Material.CLOCK : Material.BARRIER,
                    Component.text(checkingDiscord ? "Checking Discord roles" : "No available tags"),
                    List.of(Component.text(checkingDiscord ? "Please reopen this menu in a moment." : "You do not have access to any tags."))));
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == PREVIOUS_SLOT) {
            open(player, holder.type(), holder.page() - 1);
            return;
        }
        if (slot == NEXT_SLOT) {
            open(player, holder.type(), holder.page() + 1);
            return;
        }
        if (slot == CLEAR_SLOT) {
            selections.set(player.getUniqueId(), holder.type(), null);
            displayModules.get(holder.type()).refresh(player);
            player.closeInventory();
            player.sendMessage(Component.text("Your " + holder.type().displayName().toLowerCase() + " has been cleared."));
            return;
        }

        TagDefinition selected = holder.choices().get(slot);
        if (selected == null) {
            return;
        }
        AccessResult result = access.check(player, selected);
        if (result == AccessResult.PENDING) {
            player.sendMessage(Component.text("Discord role information is still loading. Please try again in a moment."));
            open(player, holder.type(), holder.page());
            return;
        }
        if (result != AccessResult.ALLOWED) {
            player.sendMessage(Component.text("You no longer have access to that tag."));
            open(player, holder.type(), holder.page());
            return;
        }

        selections.set(player.getUniqueId(), holder.type(), selected.id());
        displayModules.get(holder.type()).refresh(player);
        player.closeInventory();
        player.sendMessage(Component.text("Selected " + holder.type().displayName().toLowerCase() + ": ").append(selected.display()));
    }

    private ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static final class MenuHolder implements InventoryHolder {
        private final TagType type;
        private final int page;
        private final Map<Integer, TagDefinition> choices = new HashMap<>();
        private Inventory inventory;

        private MenuHolder(TagType type, int page) {
            this.type = type;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private void addChoice(int slot, TagDefinition definition) {
            choices.put(slot, definition);
        }

        private Map<Integer, TagDefinition> choices() {
            return choices;
        }

        private TagType type() {
            return type;
        }

        private int page() {
            return page;
        }
    }
}
