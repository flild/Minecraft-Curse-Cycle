# Ассеты чёрной дыры

Используется [Black Hole — Grab3D](https://grab3d.com/models/sci-fi/black-hole/), лицензия **CC0 1.0**.

- Исходник: https://grab3d.com/dl/black-hole/black-hole.glb
- Лицензия автора: https://grab3d.com/dl/black-hole/LICENSE.txt
- Метаданные автора: https://grab3d.com/dl/black-hole/manifest.json
- SHA-256 скачанного GLB: `8fc9f97b4b65b5c0b01497cf80e92b4fa2e70a80f5dccf390d2b5969cb3bf09f`.
- Копия лицензии в моде: `assets/cursecycle/licenses/grab3d-black-hole.txt`.

Из GLB взяты шесть мешей: `disk_hot_glow`, `disk_lilac_glow`, `disk_orange_glow`, `disk_pink_glow`, `photon_ring_glow`, `swirl_glow` (с префиксом `black-hole_`). Подставка, её подсветка и PBR-тело исключены. Геометрия центрирована по диску, нормирована и преобразована в `assets/cursecycle/models/black_hole.bin`: 13 680 треугольников, 492 584 байта. Нормали и PBR-текстуры не нужны для самосветящегося эффекта.

Для воспроизведения скачать GLB по ссылке выше, проверить SHA-256 и выполнить из корня репозитория:

```powershell
python tools/convert_black_hole.py path/to/black-hole.glb
```

Формат BHM1: big-endian magic `0x42484D31`, int количества слоёв; затем для каждого слоя int количества вершин, три int RGB и плоские float XYZ для треугольников. Клиент загружает его через resource manager и один статический GPU-буфер. GLB-парсер или сторонняя библиотека в игре не требуются.

Чёрное ядро, анимация, шейдер неоднородного свечения, геометрия радиуса и полёт обломков реализованы в Curse-Cycle. Шейдер использует меш скачанного ассета; это не искажение изображения мира и не полноценная симуляция гравитационного линзирования.

Оригинальный GLB и другие рассмотренные, но неиспользованные изображения остаются только в игнорируемом `build/black-hole-assets` и в JAR не попадают.
