# SUP2I FOOD — Roadmap Backend & Analyse de l'existant

**Branche analysée :** `hamza`
**Date :** 27/08/2026
**Stack constatée :** Spring Boot 4.1.0, Java 21, PostgreSQL 17, Flyway (V001→V059), Testcontainers
**Auteur de l'analyse :** Assistant (Claude), à la demande de Ken

> Ce document est le rapport final de l'analyse. Il sert aussi de source au livrable Word
> `docs/ROADMAP_BACKEND_HAMZA.docx`. Aucune modification de code n'a été effectuée à cette étape.

---

## 0. Méthodologie

1. Lecture intégrale de `docs/SUP2I_FOOD_CAHIER_DES_CHARGES_MASTER.docx` (63 sections, ~129 000 caractères).
2. Lecture intégrale de `docs/SUP2I FOOD ROADMAP.pdf` (50 points, 33 pages).
3. Extraction **factuelle** du code :
   - Liste exhaustive des `CREATE TABLE` dans les 59 migrations Flyway (**173 tables**).
   - Liste exhaustive des classes `@Entity` / `@Table` Java (**55 tables réellement mappées**, en comptant `role_permissions` qui est mappée via `@JoinTable` sur `Role`, sans classe dédiée).
   - Diff table-par-table → **118 tables sur 173 (68 %) n'ont strictement aucune classe Java** (ni `@Entity`, ni accès `JdbcTemplate`/requête native vérifiée).
   - Lecture complète du moteur de commandes (`OrderService`, 2367 lignes), de la configuration de sécurité (`SecurityConfig`), du gestionnaire d'erreurs global, des services d'authentification, et inspection ciblée des services catalogue/stock.
   - Vérification de l'historique Git (30 commits, branche `hamza` à jour avec `origin/hamza`).
4. Aucune supposition : chaque statut ✅/🟡/🔴 ci-dessous est appuyé sur des fichiers/classes identifiés.

---

## 1. Constat global (chiffres clés)

| Indicateur | Valeur |
|---|---|
| Fichiers Java (`src/main/java`) | 311 |
| Migrations Flyway | 59 (V001 → V059) |
| Tables SQL définies | 173 |
| Tables avec entité JPA | 55 (32 %) |
| Tables **sans aucune implémentation Java** | 118 (68 %) |
| Fichiers de test | 12 (E2E, Testcontainers) — Auth, Catalog (×5), Inventory (×5), Order |
| Endpoints REST exposés | ~60, répartis sur seulement 4 modules : `catalog`, `inventory`, `order`, `security` |
| Commits sur `hamza` | 30 |
| Fichiers normatifs demandés par la roadmap (`DECISIONS.md`, `SUP2I_FOOD_ERD.md`, `SUP2I_FOOD_OPENAPI.yaml`, `SUP2I_FOOD_STATE_MACHINES.md`, `SUP2I_FOOD_PERMISSIONS_MATRIX.md`, `SUP2I_FOOD_ERROR_CATALOG.md`, `OPEN_DECISIONS.md`) | **0 présent dans le repo** |
| Dépendance Swagger/OpenAPI dans `pom.xml` | **Absente** |
| CI (`.github/workflows`), `Dockerfile`, `docker-compose` | **Absents** |
| Endpoint de audit générique / table `audit_logs` mappée | **Absent** (seul `auth_login_events` est tracé) |

**Lecture du signal :** le collègue n'a pas travaillé "large et superficiel" — il a construit **en profondeur** un socle Auth + Catalogue + Stock + début Commande, avec une qualité technique réelle (verrous `pg_advisory_xact_lock`, réservation de stock transactionnelle, historisation des prix, MFA TOTP, rate limiting login, tests E2E Testcontainers). Mais **7 des 8 modules du monolithe modulaire prévu au cahier des charges n'ont pas démarré** : Payments, POS, Kitchen/KDS, Canteen, Food Pass, Notifications, Promotions/Loyalty sont à 0 %.

---

## 2. Inventaire par module Java existant

### 2.1 `organization` (5 fichiers) — ✅ Fondations multi-campus
- **Domaine :** `Organization`, `Campus`, `Location`, `LocationType`.
- **Repository :** `LocationRepository` uniquement (pas de repo dédié Organization/Campus — accès indirect).
- **Pas de service, pas de controller** : module 100 % socle, consommé par `order`/`identity`.
- **Tables couvertes :** `organizations` ✅, `campuses` ✅, `locations` ✅.
- **Tables Flyway liées mais non implémentées :** `location_business_hours` (V039), `location_schedule_exceptions` (V054), `campus_events` (V041), `academic_calendars`/`academic_calendar_events` (V031), `academic_groups`/`academic_schedule_slots` (V042).

