# Event Navigator Eclipse Plugin

## Áttekintés

Az **Event Navigator Plugin** egy Eclipse IDE kiegészítő, amely eseményvezérelt Java alkalmazások fejlesztését támogatja. A plugin intelligens navigációt, kódanalízist és vizualizációt biztosít az esemény publikálók (publishers) és feliratkozók (subscribers) között.

## Release Notes

### Ismert hibák

⚠️ **Project refresh esetén a JSON index nem mindig frissül automatikusan.**

- A jobb klikkes manuális indexelés továbbra is megbízhatóan működik.
- Használd a `Window → Preferences → Event Navigator` menüben a **Clear Cache** gombot, ha hibás navigációt tapasztalsz.

## Projekt Struktúra

```
hu.it4all.plugin.navigator/
│
├── META-INF/
│   └── MANIFEST.MF              # OSGi bundle manifest
│
├── src/
│   └── org/smartbit4all/eclipse/event/
│       ├── EventActivator.java                    # Plugin aktivátor
│       │
│       ├── ast/                                   # AST elemzés
│       │   ├── ASTVisitors.java
│       │   ├── EventAnnotationScanner.java
│       │   ├── EventPublisherScanner.java
│       │   └── JavaElementResolver.java
│       │
│       ├── core/                                  # Core komponensek
│       │   ├── EventDefinition.java
│       │   ├── EventIndexManager.java
│       │   ├── EventIndexPersistence.java
│       │   ├── EventLogger.java
│       │   ├── EventPluginProperties.java
│       │   ├── EventPublisherInfo.java
│       │   ├── EventRegistry.java
│       │   ├── EventResourceChangeListener.java
│       │   └── EventSubscriberInfo.java
│       │
│       ├── navigation/                            # Navigációs funkciók
│       │   ├── EventHyperlinkDetector.java
│       │   ├── EventNavigationHandler.java
│       │   ├── EventSubscriberListHyperlink.java
│       │   ├── FindEventHandlersAction.java
│       │   └── JavaElementHyperlink.java
│       │
│       ├── preferences/                           # Beállítások
│       │   ├── EventPluginPreferencePage.java
│       │   └── EventPluginPreferences.java
│       │
│       ├── refactoring/                           # Refaktorálás
│       │   ├── DeleteEventParticipant.java
│       │   ├── MoveApiParticipant.java
│       │   └── RenameEventParticipant.java
│       │
│       ├── ui/                                    # UI komponensek
│       │   ├── commands/
│       │   ├── gutter/
│       │   ├── hover/
│       │   ├── views/
│       │   └── wizards/
│       │
│       └── validation/                            # Validáció
│           ├── EventMarkerManager.java
│           ├── EventQuickFixProcessor.java
│           └── EventValidationParticipant.java
│
├── icons/                                         # Plugin ikonok
├── videoResources/                                # Demó videók
├── plugin.xml                                     # Eclipse plugin konfiguráció
├── plugin.properties                              # Plugin properties
├── build.properties                               # Build konfiguráció
└── README.md                                      # Ez a dokumentáció

```

## Telepítés

1. Másold be a `.jar` fájlt ide:

   ```
   C:\Users\<your user>\eclipse\<version>\eclipse\dropins
   ```

2. Indítsd újra az Eclipse-t

3. A plugin automatikusan elérhető lesz

### Előfeltételek

- **Eclipse IDE**: 2020-09 vagy újabb verzió
- **Java Runtime**: JavaSE-11 vagy újabb
- **Eclipse JDT**: Java Development Tools telepítve

## Használat

### Navigáció

![Navigáció Demo](videoResources/navigation.gif)

#### Publisher → Subscribers navigáció

1. Vidd az egeret az `invocationApi.publish` fölé
2. Tartsd lenyomva a **Ctrl**-t
3. A megjelenő popup **utolsó opciója** kilistázza az összes hozzá tartozó subscribert
4. Kattints a kívánt subscriberre a navigáláshoz

#### Subscriber → Publisher navigáció

1. Vidd az egeret az `@EventSubscription` fölé
2. Tartsd lenyomva a **Ctrl**-t és hover
3. Navigálhatsz a publisherhez

Az alap esemény-navigáció mindkét irányban működik.

### Indexelés – Új beállítások

**Elérés:** `Window → Preferences → Event Navigator`

