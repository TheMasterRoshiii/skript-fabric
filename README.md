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
8. [Soporte y limitaciones](#soporte-y-limitaciones)
9. [Diferencias con el Skript original](#diferencias-con-el-skript-original)
10. [Compilar desde fuente](#compilar-desde-fuente)

---

## Instalación

1. Copia `build/libs/skript-1.0.0.jar` a la carpeta `mods/` de un servidor Fabric 1.21.1 con fabric-api instalado.
2. Arranca el servidor una vez: se crea `skript/scripts/` dentro del directorio de trabajo del servidor.
3. Guarda tus scripts como texto plano dentro de esa carpeta y reinicia o ejecuta `/skript reload`.

Se aceptan `.sk` sin distinguir mayúsculas (`.SK` también) y `.sk.txt` para editores de hosts que añaden
`.txt`. El tipo MIME y la asociación del archivo en el panel no intervienen. La lectura usa UTF-8 por defecto,
elimina su BOM si existe y admite UTF-16 LE/BE con BOM. Una codificación inválida genera un error con la ruta
completa; no se sustituye texto silenciosamente.

Configuración opcional en `skript/config.properties`:

```properties
scripts.folder=scripts
variables.file=variables.json
variables.save-interval-seconds=120
log.verbose=false
patches.itemconsume=true
```

`scripts.folder` admite una ruta relativa a `skript/` o una ruta absoluta, incluso con espacios. El log muestra
la carpeta buscada y la ruta de cada archivo cargado, junto con su número de triggers, comandos y funciones.
Se recorren subcarpetas en orden de ruta; archivos y subcarpetas cuyo nombre empieza por `-` se ignoran.
Los enlaces a archivos regulares se admiten; no se recorren enlaces a directorios.

### Si el host no reconoce el archivo

Comprueba la ruta que aparece en el log, guarda el archivo como texto plano UTF-8 con nombre `funciones.sk`
o `funciones.sk.txt` y ejecuta `/skript reload`. El mod abre el contenido directamente. Un `.txt` genérico
no se carga y un error de sintaxis sigue siendo un error aunque la extensión sea válida.

Los errores muestran `ruta:línea:columna`, la causa y el fragmento señalado con `<--[HERE]` en el log y en
`/skript reload`. Si falla un argumento, indican el tipo o la sintaxis esperada. Cuando no se puede aislar
el fragmento, señalan el inicio de la línea.
Los errores de lectura indican el archivo y el motivo; las opciones inválidas de `config.properties`
indican la clave, el valor esperado y el valor predeterminado usado.

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
on load                    on item consume            on item use
on item use of apple       on totem pop               on equipment change
on player equipment change on bed enter               on bed leave
on player world change     on entity world change     on entity load
on entity load of zombie   on entity unload           on server start
on server stop
```

Los filtros `of <tipo>` aceptan `player`, nombres de entidad (`zombie`) o items/bloques según el evento. Un tipo desconocido impide cargar el script e indica archivo, línea, columna y el tipo esperado. `break` y `place` requieren un item de bloque. Los eventos cancelables (`chat`, `damage`, `break`, clicks, `command`, `item consume`, `item use`) respetan `cancel event`. En los demás, usarlo produce un error con archivo, línea y causa.

Valores disponibles por evento: `player`, `attacker`, `victim`, `message`, `damage`, `command`, `event-block`, `event-item`, `world`.

`on item consume` se dispara al completar el uso de comida, pociones o leche, antes de consumir el objeto.
Permite usar `player`, `event-item`, `name of event-item` (incluye el nombre personalizado) y `world`.
`cancel event` evita la consumición y sus efectos. `patches.itemconsume=false` desactiva su Mixin al reiniciar;
los scripts que declaren ese evento reciben un error con la opción que deben activar.

`on item use` ocurre al intentar usar el ítem; `on item consume`, al terminar de consumirlo.
`on totem pop` ocurre después de que Minecraft consuma el tótem y aplique sus efectos. Conserva el nombre
personalizado en `name of event-item`; no admite cancelación. `patches.totempop=false` desactiva su Mixin al
reiniciar y explica qué opción activar si un script declara ese evento.

```vb
on totem pop:
    if name of event-item is "Amuleto":
        send "Tu Amuleto te salvó." to player
```

| Evento | Valores adicionales |
|---|---|
| `item use`, `totem pop` | `event-item`, `name of event-item`, `event-entity`, `player`, `world` |
| `equipment change` | `event-item`, `previous item`, `equipment slot`, `event-entity`, `player`, `world` |
| `bed enter`, `bed leave` | `player`, `bed`, `world` |
| `world change`, `dimension change` | `event-entity`, `player` si es jugador, `previous world`, `world` de destino |
| `entity load`, `entity unload` | `event-entity`, `player` si es jugador, `world` |

Los cambios de equipo, cama y mundo observan acciones ya realizadas. `entity load` y `entity unload`
también incluyen cargas y descargas de chunks; no equivalen a nacimiento y muerte.
`server start` ocurre después de cargar los scripts; `server stop`, antes de descargarlos.

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
op / not op                                 %player% is op / is not op
```

La comparación usa ANY-semantics sobre listas: `{lista::*} contains 3` es verdadero si algún elemento coincide.

`op` usa el jugador del evento y consulta los operadores del servidor, incluso con nivel 1.
También acepta `player is op`, `player is an operator`, `not op` y `player is not op`.
Sin jugador asociado, estas condiciones dan falso. Para ejecutar el cuerpo de un evento solo para operadores:

```vb
on item consume:
    if op:
        send "Consumido por un operador." to player
```

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

Requieren nivel de permiso 2. `/skript list` muestra rutas relativas a la carpeta configurada. Para archivos
con igual nombre en subcarpetas usa, por ejemplo, `/skript reload util/funciones.sk`; se admiten nombres con
espacios. Un nombre de archivo sin ruta solo sirve si es único entre los scripts cargados. Usa la ruta relativa
para cargar un archivo nuevo o corregido que antes falló. Recargar un archivo con errores conserva su
versión anterior y su estado. El comando confirma la solicitud y muestra el resultado al terminar; solo admite
una recarga pendiente. La lectura de archivos ocurre en un hilo virtual. La recarga completa prepara la carpeta
antes de reemplazar los scripts: si falla el escaneo, conserva los anteriores; los archivos que no compilen
quedan fuera de la nueva carga. El resultado indica cuántos archivos cargaron y cuántos fallaron.

## Arquitectura

```
dev.me.master.skript
├── Skript              entrypoint: boot idempotente + ciclo de vida
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
├── loader/             selección/decodificación de texto → árbol de nodos → triggers
├── events/             payloads sellados (sealed records) + tabla de dispatch
├── registrations/      sintaxis por defecto (tipos, expresiones, condiciones,
│                       efectos, eventos, event values)
├── scheduler/          cola por ticks + triggers periódicos
├── variables/          almacenamiento global concurrente + persistencia JSON
├── scripts/            gestor de ciclo de vida + puente de comandos a Brigadier
├── command/            /skript ...
├── bridge/             adaptadores de callbacks de fabric-api
├── mixin/              hooks de comandos/interacción + accessor de Brigadier
├── types/              ItemType, WorldPos, BlockRef, AliasIndex sobre registries
└── util/               TimeSpan, logging, puente a fabric-loader
```

Decisiones clave:

- **Patrones → regex**: cada patrón se compila a un `Pattern` con grupos perezosos, obteniendo backtracking correcto gratis. Un espacio antes de `[opcional]` se absorbe dentro de la opcionalidad.
- **Intérprete de frames**: las secciones no se recursan; empujan frames a una pila. Un `wait` lanza una señal, el executor se snapshot-ea y se agenda. Al reanudarse, loops, ifs anidados y funciones continúan exactamente donde estaban.
- **Resolución tolerante**: si un patrón casa pero su fábrica rechaza los operandos (validación de tipos), el parser sigue con el siguiente candidato antes de rendirse.
- **Registros congelados**: toda la sintaxis se registra durante `bootEngine()` y se congela; después solo hay lecturas lock-free.

## Modelo de hilos

Los triggers, funciones, compilación y cambios del juego se ejecutan en el hilo del servidor.

| Caso | Mecanismo |
|---|---|
| Lectura de recargas | un hilo virtual; contenidos inmutables devueltos mediante una cola de capacidad 1 |
| Guardado periódico de variables | hilo virtual + snapshot sobre `ConcurrentHashMap`, gate con `AtomicBoolean` |
| Parada del servidor | `saveNow()` espera ≤ 5 s al writer en vuelo y vuelca síncrono |

`wait` suspende el trigger y lo re-agenda por tick. `cancel event` debe ejecutarse antes de `wait`: después, el evento ya terminó y se informa el archivo, la línea y la corrección.

## Acceso profundo

| Objetivo | Técnica | Verificación |
|---|---|---|
| Evento `on command` | `@Inject(HEAD, cancellable)` en `CommandManager#executeWithPrefix(ServerCommandSource, String)` | Mixin requerido con `defaultRequire=1` |
| Evento `on item consume` | Inyección en `ServerPlayerEntity#consumeItem`, antes del paquete de finalización | Mixin requerido; se puede desactivar con `patches.itemconsume=false` |
| Evento `on totem pop` | Inyección en `LivingEntity#tryUseTotem`, después del estado 35 | Mixin requerido; se puede desactivar con `patches.totempop=false` |
| Kick (`connection.disconnect`) | Access Widener: campo `ServerCommonNetworkHandler.connection` → accessible | validado por `validateAccessWidener` en build |
| Remoción de comandos de script | Accessor Mixin sobre `CommandNode.children/literals/arguments` | comprobación del accessor al iniciar; `AssertionError` si no se aplicó |

## Soporte y limitaciones

- Esta rama soporta Fabric con Minecraft 1.21.1 y Java 21. No se ofrece soporte para otros loaders o versiones.
- UTF-16 requiere BOM; otros formatos de texto y archivos `.txt` genéricos no se autodetectan.
- Las funciones deben definirse antes de sus llamadas. Entre archivos, importa el orden de ruta de carga.
- Una llamada ya compilada conserva su definición de función. Usa `/skript reload` completo si cambias
  funciones que otros scripts llaman.
- La recarga completa deja fuera archivos inválidos. El comando y el log muestran la ruta, línea, columna y causa
  del error; los fallos de lectura indican la ruta y el motivo.

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

GitHub Actions compila con Java 21 en cada push y pull request; también permite ejecución manual.
Descarga el `.jar` desde `Actions` → `Build` → la ejecución → `Artifacts` → `skript-<commit>`.
