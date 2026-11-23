package ru.example.privateblocks;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class DynamiteCommand implements CommandExecutor, TabCompleter {
    private final PrivateBlocksPlugin plugin;

    public DynamiteCommand(PrivateBlocksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("privateblocks.admin")) {
            s.sendMessage(plugin.plain("<red>Нет прав."));
            return true;
        }
        if (a.length < 3 || !a[0].equalsIgnoreCase("give")) {
            s.sendMessage(plugin.msg("usage", "usage", "/dynamite give [ник] [A-F] [кол-во]"));
            return true;
        }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) {
            s.sendMessage(plugin.msg("player-not-found"));
            return true;
        }
        Dynamite d = plugin.dynamite(a[2]);
        if (d == null) {
            String list = plugin.dynamites().stream().map(Dynamite::id).collect(Collectors.joining(", "));
            s.sendMessage(plugin.msg("unknown-dynamite", "list", list));
            return true;
        }
        int amount = 1;
        if (a.length > 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(a[3])));
            } catch (NumberFormatException ignored) {
            }
        }
        Map<Integer, ItemStack> left = t.getInventory().addItem(plugin.createDynamite(d, amount));
        for (ItemStack it : left.values()) t.getWorld().dropItemNaturally(t.getLocation(), it);
        s.sendMessage(plugin.msg("given", "player", t.getName(), "name", "Динамит " + d.id(),
                "amount", String.valueOf(amount)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("privateblocks.admin")) return out;
        if (a.length == 1) out.add("give");
        else if (a.length == 2) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        else if (a.length == 3) plugin.dynamites().forEach(d -> out.add(d.id()));
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
