# Event Publisher-Listener Mechanizmus Elemzése

## Tartalomjegyzék
1. [Áttekintés](#áttekintés)
2. [A Mechanizmus Működése](#a-mechanizmus-működése)
3. [Navigálási Bonyolultság](#navigálási-bonyolultság)
4. [Kód Példák](#kód-példák)
5. [Összegzés](#összegzés)

---

## Áttekintés

Az event publisher-listener mechanizmus egy **indirekt, invocation-alapú** eseménykezelési rendszer a platformban, amely lehetővé teszi a loose coupling-ot az API-k között. A mechanizmus implementálása a **platform** modulban található, míg konkrét használata a **mod-documentdossier** modulban látható.

### Főbb Komponensek

| Komponens | Felelősség | Lokáció |
|-----------|-----------|----------|
| `InvocationApi` | Event publisher factory, invocation orchestration | `platform/api` |
| `EventPublisher` | Event publikálás koordinálása | `platform/api` |
| `InvocationRegisterApi` | Subscription registry, API metadata | `platform/api` |
| `@EventSubscription` | Subscriber metódus annotáció | `platform/api` |
| `ProviderApiInvocationHandler` | API metadata kinyerés (reflection) | `platform/api` |

---

## A Mechanizmus Működése

A mechanizmus **három fő fázisból** áll:

---

### 1. Regisztrációs Fázis (Alkalmazás Induláskor)

#### A) Subscriber Annotálása

**Fájl:** `mod-documentdossier/api/src/main/java/org/smartbit4all/documentdossier/api/mdm/MdmEventHandlerApi.java`

```java
public interface MdmEventHandlerApi extends MDMSubscriberApi {

  @EventSubscription(
      api = MasterDataManagementApi.API,  // Publisher API azonosító
      event = MasterDataManagementApi.STATE_CHANGED,  // Event név
      channel = DocumentDossierApiConfig.DOCUMENT_TYPE_SUBSCRIPTION_ASYNC_CHANNEL)
  void stateChanged(String event, String scope, URI definition, URI state, URI prevState,
      URI branchUri);

  @EventSubscription(
      api = MasterDataManagementApi.API,
      event = MDMEntryApi.INACTIVATED,
      channel = DocumentDossierApiConfig.DOCUMENT_TYPE_SUBSCRIPTION_ASYNC_CHANNEL)
  void entryInactivated(URI definition, String entryDescriptorName, URI objectUri,
      URI branchUri);
}
```

**Annotáció paraméterek:**
- `api`: A publisher API teljes osztályneve (String konstans)
- `event`: Az event neve (String konstans)
- `channel`: Az aszinkron channel neve a feldolgozáshoz
- `type`: Subscription típus (ONE_RUNTIME, ALL_RUNTIMES, SESSIONS)
- `asynchronous`: Aszinkron feldolgozás engedélyezése (default: true)

---

#### B) Reflection-alapú Metadata Gyűjtés

**Fájl:** `platform/api/src/main/java/org/smartbit4all/api/invocation/ProviderApiInvocationHandler.java`

```java
ApiData getData() {
  Set<Method> allMethods = ReflectionUtility.allMethods(interfaceClass);
  
  // Event subscriptions gyűjtése
  List<EventSubscriptionData> subscriptions = allMethods.stream().map(m -> {
    EventSubscription s =
        ReflectionUtility.getNearestAnnotation(m, EventSubscription.class);
    return s == null ? null
        : new EventSubscriptionData()
            .api(s.api())
            .event(s.event())
            .asynchronous(s.asynchronous())
            .channel(s.channel())
            .type(s.type())
            .subscribedApi(interfaceClass.getName())
            .subscribedMethod(m.getName());
  }).filter(s -> s != null).collect(toList());
  
  return new ApiData()
      .interfaceName(interfaceClass.getName())
      .name(name)
      .uri(uri)
      .methods(methods)
      .eventSubscriptions(subscriptions);
}
```

**Működés:**
1. Reflection-nel végigmegy az interface összes metódusán
2. Megkeresi az `@EventSubscription` annotációkat
3. `EventSubscriptionData` objektumokat hoz létre
4. Az `ApiData` részeként Storage-ba menti

---

#### C) Registry Frissítés

**Fájl:** `platform/api/src/main/java/org/smartbit4all/api/invocation/InvocationRegisterApiIml.java`

```java
private Map<String, List<EventSubscriptionData>> eventSubscriptionsByApis = new HashMap<>();

private ApiDescriptor addToApiRegister(ApiData apiData) {
  Map<String, ApiDescriptor> apisByName =
      apiRegister.computeIfAbsent(apiData.getInterfaceName(), n -> new HashMap<>());
  ApiDescriptor apiDescriptor =
      apisByName.computeIfAbsent(apiData.getName(), n -> new ApiDescriptor(apiData));
      
  // Subscriptions hozzáadása a registryhez
  for (EventSubscriptionData subscription : apiData.getEventSubscriptions()) {
    List<EventSubscriptionData> subscriptionsByApi =
        eventSubscriptionsByApis.computeIfAbsent(subscription.getApi(), n -> new ArrayList<>());
    subscriptionsByApi.add(subscription);
  }
  
  return apiDescriptor;
}

@Override
public List<EventSubscriptionData> getSubscriptions(String interfaceName) {
  return eventSubscriptionsByApis.getOrDefault(interfaceName, Collections.emptyList());
}
```

**Tárolás:**
- `eventSubscriptionsByApis`: Map<PublisherApiName, List<EventSubscriptionData>>
- Gyors lookup: publisher API alapján megtalálható az összes subscriber
- Runtime-ban memóriában tartott cache

---

### 2. Event Publikálás Fázis

#### A) Publisher API Kód

**Fájl:** `platform/api/src/main/java/org/smartbit4all/api/mdm/MDMModificationApiImpl.java`

```java
void fireModificationEvent(String event, String scope, URI definition, URI state,
    URI prevState, URI branchUri) {
  invocationApi
      .publisher(
          MasterDataManagementApi.class,      // Publisher API class
          MDMSubscriberApi.class,             // Subscriber API class
          MasterDataManagementApi.STATE_CHANGED)  // Event név
      .publish(api -> api.stateChanged(event, scope, definition, state, prevState, branchUri));
}
```

**Lépések:**
1. `invocationApi.publisher()` létrehoz egy `EventPublisher` instance-t
2. `publish()` lambda-ban meghívja a subscriber interface metódust
3. A hívás **nem fut le valójában**, csak rögzíti az invocation-t

---

#### B) EventPublisher Belső Működés

**Fájl:** `platform/api/src/main/java/org/smartbit4all/api/invocation/EventPublisher.java`

```java
public void publish(Consumer<S> apiCall) {
  // 1. Publisher API lekérdezése
  ApiDescriptor api = invocationRegisterApi.getApi(publisherApiClass.getName(), null);
  if (api == null) {
    throw new IllegalArgumentException(
        "The publisher api " + publisherApiClass + " was not found.");
  }

  // 2. Összes subscription lekérdezése
  Map<String, List<InvocationRequest>> requestsByChannel = new HashMap<>();
  List<EventSubscriptionData> eventSubscriptions =
      invocationRegisterApi.getSubscriptions(api.getApiData().getInterfaceName());
      
  // 3. Megfelelő subscriptions szűrése event név alapján
  for (EventSubscriptionData sub : eventSubscriptions) {
    if (event.equals(sub.getEvent())) {
      List<InvocationRequest> channelRequests =
          requestsByChannel.computeIfAbsent(sub.getChannel(), c -> new ArrayList<>());
          
      if (sub.getType() == EventSubscriptionType.ONE_RUNTIME) {
        // InvocationRequest build
        channelRequests.add(
          invocationApi.builder(subscriberApiClass).build(apiCall)
              .interfaceClass(sub.getSubscribedApi())
              .methodName(sub.getSubscribedMethod())
        );
      } else if (sub.getType() == EventSubscriptionType.SESSIONS) {
        // Session-based subscriptions
        List<Session> activeSessions = sessionManagementApi.getActiveSessions(sub.getEvent());
        activeSessions.stream()
            .map(s -> invocationApi.builder(subscriberApiClass).build(apiCall)
                .interfaceClass(sub.getSubscribedApi())
                .methodName(sub.getSubscribedMethod())
                .sessionUri(s.getUri()))
            .forEach(r -> channelRequests.add(r));
      }
    }
  }
  
  // 4. Összes request aszinkron végrehajtása channel-enként
  for (Entry<String, List<InvocationRequest>> entry : requestsByChannel.entrySet()) {
    for (InvocationRequest request : entry.getValue()) {
      invocationApi.invokeAsync(request, entry.getKey());
    }
  }
}
```

**Működési Lépések:**
1. **Lookup**: Megkeresi a publisher API-t a registry-ben
2. **Discovery**: Lekérdezi az összes subscription-t (`getSubscriptions()`)
3. **Filter**: Event név alapján szűr
4. **Build**: Minden subscriber-hez létrehoz egy `InvocationRequest`-et
5. **Group**: Channel szerint csoportosít
6. **Execute**: Aszinkron módon ütemezi az invocation-öket

---

#### C) InvocationRequest Build

**Fájl:** `platform/api/src/main/java/org/smartbit4all/api/invocation/InvocationApi.java`

```java
<T> InvocationBuilder<T> builder(Class<T> apiInterface);
```

Az `InvocationBuilder`:
- Lambda expression-ből kinyeri a metódus hívást
- Rögzíti a paramétereket
- `InvocationRequest` objektumba csomagolja
- Szerializálható formátum → storage/queue-ba menthető

---

### 3. Aszinkron Végrehajtás

#### A) Channel Feldolgozás

**Async Channel:**
- Háttérben futó thread pool
- Mentett invocation request-ek feldolgozása
- Transaction kezelés
- Retry logika hiba esetén

#### B) Subscriber Metódus Hívás

**Fájl:** `mod-documentdossier/api/src/main/java/org/smartbit4all/documentdossier/api/mdm/MdmEventHandlerApiImpl.java`

```java
public class MdmEventHandlerApiImpl implements MdmEventHandlerApi {

  @Autowired
  protected DocumentTypeApi documentTypeApi;

  @Override
  public void stateChanged(String event, String scope, URI definition, URI state, 
      URI prevState, URI branchUri) {
    // Event handler logika
    // ...
  }

  @Override
  public void entryInactivated(URI definition, String entryDescriptorName, URI objectUri,
      URI branchUri) {
    handlePropertiesRemoved(entryDescriptorName, objectUri, branchUri);
  }

  private void handlePropertiesRemoved(String entryDescriptorName, URI objectUri,
      URI branchUri) {
    if (DocumentDossierConstants.DOCUMENT_PROPERTIES.equals(entryDescriptorName)) {
      ObjectPropertyDescriptor objectPropertyDescriptor =
          objectApi.loadLatest(objectUri).getObject(ObjectPropertyDescriptor.class);
      List<ObjectPropertyDescriptor> propertyDescriptorsToReduce =
          List.of(objectPropertyDescriptor);
      mdmHelper.getDocumentTypesWithTags(objectPropertyDescriptor.getTags(), branchUri).forEach(
          e -> documentTypeApi.reduceDataSheetDefinition(e.getValue(),
              propertyDescriptorsToReduce, branchUri, e.getKey()));
    }
  }
}
```

**Végrehajtás:**
- Az invocation mechanizmus reflection-nel meghívja a metódust
- Paraméterek deszerializálása
- Metódus végrehajtása
- Eredmény mentése (opcionális)

---

## Navigálási Bonyolultság

### Probléma 1: 🔴 Indirekt Kapcsolat

#### Nincs Közvetlen Kód Hivatkozás

**Publisher oldal:**
```java
// MDMModificationApiImpl.java
invocationApi.publisher(
    MasterDataManagementApi.class,  //  Class reference
    MDMSubscriberApi.class,
    MasterDataManagementApi.STATE_CHANGED  //  String konstans
)
```

**Subscriber oldal:**
```java
// MdmEventHandlerApi.java
@EventSubscription(
    api = MasterDataManagementApi.API,  // String konstans ("org.smartbit4all.api.mdm...")
    event = MasterDataManagementApi.STATE_CHANGED  //  String konstans
)
void stateChanged(...) { }
```

####  IDE Eszközök Nem Működnek

| IDE Funkció | Működés | Következmény |
|-------------|---------|--------------|
| **Find Usages** |  Nem talál | Nem látod ki hívja/feliratkozik |
| **Go to Declaration** |  Nem működik | Nem ugorhatsz a subscriber-re |
| **Refactor → Rename** |  Elszakít | String konstans nem követi az osztályt |
| **Refactor → Move** |  Elszakít | Névtér változás nem updatel |
| **Call Hierarchy** |  Üres | Reflection miatt láthatatlan |

#### Manuális Keresés Szükséges

```
1. Találd meg a konstanst:
   MasterDataManagementApi.STATE_CHANGED = "stateChanged"

2. Grep search a workspace-ben:
   @EventSubscription.*api.*=.*MasterDataManagementApi
   
3. Szűrd az event paramétert:
   event.*=.*STATE_CHANGED

4. Ellenőrizd minden találatot manuálisan
```

---

### Probléma 2: 🔴 Többszörös Indirection

#### Hívási Lánc: 7 Réteg

```
1. Publisher API
   └─> MDMModificationApiImpl.fireModificationEvent()
   
2. InvocationApi
   └─> InvocationApiImpl.publisher()
   
3. EventPublisher
   └─> EventPublisher.publish()
   
4. InvocationRegisterApi
   └─> InvocationRegisterApiIml.getSubscriptions()
   
5. Storage Lookup
   └─> eventSubscriptionsByApis.get(publisherApi)
   
6. InvocationRequest Build
   └─> InvocationBuilder.build()
   
7. AsyncChannel
   └─> Reflection invoke → Subscriber metódus
```

####  Fejlesztői terhelés

Ahhoz hogy a developer megértse, hogy **ki fogja feldolgozni az eventet**:

1.  Megtalálja a publisher kódot
2.  Identifikálja a konstansokat (`API`, `EVENT`)
3.  **Keresnie kell** `@EventSubscription` annotációkat a teljes codebase-ben
4.  **Ellenőriznie kell** az `api` és `event` paramétereket manuálisan
5.  **Trace-elnie kell** a channel konfigurációt
6.  **Runtime-ban reflectionnel** hívódik meg → nincs statikus kapcsolat

---

### Probléma 3: 🔴 Runtime Registry

#### In-Memory Map

**Fájl:** `InvocationRegisterApiIml.java`

```java
private Map<String, List<EventSubscriptionData>> eventSubscriptionsByApis = new HashMap<>();
```

#### Nem Látható Statikus Elemzéssel

- Registry **runtime-ban** töltődik fel
- `@ApplicationStartedEvent` listener építi fel
- Reflection scan az összes `@EventSubscription` annotációra
- **Debuggolás nélkül** nehéz felfedezni a kapcsolatokat

#### Debug Stratégia

```java
// Breakpoint ide:
@Override
public List<EventSubscriptionData> getSubscriptions(String interfaceName) {
  return eventSubscriptionsByApis.getOrDefault(interfaceName, Collections.emptyList());
                                  // Inspect ezen a Map-en!
}
```

**Mit lát:**
```
eventSubscriptionsByApis = {
  "org.smartbit4all.api.mdm.MasterDataManagementApi" -> [
    EventSubscriptionData {
      api: "org.smartbit4all.api.mdm.MasterDataManagementApi",
      event: "stateChanged",
      subscribedApi: "org.smartbit4all.documentdossier.api.mdm.MdmEventHandlerApi",
      subscribedMethod: "stateChanged",
      channel: "documentTypeSubscriptionAsyncChannel"
    },
    ...
  ]
}
```

---

### Probléma 4: 🔴 String-Based Event Naming

#### Event Konstansok

**Publisher API:**
```java
public interface MasterDataManagementApi {
  static final String API = "org.smartbit4all.api.mdm.MasterDataManagementApi";
  static final String STATE_CHANGED = "stateChanged";
}
```

#### Kockázatok

| Probléma | Példa | Következmény |
|----------|-------|--------------|
| **Typo** | `"stateChangd"` vs `"stateChanged"` | Runtime-ban nem talál subscriber |
| **Refactoring** | Metódus rename: `stateChanged()` → `onStateChange()` | Elszakad a kapcsolat |
| **IDE Support** | Autocomplete nem működik mindenhol | Fejlesztői produktivitás csökken |
| **Compile-Time Check** | Nincs | Hibák csak runtime-ban derülnek ki |

#### Lehetséges Javítás (Ötlet)

```java
// Type-safe event definition
public interface MDMEvents {
  EventDef<StateChangeParams> STATE_CHANGED = new EventDef<>("stateChanged");
}

@EventSubscription(event = MDMEvents.STATE_CHANGED)
void handleStateChange(StateChangeParams params);
```

---

### Probléma 5: 🔴 Scatter-Gather Pattern

#### Egy Event → Több Subscriber

```java
// Publisher: 1 event kiadás
invocationApi.publisher(..., STATE_CHANGED).publish(...);

// Subscriber 1
@EventSubscription(api = MasterDataManagementApi.API, event = STATE_CHANGED)
void subscriberA() { ... }

// Subscriber 2
@EventSubscription(api = MasterDataManagementApi.API, event = STATE_CHANGED)
void subscriberB() { ... }

// Subscriber 3 (másik modul)
@EventSubscription(api = MasterDataManagementApi.API, event = STATE_CHANGED)
void subscriberC() { ... }
```

#### Nem Látható Egyből

- **Hány handler** reagál az eventre?
- **Milyen sorrendben** futnak?
- **Konkurensen** futnak vagy szekvenciálisan?
- **Melyik channel-en** keresztül?

#### Discovery Folyamat (AI a legjobb barát ebben ^^)

```bash
# 1. Keresd meg az összes subscriber-t
grep -r "@EventSubscription" --include="*.java" | grep "STATE_CHANGED"

# 2. Ellenőrizd a channel konfigurációt
# Különböző channel = párhuzamos végrehajtás
# Ugyanaz a channel = sorban végrehajtás

# 3. Trace-eld a függőségeket
# Subscriber A meghívja Subscriber B-t?
# Race condition lehetséges?
```

---

### Probléma 6: 🔴 Cross-Module Dependencies

#### Module Határok Átlépése

```
platform/api
  ├─ MasterDataManagementApi (Publisher)
  └─ InvocationApi

mod-documentdossier/api
  └─ MdmEventHandlerApi (Subscriber)
```

#### Dependency Graph Rejtett

- Publisher **nem függ** a subscriber-től (compile time)
- Subscriber **függ** a publisher-től (API konstansok)
- Runtime-ban: **kétirányú kommunikáció**
- Module dependency graph **nem tükrözi** a valós függőségeket

#### Valós vs Látható Függőségek

```
Compile-time (látható):
  mod-documentdossier -> platform

Runtime (rejtett):
  platform -> mod-documentdossier (event callback)
```

---

## Navigációs Stratégiák

### Stratégia 1: Konstans Alapú Keresés

#### Lépések

1. **Találd meg a Publisher API konstanst:**
   ```java
   public interface MasterDataManagementApi {
     static final String API = "org.smartbit4all.api.mdm.MasterDataManagementApi";
     static final String STATE_CHANGED = "stateChanged";
   }
   ```

2. **Grep search az annotációkra:**
   ```bash
   grep -r "@EventSubscription" --include="*.java"
   ```

3. **Szűrés API-ra:**
   ```bash
   grep -r "@EventSubscription" --include="*.java" | grep "MasterDataManagementApi.API"
   ```

4. **Szűrés Event-re:**
   ```bash
   grep -r "STATE_CHANGED" --include="*.java" | grep "@EventSubscription" -A 2
   ```

---

### Stratégia 2: Runtime Debug

#### Breakpoint Helyek

1. **EventPublisher.publish():**
   ```java
   List<EventSubscriptionData> eventSubscriptions =
       invocationRegisterApi.getSubscriptions(api.getApiData().getInterfaceName());
       // Breakpoint: nézd meg a listát
   ```

2. **InvocationRegisterApiIml.getSubscriptions():**
   ```java
   return eventSubscriptionsByApis.getOrDefault(interfaceName, Collections.emptyList());
   // Inspect: eventSubscriptionsByApis Map
   ```

3. **Subscriber Metódus:**
   ```java
   @Override
   public void stateChanged(...) {
     // Breakpoint: megnézed melyik event miatt hívódott meg
   }
   ```

---

### Stratégia 3: Storage Inspekció

#### API Registry Data

**Storage lokáció:**
```
scheme: apiregistration
path: /ApiRegistryData
```

**Bean struktúra:**
```java
ApiRegistryData
  └─ apis: List<ApiData>
       └─ eventSubscriptions: List<EventSubscriptionData>
            ├─ api: "org.smartbit4all.api.mdm.MasterDataManagementApi"
            ├─ event: "stateChanged"
            ├─ subscribedApi: "org.smartbit4all.documentdossier.api.mdm.MdmEventHandlerApi"
            ├─ subscribedMethod: "stateChanged"
            └─ channel: "documentTypeSubscriptionAsyncChannel"
```

**Lekérdezés:**
```java
ObjectNode node = objectApi.load(InvocationRegisterApiIml.REGISTER_URI);
ApiRegistryData registry = node.getObject(ApiRegistryData.class);
List<EventSubscriptionData> allSubscriptions = registry.getApis().stream()
    .flatMap(api -> api.getEventSubscriptions().stream())
    .collect(toList());
```

---

### Stratégia 5: Custom IDE Plugin (HEHE)

#### Hiányzó Funkciók

- **"Find Event Subscribers"** command
- **"Go to Event Handler"** navigation
- **Event Subscription Graph** visualization
- **Refactoring support** event nevekre
- **Code completion** event konstansokhoz

---

## Kód Példák

### Példa 1: Teljes Flow - Document Dossier

#### 1. Subscriber Definíció

**Fájl:** `mod-documentdossier/api/src/main/java/org/smartbit4all/documentdossier/api/mdm/MdmEventHandlerApi.java`

```java
package org.smartbit4all.documentdossier.api.mdm;

import java.net.URI;
import org.smartbit4all.api.invocation.EventSubscription;
import org.smartbit4all.api.mdm.MDMEntryApi;
import org.smartbit4all.api.mdm.MDMSubscriberApi;
import org.smartbit4all.api.mdm.MasterDataManagementApi;
import org.smartbit4all.documentdossier.api.config.DocumentDossierApiConfig;

public interface MdmEventHandlerApi extends MDMSubscriberApi {

  @Override
  @EventSubscription(
      api = MasterDataManagementApi.API,
      event = MasterDataManagementApi.STATE_CHANGED,
      channel = DocumentDossierApiConfig.DOCUMENT_TYPE_SUBSCRIPTION_ASYNC_CHANNEL)
  void stateChanged(String event, String scope, URI definition, URI state, URI prevState,
      URI branchUri);

  @Override
  @EventSubscription(
      api = MasterDataManagementApi.API,
      event = MDMEntryApi.INACTIVATED,
      channel = DocumentDossierApiConfig.DOCUMENT_TYPE_SUBSCRIPTION_ASYNC_CHANNEL)
  void entryInactivated(URI definition, String entryDescriptorName, URI objectUri,
      URI branchUri);

  @Override
  @EventSubscription(
      api = MasterDataManagementApi.API,
      event = MDMEntryApi.REMOVED,
      channel = DocumentDossierApiConfig.DOCUMENT_TYPE_SUBSCRIPTION_ASYNC_CHANNEL)
  void entryRemoved(URI definition, String entryDescriptorName, URI objectUri,
      URI branchUri);
}
```

---

#### 2. Subscriber Implementáció

**Fájl:** `mod-documentdossier/api/src/main/java/org/smartbit4all/documentdossier/api/mdm/MdmEventHandlerApiImpl.java`

```java
package org.smartbit4all.documentdossier.api.mdm;

import java.net.URI;
import java.util.List;
import org.smartbit4all.api.object.bean.ObjectPropertyDescriptor;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.documentdossier.api.DocumentDossierConstants;
import org.smartbit4all.documentdossier.api.DocumentTypeApi;
import org.springframework.beans.factory.annotation.Autowired;

public class MdmEventHandlerApiImpl implements MdmEventHandlerApi {

  @Autowired
  protected ObjectApi objectApi;
  @Autowired
  protected DocumentTypeApi documentTypeApi;
  @Autowired
  private MDMHelperApi mdmHelper;

  @Override
  public void stateChanged(String event, String scope, URI definition, URI state, URI prevState,
      URI branchUri) {
    // State change handling logic
    // Lehet hogy nem csinál semmit jelenleg
  }

  @Override
  public void entryInactivated(URI definition, String entryDescriptorName, URI objectUri,
      URI branchUri) {
    handlePropertiesRemoved(entryDescriptorName, objectUri, branchUri);
  }

  @Override
  public void entryRemoved(URI definition, String entryDescriptorName, URI objectUri,
      URI branchUri) {
    handlePropertiesRemoved(entryDescriptorName, objectUri, branchUri);
  }

  private void handlePropertiesRemoved(String entryDescriptorName, URI objectUri,
      URI branchUri) {
    if (DocumentDossierConstants.DOCUMENT_PROPERTIES.equals(entryDescriptorName)) {
      ObjectPropertyDescriptor objectPropertyDescriptor =
          objectApi.loadLatest(objectUri).getObject(ObjectPropertyDescriptor.class);
      List<ObjectPropertyDescriptor> propertyDescriptorsToReduce =
          List.of(objectPropertyDescriptor);
      mdmHelper.getDocumentTypesWithTags(objectPropertyDescriptor.getTags(), branchUri).forEach(
          e -> documentTypeApi.reduceDataSheetDefinition(e.getValue(),
              propertyDescriptorsToReduce, branchUri, e.getKey()));
    }
  }
}
```

---

#### 3. Config: API Provider Regisztráció

**Szükséges:** Spring Bean definíció `ProviderApiInvocationHandler`-rel

```java
@Configuration
public class DocumentDossierApiConfig {

  public static final String DOCUMENT_TYPE_SUBSCRIPTION_ASYNC_CHANNEL = 
      "documentTypeSubscriptionAsyncChannel";

  @Bean
  public MdmEventHandlerApi mdmEventHandlerApi() {
    return new MdmEventHandlerApiImpl();
  }

  @Bean
  public ProviderApiInvocationHandler<MdmEventHandlerApi> mdmEventHandlerApiProvider(
      MdmEventHandlerApi api) {
    return ProviderApiInvocationHandler.providerOf(MdmEventHandlerApi.class, api);
  }
}
```

---

#### 4. Publisher Hívás

**Fájl:** `platform/api/src/main/java/org/smartbit4all/api/mdm/MDMModificationApiImpl.java`

```java
package org.smartbit4all.api.mdm;

import java.net.URI;
import org.smartbit4all.api.invocation.InvocationApi;
import org.springframework.beans.factory.annotation.Autowired;

public class MDMModificationApiImpl implements MDMModificationApi {

  @Autowired
  private InvocationApi invocationApi;

  void fireModificationEvent(String event, String scope, URI definition, URI state,
      URI prevState, URI branchUri) {
    invocationApi
        .publisher(
            MasterDataManagementApi.class,
            MDMSubscriberApi.class,
            MasterDataManagementApi.STATE_CHANGED)
        .publish(api -> api.stateChanged(event, scope, definition, state, prevState, branchUri));
  }
  
  public void someBusinessMethod() {
    // Business logic
    // ...
    
    // Event publikálás
    fireModificationEvent("UPDATE", "global", definitionUri, newState, oldState, branchUri);
  }
}
```

---

#### 5. Publisher API Definíció

**Fájl:** `platform/api/src/main/java/org/smartbit4all/api/mdm/MasterDataManagementApi.java`

```java
package org.smartbit4all.api.mdm;

public interface MasterDataManagementApi {
  
  // API konstans (FQN)
  static final String API = "org.smartbit4all.api.mdm.MasterDataManagementApi";
  
  // Event konstansok
  static final String STATE_CHANGED = "stateChanged";
  
  // Business metódusok
  // ...
}
```

---

#### 6. Subscriber Base Interface

**Fájl:** `platform/api/src/main/java/org/smartbit4all/api/mdm/MDMSubscriberApi.java`

```java
package org.smartbit4all.api.mdm;

import java.net.URI;

/**
 * Base interface for MDM event subscribers.
 * Implementors should annotate methods with @EventSubscription.
 */
public interface MDMSubscriberApi {
  
  void stateChanged(String event, String scope, URI definition, URI state, URI prevState,
      URI branchUri);
  
  // További event handler method signatúrák...
}
```

---

### Példa 2: Test Environment Setup

**Fájl:** `platform/api/src/testFixtures/java/org/smartbit4all/api/invocation/TestEventPublisherApi.java`

```java
package org.smartbit4all.api.invocation;

public interface TestEventPublisherApi {

  static final String API = "org.smartbit4all.api.invocation.TestEventPublisherApi";
  static final String EVENT1 = "event1";
  static final String EVENT2 = "event2";

  String fireSomeEvent(String param);
  void event(String param);
}
```

**Fájl:** `platform/api/src/testFixtures/java/org/smartbit4all/api/invocation/TestEventPublisherApiImpl.java`

```java
package org.smartbit4all.api.invocation;

import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;

public class TestEventPublisherApiImpl implements TestEventPublisherApi {

  @Autowired
  private InvocationApi invocationApi;

  private Random rnd = new Random();

  @Override
  public void event(String param) {}

  @Override
  public String fireSomeEvent(String param) {
    String event = rnd.nextBoolean() ? EVENT1 : EVENT2;
    invocationApi.publisher(TestEventPublisherApi.class, TestEventPublisherApi.class, event)
        .publish(api -> api.event(param));
    return event;
  }
}
```

**Fájl:** `platform/api/src/testFixtures/java/org/smartbit4all/api/invocation/TestEventSubscriberApi.java`

```java
package org.smartbit4all.api.invocation;

public interface TestEventSubscriberApi {

  @EventSubscription(api = TestEventPublisherApi.API, event = TestEventPublisherApi.EVENT1,
      channel = InvocationTestConfig.GLOBAL_ASYNC_CHANNEL)
  void eventConsumer1(String param);

  @EventSubscription(api = TestEventPublisherApi.API, event = TestEventPublisherApi.EVENT2,
      channel = InvocationTestConfig.SECOND_ASYNC_CHANNEL)
  void eventConsumer2(String param);
}
```

---