#### 1) Auto-index on file save ✓

Automatikus indexelés Java fájl mentésekor.

- **BE (alapértelmezett)**: Minden mentéskor frissül az index (ajánlott).
- **KI**: Csak manuális indexelés vagy build esetén frissül.

#### 2) Re-index on project clean/rebuild

Újraindexelés project clean/build esetén.

- **BE**: Clean/Build után teljes újraindexelés.
- **KI (alapértelmezett)**: Build nem triggerel indexelést.

#### 3) Clear Cache gomb

Törli a mentett JSON cache-t.

- Következő Eclipse indításkor újraindexeli a workspace-t.
- **Használd, ha:** korrupt adatok, hibás navigáció, eltűnt linkek.

#### Cache fájl helye

```
<workspace>/.metadata/.plugins/org.smartbit4all.eclipse.event/event-index.json
```

## Konfiguráció

### plugin.properties

A plugin viselkedését a `plugin.properties` fájlban konfigurálhatod:

```properties
# Esemény annotáció konfigurálása
event.subscription.annotation.fqn=org.smartbit4all.api.invocation.EventSubscription
event.subscription.annotation.name=EventSubscription

# Esemény paraméterek
event.subscriber.info.api=api
event.subscriber.info.event=event
event.subscriber.info.channel=channel

# Publikáló API konfigurálása
event.publisher.invocation.api.fqn=org.smartbit4all.api.invocation.InvocationApi
event.publisher.method.name=publisher

# Logolás
event.logging.enabled=true

# Indexelési prioritás
indexing.priority.module=platform
```

## Technológiai Stack

- **Eclipse Plugin Framework**: OSGi bundle alapú architektúra
- **Eclipse JDT**: Java Development Tools integráció
- **AST Parser**: Eclipse JDT AST API (JLS Latest)
- **Persistence**: Gson alapú JSON szerializáció
- **Threading**: Eclipse Jobs API háttérműveletekhez
- **UI**: Eclipse JFace és SWT

## Architektúra

### Komponensek

#### 1. **EventActivator**

- Plugin életciklus kezelése
- Komponensek inicializálása
- Resource change listener regisztrálása

#### 2. **EventIndexManager**

- Workspace-szintű esemény index kezelése
- Perzisztens tárolás Gson-nal
- Változások automatikus követése

#### 3. **AST Scanners**

- `EventAnnotationScanner`: `@EventSubscription` annotációk detektálása
- `EventPublisherScanner`: Esemény publikálások felismerése
- `ASTVisitors`: Általános AST bejárási logika

#### 4. **EventHyperlinkDetector**

- Ctrl+Click navigáció implementálása
- Hiperhivatkozások generálása runtime-ban
- Kontextus-függő navigációs javaslatok

#### 5. **Validation & Markers**

- `EventValidationParticipant`: Valós idejű validáció
- `EventMarkerManager`: Marker ikonok kezelése
- `EventQuickFixProcessor`: Quick-fix javaslatok

#### 6. **Refactoring Participants**

- `RenameEventParticipant`: Események átnevezése
- `DeleteEventParticipant`: Események törlése
- `MoveApiParticipant`: API mozgatás támogatása

## Hibaelhárítás

### Plugin nem töltődik be

- Ellenőrizd, hogy az Eclipse verzió kompatibilis-e (2020-09+)
- Nézd meg az Error Log-ot: `Window > Show View > Error Log`
- Próbáld meg újraindítani Eclipse-t `-clean` kapcsolóval

### Navigáció nem működik

- Indítsd újra az indexelést: `Event Navigator > Index Workspace`
- Ellenőrizd, hogy a `plugin.properties` megfelelően van-e konfigurálva
- Nézd meg a Console-t a hibaüzenetekért

### Index nem frissül automatikusan

- Ellenőrizd, hogy az `EventResourceChangeListener` regisztrálva van-e
- Nézd a logokat: `EventActivator` indítási üzenetei
- Manuális frissítés: `Event Navigator > Index Workspace`

## Roadmap & Jövőbeli Fejlesztések

- [ ] **Event Graph View**: Vizuális ábra az események és függőségeik között
- [ ] **Code Completion**: Automatikus kiegészítés esemény nevekhez
- [ ] **Performance optimalizáció**: Nagy projektek gyorsabb indexelése