### 2.2 `identity` (18 fichiers) — 🟡 Partiel
- **Domaine :** `User`, `Student`, `Role`, `Permission`, `UserRole`, `AuthIdentity`, `AuthLoginEvent`, enums (`EnrollmentStatus`, `UserStatus`, `AuthProviderType`, `AuthLoginResult`).
- **Repositories :** User, Student, Role, Permission, UserRole, AuthIdentity, AuthLoginEvent.
- **Aucun controller propre** — exposé uniquement via `security.api.MeController`.
- **Tables couvertes :** `users`, `students`, `roles`, `permissions`, `user_roles`, `role_permissions` (via `@JoinTable`), `auth_identities`, `auth_login_events`.
- **Manque total (0 Java) :** `student_photos`, `student_dietary_restrictions`, `student_allergens`, `student_dietary_tags`, `student_segments`/`student_segment_memberships`, `student_group_memberships`, `student_budget_settings`.
  → Impact direct : la photo étudiant (gouvernance obligatoire section 3.1), les allergies déclarées (section 4.7, affichage obligatoire dans l'app), et la segmentation marketing sont **absentes**, alors que les tables existent depuis V002/V024/V042/V044.

### 2.3 `security` (56 fichiers) — 🟡 Solide sur l'auth, silencieux sur l'audit métier
- **Auth :** `AuthController` (login, refresh, logout, MFA TOTP setup/confirm), JWT stateless (`JwtService`), refresh tokens, `SessionValidationFilter` (révocation), `LoginRateLimitService`, `AuthLoginAuditService`, MFA complet (`MfaEnrollmentService`, `MfaVerificationService`, `MfaRecoveryCodeService`, `TotpService`, `Base32Codec`).
- **`SecurityConfig` :** ressource server JWT + RBAC par claims `roles`/`permissions` → `GrantedAuthority`, `@EnableMethodSecurity`, CORS configuré explicitement, sessions `STATELESS`. C'est du niveau production, **en avance** sur la roadmap (le hardening sécurité est prévu en position 36/50 dans le PDF, ici il est déjà largement fait).
- **Tables couvertes :** `refresh_tokens`, `password_reset_tokens` (entité présente, **mais aucun endpoint `/forgot-password` trouvé** — flux incomplet), `device_tokens` (entité présente mais aucun service de push), `user_mfa_methods`, `user_mfa_recovery_codes`.
- **Manque critique (0 Java) : `audit_logs`.** Le cahier des charges gèle pourtant la règle *« Toute action sensible est auditée »* (section 56.1) — aujourd'hui, **rien n'audite** un changement de prix, un remboursement, une correction de stock ou un remplacement de carte, car ces fonctionnalités n'existent pas encore et qu'aucun `AuditLog` générique n'a été créé.
- Autres tables non implémentées : `system_settings`, `setting_definitions`, `data_retention_policies`, `idempotency_records`, `outbox_events`, `file_assets`, `file_asset_links`.

### 2.4 `catalog` (121 fichiers) — ✅ Le module le plus complet
- **Domaine :** Category, Product, ProductVariant, ProductOption(Group/Component), ProductBarcode, ProductPriceHistory, ProductSubstitution, ProductDietaryTag, ProductAllergen, ProductLocationSetting, Recipe, RecipeItem, Ingredient, IngredientAllergen, Allergen, DietaryTag, Menu, MenuSection, MenuItem.
- **11 controllers** (Admin* + public) : gestion catégories/produits/variantes/options/codes-barres/recettes/menus/allergènes/substitutions/paramètres par site.
- **Tables couvertes :** quasi toutes celles de V003, V004, V024, V025, V052.
- **Manque (0 Java) :** `favorites` (favoris étudiant — fonctionnalité mobile explicite section 4.5), `product_allergen_links` / `product_dietary_tag_links` (V024, semblent être une v1 remplacée par `product_allergens`/`product_dietary_tags` de V003/V025 — **incohérence de schéma à clarifier**, deux paires de tables qui se recouvrent fonctionnellement), `product_interaction_events` (tracking vues/clics — alimente les recommandations, V2), `menu_proposals`/`menu_vote_*` (vote de menus, section 19.2, explicitement V2/futur).

### 2.5 `inventory` (77 fichiers) — ✅ Très mature
- **Domaine :** StockLocation, StockItem, StockBalance, InventoryMovement(+Lot), StockLot, StockAlert, InventorySession, InventoryCountLine, StockReceipt(+Line), StockTransfer(+Line).
- **5 controllers admin** : stock de base, alertes (avec réconciliation automatique OUT_OF_STOCK/LOW_STOCK/CRITICAL), réceptions + lots de péremption, inventaires physiques (comptage + écarts), transferts entre sites (workflow approve→dispatch→receive→cancel).
- Couvre une bonne partie de la section 25 du cahier des charges (mouvements, lots, seuils).
- **Point d'attention :** `InventoryAlertService` **génère des alertes** (`StockAlert`) mais **ne désactive jamais automatiquement un produit** quand son ingrédient tombe à zéro. La règle du cahier des charges (« rupture de poulet → Tacos Poulet indisponible », section 9.2/25.3) est en réalité satisfaite *indirectement* : `OrderService.reserveStock()` bloque la commande si le stock est insuffisant au moment de la réservation — mais rien n'expose un flag `disponible=false` pour l'affichage catalogue en temps réel (l'app afficherait le produit comme actif jusqu'à l'échec de commande).
- **Manque total (0 Java) : `waste_records`, `waste_reasons`.** Le gaspillage est pourtant listé explicitement dans le MVP (section 15.1 « Stock : … alertes simples » + section 9.5, un livrable MVP à part entière).
- `purchase_orders`/`purchase_order_items` (fournisseurs) : non implémenté, mais c'est cohérent — la roadmap PDF (point 33) place explicitement les fournisseurs *après* le stock principal.
- `production_runs`/`production_run_items`/`production_allocations`/`preparation_routes` (V55/V56, routage cuisine) : 0 Java — cohérent avec l'absence totale du module Cuisine/KDS.

