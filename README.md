# TownySubscriptionsBridge

TownySMP protection layer for subscriptions and permanent shop upgrades. It
keeps expired ranks from leaving excessive writable homes, vaults, or active
QuickShop limits while allowing players to remove their own excess safely.

## Runtime

- Paper 1.21.x
- QuickShop-Hikari
- LuckPerms
- Optional: EssentialsX and AxVaults
- Plugin version represented here: `1.2.0`

The active TownySMP defaults are retained in
`src/main/resources/config.yml`.

## Source recovery note

The original source project was no longer present. These Java sources were
recovered from the deployed `TownySubscriptionsBridge.jar` with Vineflower
1.12.0. Control flow, API calls, permission rules, messages, and public
behavior are preserved. Original local variable names, comments, formatting,
and the old build definition cannot be recovered from bytecode, so this
repository should be treated as a readable diagnostic baseline rather than a
byte-for-byte reproducible source release.

Deployed JAR SHA-256:

```text
72bd5359c444eaea1bdb26a2ba0a206b6d68ffb9bdaeec79ed7e606d7c1fa196
```
