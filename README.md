# TownySubscriptionsBridge

TownyReborn protection layer for subscriptions and permanent shop upgrades. It
keeps expired ranks from leaving excessive writable homes, vaults, or active
QuickShop limits while allowing players to remove their own excess safely.

## Runtime

- Paper 26.2 / Java 25
- QuickShop-Hikari
- LuckPerms
- Optional: EssentialsX and AxVaults
- Plugin version represented here: `1.3.1`

The active TownyReborn defaults are retained in
`src/main/resources/config.yml`.

## Source recovery note

The original source project was no longer present. These Java sources were
recovered from the deployed `TownySubscriptionsBridge.jar` with Vineflower
1.12.0. Control flow and permission rules were preserved, then a reproducible
Maven build against Paper 26.2, the published QuickShop-Hikari 6.3.0.0 API and
LuckPerms 5.5 was added for the TownyReborn 1.3.1 release. Runtime remains
compatible with the server's QuickShop-Hikari 6.3.0.2 build.

Deployed JAR SHA-256:

```text
72bd5359c444eaea1bdb26a2ba0a206b6d68ffb9bdaeec79ed7e606d7c1fa196
```
