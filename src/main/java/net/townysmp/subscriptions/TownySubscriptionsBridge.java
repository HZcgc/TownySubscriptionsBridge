package net.townysmp.subscriptions;

import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.event.Phase;
import com.ghostchu.quickshop.api.event.economy.ShopPurchaseEvent;
import com.ghostchu.quickshop.api.event.management.ShopCreateEvent;
import com.ghostchu.quickshop.api.event.management.ShopDeleteEvent;
import com.ghostchu.quickshop.api.event.settings.type.ShopOwnerEvent;
import com.ghostchu.quickshop.api.event.user.UserLimitCalculateEvent;
import com.ghostchu.quickshop.api.obj.QUser;
import com.ghostchu.quickshop.api.shop.Shop;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class TownySubscriptionsBridge extends JavaPlugin implements Listener {
   private static final Pattern HEX_COLOR = Pattern.compile("&#([A-Fa-f0-9]{6})");
   private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)&([0-9A-FK-OR])");
   private List<SubscriptionRules.LimitPermission> homeLimits = List.of();
   private List<SubscriptionRules.LimitPermission> vaultWriteLimits = List.of();
   private List<SubscriptionRules.LimitPermission> shopLimits = List.of();
   private Set<String> restrictedHomeCommands = Set.of();
   private Set<String> homeListCommands = Set.of();
   private boolean homeProtectionEnabled;
   private boolean vaultProtectionEnabled;
   private boolean shopProtectionEnabled;
   private int fallbackHomeLimit;
   private int fallbackVaultWriteLimit;
   private int fallbackShopLimit;
   private int extraShopAmount;
   private int absoluteShopLimit;
   private String bypassPermission;
   private String extraShopPermission;
   private String unlimitedHomesPermission;
   private String vaultHolderClassName;
   private String prefix;
   private String homeBlockedMessage;
   private String homesListMessage;
   private String noHomesMessage;
   private String vaultReadOnlyMessage;
   private String shopOverLimitMessage;
   private String shopOwnerOverLimitMessage;
   private String shopProfileLoadingMessage;
   private String shopProtectionUnavailableMessage;
   private long messageCooldownMillis;
   private Plugin essentials;
   private Method essentialsGetUser;
   private Method essentialsUserGetHomes;
   private boolean essentialsFailureLogged;
   private boolean quickShopFailureLogged;
   private boolean luckPermsFailureLogged;
   private final Map<UUID, Long> lastMessageAt = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> shopCounts = new ConcurrentHashMap<>();
   private final Map<UUID, TownySubscriptionsBridge.ShopPermissionProfile> offlineShopProfiles = new ConcurrentHashMap<>();
   private final Set<UUID> pendingProfileLoads = ConcurrentHashMap.newKeySet();
   private QuickShopAPI quickShop;
   private LuckPerms luckPerms;

   public void onEnable() {
      this.saveDefaultConfig();
      this.loadSettings();
      this.initializeEssentialsHook();
      this.initializeShopHooks();
      this.getServer().getPluginManager().registerEvents(this, this);
      this.getLogger()
         .info(
            "TownyReborn subscription protections enabled: homes="
               + this.homeProtectionEnabled
               + ", withdrawal-only vaults="
               + this.vaultProtectionEnabled
               + ", over-limit shop trading="
               + this.shopProtectionEnabled
               + "."
         );
   }

   private void loadSettings() {
      FileConfiguration var1 = this.getConfig();
      this.homeProtectionEnabled = var1.getBoolean("home.enabled", true);
      this.vaultProtectionEnabled = var1.getBoolean("vault.enabled", true);
      this.shopProtectionEnabled = var1.getBoolean("shop.enabled", true);
      this.fallbackHomeLimit = var1.getInt("home.fallback-limit", 5);
      this.fallbackVaultWriteLimit = var1.getInt("vault.fallback-write-limit", 2);
      this.fallbackShopLimit = var1.getInt("shop.fallback-limit", 10);
      this.extraShopAmount = Math.max(0, var1.getInt("shop.extra.amount", 5));
      this.absoluteShopLimit = Math.max(this.fallbackShopLimit, var1.getInt("shop.extra.absolute-max", 50));
      this.bypassPermission = var1.getString("bypass-permission", "townysmp.subscriptions.bypass");
      this.extraShopPermission = var1.getString("shop.extra.permission", "townysmp.subscriptions.shops.extra.5");
      this.unlimitedHomesPermission = var1.getString("home.unlimited-permission", "townysmp.subscriptions.homes.unlimited");
      this.vaultHolderClassName = var1.getString("vault.holder-class", "com.artillexstudios.axvaults.vaults.Vault");
      this.prefix = color(var1.getString("messages.prefix", "&#FF55FF&lTOWNY&#55FF55&lREBORN &8» &r&7"));
      this.homeBlockedMessage = color(
         var1.getString(
            "messages.home-over-limit",
            "You have &d%current%&7 homes, but your current limit is &a%limit%&7. Use &d/delhome <name>&7 until you are back within the limit."
         )
      );
      this.homesListMessage = color(var1.getString("messages.homes-list", "Your homes (&d%current%&7/&a%limit%&7): %homes%"));
      this.noHomesMessage = color(var1.getString("messages.no-homes", "You do not have any homes yet. Use &d/sethome <name>&7 in the survival world."));
      this.vaultReadOnlyMessage = color(
         var1.getString("messages.vault-read-only", "Vault &d#%vault%&7 is withdrawal-only. You may remove items, but you cannot store anything new.")
      );
      this.shopOverLimitMessage = color(
         var1.getString("messages.shop-over-limit", "This shop is paused because its owner has &d%current%&7 shops, but their current limit is &a%limit%&7.")
      );
      this.shopOwnerOverLimitMessage = color(
         var1.getString(
            "messages.shop-owner-over-limit",
            "Your &d%current%&7 shops are paused because your current limit is &a%limit%&7. Remove &d%excess%&7 shop(s) to resume trading."
         )
      );
      this.shopProfileLoadingMessage = color(
         var1.getString("messages.shop-profile-loading", "This shop's owner permissions are loading. Please try again in a moment.")
      );
      this.shopProtectionUnavailableMessage = color(
         var1.getString("messages.shop-protection-unavailable", "Shop protection is temporarily unavailable. Please try again shortly.")
      );
      this.messageCooldownMillis = var1.getInt("messages.cooldown-milliseconds", 1500);
      this.homeLimits = this.parseLimits("home.limit-permissions");
      this.vaultWriteLimits = this.parseLimits("vault.write-limit-permissions");
      this.shopLimits = this.parseLimits("shop.limit-permissions");
      HashSet var2 = new HashSet();

      for (String var4 : var1.getStringList("home.restricted-commands")) {
         String var5 = SubscriptionRules.commandLabel(var4);
         if (!var5.isEmpty()) {
            var2.add(var5);
         }
      }

      this.restrictedHomeCommands = Set.copyOf(var2);
      var2.clear();

      for (String var7 : var1.getStringList("home.list-commands")) {
         String var8 = SubscriptionRules.commandLabel(var7);
         if (!var8.isEmpty()) {
            var2.add(var8);
         }
      }

      this.homeListCommands = Set.copyOf(var2);
   }

   private List<SubscriptionRules.LimitPermission> parseLimits(String var1) {
      try {
         return SubscriptionRules.parseLimitPermissions(this.getConfig().getStringList(var1));
      } catch (RuntimeException var3) {
         this.getLogger().severe("Invalid setting at " + var1 + ": " + var3.getMessage());
         return List.of();
      }
   }

   private void initializeEssentialsHook() {
      this.essentials = this.getServer().getPluginManager().getPlugin("Essentials");
      if (this.essentials == null) {
         this.getLogger().warning("Essentials was not found. Strict home-limit protection will fail open.");
      } else {
         try {
            this.essentialsGetUser = this.essentials.getClass().getMethod("getUser", Player.class);
         } catch (ReflectiveOperationException var2) {
            this.logEssentialsFailure(var2);
         }
      }
   }

   private void initializeShopHooks() {
      if (this.shopProtectionEnabled) {
         try {
            this.quickShop = QuickShopAPI.getInstance();
            this.luckPerms = LuckPermsProvider.get();
            this.rebuildShopCounts();
            this.luckPerms
               .getEventBus()
               .subscribe(
                  this,
                  UserDataRecalculateEvent.class,
                  var1 -> this.offlineShopProfiles.put(var1.getUser().getUniqueId(), this.profileFromLuckPerms(var1.getUser()))
               );
            this.shopCounts.forEach((var1, var2x) -> {
               if (var2x > this.fallbackShopLimit) {
                  this.loadShopProfile(var1);
               }
            });
         } catch (RuntimeException | LinkageError var2) {
            this.shopProtectionEnabled = false;
            this.logQuickShopFailure("Shop protection could not be initialized", var2);
         }
      }
   }

   private void rebuildShopCounts() {
      ConcurrentHashMap var1 = new ConcurrentHashMap();

      for (Shop var3 : this.quickShop.getShopManager().getAllShops()) {
         UUID var4 = this.realOwnerId(var3);
         if (var4 != null) {
            var1.merge(var4, 1, Integer::sum);
         }
      }

      this.shopCounts.clear();
      this.shopCounts.putAll(var1);
      this.getLogger().info("Indexed " + var1.values().stream().mapToInt(Integer::intValue).sum() + " QuickShop shops for subscription expiry protection.");
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onPlayerCommand(PlayerCommandPreprocessEvent var1) {
      if (this.homeProtectionEnabled) {
         Player var2 = var1.getPlayer();
         String var3 = SubscriptionRules.commandLabel(var1.getMessage());
         if (this.homeListCommands.contains(var3)) {
            TownySubscriptionsBridge.HomeSnapshot var7 = this.getHomes(var2);
            if (var7 != null) {
               var1.setCancelled(true);
               this.sendHomesList(var2, var7);
            }
         } else if (!this.hasBypass(var2) && !var2.hasPermission(this.unlimitedHomesPermission)) {
            if (this.restrictedHomeCommands.contains(var3)) {
               int var4 = SubscriptionRules.resolveLimit(this.homeLimits, this.fallbackHomeLimit, var2::hasPermission);
               TownySubscriptionsBridge.HomeSnapshot var5 = this.getHomes(var2);
               int var6 = var5 == null ? -1 : var5.count();
               if (var6 >= 0 && var6 > var4) {
                  var1.setCancelled(true);
                  this.sendThrottled(var2, this.homeBlockedMessage.replace("%current%", Integer.toString(var6)).replace("%limit%", Integer.toString(var4)));
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onVaultClick(InventoryClickEvent var1) {
      if (this.vaultProtectionEnabled && var1.getWhoClicked() instanceof Player var2 && !this.hasBypass(var2)) {
         Inventory var7 = var1.getView().getTopInventory();
         Object var4 = this.vaultHolder(var7);
         if (var4 != null) {
            int var5 = this.getVaultId(var4);
            if (var5 >= 1) {
               int var6 = SubscriptionRules.resolveLimit(this.vaultWriteLimits, this.fallbackVaultWriteLimit, var2::hasPermission);
               if (var5 > var6) {
                  if (SubscriptionRules.blocksDepositClick(var1.getRawSlot(), var7.getSize(), var1.getAction().name())) {
                     var1.setCancelled(true);
                     this.sendThrottled(var2, this.vaultReadOnlyMessage.replace("%vault%", Integer.toString(var5)));
                  }
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onVaultDrag(InventoryDragEvent var1) {
      if (this.vaultProtectionEnabled && var1.getWhoClicked() instanceof Player var2 && !this.hasBypass(var2)) {
         Inventory var9 = var1.getView().getTopInventory();
         Object var4 = this.vaultHolder(var9);
         if (var4 != null) {
            int var5 = this.getVaultId(var4);
            int var6 = SubscriptionRules.resolveLimit(this.vaultWriteLimits, this.fallbackVaultWriteLimit, var2::hasPermission);
            if (var5 >= 1 && var5 > var6) {
               int var7 = var9.getSize();
               boolean var8 = var1.getRawSlots().stream().anyMatch(var1x -> var1x >= 0 && var1x < var7);
               if (var8) {
                  var1.setCancelled(true);
                  this.sendThrottled(var2, this.vaultReadOnlyMessage.replace("%vault%", Integer.toString(var5)));
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onShopPurchase(ShopPurchaseEvent var1) {
      if (this.shopProtectionEnabled && this.quickShop != null) {
         Shop var2 = var1.getShop();
         if (!var2.isUnlimited()) {
            UUID var3 = this.realOwnerId(var2);
            if (var3 != null) {
               int var4 = this.shopCounts.getOrDefault(var3, 0);
               if (var4 > this.fallbackShopLimit) {
                  Player var5 = this.getServer().getPlayer(var3);
                  TownySubscriptionsBridge.ShopPermissionProfile var6 = this.resolveShopProfile(var3, var5);
                  if (var6 == null) {
                     this.cancelShopTrade(
                        var1, this.prefix + (this.luckPermsFailureLogged ? this.shopProtectionUnavailableMessage : this.shopProfileLoadingMessage)
                     );
                  } else if (!var6.bypass() && var4 > var6.limit()) {
                     String var7 = this.shopOverLimitMessage
                        .replace("%current%", Integer.toString(var4))
                        .replace("%limit%", Integer.toString(var6.limit()))
                        .replace("%excess%", Integer.toString(var4 - var6.limit()));
                     this.cancelShopTrade(var1, this.prefix + var7);
                     if (var5 != null) {
                        this.sendThrottled(
                           var5,
                           this.shopOwnerOverLimitMessage
                              .replace("%current%", Integer.toString(var4))
                              .replace("%limit%", Integer.toString(var6.limit()))
                              .replace("%excess%", Integer.toString(var4 - var6.limit()))
                        );
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onShopLimitCalculate(UserLimitCalculateEvent var1) {
      if (this.shopProtectionEnabled && this.extraShopAmount > 0 && this.extraShopPermission != null && !this.extraShopPermission.isBlank()) {
         QUser var2 = var1.user();
         if (var2 != null && var2.isRealPlayer()) {
            Player var3 = (Player)var2.getBukkitPlayer().orElse(null);
            if (var3 != null && var3.hasPermission(this.extraShopPermission)) {
               int var4 = var1.limit();
               if (var4 < this.absoluteShopLimit) {
                  var1.limit(Math.min(this.absoluteShopLimit, var4 + this.extraShopAmount));
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onShopCreate(ShopCreateEvent var1) {
      if (this.shopProtectionEnabled && var1.isPhase(Phase.POST)) {
         var1.shop().ifPresent(var1x -> this.changeShopCount(this.realOwnerId((Shop<?, ?>)var1x), 1));
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onShopDelete(ShopDeleteEvent var1) {
      if (this.shopProtectionEnabled && var1.isPhase(Phase.POST)) {
         var1.shop().ifPresent(var1x -> this.changeShopCount(this.realOwnerId((Shop<?, ?>)var1x), -1));
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onShopOwnerChange(ShopOwnerEvent var1) {
      if (this.shopProtectionEnabled && var1.isPhase(Phase.POST)) {
         this.changeShopCount(this.realUserId(var1.old()), -1);
         this.changeShopCount(this.realUserId(var1.updated()), 1);
      }
   }

   private TownySubscriptionsBridge.ShopPermissionProfile resolveShopProfile(UUID var1, Player var2) {
      if (var2 != null) {
         return new TownySubscriptionsBridge.ShopPermissionProfile(this.resolveShopLimit(var2::hasPermission), this.hasBypass(var2));
      } else {
         TownySubscriptionsBridge.ShopPermissionProfile var3 = this.offlineShopProfiles.get(var1);
         if (var3 != null) {
            return var3;
         } else if (this.luckPerms == null) {
            return null;
         } else {
            User var4 = this.luckPerms.getUserManager().getUser(var1);
            if (var4 != null) {
               TownySubscriptionsBridge.ShopPermissionProfile var5 = this.profileFromLuckPerms(var4);
               this.offlineShopProfiles.put(var1, var5);
               return var5;
            } else {
               this.loadShopProfile(var1);
               return null;
            }
         }
      }
   }

   private void loadShopProfile(UUID var1) {
      if (this.pendingProfileLoads.add(var1)) {
         this.luckPerms
            .getUserManager()
            .loadUser(var1)
            .whenComplete(
               (var2, var3) -> {
                  this.pendingProfileLoads.remove(var1);
                  if (var3 == null && var2 != null) {
                     this.offlineShopProfiles.put(var1, this.profileFromLuckPerms(var2));
                  } else {
                     this.logLuckPermsFailure(
                        var3 == null ? new IllegalStateException("LuckPerms returned no user for " + var1) : this.unwrapCompletionException(var3)
                     );
                  }
               }
            );
      }
   }

   private TownySubscriptionsBridge.ShopPermissionProfile profileFromLuckPerms(User var1) {
      Predicate var2 = var1x -> var1.getCachedData().getPermissionData().checkPermission(var1x).asBoolean();
      return new TownySubscriptionsBridge.ShopPermissionProfile(
         this.resolveShopLimit(var2), this.bypassPermission != null && !this.bypassPermission.isBlank() && var2.test(this.bypassPermission)
      );
   }

   private int resolveShopLimit(Predicate<String> var1) {
      int var2 = SubscriptionRules.resolveLimit(this.shopLimits, this.fallbackShopLimit, var1);
      return this.extraShopAmount > 0
            && this.extraShopPermission != null
            && !this.extraShopPermission.isBlank()
            && var1.test(this.extraShopPermission)
            && var2 < this.absoluteShopLimit
         ? Math.min(this.absoluteShopLimit, var2 + this.extraShopAmount)
         : var2;
   }

   private void changeShopCount(UUID var1, int var2) {
      if (var1 != null) {
         this.shopCounts.compute(var1, (var1x, var2x) -> {
            int var3 = Math.max(0, (var2x == null ? 0 : var2x) + var2);
            return var3 == 0 ? null : var3;
         });
      }
   }

   private void cancelShopTrade(ShopPurchaseEvent var1, String var2) {
      var1.setCancelled(true, var2);
   }

   private UUID realOwnerId(Shop<?, ?> var1) {
      return var1 != null && !var1.isUnlimited() ? this.realUserId(var1.getOwner()) : null;
   }

   private UUID realUserId(QUser var1) {
      return var1 != null && var1.isRealPlayer() ? var1.getUniqueId() : null;
   }

   private Throwable unwrapCompletionException(Throwable var1) {
      return var1 instanceof CompletionException && var1.getCause() != null ? var1.getCause() : var1;
   }

   private boolean hasBypass(Player var1) {
      return this.bypassPermission != null && !this.bypassPermission.isBlank() && var1.hasPermission(this.bypassPermission);
   }

   private TownySubscriptionsBridge.HomeSnapshot getHomes(Player var1) {
      if (this.essentials != null && this.essentialsGetUser != null) {
         try {
            Object var2 = this.essentialsGetUser.invoke(this.essentials, var1);
            if (var2 == null) {
               return new TownySubscriptionsBridge.HomeSnapshot(0, List.of());
            }

            if (this.essentialsUserGetHomes == null || !this.essentialsUserGetHomes.getDeclaringClass().isInstance(var2)) {
               this.essentialsUserGetHomes = var2.getClass().getMethod("getHomes");
            }

            Object var3 = this.essentialsUserGetHomes.invoke(var2);
            ArrayList var4 = new ArrayList();
            if (var3 instanceof Collection var5) {
               var5.forEach(var1x -> var4.add(String.valueOf(var1x)));
            } else if (var3 instanceof Map var6) {
               var6.keySet().forEach(var1x -> var4.add(String.valueOf(var1x)));
            } else {
               if (var3 == null || !var3.getClass().isArray()) {
                  throw new IllegalStateException("Unsupported Essentials homes container: " + (var3 == null ? "null" : var3.getClass().getName()));
               }

               for (int var7 = 0; var7 < Array.getLength(var3); var7++) {
                  var4.add(String.valueOf(Array.get(var3, var7)));
               }
            }

            var4.sort(String.CASE_INSENSITIVE_ORDER);
            return new TownySubscriptionsBridge.HomeSnapshot(var4.size(), List.copyOf(var4));
         } catch (ReflectiveOperationException | RuntimeException var8) {
            this.logEssentialsFailure(var8);
            return null;
         }
      } else {
         return null;
      }
   }

   private void sendHomesList(Player var1, TownySubscriptionsBridge.HomeSnapshot var2) {
      if (var2.names().isEmpty()) {
         this.sendThrottled(var1, this.noHomesMessage);
      } else {
         int var3 = var1.hasPermission(this.unlimitedHomesPermission)
            ? Integer.MAX_VALUE
            : SubscriptionRules.resolveLimit(this.homeLimits, this.fallbackHomeLimit, var1::hasPermission);
         String var4 = var3 == Integer.MAX_VALUE ? "unlimited" : Integer.toString(var3);
         StringBuilder var5 = new StringBuilder();

         for (int var6 = 0; var6 < var2.names().size(); var6++) {
            if (var6 > 0) {
               var5.append("§7, ");
            }

            var5.append("§d").append(var2.names().get(var6));
         }

         this.sendThrottled(
            var1, this.homesListMessage.replace("%current%", Integer.toString(var2.count())).replace("%limit%", var4).replace("%homes%", var5.toString())
         );
      }
   }

   private Object vaultHolder(Inventory var1) {
      InventoryHolder var2 = var1.getHolder();
      if (var2 == null) {
         return null;
      }

      for (Class var3 = var2.getClass(); var3 != null; var3 = var3.getSuperclass()) {
         if (var3.getName().equals(this.vaultHolderClassName)) {
            return var2;
         }
      }

      return null;
   }

   private int getVaultId(Object var1) {
      try {
         Method var2 = var1.getClass().getMethod("getId");
         return var2.invoke(var1) instanceof Number var4 ? var4.intValue() : -1;
      } catch (ReflectiveOperationException var5) {
         this.getLogger().warning("Unable to identify an AxVaults vault: " + var5.getMessage());
         return -1;
      }
   }

   private void logEssentialsFailure(Exception var1) {
      if (!this.essentialsFailureLogged) {
         this.essentialsFailureLogged = true;
         this.getLogger()
            .severe("EssentialsX 2.22 home lookup failed; home commands will not be blocked: " + var1.getClass().getSimpleName() + ": " + var1.getMessage());
      }
   }

   private void logQuickShopFailure(String var1, Throwable var2) {
      if (!this.quickShopFailureLogged) {
         this.quickShopFailureLogged = true;
         this.getLogger().severe(var1 + ": " + var2.getClass().getSimpleName() + ": " + var2.getMessage());
      }
   }

   private void logLuckPermsFailure(Throwable var1) {
      if (!this.luckPermsFailureLogged) {
         this.luckPermsFailureLogged = true;
         this.getLogger()
            .severe(
               "Unable to load an offline shop owner's LuckPerms data; their over-limit shops remain paused: "
                  + var1.getClass().getSimpleName()
                  + ": "
                  + var1.getMessage()
            );
      }
   }

   private void sendThrottled(Player var1, String var2) {
      long var3 = System.currentTimeMillis();
      Long var5 = this.lastMessageAt.put(var1.getUniqueId(), var3);
      if (var5 == null || var3 - var5 >= this.messageCooldownMillis) {
         var1.sendMessage(this.prefix + var2);
      }
   }

   private static String color(String var0) {
      if (var0 == null) {
         return "";
      }

      Matcher var1 = HEX_COLOR.matcher(var0);
      StringBuffer var2 = new StringBuffer();

      while (var1.find()) {
         String var3 = var1.group(1).toUpperCase(Locale.ROOT);
         StringBuilder var4 = new StringBuilder("§x");

         for (char var8 : var3.toCharArray()) {
            var4.append('§').append(var8);
         }

         var1.appendReplacement(var2, Matcher.quoteReplacement(var4.toString()));
      }

      var1.appendTail(var2);
      return LEGACY_COLOR.matcher(var2.toString()).replaceAll("§$1");
   }

   private record HomeSnapshot(int count, List<String> names) {
   }

   private record ShopPermissionProfile(int limit, boolean bypass) {
   }
}
