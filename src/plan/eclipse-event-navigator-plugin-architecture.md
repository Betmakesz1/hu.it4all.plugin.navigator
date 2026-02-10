# Eclipse Event Navigator Plugin - Architektúrális Terv

## Tartalomjegyzék
1. [Projekt Áttekintés](#projekt-áttekintés)
2. [Célkitűzések](#célkitűzések)
3. [Technológiai Stack](#technológiai-stack)
4. [Rendszer Architektúra](#rendszer-architektúra)
5. [Komponensek Részletesen](#komponensek-részletesen)
6. [Funkciók Specifikációja](#funkciók-specifikációja)
7. [UI/UX Terv](#uiux-terv)
8. [Implementációs Fázisok](#implementációs-fázisok)
9. [Technikai Kihívások](#technikai-kihívások)
10. [Tesztelési Stratégia](#tesztelési-stratégia)

---

## Projekt Áttekintés
A **event-publisher-listener-plugin-machanizmus.md**-ben megfogalmazott probléma alapján

### Megoldás

Egy **Eclipse plugin**, amely:
-  Felismeri a `@EventSubscription` és event publisher pattern-eket
-  Visualizálja a publisher-subscriber kapcsolatokat
-  Navigációt biztosít a kapcsolódó kódok között
-  Támogatja a refactoring műveleteket
-  Validálja az event definíciókat compile-time-ban
-  Nyomon követi a változásokat és frissíti az indexet

---

## Célkitűzések

### Fő Célok

| # | Cél | Prioritás |
|---|-----|-----------|
| 1 | Navigation: Publisher → Subscribers | **P0** | 
| 2 | Navigation: Subscriber → Publisher | **P0** | 
| 3 | Gutter Icons (event jelzések) | **P0** |
| 4 | Quick Info (hover tooltip) | **P1** |
| 5 | Event Validation | **P1** |
| 6 | Find All Event Handlers | **P1** |

### További Célok (A jövőben)

| # | Cél | Prioritás |
|---|-----|-----------|
| 7 | Refactoring Support | **P2** | 
| 8 | Event Subscription Graph View | **P2** |
| 9 | Event Flow Debugger | **P3** |
| 10 | Code Generation (templates) | **P3** |

---

## Technológiai Stack

### Alap Technológiák

| Technológia | Verzió | Szerepkör |
|-------------|--------|-----------|
| **Eclipse Platform** | 4.x+ | IDE framework |
| **Eclipse JDT** | Latest | Java AST parsing, compilation units |
| **OSGi** | - | Plugin lifecycle management |
| **SWT/JFace** | - | UI components |
| **EMF** | - | Model management (optional) |
| **Xtext** (optional) | - | DSL parsing (ha kell) |

### Eclipse Extension Points

| Extension Point | Használat |
|----------------|-----------|
| `org.eclipse.jdt.ui.javaElementFilters` | Java element szűrés |
| `org.eclipse.jdt.core.compilationParticipant` | Build-time validation |
| `org.eclipse.jdt.ui.quickFixProcessors` | Quick fix javaslatok |
| `org.eclipse.ui.editors.markerAnnotationSpecification` | Gutter icons |
| `org.eclipse.ui.views` | Custom view (Event Graph) |
| `org.eclipse.ltk.core.refactoring.refactoringContributions` | Refactoring support |
| `org.eclipse.jdt.ui.javaCompletionProposalComputer` | Code completion |

### Build System

```xml
<!-- Maven Tycho for Eclipse plugin build -->
<build>
  <plugins>
    <plugin>
      <groupId>org.eclipse.tycho</groupId>
      <artifactId>tycho-maven-plugin</artifactId>
      <version>2.7.5</version>
    </plugin>
  </plugins>
</build>
```

---

## Rendszer Architektúra

### Package Structure

```
org.smartbit4all.eclipse.event/
├── core/
│   ├── EventIndexManager.java          # Központi index kezelő
│   ├── EventDefinition.java            # Event model
│   ├── EventPublisherInfo.java         # Publisher metadata
│   ├── EventSubscriberInfo.java        # Subscriber metadata
│   └── EventRegistry.java              # Runtime registry
├── ast/
│   ├── EventAnnotationScanner.java     # @EventSubscription scanner
│   ├── EventPublisherScanner.java      # invocationApi.publisher() scanner
│   ├── ASTVisitors.java                # AST visitor implementations
│   └── JavaElementResolver.java        # Java element resolution
├── navigation/
│   ├── EventHyperlinkDetector.java     # Ctrl+Click navigation
│   ├── EventNavigationHandler.java     # Go To Event Handler
│   └── FindEventHandlersAction.java    # Find All Handlers
├── validation/
│   ├── EventValidationParticipant.java # Build-time validation
│   ├── EventMarkerManager.java         # Error/Warning markers
│   └── EventQuickFixProcessor.java     # Quick fix proposals
├── ui/
│   ├── gutter/
│   │   ├── EventGutterIconProvider.java    # Publisher/Subscriber icons
│   │   └── EventGutterActionHandler.java   # Icon click actions
│   ├── hover/
│   │   ├── EventHoverProvider.java         # Tooltip info
│   │   └── EventHoverContent.java          # Hover content generator
│   ├── views/
│   │   ├── EventGraphView.java             # Subscription graph view
│   │   ├── EventGraphModel.java            # Graph model
│   │   └── EventGraphRenderer.java         # Graph visualization
│   └── wizards/
│       ├── NewEventWizard.java             # Event creation wizard
│       └── NewSubscriberWizard.java        # Subscriber creation wizard
├── refactoring/
│   ├── RenameEventParticipant.java         # Event rename support
│   ├── MoveApiParticipant.java             # API move support
│   └── DeleteEventParticipant.java         # Event delete warning
└── preferences/
    ├── EventPluginPreferences.java         # Plugin preferences
    └── EventPluginPreferencePage.java      # Preferences UI
```

---

## Komponensek Részletesen

### 1. EventIndexManager

**Felelősség:** Központi index kezelés az event kapcsolatokról

```java
public class EventIndexManager {
  
  // Singleton instance
  private static EventIndexManager instance;
  
  // Index adatstruktúrák
  private Map<String, EventDefinition> eventsByKey;           // "api.event" -> EventDefinition
  private Map<IMethod, EventPublisherInfo> publishers;        // Method -> Publisher info
  private Map<IMethod, EventSubscriberInfo> subscribers;      // Method -> Subscriber info
  
  // Index frissítés
  public void indexWorkspace() { }
  public void indexProject(IProject project) { }
  public void indexCompilationUnit(ICompilationUnit unit) { }
  
  // Query API
  public List<EventSubscriberInfo> findSubscribers(EventDefinition event) { }
  public EventPublisherInfo findPublisher(EventDefinition event) { }
  public EventDefinition findEvent(String api, String event) { }
  
  // Listener registry
  public void addIndexChangeListener(IEventIndexChangeListener listener) { }
  public void removeIndexChangeListener(IEventIndexChangeListener listener) { }
}
```

**Index Frissítési Stratégia:**

**Perzisztencia:**

```java
// Index mentése workspace metadata-ba
private void saveIndex() {
  IPath statePath = EventPlugin.getDefault().getStateLocation();
  File indexFile = statePath.append("event-index.bin").toFile();
  // Serialize index
}

private void loadIndex() {
  // Deserialize index
  // Ha korrupt vagy régi verzió -> rebuild
}
```

---

### 2. EventAnnotationScanner

**Felelősség:** `@EventSubscription` annotációk felismerése

```java
public class EventAnnotationScanner {
  
  /**
   * Scans a compilation unit for @EventSubscription annotations
   */
  public List<EventSubscriberInfo> scanForSubscribers(ICompilationUnit unit) {
    CompilationUnit ast = parse(unit);
    EventSubscriptionVisitor visitor = new EventSubscriptionVisitor();
    ast.accept(visitor);
    return visitor.getSubscribers();
  }
  
  private class EventSubscriptionVisitor extends ASTVisitor {
    private List<EventSubscriberInfo> subscribers = new ArrayList<>();
    
    @Override
    public boolean visit(MethodDeclaration node) {
      // Keresés: @EventSubscription annotáció
      for (Object modifier : node.modifiers()) {
        if (modifier instanceof Annotation) {
          Annotation annotation = (Annotation) modifier;
          if (isEventSubscriptionAnnotation(annotation)) {
            EventSubscriberInfo info = extractSubscriberInfo(node, annotation);
            subscribers.add(info);
          }
        }
      }
      return super.visit(node);
    }
    
    private boolean isEventSubscriptionAnnotation(Annotation annotation) {
      String fqn = annotation.resolveTypeBinding().getQualifiedName();
      return "org.smartbit4all.api.invocation.EventSubscription".equals(fqn);
    }
    
    private EventSubscriberInfo extractSubscriberInfo(MethodDeclaration method, 
                                                       Annotation annotation) {
      // Annotáció paraméterek kinyerése: api, event, channel
      String api = getAnnotationValue(annotation, "api");
      String event = getAnnotationValue(annotation, "event");
      String channel = getAnnotationValue(annotation, "channel");
      
      // Method info
      IMethod methodBinding = (IMethod) method.resolveBinding().getJavaElement();
      IType declaringType = methodBinding.getDeclaringType();
      
      return new EventSubscriberInfo(
          api,
          event,
          channel,
          declaringType.getFullyQualifiedName(),
          methodBinding.getElementName(),
          methodBinding
      );
    }
  }
}
```

**Felismert Pattern:**

```java
@EventSubscription(
    api = MasterDataManagementApi.API,          // ← String konstans resolválás
    event = MasterDataManagementApi.STATE_CHANGED,  // ← String konstans resolválás
    channel = "asyncChannel"
)
void stateChanged(String event, ...) { }
```

**Kihívás:** String konstansok resolválása

```java
private String resolveConstant(Expression expr) {
  if (expr instanceof QualifiedName) {
    QualifiedName qName = (QualifiedName) expr;
    IBinding binding = qName.resolveBinding();
    if (binding instanceof IVariableBinding) {
      IVariableBinding varBinding = (IVariableBinding) binding;
      if (varBinding.isField() && Modifier.isFinal(varBinding.getModifiers())) {
        // Field konstans értékének lekérdezése
        return (String) varBinding.getConstantValue();
      }
    }
  }
  return null;
}
```

---

### 3. EventPublisherScanner

**Felelősség:** `invocationApi.publisher()` hívások felismerése

```java
public class EventPublisherScanner {
  
  public List<EventPublisherInfo> scanForPublishers(ICompilationUnit unit) {
    CompilationUnit ast = parse(unit);
    EventPublisherVisitor visitor = new EventPublisherVisitor();
    ast.accept(visitor);
    return visitor.getPublishers();
  }
  
  private class EventPublisherVisitor extends ASTVisitor {
    private List<EventPublisherInfo> publishers = new ArrayList<>();
    
    @Override
    public boolean visit(MethodInvocation node) {
      // Pattern: invocationApi.publisher(PublisherApi.class, SubscriberApi.class, EVENT)
      if (isPublisherInvocation(node)) {
        EventPublisherInfo info = extractPublisherInfo(node);
        publishers.add(info);
      }
      return super.visit(node);
    }
    
    private boolean isPublisherInvocation(MethodInvocation node) {
      String methodName = node.getName().getIdentifier();
      if (!"publisher".equals(methodName)) {
        return false;
      }
      
      Expression expr = node.getExpression();
      if (expr == null) {
        return false;
      }
      
      ITypeBinding typeBinding = expr.resolveTypeBinding();
      if (typeBinding == null) {
        return false;
      }
      
      String typeName = typeBinding.getQualifiedName();
      return "org.smartbit4all.api.invocation.InvocationApi".equals(typeName);
    }
    
    private EventPublisherInfo extractPublisherInfo(MethodInvocation node) {
      List<?> args = node.arguments();
      
      // Arg 0: Publisher API class
      TypeLiteral publisherApiClass = (TypeLiteral) args.get(0);
      String publisherApi = publisherApiClass.getType().resolveBinding().getQualifiedName();
      
      // Arg 1: Subscriber API class  
      TypeLiteral subscriberApiClass = (TypeLiteral) args.get(1);
      String subscriberApi = subscriberApiClass.getType().resolveBinding().getQualifiedName();
      
      // Arg 2: Event name (constant)
      Expression eventExpr = (Expression) args.get(2);
      String event = resolveConstant(eventExpr);
      
      // Encompassing method
      MethodDeclaration enclosingMethod = findEnclosingMethod(node);
      IMethod methodElement = (IMethod) enclosingMethod.resolveBinding().getJavaElement();
      
      return new EventPublisherInfo(
          publisherApi,
          event,
          methodElement.getDeclaringType().getFullyQualifiedName(),
          methodElement.getElementName(),
          methodElement,
          node
      );
    }
  }
}
```

**Felismert Pattern:**

```java
invocationApi
    .publisher(MasterDataManagementApi.class, MDMSubscriberApi.class, 
               MasterDataManagementApi.STATE_CHANGED)
    .publish(api -> api.stateChanged(...));
```

---

### 4. EventHyperlinkDetector

**Felelősség:** Ctrl+Click navigáció

```java
public class EventHyperlinkDetector implements IHyperlinkDetector {
  
  @Override
  public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region,
                                       boolean canShowMultiple) {
    IDocument document = textViewer.getDocument();
    ICompilationUnit unit = getCompilationUnit(textViewer);
    
    // Parse AST a kurzor pozíciónál
    ASTNode node = NodeFinder.perform(parse(unit), region.getOffset(), region.getLength());
    
    // 1. Kattintás @EventSubscription annotáción?
    if (isEventSubscriptionAnnotation(node)) {
      return createSubscriberToPublisherHyperlinks(node);
    }
    
    // 2. Kattintás publisher() híváson?
    if (isPublisherInvocation(node)) {
      return createPublisherToSubscribersHyperlinks(node);
    }
    
    // 3. Kattintás event konstanson?
    if (isEventConstant(node)) {
      return createEventUsagesHyperlinks(node);
    }
    
    return null;
  }
  
  private IHyperlink[] createPublisherToSubscribersHyperlinks(ASTNode node) {
    EventPublisherInfo publisherInfo = extractPublisherInfo(node);
    EventDefinition event = new EventDefinition(publisherInfo.getApi(), 
                                                 publisherInfo.getEvent());
    
    List<EventSubscriberInfo> subscribers = 
        EventIndexManager.getInstance().findSubscribers(event);
    
    if (subscribers.isEmpty()) {
      return null;
    }
    
    if (subscribers.size() == 1) {
      // Single subscriber: direct jump
      return new IHyperlink[] {
        new JavaElementHyperlink(subscribers.get(0).getMethod())
      };
    } else {
      // Multiple subscribers: show list
      return new IHyperlink[] {
        new EventSubscriberListHyperlink(event, subscribers)
      };
    }
  }
}
```

**Navigációs Lehetőségek:**

| Kattintás Helye | Navigációs Cél | Akció |
|-----------------|----------------|-------|
| `@EventSubscription(api=...)` | Publisher API class | Open publisher |
| `@EventSubscription(event=...)` | Publisher method | Open publishing code |
| `invocationApi.publisher()` | Subscriber methods | List of handlers |
| Event constant definition | All usages | Find all publishers/subscribers |

---

### 5. EventGutterIconProvider

**Felelősség:** Margin icons (gutter) a publisher/subscriber mellett

```java
public class EventGutterIconProvider implements IAnnotationModelExtension {
  
  private static final String PUBLISHER_ICON = "icons/event_publisher.png";
  private static final String SUBSCRIBER_ICON = "icons/event_subscriber.png";
  
  public void updateAnnotations(ICompilationUnit unit) {
    IAnnotationModel annotationModel = getAnnotationModel(unit);
    
    // Clear old annotations
    removeOldEventAnnotations(annotationModel);
    
    // Scan for publishers
    List<EventPublisherInfo> publishers = EventPublisherScanner.scan(unit);
    for (EventPublisherInfo pub : publishers) {
      Position pos = getMethodPosition(pub.getMethod());
      EventPublisherAnnotation annotation = new EventPublisherAnnotation(pub);
      annotationModel.addAnnotation(annotation, pos);
    }
    
    // Scan for subscribers
    List<EventSubscriberInfo> subscribers = EventAnnotationScanner.scan(unit);
    for (EventSubscriberInfo sub : subscribers) {
      Position pos = getMethodPosition(sub.getMethod());
      EventSubscriberAnnotation annotation = new EventSubscriberAnnotation(sub);
      annotationModel.addAnnotation(annotation, pos);
    }
  }
  
  public class EventPublisherAnnotation extends Annotation {
    private EventPublisherInfo info;
    
    @Override
    public String getText() {
      int count = EventIndexManager.getInstance()
          .findSubscribers(info.getEvent()).size();
      return String.format("Publishes event: %s (%d subscribers)", 
                           info.getEvent(), count);
    }
  }
}
```

**Icon Példák:**

```
Publisher icon (kék): Event kiadása
Subscriber icon (zöld): Event fogadása
Warning icon (sárga): Nincs subscriber / publisher
```

**Hover viselkedés:**

```
┌─────────────────────────────────────┐
│ Event Publisher                     │
├─────────────────────────────────────┤
│ Event: STATE_CHANGED                │
│ API: MasterDataManagementApi        │
│ Subscribers: 3                      │
│                                     │
│ • MdmEventHandlerApi.stateChanged() │
│ • LoggingSubscriber.onStateChange() │
│ • AuditSubscriber.logChange()       │
│                                     │
│ [Navigálás a Subscriberekhez...]    │
└─────────────────────────────────────┘
```

---

### 6. EventValidationParticipant

**Felelősség:** Build-time validáció

```java
public class EventValidationParticipant extends CompilationParticipant {
  
  @Override
  public void buildStarting(BuildContext[] files, boolean isBatch) {
    for (BuildContext context : files) {
      IFile file = context.getFile();
      if (!isJavaFile(file)) continue;
      
      ICompilationUnit unit = getCompilationUnit(file);
      validateEventUsages(unit, context);
    }
  }
  
  private void validateEventUsages(ICompilationUnit unit, BuildContext context) {
    // 1. Validate subscribers
    List<EventSubscriberInfo> subscribers = EventAnnotationScanner.scan(unit);
    for (EventSubscriberInfo sub : subscribers) {
      validateSubscriber(sub, context);
    }
    
    // 2. Validate publishers
    List<EventPublisherInfo> publishers = EventPublisherScanner.scan(unit);
    for (EventPublisherInfo pub : publishers) {
      validatePublisher(pub, context);
    }
  }
  
  private void validateSubscriber(EventSubscriberInfo sub, BuildContext context) {
    EventDefinition event = new EventDefinition(sub.getApi(), sub.getEvent());
    
    // Check 1: Publisher exists?
    EventPublisherInfo publisher = EventIndexManager.getInstance().findPublisher(event);
    if (publisher == null) {
      createWarningMarker(context, sub.getMethod(), 
          String.format("No publisher found for event: %s.%s", sub.getApi(), sub.getEvent()));
    }
    
    // Check 2: Method signature matches?
    if (publisher != null) {
      if (!methodSignaturesMatch(sub.getMethod(), publisher.getPublishMethod())) {
        createErrorMarker(context, sub.getMethod(),
            "Subscriber method signature does not match publisher");
      }
    }
    
    // Check 3: Channel exists?
    if (!channelConfigurationExists(sub.getChannel())) {
      createWarningMarker(context, sub.getMethod(),
          String.format("Async channel not configured: %s", sub.getChannel()));
    }
  }
  
  private void validatePublisher(EventPublisherInfo pub, BuildContext context) {
    EventDefinition event = new EventDefinition(pub.getApi(), pub.getEvent());
    
    // Check: Has subscribers?
    List<EventSubscriberInfo> subscribers = 
        EventIndexManager.getInstance().findSubscribers(event);
    
    if (subscribers.isEmpty()) {
      createWarningMarker(context, pub.getMethod(),
          String.format("Event %s has no subscribers", event));
    }
  }
}
```

**Validation Rules:**

| Szabály | Severity | Leírás |
|---------|----------|--------|
| No publisher for subscriber | Warning | Subscriber declared but no publisher exists |
| No subscribers for publisher | Warning | Publisher fires event but no handlers |
| Method signature mismatch | Error | Subscriber params don't match publisher |
| Channel not configured | Warning | Async channel referenced but not defined |
| Duplicate event definition | Error | Same event name used for different signatures |
| Cyclic event dependency | Warning | Event A triggers B which triggers A |

---

### 7. EventGraphView

**Felelősség:** Visual event subscription graph

```java
public class EventGraphView extends ViewPart {
  
  private GraphViewer graphViewer;
  private EventGraphModel model;
  
  @Override
  public void createPartControl(Composite parent) {
    graphViewer = new GraphViewer(parent, SWT.NONE);
    graphViewer.setContentProvider(new EventGraphContentProvider());
    graphViewer.setLabelProvider(new EventGraphLabelProvider());
    
    // Layout algorithm
    graphViewer.setLayoutAlgorithm(new TreeLayoutAlgorithm());
    
    // Input: all events in workspace
    refreshModel();
    graphViewer.setInput(model);
    
    // Interaction
    addSelectionListener();
    addDoubleClickListener();
  }
  
  private void addDoubleClickListener() {
    graphViewer.addDoubleClickListener(event -> {
      IStructuredSelection selection = (IStructuredSelection) event.getSelection();
      Object element = selection.getFirstElement();
      
      if (element instanceof EventPublisherInfo) {
        openInEditor((EventPublisherInfo) element);
      } else if (element instanceof EventSubscriberInfo) {
        openInEditor((EventSubscriberInfo) element);
      }
    });
  }
  
  public void refresh() {
    refreshModel();
    graphViewer.setInput(model);
  }
  
  private void refreshModel() {
    model = new EventGraphModel();
    EventIndexManager index = EventIndexManager.getInstance();
    
    // Build graph
    for (EventDefinition event : index.getAllEvents()) {
      EventPublisherInfo publisher = index.findPublisher(event);
      List<EventSubscriberInfo> subscribers = index.findSubscribers(event);
      
      if (publisher != null) {
        model.addNode(publisher);
        for (EventSubscriberInfo sub : subscribers) {
          model.addNode(sub);
          model.addEdge(publisher, sub);
        }
      }
    }
  }
}
```

**Vizualizációs példa:**

```
┌─────────────────────────────────────────────────────────┐
│ Event Subscription Graph                [Frissítés]     │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌────────────────────────────┐                         │
│  │ MasterDataManagementApi    │                         │
│  │ .fireModificationEvent()   │                         │
│  │                            │                         │
│  │ [PUB] STATE_CHANGED        │                         │
│  └──────────┬─────────────────┘                         │
│             │                                           │
│     ┌───────┼────────────────────────┐                  │
│     │       │                        │                  │
│     ▼       ▼                        ▼                  │
│  ┌───────┐ ┌───────┐             ┌───────┐              │
│  │[SUB]  │ │[SUB]  │             │[SUB]  │              │
│  │Mdm    │ │Log    │             │Audit  │              │
│  │Handler│ │Subs.  │             │Subs.  │              │
│  └───────┘ └───────┘             └───────┘              │
│                                                         │
│  Jelmagyarázat:                                         │
│  [PUB] Publisher  [SUB] Subscriber  [!] Nincs subscriber│
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**Gráf funkciók:**

- **Nagyítás:** Egér görgő
- **Mozgatás:** Húzás egérrel
- **Szűrés:** API, event név, modul szerint
- **Kiemelés:** Kapcsolódó csomópontok kiválasztáskor
- **Export:** PNG, SVG
- **Elrendezés:** Fa, Erő-irányított, Hierarchikus

---

## Funkciók Specifikációja

### F1: Navigáció - Publisher-től Subscriberekhez

**Felhasználói történet:**
> Fejlesztőként amikor Ctrl+Click-kel egy event publisher hívásra kattintok, ugorni szeretnék azokhoz a subscriber metódusokhoz, amelyek kezelik azt az eventet.

**Implementáció:**

1. A felhasználó a kurzort az `invocationApi.publisher().publish()` sorra helyezi
2. Megnyomja a `Ctrl`-t (megjelenik a hiperhivatkozás aláhúzás)
3. Kattint → A plugin elemzi az AST-t
4. Kinyeri az event információt (API, event név)
5. Lekérdezi az indexből a subscribereket
6. Ha 1 subscriber van: közvetlen ugrás
7. Ha több van: listázó dialógus megjelenítése

**UI Mockup - Több Subscriber:**

```
┌──────────────────────────────────────────┐
│ Select Event Handler (3 matches)         │
├──────────────────────────────────────────┤
│ ☑ MdmEventHandlerApi.stateChanged()      │
│   mod-documentdossier/api                │
│                                          │
│ ☑ LoggingEventHandler.onStateChange()   │
│   platform/api                           │
│                                          │
│ ☑ AuditSubscriber.handleMdmEvent()       │
│   mod-audit/api                          │
└──────────────────────────────────────────┘
     [Open All]  [Cancel]
```

---

### F2: Navigáció - Subscriber-től Publisher-hez

**Felhasználói történet:**
> Fejlesztőként amikor Ctrl+Click-kel egy @EventSubscription annotációra kattintok, ugorni szeretnék ahhoz a kódhoz, amely publikálja azt az eventet.

**Implementáció:**

1. A felhasználó a kurzort az `@EventSubscription` annotációra helyezi
2. Megnyomja a `Ctrl+Click`-et
3. A plugin feloldja az `api` és `event` értékeket
4. Lekérdezi az indexből a publishert
5. Ha megtalálta: ugrás a publisher metódushoz
6. Ha nem találta: figyelmeztető dialógus megjelenítése

**Dialógus - Publisher nem található:**

```
┌────────────────────────────────────────┐
│ Publisher nem található                │
├────────────────────────────────────────┤
│ Nem található publisher az eventhez:   │
│                                        │
│ API: MasterDataManagementApi           │
│ Event: STATE_CHANGED                   │
│                                        │
│ Lehetséges okok:                       │
│ • Publisher még nincs implementálva    │
│ • Event név elgépelés                  │
│ • Publisher nem indexelt projektben    │
│                                        │
│ [Keresés a Workspace-ben] [Mégse]      │
└────────────────────────────────────────┘
```

---

### F3: Gutter ikonok

**Felhasználói történet:**
> Fejlesztőként látni szeretném az editor margón a vizuális jelzéseket, amelyek mutatják, hogy mely metódusok publikálnak vagy iratkoznak fel eventekre.

**Implementáció:**

1. A plugin figyeli az editor megnyitási eseményeket
2. Átvizsgálja a jelenlegi fájlt publisherekre/subscriberekre
3. Hozzáadja a margó annotációkat (IAnnotation)
4. Frissíti a fájl változások esetén (inkrementálisan)

**Ikon állapotok:**

| Ikon | Jelentés | Hover szöveg |
|------|---------|------------|
| [PUB] (Kék) | Event publisher | "Publishes: STATE_CHANGED (3 subscribers)" |
| [SUB] (Zöld) | Event subscriber | "Subscribes: STATE_CHANGED from MasterDataMgmt" |
| [PUB!] (Narancs) | Publisher subscriberek nélkül | "Warning: No subscribers for this event" |
| [SUB!] (Narancs) | Subscriber publisher nélkül | "Warning: Publisher not found" |

**Kattintási műveletek:**

- **Bal klikk:** Hover megjelenítése részletekkel
- **Ctrl+Bal klikk:** Navigálás a kapcsolódó kódhoz
- **Jobb klikk:** Kontextus menü (Handlerek keresése, Gráf megjelenítése)

---

### F4: Gyors információ (Hover)

**Felhasználói történet:**
> Fejlesztőként amikor egy event-hez kapcsolódó kód fölé viszem az egeret, gyors információt szeretnék látni az event kapcsolatokról.

**Hover tartalom - Publisher:**

```
─────────────────────────────────────────
Event Publisher
─────────────────────────────────────────
API:   MasterDataManagementApi
Event: STATE_CHANGED
Metódus: fireModificationEvent()

Subscriberek (3):
  • MdmEventHandlerApi.stateChanged()
    Channel: documentTypeSubscriptionAsyncChannel
  
  • LoggingSubscriber.logEvent()
    Channel: loggingChannel
  
  • AuditTrailApi.recordChange()
    Channel: auditChannel

─────────────────────────────────────────
Ctrl+Click a subscriberekhez való navigáláshoz
─────────────────────────────────────────
```

**Hover tartalom - Subscriber:**

```
─────────────────────────────────────────
Event Subscriber
─────────────────────────────────────────
Feliratkozás:
  API:   MasterDataManagementApi
  Event: STATE_CHANGED

Channel: documentTypeSubscriptionAsyncChannel
Típus:   ONE_RUNTIME
Async:   Igen

Publisher:
  • MDMModificationApiImpl.fireModificationEvent()
    platform/api

─────────────────────────────────────────
Ctrl+Click a publisherhez való navigáláshoz
─────────────────────────────────────────
```

---

### F5: Event validáció

**Felhasználói történet:**
> Fejlesztőként figyelmeztetést szeretnék kapni fordítási időben, ha az event definícióimmal probléma van.

**Validációs jelölések:**

```java
@EventSubscription(
    api = MasterDataManagementApi.API,
    event = "stateChangedTypo",  // ← Figyelmeztetés: Event nem található
    channel = "nonExistentChannel"  // ← Figyelmeztetés: Channel nincs konfigurálva
)
void stateChanged(...) { }
```

**Problem nézet integráció:**

```
Problems (4 items)
├─ ⚠️ Warning: No publisher found for event 'MasterDataManagementApi.stateChangedTypo'
│  MdmEventHandlerApi.java  line 15
│
├─ ⚠️ Warning: Event 'STATE_CHANGED' has no subscribers
│  MDMModificationApiImpl.java  line 89
│
├─ ❌ Error: Subscriber method signature does not match publisher
│  LoggingSubscriber.java  line 42
│
└─ ⚠️ Warning: Channel 'nonExistentChannel' is not configured
   MdmEventHandlerApi.java  line 15
```

**Gyors javítások:**

| Probléma | Gyors javítás |
|---------|-----------|
| Event not found | "Create event constant", "Search for similar events" |
| No subscribers | "Create subscriber template", "Ignore warning" |
| Signature mismatch | "Update subscriber signature", "Update publisher signature" |
| Channel not configured | "Create channel configuration", "Use default channel" |

---

### F6: Összes Event Handler keresése

**Felhasználói történet:**
> Fejlesztőként meg szeretném találni az összes handlert egy adott eventhez az egész workspace-ben.

**Aktiválás:**
- Jobb klikk az event konstanson → "Event Handlerek keresése"
- Jobb klikk az `@EventSubscription`-ön → "Összes Subscriber keresése"
- Jobb klikk a publisher híváson → "Összes Handler keresése"

**Keresési nézet integráció:**

```
┌───────────────────────────────────────────────────────┐
│ Keresés: Handlerek a 'MasterDataMgmt.STATE_CHANGED'-hez │
├───────────────────────────────────────────────────────┤
│ 3 handler található                                   │
│                                                       │
│ [SUB] MdmEventHandlerApi.stateChanged()               │
│    mod-documentdossier/api/.../MdmEventHandlerApi.java│
│    15: @EventSubscription(api = ..., event = ...)     │
│                                                       │
│ 📥 LoggingSubscriber.onStateChange()                  │
│    platform/api/.../LoggingSubscriber.java            │
│    42: @EventSubscription(api = ..., event = ...)     │
│                                                       │
│ 📥 AuditTrailApi.recordStateChange()                  │
│    mod-audit/api/.../AuditTrailApi.java               │
│    88: @EventSubscription(api = ..., event = ...)     │
└───────────────────────────────────────────────────────┘
```

---

### F7: Refactoring támogatás (Post-MVP)

**Event konstans átnevezése:**

```java
// Before
public static final String STATE_CHANGED = "stateChanged";

// User renames to: STATE_MODIFIED

// Plugin updates:
// 1. Constant definition ✓
// 2. All @EventSubscription(event = STATE_CHANGED) ✓
// 3. All publisher calls ✓
// 4. Documentation references ✓
```

**Refactoring résztvevő:**

```java
public class RenameEventParticipant extends RenameParticipant {
  
  @Override
  public RefactoringStatus checkConditions(IProgressMonitor pm, CheckConditionsContext context) {
    // Check if element is event constant
    if (!isEventConstant(getAffectedObject())) {
      return null;
    }
    
    // Find all usages
    List<IMethod> subscribers = findSubscribers();
    List<MethodInvocation> publishers = findPublishers();
    
    if (subscribers.isEmpty() && publishers.isEmpty()) {
      return RefactoringStatus.createWarningStatus(
          "No event usages found. This might not be an event constant.");
    }
    
    return new RefactoringStatus();
  }
  
  @Override
  public Change createChange(IProgressMonitor pm) {
    CompositeChange change = new CompositeChange("Rename event references");
    
    // Update all @EventSubscription annotations
    for (IMethod subscriber : findSubscribers()) {
      change.add(createAnnotationParamChange(subscriber, getNewName()));
    }
    
    // Update all publisher calls
    for (MethodInvocation publisher : findPublishers()) {
      change.add(createPublisherArgChange(publisher, getNewName()));
    }
    
    return change;
  }
}
```

**Előnézet:**

```
┌────────────────────────────────────────────────────────┐
│ Event konstans átnevezésének előnézete                 │
├────────────────────────────────────────────────────────┤
│ ☑ MasterDataManagementApi.java                         │
│   - public static final String STATE_CHANGED = ...     │
│   + public static final String STATE_MODIFIED = ...    │
│                                                        │
│ ☑ MdmEventHandlerApi.java                             │
│   - event = MasterDataManagementApi.STATE_CHANGED,     │
│   + event = MasterDataManagementApi.STATE_MODIFIED,    │
│                                                        │
│ ☑ MDMModificationApiImpl.java                         │
│   - MasterDataManagementApi.STATE_CHANGED)             │
│   + MasterDataManagementApi.STATE_MODIFIED)            │
│                                                        │
│ 3 fájl módosul                                        │
└────────────────────────────────────────────────────────┘
   [OK]  [Mégse]
```

---

### F8: Event Subscription Graph nézet (Újabb verzió)

**Funkciók:**

1. **Vizuális gráf:**
   - Csomópontok: Publisher/Subscriber metódusok
   - Élek: Event kapcsolatok
   - Színek: Modul vagy event típus szerint
   - Formák: Publisher (négyzet), Subscriber (kör)

2. **Interakciók:**
   - Dupla klikk csomóponton → Megnyitás az editorban
   - Jobb klikk → Kontextus menü (Összes keresése, Szűrés)
   - Csomópont kiválasztása → Kapcsolódó csomópontok kiemelése
   - Keresősáv → Szűrés event név szerint

3. **Elrendezések:**
   - Fa elrendezés (hierarchikus)
   - Erő-irányított (organikus)
   - Körkörös
   - Manuális pozícionálás

4. **Szűrők:**
   - Modul szerint
   - Event név szerint
   - Channel szerint
   - Csak árva publisherek/subscriberek

5. **Export:**
   - PNG kép
   - SVG vektor
   - GraphML formátum
   - Mermaid diagram

---

### F9: Kódgenerálás (Post-MVP)

**Új Event varázsló:**

```
┌──────────────────────────────────────────────────────┐
│ Új Event definíció varázsló               [1/3]      │
├──────────────────────────────────────────────────────┤
│ Event információk                                    │
│                                                      │
│ API osztály:    [MasterDataManagementApi     V]      │
│ Event név:      [DOCUMENT_CREATED          ]         │
│ Event konstans: [DOCUMENT_CREATED          ]         │
│                                                      │
│ Paraméterek:                             [Hozzáad]   │
│ ┌────────────────────────────────────────────────┐   │
│ │ URI documentUri                         [Edit] │   │
│ │ String documentType                     [Edit] │   │
│ │ OffsetDateTime createdAt                [Edit] │   │
│ └────────────────────────────────────────────────┘   │
│                                                      │
│ [< Back]  [Next >]  [Finish]  [Cancel]               │
└──────────────────────────────────────────────────────┘
```

**Generated Code:**

```java
// 1. Event constant in publisher API
public interface MasterDataManagementApi {
  static final String DOCUMENT_CREATED = "documentCreated";
  
  // Event method (generated)
  void documentCreated(URI documentUri, String documentType, OffsetDateTime createdAt);
}

// 2. Subscriber interface method
public interface MDMSubscriberApi {
  void documentCreated(URI documentUri, String documentType, OffsetDateTime createdAt);
}

// 3. Publisher template
// In publisher implementation class
void fireDocumentCreatedEvent(URI documentUri, String documentType, OffsetDateTime createdAt) {
  invocationApi
      .publisher(MasterDataManagementApi.class, MDMSubscriberApi.class, 
                 MasterDataManagementApi.DOCUMENT_CREATED)
      .publish(api -> api.documentCreated(documentUri, documentType, createdAt));
}

// 4. Subscriber template
@EventSubscription(
    api = MasterDataManagementApi.API,
    event = MasterDataManagementApi.DOCUMENT_CREATED,
    channel = "defaultChannel")
void documentCreated(URI documentUri, String documentType, OffsetDateTime createdAt) {
  // TODO: Implement event handling logic
}
```

---

## UI/UX Terv

### Eszköztár műveletek

```
Eclipse Eszköztár
├─ Event gráf megjelenítése
├─ Event Handlerek keresése
├─ Event Plugin beállítások
└─ Event index újraépítése
```

### Kontextus menü elemek

**Event konstanson:**
```
Jobb klikk a "STATE_CHANGED"-en
├─ Event Handlerek keresése
├─ Megjelenítés az Event gráfban
├─ Event átnevezése (Refactoring)
└─ Event tulajdonságok
```

**@EventSubscription-ön:**
```
Jobb klikk az annotáción
├─ Ugrás a Publisherhez
├─ Összes Subscriber keresése
├─ Event kapcsolat validálása
└─ Megjelenítés az Event gráfban
```

**Publisher híváson:**
```
Jobb klikk az invocationApi.publisher() soron
├─ Ugrás a Subscriberekhez
├─ Összes Handler keresése
├─ Megjelenítés az Event gráfban
└─ Subscriber sablon hozzáadása
```

### Beállítások oldal

```
┌────────────────────────────────────────────────────────┐
│ Eclipse Preferences > Event Navigator                  │
├────────────────────────────────────────────────────────┤
│                                                        │
│ ☑ Enable event navigation                             │
│ ☑ Show gutter icons                                   │
│ ☑ Enable hover tooltips                               │
│ ☑ Validate events on build                            │
│                                                        │
│ Gutter Icons:                                          │
│   Publisher icon: [📤] [Browse...]                     │
│   Subscriber icon: [📥] [Browse...]                    │
│                                                        │
│ Validation Severity:                                   │
│   No publisher found:     [Warning ▼]                  │
│   No subscribers found:   [Ignore  ▼]                  │
│   Signature mismatch:     [Error   ▼]                  │
│   Channel not configured: [Warning ▼]                  │
│                                                        │
│ Index:                                                 │
│   ☑ Index on workspace startup                        │
│   ☑ Incremental indexing                              │
│   Index location: workspace/.metadata/event-index      │
│   [Rebuild Index Now]                                  │
│                                                        │
│ [Apply] [Apply and Close] [Restore Defaults] [Cancel] │
└────────────────────────────────────────────────────────┘
```

---

## Implementációs Fázisok

### Phase 1: MVP - Core Navigation

| Feladat | Deliverable |
|---------|-------------|
| Index infrastructure | EventIndexManager, AST scanners |
| Publisher scanner | EventPublisherScanner working |
| Subscriber scanner | EventAnnotationScanner working |
| Navigation | Hyperlink detector, jump to code |
| Testing & Polish | Unit tests, integration tests |

**Mérföldkő:** Ctrl+Click navigáció működik publisherek és subscriberek között

---

### Phase 2: Vizuális jelzők

| Feladat | Deliverable |
|---------|-------------|
| Gutter icons | Margin annotations visible |
| Hover provider | Tooltip with event info |
| Polish & Testing | Icon updates on file change |

**Mérföldkő:** Vizuális visszajelzés az editorban

---

### Phase 3: Validáció

| Feladat | Deliverable |
|---------|-------------|
| Validation rules | Missing publisher/subscriber detection |
| Marker integration | Problems view showing warnings |
| Quick fixes | Create subscriber, ignore warning |

**Mérföldkő:** Build-time validáció működik

---

### Phase 4: Haladó funkciók

| Feladat | Deliverable |
|---------|-------------|
| Graph view | Basic visualization |
| Graph interactions | Click, filter, export |
| Refactoring support | Rename participant |
| Code generation | Event wizard |

**Mérföldkő:** Teljes funkcióhalmaz

---

## Technikai Kihívások

### 1. Kihívás: String konstans feloldás

**Probléma:**
```java
@EventSubscription(
    api = MasterDataManagementApi.API,  // String constant reference
    event = MasterDataManagementApi.STATE_CHANGED
)
```

**Megoldás:**
- AST-ben `resolveConstantExpressionValue()` használata
- Field constant value lookup via JDT binding
- Fallback: Source code parsing if binding unavailable

**Implementáció:**
```java
private String resolveConstant(Expression expr) {
  if (expr instanceof QualifiedName) {
    IBinding binding = ((QualifiedName) expr).resolveBinding();
    if (binding instanceof IVariableBinding) {
      Object constValue = ((IVariableBinding) binding).getConstantValue();
      if (constValue instanceof String) {
        return (String) constValue;
      }
    }
  }
  // Fallback: parse source
  return parseConstantFromSource(expr);
}
```

---

### 2. Kihívás: Inkrementális index frissítések

**Probléma:**
- Full workspace scan lassú (thousands of files)
- File change minden 5 másodpercben

**Megoldás:**
- Incremental indexing only changed files
- Dirty flag az érintett kapcsolatokra
- Background job periodic cleanup

**Implementáció:**
```java
public class IncrementalIndexer implements IResourceChangeListener {
  
  @Override
  public void resourceChanged(IResourceChangeEvent event) {
    IResourceDelta delta = event.getDelta();
    IncrementalIndexVisitor visitor = new IncrementalIndexVisitor();
    delta.accept(visitor);
    
    // Batch update
    List<ICompilationUnit> changedUnits = visitor.getChangedUnits();
    updateIndex(changedUnits);
  }
  
  private void updateIndex(List<ICompilationUnit> units) {
    // Remove old entries
    for (ICompilationUnit unit : units) {
      eventIndex.remove(unit);
    }
    
    // Re-scan
    for (ICompilationUnit unit : units) {
      eventIndex.scan(unit);
    }
    
    // Notify listeners
    notifyIndexChanged(units);
  }
}
```

---

### 3. Kihívás: Projekt-közötti függőségek

**Probléma:**
- Publisher in platform project
- Subscriber in mod-documentdossier project
- How to find cross-project references?

**Megoldás:**
- Workspace-wide index (not project-scoped)
- Dependency graph aware scanning
- Multi-project search

**Implementáció:**
```java
public List<EventSubscriberInfo> findSubscribers(EventDefinition event) {
  List<EventSubscriberInfo> result = new ArrayList<>();
  
  // Search in all projects
  for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
    if (!project.isAccessible()) continue;
    
    IJavaProject javaProject = JavaCore.create(project);
    if (javaProject == null) continue;
    
    // Search in this project
    result.addAll(findSubscribersInProject(event, javaProject));
  }
  
  return result;
}
```

---

### 4. Kihívás: Lambda kifejezés feldolgozás

**Probléma:**
```java
// Lambda parameter type inference
.publish(api -> api.stateChanged(...))
        // ^^^ Milyen típusú az 'api'?
```

**Megoldás:**
- Lambda expression type inference via JDT
- `LambdaExpression.resolveMethodBinding()`
- Functional interface parameter type

**Implementáció:**
```java
private IMethodBinding resolveLambdaMethod(LambdaExpression lambda) {
  IMethodBinding functionalMethod = lambda.resolveMethodBinding();
  if (functionalMethod != null) {
    return functionalMethod;
  }
  
  // Fallback: infer from context
  ITypeBinding functionalInterface = inferFunctionalInterface(lambda);
  return findSingleAbstractMethod(functionalInterface);
}
```

---

### 5. Kihívás: Teljesítmény nagy méretekben

**Probléma:**
- Large workspace (100+ projects, 50K+ files)
- Index size can be huge
- Memory constraints

**Megoldás:**
- Lazy loading of index data
- LRU cache for recently used events
- Database-backed index (SQLite)
- Periodic garbage collection

**Implementáció:**
```java
public class EventIndexStorage {
  
  private Connection db;
  private Map<String, EventDefinition> cache;
  
  public EventIndexStorage(File dbFile) {
    db = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
    cache = new LRUCache<>(1000); // Cache 1000 events
    
    createSchema();
  }
  
  private void createSchema() {
    db.execute(
      "CREATE TABLE IF NOT EXISTS events (" +
      "  api TEXT, " +
      "  event TEXT, " +
      "  PRIMARY KEY (api, event)" +
      ")");
    
    db.execute(
      "CREATE TABLE IF NOT EXISTS publishers (" +
      "  event_api TEXT, " +
      "  event_name TEXT, " +
      "  class_name TEXT, " +
      "  method_name TEXT, " +
      "  FOREIGN KEY (event_api, event_name) REFERENCES events(api, event)" +
      ")");
    
    // Similar for subscribers
  }
  
  public List<EventSubscriberInfo> findSubscribers(EventDefinition event) {
    // Check cache first
    String key = event.getKey();
    if (cache.containsKey(key)) {
      return cache.get(key).getSubscribers();
    }
    
    // Query database
    List<EventSubscriberInfo> subscribers = querySubscribers(event);
    cacheEvent(event, subscribers);
    return subscribers;
  }
}
```

---

### 6. Kihívás: Eclipse verzió kompatibilitás (Low prio)

**Probléma:**
- Different Eclipse versions have different APIs
- JDT API changes between versions

**Megoldás:**
- Target Eclipse 2023-12 (4.30) or later
- Use stable APIs only
- Version compatibility matrix
- Conditional compilation for version-specific code

**plugin.xml:**
```xml
<plugin>
  <requires>
    <import plugin="org.eclipse.core.runtime" version="3.20.0"/>
    <import plugin="org.eclipse.jdt.core" version="3.30.0"/>
    <import plugin="org.eclipse.jdt.ui" version="3.26.0"/>
  </requires>
</plugin>
```

---

## Tesztelési Stratégia

### Unit tesztek

**Hatókör:** Egyedi komponens logika

| Test Class | Coverage |
|------------|----------|
| `EventAnnotationScannerTest` | AST parsing, annotation extraction |
| `EventPublisherScannerTest` | Publisher pattern detection |
| `EventIndexManagerTest` | Index add/remove/update operations |
| `ConstantResolverTest` | String constant resolution |

**Example Test:**
```java
@Test
public void testSubscriberScanning() {
  // Given
  ICompilationUnit unit = createCompilationUnit(
    "public interface MySubscriber {",
    "  @EventSubscription(api = \"MyApi\", event = \"myEvent\")",
    "  void handleEvent(String param);",
    "}"
  );
  
  // When
  List<EventSubscriberInfo> subscribers = scanner.scanForSubscribers(unit);
  
  // Then
  assertEquals(1, subscribers.size());
  EventSubscriberInfo sub = subscribers.get(0);
  assertEquals("MyApi", sub.getApi());
  assertEquals("myEvent", sub.getEvent());
  assertEquals("handleEvent", sub.getMethod().getElementName());
}
```

---

### Integrációs tesztek

**Hatókör:** Plugin integráció az Eclipse-sel

| Test Suite | Coverage |
|------------|----------|
| `NavigationIntegrationTest` | End-to-end navigation flow |
| `ValidationIntegrationTest` | Build-time validation |
| `RefactoringIntegrationTest` | Refactoring participant |
| `UIIntegrationTest` | Gutter icons, hover tooltips |

**Keretrendszer:** Eclipse SWTBot UI teszteléshez

**Példa teszt:**
```java
@Test
public void testNavigationFromPublisherToSubscriber() {
  // Given
  bot.openEditor("MDMModificationApiImpl.java");
  
  // When  
  SWTBotEclipseEditor editor = bot.activeEditor().toTextEditor();
  editor.navigateTo("invocationApi.publisher");
  editor.pressShortcut(Keystrokes.CTRL, Keystrokes.LF); // Ctrl+Click
  
  // Then
  assertEquals("MdmEventHandlerApi.java", bot.activeEditor().getTitle());
  assertTrue(editor.getText().contains("@EventSubscription"));
}
```

---

### Teljesítmény tesztek (Low prio)

**Metrikák:**
- Index szkennelési idő (teljes workspace)
- Inkrementális frissítési idő (egyetlen fájl)
- Navigációs válaszidő
- Memória lábnyom

**Teljesítménymérések:**
```java
@Test
public void testIndexPerformance() {
  // Given: Workspace with 10,000 Java files
  IWorkspace workspace = createLargeWorkspace(10_000);
  
  // When
  long startTime = System.currentTimeMillis();
  eventIndex.indexWorkspace();
  long duration = System.currentTimeMillis() - startTime;
  
  // Then
  assertTrue("Indexing should complete within 30 seconds", 
             duration < 30_000);
  
  long memoryUsage = getMemoryUsage();
  assertTrue("Memory usage should be under 200MB",
             memoryUsage < 200 * 1024 * 1024);
}
```

---

### Felhasználói elfogadási tesztek

**Forgatókönyvek:**

1. **Alap navigáció:**
   - Felhasználó megnyitja a publisher kódot
   - Ctrl+Click az eventen → Ugrik a subscriberhez
   - A navigáció 500ms alatt befejeződik

2. **Validáció:**
   - Felhasználó subscribert hoz létre elgépelt event névvel
   - A build elindítja a validációt
   - Figyelmeztető jelölés megjelenik a Problems nézetben

3. **Refactoring:**
   - Felhasználó átnevez egy event konstanst
   - A plugin frissíti az összes hivatkozást
   - Nem marad törött hivatkozás

4. **Gráf nézet:**
   - Felhasználó megnyitja az Event Graph nézetet
   - A gráf megjeleníti az összes eventet
   - Dupla klikk a csomóponton → Megnyílik az editorban

---

## Szállítás és telepítés

### P2 Update Site

**Struktúra:**
```
update-site/
├── features/
│   └── org.smartbit4all.eclipse.event.feature_1.0.0.jar
├── plugins/
│   ├── org.smartbit4all.eclipse.event.core_1.0.0.jar
│   └── org.smartbit4all.eclipse.event.ui_1.0.0.jar
├── artifacts.jar
├── content.jar
└── site.xml
```

**Telepítés:**
```
Help > Install New Software...
Add... > Location: http://update.smartbit4all.org/event-navigator
Select: Event Navigator Plugin
Install > Restart Eclipse
```

---

### Marketplace lista

**Eclipse Marketplace:**
- Név: "Event Navigator for SmartBit4All Platform"
- Kategória: Java Development Tools
- Címkék: navigation, events, invocation, refactoring
- Licenc: LGPL v3

---
