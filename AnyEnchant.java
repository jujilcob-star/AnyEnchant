package fr.anyenchant;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AnyEnchant : n'importe quel enchantement sur n'importe quel objet.
 *
 * /enchantany <enchantement> [niveau]            -> enchante l'objet en main
 * /enchantbook <enchantement> [niveau] [quantite] -> donne un livre ; clic gauche
 *                                                    avec le livre sur un objet = enchantement applique
 */
public final class AnyEnchant extends JavaPlugin implements Listener, TabExecutor {

    private static final int MAX_LEVEL = 255;

    private NamespacedKey enchantKey;
    private NamespacedKey levelKey;

    @Override
    public void onEnable() {
        enchantKey = new NamespacedKey(this, "book_enchant");
        levelKey = new NamespacedKey(this, "book_level");

        getServer().getPluginManager().registerEvents(this, this);

        for (String name : new String[] {"enchantany", "enchantbook"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(this);
                cmd.setTabCompleter(this);
            }
        }
        getLogger().info("AnyEnchant active.");
    }

    // ------------------------------------------------------------------
    // Commandes
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Cette commande doit etre utilisee en jeu.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            return false; // affiche l'usage defini dans plugin.yml
        }

        Enchantment enchantment = findEnchantment(args[0]);
        if (enchantment == null) {
            player.sendMessage(Component.text("Enchantement inconnu : " + args[0], NamedTextColor.RED));
            return true;
        }

        int level = 1;
        if (args.length >= 2) {
            level = parseInt(args[1]);
            if (level < 1 || level > MAX_LEVEL) {
                player.sendMessage(Component.text("Le niveau doit etre entre 1 et " + MAX_LEVEL + ".", NamedTextColor.RED));
                return true;
            }
        }

        if (command.getName().equalsIgnoreCase("enchantany")) {
            enchantHeldItem(player, enchantment, level);
        } else {
            int amount = 1;
            if (args.length >= 3) {
                amount = parseInt(args[2]);
                if (amount < 1 || amount > 36) {
                    player.sendMessage(Component.text("La quantite doit etre entre 1 et 36.", NamedTextColor.RED));
                    return true;
                }
            }
            giveBooks(player, enchantment, level, amount);
        }
        return true;
    }

    private void enchantHeldItem(Player player, Enchantment enchantment, int level) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(Component.text("Tiens un objet en main.", NamedTextColor.RED));
            return;
        }
        hand.addUnsafeEnchantment(enchantment, level);
        player.getInventory().setItemInMainHand(hand);
        player.sendMessage(Component.text(
            "Enchantement applique : " + niceName(enchantment) + " " + level, NamedTextColor.GREEN));
    }

    private void giveBooks(Player player, Enchantment enchantment, int level, int amount) {
        for (int i = 0; i < amount; i++) {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta meta = book.getItemMeta();

            meta.displayName(Component.text("Livre : " + niceName(enchantment) + " " + level,
                NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Clic gauche avec ce livre sur un objet",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(enchantKey, PersistentDataType.STRING, enchantment.getKey().toString());
            pdc.set(levelKey, PersistentDataType.INTEGER, level);
            book.setItemMeta(meta);

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(book);
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
        player.sendMessage(Component.text(
            amount + " livre(s) donne(s) : " + niceName(enchantment) + " " + level, NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            Registry.ENCHANTMENT.stream()
                .map(e -> e.getKey().getKey())
                .filter(name -> name.startsWith(prefix))
                .sorted()
                .forEach(out::add);
        } else if (args.length == 2) {
            out.addAll(List.of("1", "3", "5", "10", "255"));
        } else if (args.length == 3 && command.getName().equalsIgnoreCase("enchantbook")) {
            out.addAll(List.of("1", "16", "36"));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Livres : clic gauche sur un objet avec le livre au curseur
    // ------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClick() != ClickType.LEFT) return;

        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();
        if (cursor == null || cursor.getType() != Material.ENCHANTED_BOOK || !cursor.hasItemMeta()) return;
        if (target == null || target.getType().isAir()) return;

        PersistentDataContainer pdc = cursor.getItemMeta().getPersistentDataContainer();
        String enchantId = pdc.get(enchantKey, PersistentDataType.STRING);
        Integer level = pdc.get(levelKey, PersistentDataType.INTEGER);
        if (enchantId == null || level == null) return; // ce n'est pas un livre AnyEnchant

        // On empeche d'appliquer un livre AnyEnchant sur un autre livre AnyEnchant
        if (target.hasItemMeta()
            && target.getItemMeta().getPersistentDataContainer().has(enchantKey, PersistentDataType.STRING)) {
            return;
        }

        NamespacedKey key = NamespacedKey.fromString(enchantId);
        Enchantment enchantment = key == null ? null : Registry.ENCHANTMENT.get(key);
        if (enchantment == null) {
            player.sendMessage(Component.text("Enchantement du livre introuvable : " + enchantId, NamedTextColor.RED));
            return;
        }

        event.setCancelled(true);

        target.addUnsafeEnchantment(enchantment, level);
        event.setCurrentItem(target);

        // On consomme un livre
        if (cursor.getAmount() > 1) {
            cursor.setAmount(cursor.getAmount() - 1);
            event.setCursor(cursor);
        } else {
            event.setCursor(null);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
        player.sendMessage(Component.text(
            "Enchantement applique : " + niceName(enchantment) + " " + level, NamedTextColor.GREEN));
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private Enchantment findEnchantment(String input) {
        String s = input.toLowerCase(Locale.ROOT).trim();
        NamespacedKey key = s.contains(":") ? NamespacedKey.fromString(s) : NamespacedKey.minecraft(s);
        return key == null ? null : Registry.ENCHANTMENT.get(key);
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String niceName(Enchantment enchantment) {
        return enchantment.getKey().getKey().replace('_', ' ');
    }
}
