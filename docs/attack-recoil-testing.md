# Как в контрстрайке — проверка attack_recoil

Испытание `attack_recoil`: MEDIUM/GLOBAL. Успешная основная ближняя атака
добавляет к текущей скорости игрока горизонтальный импульс 0,6 от цели,
сохраняя Y. Итоговая дистанция зависит от ванильной физики.

## Реализация

`AttackRecoilPlayerEntityMixin` оборачивает только `Entity.damage` основной
цели в `PlayerEntity.attack`; sweep использует `LivingEntity.damage`.
`@Share` хранит receipt одного вызова. Принятие определяется фактическим
уменьшением HP/поглощения до тотема в обоих `applyDamage`, а не boolean return.
Импульс применяется на нормальном возврате всей атаки после sprint damping
и реакций зачарований. При совпадении XZ берётся противоположный yaw,
независимо от pitch. Сопротивление отбрасыванию и приседание его не уменьшают.
Creative/Spectator, мёртвый атакующий, пассажир и стойка исключены.

Сервер выставляет `velocityModified`. Локальный байткод 1.21.1/Yarn build.3
подтверждает отправку через `EntityTrackerEntry.sendSyncPacket` наблюдателям
и самому игроку. Собственного клиентского импульса, C2S, таймера, UUID map,
NBT-маркера или восстановления старой скорости нет. После выключения
прекращаются новые импульсы, уже полученная скорость сохраняется.

## Исправление ревью 2026-11-03

`attack_recoil-F01`: ALLOW_DAMAGE основной атаки вызывает вложенный damage
той же цели и отменяет основную атаку. Раньше вложенное уменьшение HP
подтверждало outer receipt: HP−1 от постороннего источника давал ложную отдачу.
Это воспроизведено на мобе и ServerPlayer без входящих damage-испытаний.

Теперь `AppliedDamageLivingEntityMixin` один раз заимствует primary receipt
для любой записываемой LivingEntity. Рекурсивный damage той же цели получает
свой receipt через `DamageHooks.isPending`; его accepted не подтверждает
родительский вызов. Совпадение DamageSource не объединяет вызовы. Pop/remove
в finally сохраняются. Урон, shield/i-frames и порядок drop→teleport не меняются.

## Свежие автоматические проверки

Все команды — из корня проекта, с существующим Gradle cache; зависимости прежние.

```powershell
.\gradlew.bat build runGametest --offline -I build/attack-recoil-review-final.init.gradle
.\gradlew.bat processGametestResources --offline --rerun-tasks
python build/attack-recoil-review-verify.py
git diff --check
git diff --cached --check
```

Финальный запуск 2026-11-03: exit 0, build, **149/149 JUnit в 54 suites**,
**59/59 GameTest**: 8 AttackRecoil, 8 DamageSlotDrop, 7 DamageTeleport,
5 BitterExperience, 4 Foundation, 7 Gills, по 5 NoHealing/OneHeart/AllDebuffs/
RandomPotion. Штатный shutdown. Это адресная регрессия общего damage/health/
effect-контракта, не новое полное ревью остальных испытаний.

Лог `build/attack-recoil-review-final.log`, XML
`build/reports/attack-recoil-review-final.xml`, JUnit
`build/reports/attack-recoil-review-final-junit`. Verifier подтвердил 5 SHA,
6 полей метаданных, регистрацию один раз, все результаты попыток, отсутствие
стендов в двух production JAR и восстановленные 43 GameTest entrypoints.

Предметные методы `AttackRecoilGameTest`:

| Метод | Что действительно исполнено |
|---|---|
| primaryDamageBeforeTotemAndFinalVelocity | Настоящая attack: inactive/start, сложение/Y/диагональ/yaw, sneak/resistance, HP/absorption/death/totem, sprint и vanilla target control, tracker packet |
| sweepShieldsFramesAndIndirectSources | Sweep с уроном вторичной цели/один импульс, два вызова в одном tick/два импульса, щит с PvP/control, i-frames/cancel/Resistance V/invulnerability/стойка/лодка/empty swing; прямые damage-запросы arrow/fire/thorns/explosion/playerAttack вне attack |
| activityLifecycleAndPlayerDamageReactions | Команды list/activate/active, modes/enabled/passenger/vehicle, late/два игрока/PERSONAL, death/respawn/live replacement, state disk, no_sprint и player target/drop once |
| nestedMobDamageCannotConfirmRejectedPrimary | Моб: nested same/different source × cancel/accept primary; HP−1/recoil0 либо HP−4/recoil1 |
| nestedPlayerDamageCannotConfirmRejectedPrimary | То же для ServerPlayer без активных incoming-damage испытаний |
| repeatedCallsExceptionsAndActualPlayerDiskReload | 32 цикла repeated/double same/GLOBAL+PERSONAL/end; thrown damage propagates, stack пуст, следующий hit работает; настоящие playerdata+PersistentState disk active/ended/offline end, HP/XP/items/foreign clock |
| heavyJumpAndGravityTravelPreserveFullRecoil | Реальный jump heavy×0,6 + полный recoil; travel triple gravity/vanilla drag; после снятия recoil вторая механика остаётся |
| actualThornsAndTeleportReactIndependently | Настоящий Thorns III enchantment на цели, наблюдаемый урон атакующему и teleport5; recoil по конечным XZ после teleport; после снятия recoil DT продолжает работать. ≤32 попыток на вариант, не benchmark |

