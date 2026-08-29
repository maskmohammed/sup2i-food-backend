# Sécurité — sup2i-food-backend

Vue des menaces, contrôles implémentés et configuration associée. La plus grande
partie de ce document décrit les mesures ajoutées/renforcées lors de la
**Phase 13 (Hardening sécurité & exploitation)**.

## 1. Modèle de menace

| Acteur | Capacités supposées | Menaces principales |
|---|---|---|
| Étudiant lambda | Compte personnel, tokens non valides | Lire/modifier des données d'autres organisations (escalade horizontale), brute-force mot de passe, XSS via du contenu libre, injection SQL |
| Employé de cantine | Un compte avec permissions limitées | Escalade de privilèges, manipulation de stocks/solde |
| Admin d'une organisation | Comptes d'administration d'une organisation | Usage abusif d'actions d'administration non tracées |
| Attaquant externe | Accès réseau au service | Scan d'endpoints non protégés (actuator, springdoc), pics de requêtes sur l'auth |

## 2. Axes de défense implémentés

### 2.1 Authentification & tokens
- **JWT HMAC-SHA256** émis par le backend seul ; validation **issuer** (Spring
  `JwtValidators.createDefaultWithIssuer`) **et audience** (validateur maison
  acceptant `aud` en `String` ou `List`) — cf. `SecurityCryptoConfig`.
- **Sessions stateless** révocables : claim `sid` vérifié en base à chaque
  requête (`SessionValidationFilter`) ; un refresh (`refresh_tokens` en base)
  invalide la session précédente. `Cache-Control: no-store` sur login/refresh.
- **MFA TOTP** : secrets chiffrés au repos (`AES`, clé via
  `SUP2I_MFA_ENCRYPTION_KEY_BASE64`), exigé pour les rôles sensibles
  (`ADMINISTRATION`, `SYSTEM_ADMIN`), 10 codes de récupération.
- **Politique de mot de passe** imposée sur le changement de mot de passe :
  longueur min 12, majuscule, minuscule, chiffre, caractère spécial, liste noire
  de mots courants (insensible à la casse, par sous-chaîne).

### 2.2 Limitation de débit (Bucket4j, en mémoire)
Périmètre filtré : `POST /api/v1/auth/*` (login, forgot-password, reset-password,
refresh, mfa/totp). Clé = première valeur de `X-Forwarded-For` sinon adresse
distante. Réponse 429 JSON `RATE_LIMITED` + header `Retry-After`.

| Bucket | Capacité | Recharge |
|---|---|---|
| LOGIN | 60 | 30 / minute |
| FORGOT_PASSWORD | 10 | 3 / minute |
| RESET_PASSWORD | 10 | 2 / minute |
| REFRESH | 120 | 60 / minute |
| MFA | 20 | 10 / minute |

Note : file d'attente non gérée (rejet immédiat au-delà des limites) ; les
buckets inconnus (napkin/souche) sont tolérés à la lecture.

### 2.3 Contrôle d'accès & données
- **RBAC** avec rôles par organisation ; validation d'appartenance
  organisationnelle côté service (pas seulement au niveau contrôleur) ;
  l'escalade horizontale renvoie 404 (non-divulgation).
- **Escalade verticale** : routes d'administration filtrées par rôle
  (`@PreAuthorize`, 404 des actifs hors périmètre).
- **Anti-XSS** : annotation `@SafeText` (rejet, ne nettoie pas) appliquée aux
  champs de saisie libre les plus exposés :
  `CreateWasteRecordRequest.reasonText`,
  `CreatePurchaseOrderRequest.notes` (≤ 1000 chars),
  `CreateReviewRequest.comment` (≤ 1000 chars).
- **Anti-SQLi** : 100 % des accès SQL par requêtes paramétrées (JPA/JPQL,
  `JdbcTemplate` avec placeholders).

