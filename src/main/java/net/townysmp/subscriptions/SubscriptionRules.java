package net.townysmp.subscriptions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

final class SubscriptionRules {
   private SubscriptionRules() {
   }

   static List<SubscriptionRules.LimitPermission> parseLimitPermissions(List<String> var0) {
      ArrayList var1 = new ArrayList();

      for (String var3 : var0) {
         int var4 = var3.indexOf(58);
         if (var4 > 0 && var4 != var3.length() - 1) {
            int var5 = Integer.parseInt(var3.substring(0, var4).trim());
            String var6 = var3.substring(var4 + 1).trim();
            if (var5 >= 0 && !var6.isEmpty()) {
               var1.add(new SubscriptionRules.LimitPermission(var5, var6));
               continue;
            }

            throw new IllegalArgumentException("Invalid limit permission entry: " + var3);
         }

         throw new IllegalArgumentException("Invalid limit permission entry: " + var3);
      }

      var1.sort(Comparator.comparingInt(SubscriptionRules.LimitPermission::limit).reversed());
      return List.copyOf(var1);
   }

   static int resolveLimit(List<SubscriptionRules.LimitPermission> var0, int var1, Predicate<String> var2) {
      for (SubscriptionRules.LimitPermission var4 : var0) {
         if (var2.test(var4.permission())) {
            return var4.limit();
         }
      }

      return var1;
   }

   static String commandLabel(String var0) {
      String var1 = var0 == null ? "" : var0.trim();

      while (var1.startsWith("/")) {
         var1 = var1.substring(1);
      }

      int var2 = var1.indexOf(32);
      if (var2 >= 0) {
         var1 = var1.substring(0, var2);
      }

      int var3 = var1.lastIndexOf(58);
      if (var3 >= 0) {
         var1 = var1.substring(var3 + 1);
      }

      return var1.toLowerCase(Locale.ROOT);
   }

   static boolean blocksDepositClick(int var0, int var1, String var2) {
      if (var0 < 0) {
         return false;
      }

      if (var0 >= var1) {
         return "MOVE_TO_OTHER_INVENTORY".equals(var2);
      }

      return switch (var2) {
         case "PICKUP_ALL", "PICKUP_SOME", "PICKUP_HALF", "PICKUP_ONE", "MOVE_TO_OTHER_INVENTORY", "COLLECT_TO_CURSOR", "DROP_ALL_SLOT", "DROP_ONE_SLOT", "NOTHING" -> false;
         default -> true;
      };
   }

   record LimitPermission(int limit, String permission) {
   }
}
