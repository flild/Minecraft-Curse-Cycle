# Документация Curse-Cycle

## Для игрока

- [Установка, правила и интерфейс](../README.md).
- [Все испытания с краткими описаниями](challenges.md).
- [Расписание, сложности, включение испытаний и настройки клиента](configuration.md).
- [Команды и права доступа](commands.md).
- [Быстрая проверка без ожидания полного цикла](quick-testing.md).

## Для разработчика

- [Архитектура, сборка, JUnit, GameTest и клиентские пробы](development.md).
- [Сценарии расписания, сна и восстановления пустого цикла](cycle-scheduling-testing.md).
- [Проверки общих механизмов](foundation-review-testing.md).
- [Визуальное оформление, ассеты и клиентская проверка](visual-refresh.md).
- [Правила работы в репозитории](../AGENTS.md).

## Проверки испытаний

Документы ниже содержат технические правила, ручные сценарии и датированные результаты прошлых запусков. Такие результаты относятся к указанной дате и версии кода; они не означают, что текущая сборка проверена заново. Логи и временные init-скрипты из `build/` могут отсутствовать в свежем клоне. Воспроизводимые общие команды приведены в руководстве разработчика.

### Лёгкие

- [Без бега](quick-testing.md) — `no_sprint`.
- [Дырявые карманы](damage-slot-drop-testing.md) — `damage_slot_drop`.
- [Как будто это рпг](forced-third-person-rpg-testing.md) — `forced_third_person_rpg`.
- [Как в контрстрайке](attack-recoil-testing.md) — `attack_recoil`.
- [Крафт по талонам](crafting-tax-testing.md) — `crafting_tax`.
- [На ощупь](blind-hud-testing.md) — `blind_hud`.
- [Не ломается, баг наверно](no-block-breaking-testing.md) — `no_block_breaking`.
- [Ну ты же не строитель](no-block-placing-testing.md) — `no_block_placing`.
- [Одноразовое меню](one-time-menu-testing.md) — `one_time_menu`.
- [Попрыгунчик](auto-jump-testing.md) — `auto_jump`.
- [Прикованные к земле](quick-testing.md) — `no_jumping`.
- [Тесновато](tiny-fov-testing.md) — `tiny_fov`.
- [Тихий час](silent-world-testing.md) — `silent_world`.
- [Только фонка не хватает](slippery-hands-testing.md) — `slippery_hands`.
- [Туда нельзя](blocked-direction-testing.md) — `blocked_direction`.

### Средние

- [Почкование](budding-testing.md) — `budding`.
- [Газ заклинило](stuck-throttle-testing.md) — `stuck_throttle`.
- [Саймон говорит](simon-says-testing.md) — `simon_says`.

- [Аптечная лотырея](random-potion-testing.md) — `random_potion`.
- [Водобоязнь](floor-is-water-testing.md) — `floor_is_water`.
- [Голый король](armor-disabled-testing.md) — `armor_disabled`.
- [Горький опыт](bitter-experience-testing.md) — `bitter_experience`.
- [Жестокая диета](infinite-diet-testing.md) — `infinite_diet`.
- [Земля тянет к себе](gravity-ten-testing.md) — `gravity_ten`.
- [Добро пожаловать на Луну](low-gravity-testing.md) — `low_gravity`.
- [Их здесь нет](invisible-mobs-testing.md) — `invisible_mobs`.
- [Конфига симпла](offset-crosshair-testing.md) — `offset_crosshair`.
- [Курчу верчу запутать хочу](hotbar-shuffle-testing.md) — `hotbar_shuffle`.
- [На массе](heavy-body-testing.md) — `heavy_body`.
- [Не трогай чужое](locked-containers-testing.md) — `locked_containers`.
- [Ноги помнят](movement-memory-testing.md) — `movement_memory`.
- [Почти как эндермен](damage-teleport-testing.md) — `damage_teleport`.
- [Рейд без выходных](endless-raid-testing.md) — `endless_raid`.
- [Смотри мне в глаза.](forced-third-person-front-testing.md) — `forced_third_person_front`.
- [Стоило купить крем от солнца](sunlight-burn-testing.md) — `sunlight_burn`.
- [Судебное решение](inventory-lockdown-testing.md) — `inventory_lockdown`.

### Сложные

- [Метеослужба предупреждала](meteor-shower-testing.md) — `meteor_shower`.

- [За тобой выехали](skeleton-hunter-testing.md) — `skeleton_hunter`.

- [Мелкий, но проблемный](tiny-player-testing.md) — `tiny_player`.
- [Большой и неудобный](giant-player-testing.md) — `giant_player`.
- [Гарантия закончилась](warranty-expired-testing.md) — `warranty_expired`.
- [После меня хоть пожар](fire-trail-testing.md) — `fire_trail`.
- [Кровавая повинность](blood-tribute-testing.md) — `blood_tribute`.
- [Пацифист](pacifist-testing.md) — `pacifist`.
- [Да как ходить то!](inverted-keyboard-testing.md) — `inverted_keyboard`.
- [Зато под водой дышать можно](gills-testing.md) — `gills`.
- [Лучше бы вечная тошнота](inverted-mouse-testing.md) — `inverted_mouse`.
- [На ручном приводе](heartbeat-testing.md) — `heartbeat`.
- [Незеритовая вечеринка](netherite-mobs-testing.md) — `netherite_mobs`.
- [Пол — это лава!](floor-is-lava-testing.md) — `the_floor_is_lava`.
- [Последнее сердечко](one-heart-testing.md) — `one_heart`.
- [С ног на голову](upside-down-camera-testing.md) — `upside_down_camera`.
- [Само не заживёт](no-healing-testing.md) — `no_healing`.
- [Собрал все болячки](all-debuffs-testing.md) — `all_debuffs`.
- [У тебя СДВГ?](adhd-no-standing-still-testing.md) — `adhd_no_standing_still`.
- [Хрупкий пол](fragile-floor-testing.md) — `fragile_floor`.

Двойное испытание и служебные заглушки описаны в [полном списке](challenges.md); примеры запуска пары — в [справочнике команд](commands.md). Для `no_jumping` и `no_sprint` ссылки ведут на общий сценарий быстрой проверки.