### 2.4 Contexte & exploitation
- **Headers** : CSP (`default-src 'none'`), `X-Frame-Options: DENY`,
  `X-Content-Type-Options: nosniff`, HSTS (prod), `Referrer-Policy: no-referrer`.
- **HTTPS** : `requiresChannel` (profil **prod**), via reverse proxy
  (`forward-headers-strategy: framework`).
- **CORS** : fermé par défaut, activable par `SUP2I_CORS_ENABLED` +
  `SUP2I_CORS_ALLOWED_ORIGINS`.
- **Actuator** : seuls `health` (public, avec probes Liveness/Readiness),
  `info` et `metrics` exposés ; `info`/`metrics` derrière authentification.
- **Springdoc/OAS** : désactivé en prod.
- **Erreurs** : réponse JSON uniforme avec `status`, `code`, `message`, `traceId`
  (transmis aux logs, corrélables avec le journal) ; le détail des validations
  est limité aux erreurs 4xx ; les erreurs internes ne divulguent pas de
  stacktrace au client.
- **Observabilité** : `traceId` (MDC) sur chaque log ; en prod, JSON structuré
  (LogstashEncoder) avec MDC — prêt pour ingestion Sentry/ELK.

### 2.5 Journalisation d'audit
Table `audit_logs` (migration V021) alimentée par `AuditLogService`
(transaction `REQUIRES_NEW`, IP source, `RESULT_SUCCESS`, raison ≤ 1000 chars).
Événements enregistrés à ce jour :
- Administration utilisateurs : `USER_ACTIVATED`, `USER_DEACTIVATED`,
  `ROLE_ASSIGNED`, `ROLE_REVOKED` (états avant/après dans `before_data` /
  `after_data`).
- Cycles de mot de passe : `PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET_COMPLETED`.

### 2.6 Ce qui est délibérément hors périmètre
- **Uploads de fichiers** : aucun endpoint d'upload n'existe → « validation
  renforcée des uploads » sans objet ; toute future expérience d'upload devra
  imposer whitelist MIME/extension, taille maximale et stockage hors du
  classpath, avec stream antivirus sauf prescrit côté organisation.
- **TLS en bout de chaîne interne** : géré par l'infrastructure (reverse proxy).

## 3. Configuration (env vars)

| Variable | Obligatoire | Défaut | Description |
|---|---|---|---|
| `SUP2I_JWT_SECRET_BASE64` | oui (hors tests) | — | Clé HMAC (≥ 32 octets) en base64 (`openssl rand -base64 32`) |
| `SUP2I_MFA_ENCRYPTION_KEY_BASE64` | oui (hors tests) | — | Clé AES de chiffrement des secrets TOTP en base64 |
| `SUP2I_JWT_AUDIENCE` | non | fallback = issuer | Nom logique du client attendu dans le claim `aud` |
| `SUP2I_RATE_LIMIT_ENABLED` | non | `true` | Active/désactive le rate limiting |
| `SUP2I_CORS_ENABLED` | non | `false` | Active CORS |
| `SUP2I_CORS_ALLOWED_ORIGINS` | non | vide | Origines autorisées (séparées par des virgules) |
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | oui (prod) | — | Datasource, fournies par `docker-compose.yml` |

Valeurs de référence complètes dans `.env.example` et
`src/main/resources/application.yaml`.

## 4. Notes récurrentes
- Le profil **test** désactive le rate limiting (`src/test/resources/application-test.yaml`)
  pour les tests fonctionnels ; le test de hardening le réactive in situ.
- La politique de mot de passe utilise une **liste noire par sous-chaîne** :
  choisir des mots interdits courts risque de refuser trop de mots de passe
  légitimes (ex. un mot de marque). Ne pas y inclure le nom du produit.
- Garder `session_revocation`/`users` en cohérence : tout redémarrage de base
  (restore) révoque effectivement les tokens (`sid` non retrouvés) — comportement
  par défaut attendu.