### 2.6 `order` (26 fichiers) — 🟡 Partiel mais rigoureux jusqu'où il va
- **Domaine :** Order, OrderItem, OrderStatusHistory, StockReservation + enums (`OrderStatus`, `OrderSource`, `OrderType`, `OrderPaymentStatus`, `StockReservationStatus`, `OrderStatusHistorySource`).
- **`OrderService` (lu intégralement, 2367 lignes)** implémente avec un vrai souci du détail :
  - Cycle **DRAFT → CREATED → AWAITING_PAYMENT → CANCELLED / EXPIRED** uniquement.
  - Limite de commandes actives = 2 (conforme au défaut gelé section 56.1).
  - TTL de paiement = 15 min (conforme au défaut gelé).
  - Réservation de stock atomique à l'entrée en `AWAITING_PAYMENT`, avec verrous `pg_advisory_xact_lock` par étudiant et par commande (anti double-achat de la dernière unité — règle critique section 12.5/37).
  - Consommation d'ingrédients calculée depuis la recette active (`Recipe.effectiveFrom/To`), avec facteur de perte (`wasteFactor`) — logique fine et alignée avec la section 25.2.
  - Revalidation stricte du prix serveur à la soumission (rejette si le prix a changé depuis le draft) — conforme à la règle *« aucune confiance dans les prix calculés côté mobile »* (section 21.1/35.2).
  - Numérotation de commande via séquence transactionnelle (`document_sequences`, `ON CONFLICT ... DO UPDATE`) — robuste en concurrence.
- **`OrderController` :** 7 endpoints (`PUT /{id}`, `POST /{id}/submit`, `/begin-payment`, `/cancel`, `/expire`, `GET /{id}`, `GET /{id}/history}`), protégés par `@PreAuthorize("isAuthenticated()")` — **pas de contrôle de rôle explicite** (la propriété de la commande est vérifiée manuellement dans le service via `requireOwnedMobileOrder`).
- **Lacunes critiques identifiées :**
  1. **Aucun statut au-delà d'`AWAITING_PAYMENT`.** `PAID`, `QUEUED`, `PREPARING`, `READY`, `COLLECTED`, `COMPLETED`, `REFUNDED`, `NO_SHOW` existent dans l'enum `OrderStatus` mais **aucune méthode de service ne les atteint**. Une commande ne peut jamais être payée aujourd'hui.
  2. **`expire()` n'est jamais déclenché automatiquement.** Il n'y a **aucun `@Scheduled`** dans tout le projet (vérifié par recherche globale). L'expiration à 15 minutes dépend d'un appel explicite du endpoint `/expire`, réalisé côté client — en pratique, une commande abandonnée reste `AWAITING_PAYMENT` avec du stock réservé indéfiniment tant que personne n'appelle cet endpoint. C'est un vrai risque opérationnel (stock fantôme).
  3. **`time_slots` (créneaux de retrait) : 0 Java.** La table existe (V007) mais rien dans `Order` ne référence de créneau, alors que la section 5.5/21.6 du cahier des charges en fait une règle MVP gelée (capacité par créneau, fermeture automatique).
  4. `order_item_options`, `order_discounts`, `order_item_menu_selections`, `shopping_carts`(+items+options) : 0 Java — les options de produit choisies en commande, les remises, et un panier serveur persistant avant la commande ne sont pas modélisés (le "draft" de `Order` fait office de panier, ce qui est une simplification raisonnable, mais les options par ligne ne sont pas stockées séparément).

### 2.7 `procurement` (2 fichiers) — 🔴 Squelette
- Seulement `Supplier` (entité) + `SupplierRepository`. Aucun service, aucun controller, aucune exception dédiée. C'est un stub laissé en place, cohérent avec le fait que les achats fournisseurs sont un chantier V2 explicite du cahier des charges (section 9.6/49) et du roadmap (point 33).