Все атаки серверные с применёнными Mixin. Игроки используют EmbeddedChannel,
стендовые позиции/зарядку/вызовы travel, а не настоящий клиентский ввод.
PvP восстанавливается, сущности и временный floor удаляются/возвращаются.
Для физики пары используется simulateMovement=true; packet emission не
доказывает доставку и применение скорости реальным клиентом.

Журнал попыток: baseline 3/3; первый red init не компилировался из-за UTF-8 BOM
(проверки не начались); red-execution 3/5 воспроизвёл два F01; fixed 5/5;
expanded 8/8; final 59/59. На каждом реально исполненном build JUnit149/149.
Ошибка запуска tools sandbox-bin access denied произошла до Gradle;
команды выполнены с разрешённым доступом. Lock-файлы не удалялись.
Подробные команды/пути и матрица полного ТЗ — локальный отчёт
`Промпты/reviews/11-attack_recoil.md`.

Оценка расходов static: gate O(G+A), импульс O(1), receipt stack O(D),
incoming dispatch O(K log K + K×(G+A)). Новые claim/peek постоянно вызываются
из damage, даже inactive; это ненулевая ограниченная работа. Нет own tick,
render, поиска/force-load, markDirty или сериализации на удар. Вложенные
receipts живут до finally. Пустой thread-local deque возможен по общему
контракту foundation-L02; Player/World в нём не удерживаются. MSPT/FPS,
allocations и длительный memory/load профиль не измерены.

## Исторические результаты

Старая реализация 2026-10-27 заявляла 80 JUnit и 23 GameTest. Эти числа
не используются как доказательство ревью 2026-11-03. Старое замечание
о ещё не реализованных heavy_body/movement_memory устарело; предметно
на этом этапе проверена только указанная пара heavy_body.

## Ручные проверки — не выполнены

1. `attack_recoil-L01`: одиночный Survival/Adventure, ровная площадка:
   list/activate/active, русские HUD/menu без отдельного спама. Удар/диагональ/
   совпадение XZ→добавленные 0,6/Y unchanged; sweep→один, промах/блок/стойка/
   полный shield/пассажир→ноль. Реальные стрелы, fire aspect delay, pet attack
   не дают своей отдачи. Проверить стену/трение/лёд, slippery_hands и
   movement_memory, no_sprint/heavy_body/gravity_ten и ответные Шипы/DT;
   после замены AR вторая механика остаётся. Portal/death/keepInventory оба
   сохраняют ванильные последствия, возрождение даёт только новые реакции.
2. `attack_recoil-L02`: dedicated + два настоящих Survival клиента и late third.
   Проверить скорость самого атакующего/наблюдателя, independent hits и
   GLOBAL/PERSONAL/double/end; disconnect/portal/death/rejoin одного не
   меняют второго, нет второго клиентского импульса или rubberband.
3. `attack_recoil-L03`: два полных новых JVM, active и ended/offline end;
   сохранить мир без игроков, новый вход. Новый успешный удар→один при active,
   ноль при ended; нет replay, отката чужих HP/XP/items/effect clocks.
   Disk roundtrip внутри одного JVM не заменяет эту проверку.
4. `attack_recoil-L04`: одинаковые мир/версии/P/G/A/attack rate/прогрев/
   длительность, inactive/active/double и primary/sweep/nested controls;
   отдельно mod gates/receipt/dispatch и vanilla combat/tracker/collision.
   MSPT/FPS/allocations/retained memory и длительный reconnect/load профиль.

Итог ревью: **pass_with_limits**, механика **fixed**, расходы
**no_issue_found / static**. Невыполненные ручные сценарии не подтверждены.
