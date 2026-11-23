# PrivateBlocks (WorldGuard) — Purpur 26.2

Приват = обычный блок: медный 3x3x3, золотой 5x5x5, железный 8x8x8, алмазный 12x12x12,
изумрудный 17x17x17, незеритовый 30x30x30. Регион создаётся через WorldGuard, дальше всё через /rg.
Только незеритовый приват выдерживает 4 взрыва ТНТ, остальные ломаются одним.

Динамит A–F выдаётся командой /dynamite give [ник] [A-F] [кол-во]. Все параметры — в config.yml.

## Требования
Purpur 26.2, Java 25, WorldGuard 7.0.18 (+ WorldEdit).

## Сборка
    mvn clean package
Jar: target/PrivateBlocks.jar -> plugins/
(если не скачивается WorldGuard — в pom.xml поменяй worldguard.version на 7.0.18-SNAPSHOT)