### 2.8 `common` (5 fichiers)
- `ApiErrorResponse`, `GlobalExceptionHandler` (format d'erreur stable `{code, message, ...}` conforme à la recommandation du PDF point 6), `RequestTrace`/`RequestTraceFilter` (corrélation de requêtes), `MeasurementUnit`.

---

## 3. Modules du cahier des charges **entièrement absents du code** (0 fichier Java)

Vérifié par recherche de classes (`Payment`, `TimeSlot`, `AuditLog`, `Refund`, `Favorite`, `PosSession`, `PosTerminal`, `Notification`, `SubscriptionPlan`, `Subscription`, `FoodPass`, `Promotion`, `LoyaltyAccount`, `Review`, `WasteRecord`, `Wallet`, `MealEntitlement`, `KitchenTicket`, `PurchaseOrder`, `SystemSetting`, `RolePermission`, `Coupon`, `Survey` → **aucune classe trouvée**) et par l'absence de tout `JdbcTemplate`/requête native en dehors des repositories `order`/`inventory` déjà connus.

| Domaine cahier des charges | EPIC(s) roadmap | Migrations concernées | Tables sans Java |
|---|---|---|---|
| **Paiements & Caisse (POS)** | EPIC-06, EPIC-08 | V009, V010, V028, V054 | `payments`, `refunds`, `refund_events`, `pos_terminals`, `pos_sessions`, `pos_session_tender_totals`, `cash_movements`, `payment_events`, `sales_receipts`, `receipt_print_events`, `scan_events` |
| **QR sécurisé** | EPIC-07 | V011 | `qr_credentials` |
| **Cuisine / KDS** | EPIC-09 | V012, V029, V055, V056 | `kitchen_tickets`, `kitchen_ticket_issues`, `kitchen_ticket_items`, `preparation_routes`, `production_runs`, `production_run_items`, `production_run_movements`, `production_allocations` |
| **Créneaux & file** | EPIC-10 | V007, V027 | `time_slots`, `time_slot_reservations` |
| **Cantine** | EPIC-11 | V013, V030 | `canteen_menus`, `canteen_menu_products`, `canteen_menu_choices`, `canteen_reservations`, `canteen_reservation_items` |
| **Abonnements & Food Pass** | EPIC-11, EPIC-12 | V014, V015, V031, V048, V057, V058 | `subscription_plans` (+versions/services), `subscriptions`, `subscription_status_history`, `meal_entitlements`(+adjustments), `meal_usages`, `meal_beneficiaries`, `food_passes`, `food_pass_events` |
| **Notifications** | EPIC-13 | V017, V034 | `notifications`, `notification_preferences`, `notification_category_preferences` |
| **Promotions & Fidélité** | EPIC-14, EPIC-15 | V016, V032, V033 | `promotions`, `promotion_rules`, `promotion_usages`, `promotion_targets`, `promotion_schedule_windows`, `coupons`, `coupon_usages`, `loyalty_accounts`, `loyalty_transactions`, `loyalty_rewards`, `student_segments` |
| **Gaspillage** | EPIC-04 (ext.) | V018, V035 | `waste_records`, `waste_reasons` |
| **Achats fournisseurs** | EPIC-04 (ext., V2) | V019 | `purchase_orders`, `purchase_order_items` |
| **Avis & enquêtes** | (V2) | V020 | `reviews`, `surveys`, `survey_questions`, `survey_responses` |
| **Audit générique & config** | EPIC-19, EPIC-17 | V021, V054 | `audit_logs`, `system_settings`, `setting_definitions`, `data_retention_policies`, `user_preferences` |
| **Technique transverse** | EPIC-19 | V022 | `outbox_events`, `idempotency_records`, `file_assets`, `file_asset_links` |
| **Reporting / Direction** | EPIC-18 | V037 | `report_snapshots`, `report_exports` |
| **Intégrations & imports** | (post-MVP) | V038 | `integration_connectors`, `integration_sync_runs/items`, `integration_inbox_events`, `import_jobs`, `import_job_rows` |
| **Événements/groupes** | (V2) | V041 | `group_orders`, `group_order_members`, `campus_events`, `event_order_details` |
| **Calendrier académique** | (V2) | V031, V042 | `academic_calendars`(+events), `academic_groups`, `academic_schedule_slots` |
| **Wallet & budget** | (V2) | V043, V044 | `wallet_accounts`, `wallet_transactions`, `wallet_topups`, `student_budget_settings` |
| **Prévision & gamification** | (V2/futur) | V045, V046, V047 | `product_interaction_events`, `demand_forecasts`, `gamification_badges`, `student_badges` |

---

## 4. Améliorations et erreurs identifiées (sur l'existant)

1. **Aucune expiration automatique des commandes.** Pas de `@Scheduled` dans tout le projet. Risque : stock réservé indéfiniment sur des commandes abandonnées. *(order, sévérité haute)*
2. **Aucun audit métier générique** malgré la règle gelée « toute action sensible est auditée » (section 56.1). Seuls les logins sont tracés (`auth_login_events`). *(security, sévérité haute — bloquant pour la conformité MVP)*
3. **Flux mot de passe oublié incomplet** : `password_reset_tokens` a une entité mais `AuthController` n'expose aucun endpoint `/forgot-password` ou `/reset-password`. *(security, sévérité moyenne)*
4. **Incohérence de schéma catalogue** : `product_allergen_links`/`product_dietary_tag_links` (V024) coexistent avec `product_allergens`/`product_dietary_tags` (V003/V025) sans que l'on sache si les premières sont mortes ou en cours de remplacement — aucune des deux n'est documentée comme dépréciée. *(catalog, sévérité basse — dette de schéma à clarifier avant d'écrire du code dessus)*
5. **Pas de champ de disponibilité calculée exposée** : la rupture de stock bloque la commande *a posteriori* (à la réservation) plutôt que de désactiver *a priori* l'affichage produit, contrairement à l'exemple du cahier des charges (section 9.2). Fonctionnellement proche, mais l'UX (« Dernières unités », badge indisponible) n'est pas alimentée par un signal serveur dédié. *(catalog/inventory, sévérité basse-moyenne)*
6. **Pas de fichiers normatifs** `DECISIONS.md`, `SUP2I_FOOD_ERD.md`, `SUP2I_FOOD_OPENAPI.yaml`, `SUP2I_FOOD_STATE_MACHINES.md`, `SUP2I_FOOD_PERMISSIONS_MATRIX.md`, `SUP2I_FOOD_ERROR_CATALOG.md`, `OPEN_DECISIONS.md`, alors que le cahier des charges (section 56) les qualifie explicitement de **normatifs, à conserver dans le repository**, et que le PDF roadmap en fait l'étape n°1. Aujourd'hui les règles métier gelées ne vivent que dans le `.docx` — un développeur qui rejoint le projet ne peut pas les retrouver dans le repo. *(gouvernance, sévérité haute pour la suite du projet à 3 devs)*
7. **Pas de contrat OpenAPI/Swagger** : aucune dépendance `springdoc-openapi` dans `pom.xml`, donc aucun contrat consultable pour les équipes Mobile/Web qui doivent développer en parallèle. *(gouvernance, sévérité haute)*
8. **Contrôle d'autorisation par rôle absent au niveau des controllers** (`@PreAuthorize("isAuthenticated()")` générique partout, jamais de vérification de rôle explicite du type `hasRole('STUDENT')` sur `OrderController`). La sécurité repose entièrement sur la logique métier interne (`requireOwnedMobileOrder`), ce qui fonctionne mais laisse la porte ouverte à un oubli futur si un nouvel endpoint est ajouté sans reproduire cette vérification. *(security, sévérité moyenne)*
9. **Order sans lien réel vers Payment** : `OrderPaymentStatus` existe comme enum sur `Order`, mais comme le module `payment` n'existe pas, ce champ n'est mis à jour par personne. C'est le symptôme direct de l'absence du module Paiement — la commande ne peut structurellement pas dépasser `AWAITING_PAYMENT`. *(order × payment, sévérité haute — bloquant)*
10. **Pas de CI (`.github/workflows`), pas de `Dockerfile`/`docker-compose`** malgré la stack `spring-boot-starter-flyway-test` + `testcontainers-postgresql` déjà en place dans `pom.xml`, ce qui montre que l'outillage de test est prêt mais pas branché à un pipeline. *(infra, sévérité moyenne)*

**Points positifs à noter et à ne pas casser en avançant :**
- Le moteur de commandes (verrous, réservation, historisation prix) est d'un niveau nettement au-dessus d'un MVP étudiant — à réutiliser comme référence de style pour les modules suivants.
- La sécurité (MFA TOTP, rate limiting, session revocation, RBAC par claims) est déjà proche d'un niveau production.
- Les tests E2E avec Testcontainers (vraie PostgreSQL, pas de mocks DB) sur order/catalog/inventory/security donnent une base fiable pour éviter les régressions.

---

## 5. Roadmap backend en phases

> Construite à partir de la section 10 de la roadmap PDF (*Auth → Users/Students → Roles/Permissions →
> Categories → Products → Pricing → Orders → Payments → Stock*), des 19 EPICs, et des dépendances
> techniques réelles observées dans le code (ex. Payment doit précéder Kitchen, qui doit précéder QR/scan
> universel côté POS, etc.). Chaque phase est indépendamment livrable au sens du cahier des charges
> (section 51, *Definition of Done*).

### Phase 0 — Gouvernance & fondations transverses
**Modules/EPICs :** transverse (aucun EPIC dédié, prérequis explicite du PDF points 1, 6, 9)
**Statut : 🔴 NON COMMENCÉ**
- Créer `DECISIONS.md` (règles métier gelées, section 56.1 du cahier des charges).
- Créer le contrat `SUP2I_FOOD_OPENAPI.yaml` (+ dépendance `springdoc-openapi-starter-webmvc-ui` pour Swagger UI auto-généré depuis les controllers existants — gain rapide vu que 4 modules exposent déjà ~60 endpoints).
- Créer `SUP2I_FOOD_STATE_MACHINES.md` (formaliser ce qui existe déjà dans `OrderStatus`/`StockReservationStatus` + ce qui reste à ajouter).
- `SUP2I_FOOD_PERMISSIONS_MATRIX.md` à partir de la matrice RBAC section 31 du cahier des charges.
- `Dockerfile` + `docker-compose.yml` (backend + PostgreSQL) + CI GitHub Actions minimale (build + tests Testcontainers).
- **Dépendances :** aucune — peut démarrer immédiatement, en parallèle de tout le reste.

### Phase 1 — Auth, Identité, RBAC
**Modules/EPICs :** EPIC-01 Auth, EPIC-02 Students, EPIC-19 Security (partiel)
**Statut : 🟡 PARTIEL** — preuve : `security/` (56 fichiers), `identity/` (18 fichiers), `AuthE2EIntegrationTest`.
- Fait : login/refresh/logout, MFA TOTP, RBAC par claims JWT, rate limiting, audit des connexions, `/me`.
- Reste à faire :
  - Flux mot de passe oublié (`password_reset_tokens` déjà en base).
  - `AuditLog` générique + service d'écriture, branché sur les futures actions sensibles (prix, remboursement, stock, carte).
  - Gestion admin des étudiants (photo, allergies, statut) — `student_photos`, `student_allergens`, `student_dietary_restrictions`, `student_dietary_tags` n'ont aucun controller/service.
  - Endpoints admin `users`/`roles`/`permissions` (CRUD) — aujourd'hui uniquement accessibles par script SQL/seed.
- **Dépendances :** Phase 0 (contrat API) recommandée avant d'ajouter de nouveaux endpoints admin.

### Phase 2 — Catalogue, Recettes & Stock
**Modules/EPICs :** EPIC-03 Catalog, EPIC-04 Inventory (cœur)
**Statut : ✅ TERMINÉ pour le cœur MVP, 🟡 PARTIEL sur les extensions**
- Fait (preuve : `catalog/` 121 fichiers, `inventory/` 77 fichiers, 10 tests E2E) : catégories, produits, variantes, options, codes-barres, recettes/ingrédients, menus, allergènes/tags diététiques, paramètres par site, mouvements de stock, lots/péremption, réceptions, comptages physiques, transferts, alertes seuils.
- Reste à faire :
  - `favorites` (favoris étudiant — mobile).
  - Clarifier/supprimer `product_allergen_links`/`product_dietary_tag_links` (doublon apparent, cf. §4.4).
  - `waste_records`/`waste_reasons` (gaspillage — MVP explicite).
  - Exposer un signal de disponibilité produit calculé (plutôt que la seule vérification à la commande).
- **Dépendances :** aucune bloquante — module déjà en état d'être consommé par les phases suivantes.

### Phase 3 — Créneaux de retrait & file virtuelle
**Modules/EPICs :** EPIC-10 Slots & Queue
**Statut : 🔴 NON COMMENCÉ**
- Entités à créer : `TimeSlot` (V007 : `start_time`, `end_time`, `capacity`, `reserved_capacity`, `status`), `TimeSlotReservation` (V027).
- Endpoints : `GET /api/v1/catalog/time-slots` (public), admin CRUD créneaux/capacité.
- Règles à coder : fermeture automatique d'un créneau plein (transaction atomique, même pattern que `reserveStock` déjà écrit dans `OrderService`), blocage des commandes trop tardives, position/estimation file virtuelle (section 4.4, 21.6).
- Intégrer `time_slot_id` dans `Order` (champ actuellement absent).
- **Dépendances :** Phase 2 (catalogue) fait. Doit être fait **avant ou en parallèle** de la Phase 4, car le paiement POS doit pouvoir afficher le créneau de la commande scannée.

### Phase 4 — Paiements & Caisse (POS)
**Modules/EPICs :** EPIC-06 Payments, EPIC-08 POS
**Statut : 🔴 NON COMMENCÉ — priorité n°1**
- C'est le blocage structurel principal : `Order` ne peut aujourd'hui jamais dépasser `AWAITING_PAYMENT`.
- Entités à créer : `Payment`, `Refund`, `PosTerminal`, `PosSession`, `CashMovement` (V009/V010/V028/V054 déjà en base).
- Règle gelée à respecter strictement (section 56.1) : *une commande non payée n'est jamais envoyée en préparation* — donc `OrderService.pay()` doit être le **seul** point d'entrée vers `PAID`, avec transaction atomique paiement+changement de statut (section 12.5).
- Endpoints : `POST /api/v1/pos/sessions/open|close`, `POST /api/v1/pos/sales`, `POST /api/v1/orders/{id}/pay`, `GET /api/v1/pos/products/by-barcode/{code}`.
- Session de caisse : ouverture/fond initial/clôture avec écart théorique vs compté (section 7.7).
- **Dépendances :** Phase 1 (rôles Caissier/Resp. Snack) et Phase 2 (codes-barres déjà prêts dans `catalog`). Bloque toutes les phases suivantes qui dépendent d'une commande `PAID`.

### Phase 5 — QR sécurisé & scan universel
**Modules/EPICs :** EPIC-07 QR
**Statut : 🔴 NON COMMENCÉ**
- Entité `QrCredential` (V011) : token signé/opaque, expiration, statut, révocation — réutilisable pour QR de commande **et** futur Food Pass.
- Endpoint `POST /api/v1/scans/resolve` (résolveur universel décrit section 23.4 et point 21 du PDF) : distingue QR commande / Food Pass / code-barres produit et retourne un type + actions autorisées.
- **Dépendances :** Phase 4 (le POS doit pouvoir scanner une commande pour la payer) — à développer en parallèle serré de la Phase 4, idéalement avant.

### Phase 6 — Cuisine / KDS
**Modules/EPICs :** EPIC-09 Kitchen
**Statut : 🔴 NON COMMENCÉ**
- Entités : `KitchenTicket`, `KitchenTicketIssue`, `KitchenTicketItem` (V012/V029), et optionnellement `PreparationRoute`/`ProductionRun*` (V55/V56) si la cuisine a plusieurs postes.
- Règle gelée : une commande `PAID` doit apparaître **automatiquement** en file KDS (`QUEUED`), jamais avant.
- Endpoints : `GET /api/v1/kds/queue`, `POST /api/v1/orders/{id}/start-preparation`, `POST /api/v1/orders/{id}/ready`.
- Effet à la mise `READY` : notification, écran mobile « commandes prêtes » (dépend de la Phase 8).
- **Dépendances :** Phase 4 (statut `PAID` doit exister).

### Phase 7 — Cantine, Abonnements & Food Pass
**Modules/EPICs :** EPIC-11 Canteen, EPIC-12 Food Pass
**Statut : 🔴 NON COMMENCÉ**
- Entités : `SubscriptionPlan`(+versions), `Subscription`, `MealEntitlement`(+adjustments), `MealUsage`, `FoodPass`, `CanteenMenu`(+choices), `CanteenReservation`(+items) — schéma déjà très détaillé (V013–V015, V030, V031, V048, V057, V058).
- Règles gelées critiques à respecter à la lettre (section 56.1/22.7-22.8) :
  - **Un seul repas valide par étudiant, par type de repas, par date** — contrainte d'unicité en base + transaction atomique au scan (même pattern `pg_advisory_xact_lock` que `OrderService`).
  - **Double scan simultané → exactement 1 succès, l'autre `MEAL_ALREADY_USED`.**
  - Perte de carte → ancien QR immédiatement invalide, nouvelle carte **conserve** abonnement/quota/historique (jamais de remise à zéro).
  - Responsable Cantine peut **voir** la photo mais jamais la **modifier**.
- Endpoints : plans, `POST /api/v1/canteen/reservations`, `POST /api/v1/admin/food-pass/issue|mark-lost|replace`, `POST /api/v1/canteen/scan/validate`.
- **Dépendances :** Phase 5 (QR partagé avec les commandes) fortement recommandée avant. Indépendant de Payments/Kitchen sur le plan technique, mais dépend de la Phase 1 (Students) et de l'Administration (Phase 9) pour l'activation d'abonnement.

### Phase 8 — Notifications
**Modules/EPICs :** EPIC-13 Notifications
**Statut : 🔴 NON COMMENCÉ**
- Entités : `Notification`, `NotificationPreference`(+catégories) (V017/V034).
- Intégration Firebase (ou équivalent) pour push — `device_tokens` déjà mappé côté `security`, prêt à être branché.
- Événements déclencheurs déjà identifiables dans le code actuel : changement de `OrderStatus` (déjà tracé dans `OrderStatusHistory`), à connecter à un événement `OrderReady`/`OrderPaid` une fois les Phases 4/6 en place.
- **Dépendances :** Phase 4 et 6 (rien à notifier tant que les commandes ne progressent pas au-delà d'`AWAITING_PAYMENT`).

### Phase 9 — Administration & Dashboard Direction
**Modules/EPICs :** EPIC-17 Administration, EPIC-18 Analytics
**Statut : 🔴 NON COMMENCÉ**
- Aujourd'hui : aucun controller d'administration transverse (recherche étudiant, activation abonnement, gestion cartes, gestion rôles) — seuls `catalog`/`inventory` ont des controllers `Admin*`.
- Entités reporting : `ReportSnapshot`, `ReportExport` (V037) — 0 Java.
- Endpoints Direction (section 33.9) : `/api/reports/executive|sales|canteen|inventory|waste|products|operations`.
- **Dépendances :** consomme les données des Phases 2 à 8 — n'a de sens qu'une fois qu'il y a des commandes payées, une cantine active et du gaspillage tracé.

### Phase 10 — Promotions & Fidélité
**Modules/EPICs :** EPIC-14 Promotions, EPIC-15 Loyalty
**Statut : 🔴 NON COMMENCÉ**
- Entités : `Promotion`(+rules/usages/targets/schedule), `Coupon`(+usages), `LoyaltyAccount`, `LoyaltyTransaction`(+rewards) (V016, V032, V033).
- Règle gelée : ledger fidélité transactionnel et immuable (jamais de simple `UPDATE points`), points crédités seulement après vente payée et non annulée.
- **Dépendances :** Phase 4 (une promotion/des points n'ont de sens que sur une vente `PAID`).

### Phase 11 — Gaspillage & Achats fournisseurs
**Modules/EPICs :** EPIC-04 (extension), EPIC-16 Waste
**Statut : 🔴 NON COMMENCÉ**
- `WasteRecord`/`WasteReason` (V018/V035) : petite phase rapide à livrer (schéma simple, réutilise `InventoryMovement` existant comme type de mouvement `WASTE`).
- `Supplier` a déjà une entité (`procurement`) — compléter avec `PurchaseOrder`/`PurchaseOrderItem` (V019). Explicitement V2 selon le cahier des charges (§9.6/§49) et la roadmap (point 33) — à ne traiter qu'après le cœur stock/commande stabilisé.
- **Dépendances :** Phase 2 (inventory) — techniquement indépendant de Payments/Kitchen, peut être avancé plus tôt si utile pour les démos Direction.

### Phase 12 — Avis, enquêtes & vote de menus
**Modules/EPICs :** (hors 19 EPICs numérotés — mentionné section 19.2/§20 du cahier des charges)
**Statut : 🔴 NON COMMENCÉ**
- `Review`, `Survey`(+questions/responses), `MenuProposal`/`MenuVote*` (V020, V036).
- Explicitement post-MVP / pilote selon le cahier des charges — à ne pas prioriser avant le pilote terrain (section 39.8, 45).
- **Dépendances :** nécessite des commandes/repas réellement consommés (Phases 4, 6, 7).

### Phase 13 — Hardening sécurité, continuité & exploitation
**Modules/EPICs :** EPIC-19 Security (complément)
**Statut : 🟡 PARTIEL**
- Fait : JWT, MFA, RBAC, rate limiting login, CORS, sessions stateless révocables.
- Reste à faire : `AuditLog` généralisé (voir §4.2 — le plus urgent), mode dégradé/panne réseau POS (section 36), sauvegardes testées + monitoring (section 38), `idempotency_records`/`outbox_events` (déjà en base, utile pour fiabiliser paiement/notifications une fois créés), tests de charge (section 37/39.7).
- **Dépendances :** transverse — l'`AuditLog` doit être posé **avant** la Phase 4 (Paiements) idéalement, pour auditer les remboursements dès leur création plutôt que de le rajouter après coup.

### Phase 14 — V2 / post-pilote (hors périmètre MVP)
**Modules/EPICs :** Wallet, Budget étudiant, Gamification, Prévision de demande (IA), Calendrier académique avancé, Intégrations/imports, Commande groupée/événementielle, Multi-site.
**Statut : 🔴 NON COMMENCÉ (attendu — hors MVP)**
- Tables déjà présentes en base par anticipation (V041–V047) mais **volontairement** non implémentées tant que le cœur MVP (Phases 0–9) n'est pas stable, conformément à la section 15.2 du cahier des charges (*« Ne pas commencer par fidélité ou IA »* — PDF point 10).

---

## 6. Tableau de synthèse

| Phase | Modules/EPICs | Statut | Dépend de |
|---|---|---|---|
| 0 — Gouvernance & fondations | transverse | 🔴 NON COMMENCÉ | — |
| 1 — Auth, Identité, RBAC | EPIC-01, 02, 19(p) | 🟡 PARTIEL | — |
| 2 — Catalogue, Recettes, Stock | EPIC-03, 04 | ✅ TERMINÉ (cœur) / 🟡 (extensions) | — |
| 3 — Créneaux & file virtuelle | EPIC-10 | 🔴 NON COMMENCÉ | Phase 2 |
| 4 — Paiements & POS | EPIC-06, 08 | 🔴 NON COMMENCÉ (priorité 1) | Phase 1, 2 |
| 5 — QR & scan universel | EPIC-07 | 🔴 NON COMMENCÉ | Phase 4 |
| 6 — Cuisine / KDS | EPIC-09 | 🔴 NON COMMENCÉ | Phase 4 |
| 7 — Cantine, Abonnements, Food Pass | EPIC-11, 12 | 🔴 NON COMMENCÉ | Phase 1, 5 |
| 8 — Notifications | EPIC-13 | 🔴 NON COMMENCÉ | Phase 4, 6 |
| 9 — Administration & Direction | EPIC-17, 18 | 🔴 NON COMMENCÉ | Phases 2–8 |
| 10 — Promotions & Fidélité | EPIC-14, 15 | 🔴 NON COMMENCÉ | Phase 4 |
| 11 — Gaspillage & Fournisseurs | EPIC-04(ext), 16 | 🔴 NON COMMENCÉ | Phase 2 |
| 12 — Avis, enquêtes, vote menus | — | 🔴 NON COMMENCÉ | Phases 4, 6, 7 |
| 13 — Hardening & exploitation | EPIC-19(compl.) | 🟡 PARTIEL | transverse, idéalement avant Phase 4 |
| 14 — V2 / post-pilote | — | 🔴 NON COMMENCÉ (attendu) | Phases 0–9 stables |

---

## 7. Synthèse et recommandation

**Où en est réellement le projet :** environ 2 phases sur 15 sont terminées ou quasi-terminées (Catalogue/Stock, et l'essentiel d'Auth/Identité), avec une exécution technique de bon niveau. Mais le backend **ne peut aujourd'hui produire aucune commande allant jusqu'au bout** : le parcours cœur du MVP décrit en conclusion du cahier des charges —

> *Commande mobile → QR → POS → Paiement → KDS → Prête → Retrait → Stock/KPI*

— est interrompu dès la 3ᵉ étape (Paiement), qui n'existe pas.

**Recommandation pour la prochaine phase à attaquer : Phase 4 — Paiements & Caisse (POS)**, immédiatement précédée ou accompagnée de la Phase 5 (QR) et, si possible, de la partie audit de la Phase 13 (poser `AuditLog` avant de créer des remboursements). C'est le nœud qui bloque structurellement toutes les phases suivantes (Kitchen, Notifications, Fidélité, Direction) et qui transforme le travail déjà solide sur Order/Catalog/Inventory en un flux réellement démontrable — condition explicite avant de présenter quoi que ce soit à SUP2I (section 47 du cahier des charges : *ne jamais confondre projet logiciel et démonstration d'un flux qui ne va pas jusqu'au bout*).

En parallèle, la Phase 0 (DECISIONS.md + OpenAPI) coûte peu et démêle un vrai risque de coordination dès qu'un 2ᵉ ou 3ᵉ développeur backend rejoint — actuellement les règles métier gelées n'existent que dans un `.docx` de 130 000 caractères, ce qui n'est pas consultable rapidement pendant le développement.

---

## Annexe A — Détail de la commande diff (méthode)

```
Tables Flyway (CREATE TABLE, V001→V059) : 173
Tables mappées par une classe @Entity (ou @JoinTable) : 55
Tables sans aucune classe Java :                        118  (68 %)
```
Liste des 55 tables implémentées : `organizations, campuses, locations, suppliers, users, user_roles,
students, roles, permissions, role_permissions(via @JoinTable), auth_login_events, auth_identities,
categories, products, product_variants, product_option_groups, product_options,
product_option_components, product_barcodes, product_price_history, product_substitutions,
product_dietary_tags, product_allergens, product_location_settings, recipes, recipe_items,
ingredients, ingredient_allergens, allergens, dietary_tags, menus, menu_sections, menu_items,
orders, order_items, order_status_history, stock_reservations, device_tokens,
password_reset_tokens, refresh_tokens, user_mfa_methods, user_mfa_recovery_codes, stock_alerts,
inventory_movement_lots, inventory_sessions, stock_receipt_lines, inventory_count_lines,
stock_locations, stock_transfer_lines, stock_lots, inventory_movements, stock_items,
stock_transfers, stock_receipts, stock_balances`.

La liste complète des 118 tables non implémentées figure dans le tableau de la section 3.

## Annexe B — Documents sources consultés
- `docs/SUP2I_FOOD_CAHIER_DES_CHARGES_MASTER.docx` (63 sections, lu intégralement)
- `docs/SUP2I FOOD ROADMAP.pdf` (50 points / 33 pages, lu intégralement)
- `src/main/java/com/sup2i/food/**` (311 fichiers)
- `src/main/resources/db/migration/V001__*.sql` → `V059__*.sql` (59 fichiers)
- `src/test/java/com/sup2i/food/**` (12 fichiers)
- `pom.xml`, `README.md`, historique Git de la branche `hamza` (30 commits)
