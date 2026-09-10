# Skript Fabric

Port de [Skript](https://github.com/SkriptLang/Skript) (plugin de scripting para Bukkit/Spigot) a un **mod de Fabric** para **Minecraft 1.21.1** con **Java 21**. Permite escribir la misma clase de scripts de alta legibilidad (`on join:`, `if ...`, `wait 5 seconds`) directamente sobre un servidor vanilla/modded sin Bukkit.

Este repositorio contiene el motor completo: parser del lenguaje, runtime reanudable, sistema de variables persistente, registro de tipos, bridge de eventos de Fabric y comandos de gestión.

---

## Tabla de contenido

1. [Instalación](#instalación)
2. [Primeros pasos](#primeros-pasos)
3. [Referencia del lenguaje](#referencia-del-lenguaje)
   - [Estructuras raíz](#estructuras-raíz)
   - [Eventos](#eventos)
   - [Secciones](#secciones)
   - [Variables](#variables)
   - [Expresiones](#expresiones)
   - [Condiciones](#condiciones)
   - [Efectos](#efectos)
   - [Funciones](#funciones)
   - [Comandos definidos por script](#comandos-definidos-por-script)
4. [Comandos de gestión](#comandos-de-gestión)
5. [Arquitectura](#arquitectura)
6. [Modelo de hilos](#modelo-de-hilos)
7. [Acceso profundo (Mixin / Access Widener)](#acceso-profundo)
8. [Rendimiento](#rendimiento)
9. [Testing](#testing)
10. [Diferencias con el Skript original](#diferencias-con-el-skript-original)
11. [Compilar desde fuente](#compilar-desde-fuente)

---

## Instalación

1. Copia `build/libs/skript-1.0.0.jar` a la carpeta `mods/` de un servidor Fabric 1.21.1 con fabric-api instalado.
2. Arranca el servidor una vez: se crea `skript/scripts/` junto al jar de configuración.
3. Escribe scripts `.sk` dentro de `skript/scripts/` y reinicia o ejecuta `/skript reload`.

Configuración opcional en `skript/config.properties`:

```properties
scripts.folder=scripts
variables.file=variables.json
variables.save-interval-seconds=120
log.verbose=false
```

Los archivos cuyo nombre empiece por `-` se ignoran (convención de "deshabilitado").

## Primeros pasos

`skript/scripts/bienvenida.sk`:

```applescript
options:
    prefix: &8[&aServidor&8]&r

on join:
    send "{@prefix} &eBienvenido, %player%!" to player
    give 5 bread to player

command /spawn:
    description: te lleva al spawn del mundo
    executable by: players
    trigger:
        teleport player to position at 0, -60, 0 in overworld
```

## Referencia del lenguaje

### Estructuras raíz

| Estructura | Descripción |
|---|---|
| `options:` | Constantes textuales sustituidas antes del parseo con `{@nombre}` |
| `variables:` | Valores iniciales de variables globales (`{x}: valor`) |
| `on <evento>:` | Trigger por evento (ver [Eventos](#eventos)) |
| `every <timespan>:` | Trigger periódico (`every 10 seconds:`) |
| `function nombre(args) :: tipo:` | Función invocable desde expresiones |
| `command /nombre <args>:` | Comando registrado en Brigadier |

### Eventos

```
on join                    on quit                    on chat
on damage                  on damage of player        on death
on death of player         on break                   on break of stone
on place                   on place of diamond block  on rightclick
on leftclick               on respawn                 on command
on load
```

Los filtros `of <tipo>` aceptan `player`, nombres de entidad (`zombie`) o items/bloques según el evento. Los eventos cancelables (`chat`, `damage`, `break`, clicks, `command`) respetan `cancel event`.

Valores disponibles por evento: `player`, `attacker`, `victim`, `message`, `damage`, `command`, `event-block`, `world`.

### Secciones

```
if <condición>:
else if <condición>:
else:

while <condición>:

loop <valores>:
    loop-value      # valor actual
    loop-index      # índice numérico (siempre disponible)
```

`loop` itera listas literales (`loop 1 and 2 and 3:`), listas de variables (`loop {misdatos::*}:`), `all players` y `all worlds`.

### Variables

```
{global}            variable global persistente
{_temporal}         variable local al trigger
{lista::1}          acceso indexado
{lista::*}          todos los elementos (iterable)
{config::%uuid of player%}   índice dinámico con cualquier expresión
```

Persisten en `skript/variables.json` entre reinicios. Se serializan strings, números, booleans, timespans, items, entity types, bloques, jugadores (UUID), mundos y posiciones.

### Expresiones

```
player  attacker  victim  message  world  event-block
health of %entity%          uuid of %entity%       name of %entity%
held item of %player%       location of %entity%
position at X, Y, Z [in %world%]
block at %posición%
x-coordinate / y-coordinate / z-coordinate of %posición%
random number between A and B
distance between %posA% and %posB%
difference between %timespanA% and %timespanB%
%number% %itemtype%                  (ej: 5 apples)
arg-N / argument N                   (args del comando actual)
loop-value / loop-index
add(2, 3)                            (llamada a función)
aritmética completa: + - * / ^ con precedencia y paréntesis
```

Literales: `"textos"`, `'textos'`, números, `true/false`, timespans (`5 seconds`, `1 hour and 30 minutes`), items (`cobblestone`, `5 apple`), entity types (`zombie`), mundos (`overworld`).

### Condiciones

```
a is b                a isn't b              a is not b
a is greater than b   a > b                  a is at least b     a >= b
a is less than b      a < b                  a is at most b      a <= b
%objs% contains %obj%                        %player% has permission x
%entities% exists / is set                   %player% is sneaking
%player% is holding %itemtype%               %number% chance
```

La comparación usa ANY-semantics sobre listas: `{lista::*} contains 3` es verdadero si algún elemento coincide.

### Efectos

```
send %text% to %players%            broadcast %text%
set {var} to %value%                add %v% to {var}
remove %v% from {var}               delete {var} / clear {var}
give %itemtype% to %player%         take %itemtype% from %player%
teleport %entity% to %posición%     kill %entity%
spawn [N] %entitytype% [at %posición%]
heal %entity%                       damage %entity% by N
set block at %bloque% to %block%    clear block at %bloque%
make %player% execute command %cmd% execute console command %cmd%
play sound %nombre% at %posición%   kick %player%
wait %timespan%                     stop trigger
exit loop                           exit section
cancel event                        return %valor%
```

### Funciones

```applescript
function add(a: number, b: number) :: number:
    return arg-1 + arg-2

# uso en cualquier expresión:
send "2+3=%add(2, 3)%" to player
```

Parámetros tipados con valores por defecto opcionales (`b: number = 5`). Tipos consultables: `number`, `integer`, `string`, `boolean`, `timespan`, `itemtype`, `entitytype`, `block`, `world`, `player`.

### Comandos definidos por script

```applescript
command /kit <player> [<string>]:
    description: entrega un kit
    usage: /kit <jugador>
    permission: kit.usar
    executable by: players and console
    trigger:
        give iron sword to arg-1
        send "&aKit entregado" to arg-1
```

Se registran en el dispatcher real de Brigadier (tab-completion del nombre incluido) y se sincronizan en cada recarga. Los args llegan como `arg-1`, `arg-2`, ... y también por nombre (`\0arg:<nombre>` interno). Permisos custom vía scoreboard tags `skript.perm.<permiso>`.

## Comandos de gestión

```
/skript reload [script]     recarga todo o un script
/skript enable <script>     habilita un script cargado
/skript disable <script>    deshabilita (también disable all)
/skript list                lista scripts con estado y nº de triggers
```

Requieren nivel de permiso 2. Errores de parseo se reportan con archivo y número de línea; un script con errores se desactiva entero sin afectar a los demás.

## Arquitectura

```
dev.me.master.skript
├── SkriptMod           entrypoint: boot idempotente + ciclo de vida
├── SkriptConfig        configuración inmutable leída una vez al boot
├── lang/               núcleo del lenguaje
│   ├── SkriptPattern   patrones Skript → java.util.regex con backtracking
│   ├── Parser          pipeline de expresiones (variable→función→aritmética→registro→literal)
│   ├── SyntaxRegistry  registro de efectos/condiciones/expresiones, freeze inmutable
│   ├── Executor        intérprete de frames explícitos, suspendible en cualquier punto
│   ├── TriggerItem     AST ejecutable sellado (Statement | Section)
│   ├── IfSection/WhileSection/LoopSection
│   ├── VariableString  texto con slots %expr%, códigos de color
│   ├── Classes         ClassInfo + comparators + converters (BFS ≤ 3 saltos)
│   └── function/       definiciones y llamadas a funciones de script
├── loader/             lectura de archivos → árbol de nodos → triggers
├── events/             payloads sellados (sealed records) + tabla de dispatch
├── registrations/      sintaxis por defecto (tipos, expresiones, condiciones,
│                       efectos, eventos, event values)
├── scheduler/          cola por ticks + triggers periódicos
├── variables/          almacenamiento global concurrente + persistencia JSON
├── scripts/            gestor de ciclo de vida + puente de comandos a Brigadier
├── command/            /skript ...
├── bridge/             adaptadores de callbacks de fabric-api
├── mixin/              hook único a CommandManager (evento command)
├── types/              ItemType, WorldPos, BlockRef, AliasIndex sobre registries
└── util/               TimeSpan, logging, puente a fabric-loader
```

Decisiones clave:

- **Patrones → regex**: cada patrón se compila a un `Pattern` con grupos perezosos, obteniendo backtracking correcto gratis. Un espacio antes de `[opcional]` se absorbe dentro de la opcionalidad.
- **Intérprete de frames**: las secciones no se recursan; empujan frames a una pila. Un `wait` lanza una señal, el executor se snapshot-ea y se agenda. Al reanudarse, loops, ifs anidados y funciones continúan exactamente donde estaban.
- **Resolución tolerante**: si un patrón casa pero su fábrica rechaza los operandos (validación de tipos), el parser sigue con el siguiente candidato antes de rendirse.
- **Registros congelados**: toda la sintaxis se registra durante `bootEngine()` y se congela; después solo hay lecturas lock-free.

## Modelo de hilos

Toda la ejecución de scripts ocurre en el **server thread**. No hay locks entre jugadores ni estado compartido mutable fuera de dos casos deliberados:

| Caso | Mecanismo |
|---|---|
| Guardado periódico de variables | hilo virtual + snapshot sobre `ConcurrentHashMap`, gate con `AtomicBoolean` |
| Parada del servidor | `saveNow()` espera ≤ 5 s al writer en vuelo y vuelca síncrono |

`wait` no bloquea nada: suspende el trigger y lo re-agenda por tick. La cancelación de eventos solo aplica durante la ejecución síncrona del trigger (igual que en Bukkit).

## Acceso profundo

Filosofía: cuando la API pública no alcanza, se baja a internals de forma **aislada, verificada y reversible**.

| Objetivo | Técnica | Verificación |
|---|---|---|
| Evento `on command` | Mixin único `@Inject(HEAD, cancellable)` en `CommandManager#executeWithPrefix(ServerCommandSource, String)` | reflexión en boot: si la firma cambia, `AssertionError` con mensaje accionable |
| Kick (`connection.disconnect`) | Access Widener: campo `ServerCommonNetworkHandler.connection` → accessible | validado por `validateAccessWidener` en build |
| Remoción de comandos de script | reflection sobre `CommandNode.children/literals` (Brigadier es librería aparte, fuera del alcance del AW) | fail-loud con `AssertionError` si cambia la forma |

Ningún tipo obfuscado o interno se filtra al código de dominio: el mixin habla con `CommandEventHook`, que expone solo una vista mínima del source.

## Rendimiento

- Cada listener de evento hace short-circuit O(1) si ningún trigger escucha ese tipo (contadores precalculados al hacer bind).
- `broadcast` usa `PlayerManager.broadcast` (un envío batched, nunca un paquete por jugador).
- Scheduler: `PriorityQueue` por tick, O(log n) por operación, sin locks (dueño único: server thread).
- Triggers periódicos: un único hook de tick, avance O(triggers activos).
- AliasIndex: nombres de items/entity types indexados una vez al boot en mapas inmutables; lookup O(1).
- Variables: `ConcurrentHashMap` global; listas como claves jerárquicas ordenadas (`l::1`, `l::2`, ...) con vista ordenada por sufijo.
- Sin `synchronized`, sin `AtomicInteger` en datos single-thread, sin copias defensivas en hot paths.

## Testing

Harness headless (`Dbg*`, fuera del jar) que ejercita sin levantar servidor:

- Carga completa de `run/skript/scripts/smoke.sk`: opciones, variables iniciales, funciones, 10 eventos, secciones anidadas, loops con `loop-index`, índices dinámicos, `wait` (suspensión verificable), comandos.
- Ejecución real de triggers headless comprobando estado final de variables.
- Round-trip de persistencia JSON (escritura → lectura → valores tipados intactos).
- Probes unitarios de patrones, aritmética, comparadores e índices.

`blocky.sk` cubre literales de bloque/item que requieren registries vivas; fuera de servidor falla de forma limpia y avisada.

En servidor real basta `/skript reload` y revisar el log: errores de parseo incluyen archivo y línea.

## Diferencias con el Skript original

Implementado con semántica equivalente pero superficie propia:

- Motor clean-room para Fabric: no depende de Bukkit ni de `ch.njol`; mismos conceptos (triggers, sections, VariableString, aliases) reimplementados.
- Subconjunto deliberado de la sintaxis (ver referencia arriba); el registro es extensible siguiendo los mismos cuatro tipos de sintaxis del original.
- `on load` dispara por script cargado; `on command` intercepta todos los comandos (incluidos vanilla) vía mixin.
- Permisos custom mediante scoreboard tags en lugar del sistema de permisos de Bukkit.
- Persistencia JSON en vez de CSV/SQL.
- Sin experimentos/addons ni timings; sin MySQL/SQLite storage.

## Compilar desde fuente

```bash
./gradlew build     # requiere JDK 21
# producto: build/libs/skript-1.0.0.jar
./gradlew validateAccessWidener   # valida el AW contra MC mapeado
```

Loom 1.17, Minecraft 1.21.1, yarn `1.21.1+build.3`, fabric-api `0.116.x`.
