# Dungeons Necromancer – Forge 1.20.1

Mob Necromancer z Minecraft Dungeons (Forge 1.20.1, Forge 47.x, Java 17).

## Sestavení jaru
Potřebuješ internet (stáhne Forge + Minecraft knihovny, ~ pár minut poprvé).

- Windows: `gradlew.bat build`
- Linux/macOS: `./gradlew build`

Hotový jar: `build/libs/necromancermod-1.0.0.jar` -> do složky `mods` (Forge 1.20.1).
JDK 17 si Gradle stáhne sám (toolchain). Gradle samotný jede i na JDK 17–21.

Bez instalace čehokoliv: nahraj složku na GitHub, workflow `.github/workflows/build.yml`
jar sestaví a nabídne ho ke stažení v záložce Actions -> Artifacts.

## Co mob umí
- 100 HP, rychlost jako skeleton, zvuky vanilla skeleton (kroky, zranění, smrt, ambient).
- Utíká, když se hráč přiblíží na ~5 bloků.
- V bezpečné vzdálenosti vyvolává zombie/skeletony (vanilla, s brněním podle obtížnosti,
  max 6 najednou, znovu vyvolává, až když zbývají < 3).
- Střílí modré koule (jen damage jako šíp skeletona: 3/4/5 podle obtížnosti).
  Když je zahnaný do kouta, spamuje je.
- Drop: kosti + náhodná enchantnutá kniha (loot table `data/necromancermod/loot_tables/entities/necromancer.json`).
- Spawn jen přes spawn egg / `/summon necromancermod:necromancer`.

## Technicky
Model + animace (idle, walk, projectile, summon) jsou z tvého Blockbench glTF,
převedené skriptem `tools/convert_gltf.py` do `assets/necromancermod/geo/necromancer.bin`.
Nepoužívá GeckoLib, žádné další mody nejsou potřeba.
Textura: `T_Skeleton_Necromancer` (webp z wiki; glTF PNG má V osu převrácenou).
Svítící oči/krystal: `necromancer_glow.png`.

Čísla k ladění: `entity/NecromancerEntity.java` (HP v `createAttributes`, vzdálenost útěku `FLEE_DISTANCE`,
cooldowny v goalech) a `NecromancerOrb.java` (damage).
