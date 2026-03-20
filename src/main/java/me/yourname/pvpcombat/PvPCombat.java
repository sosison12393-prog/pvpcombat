package me.yourname.pvpcombat;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class PvPCombat extends JavaPlugin implements Listener {

    private final Map<UUID, Integer> combatTime = new HashMap<>();
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    private static final int MAX_TIME = 15;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<UUID> iterator = combatTime.keySet().iterator();

                while (iterator.hasNext()) {
                    UUID uuid = iterator.next();
                    Player player = Bukkit.getPlayer(uuid);

                    if (player == null || !player.isOnline()) {
                        cleanup(uuid);
                        iterator.remove();
                        continue;
                    }

                    int time = combatTime.getOrDefault(uuid, 0);

                    if (time <= 0) {
                        iterator.remove();
                        removeCombat(player);
                        continue;
                    }

                    int newTime = time - 1;
                    combatTime.put(uuid, newTime);

                    BossBar bar = bossBars.get(uuid);
                    if (bar != null) {
                        double progress = (double) newTime / MAX_TIME;
                        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
                        bar.setTitle("§cPvP режим: " + newTime + " сек");
                    }
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {

        if (!(e.getEntity() instanceof Player victim)) return;

        Player attacker = null;

        if (e.getDamager() instanceof Player p) {
            attacker = p;
        } else if (e.getDamager() instanceof Projectile proj &&
                proj.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }

        if (attacker == null || attacker.equals(victim)) return;

        startCombat(victim, attacker);
        startCombat(attacker, victim);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player player = e.getPlayer();

        if (!combatTime.containsKey(player.getUniqueId())) return;

        String cmd = e.getMessage().toLowerCase();

        List<String> allowed = List.of(
                "/msg", "/tell", "/r",
                "/login", "/register"
        );

        if (allowed.stream().anyMatch(cmd::startsWith)) return;

        e.setCancelled(true);
        player.sendMessage("§cНельзя использовать команды во время PvP!");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!combatTime.containsKey(uuid)) return;

        UUID killerUUID = lastAttacker.get(uuid);

        if (killerUUID != null) {
            Player killer = Bukkit.getPlayer(killerUUID);

            if (killer != null && killer.isOnline()) {
                killer.sendMessage("§aТебе засчитан килл! " + player.getName() + " вышел из PvP");
            }
        }

        Bukkit.broadcastMessage("§c" + player.getName() + " вышел во время PvP и считается убитым!");

        cleanup(uuid);
    }

    private void startCombat(Player player, Player attacker) {
        UUID uuid = player.getUniqueId();

        boolean wasInCombat = combatTime.containsKey(uuid);

        combatTime.put(uuid, MAX_TIME);
        lastAttacker.put(uuid, attacker.getUniqueId());

        BossBar bar = bossBars.get(uuid);

        if (bar == null) {
            bar = Bukkit.createBossBar(
                    "§cPvP режим",
                    BarColor.RED,
                    BarStyle.SOLID
            );
            bossBars.put(uuid, bar);
        }

        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }

        if (!wasInCombat) {
            player.sendMessage("§cТы в PvP! Не выходи 15 секунд!");
            player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_HURT, 1f, 1f);
        }
    }

    private void removeCombat(Player player) {
        UUID uuid = player.getUniqueId();

        BossBar bar = bossBars.remove(uuid);
        if (bar != null) bar.removeAll();

        lastAttacker.remove(uuid);

        player.sendMessage("§aТы вышел из PvP");
    }

    private void cleanup(UUID uuid) {
        combatTime.remove(uuid);
        lastAttacker.remove(uuid);

        BossBar bar = bossBars.remove(uuid);
        if (bar != null) bar.removeAll();
    }

    @Override
    public void onDisable() {
        bossBars.values().forEach(BossBar::removeAll);
        bossBars.clear();
        combatTime.clear();
        lastAttacker.clear();
    }
    }
