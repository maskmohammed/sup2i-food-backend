# Décisions — sup2i-food-backend

Registre des décisions d'architecture prises en cours de projet, versant
« sécurité & exploitation » (Phase 13). Constitution française de type
tracé de décision ; chaque entrée : contexte → décision → conséquences.
Ce préfixe est destiné à être fusionné avec le DECISIONS.md « Phase 0 »
lorsqu'il sera rédigé.

## D13.1 — JWT : exiger l'audience (claim `aud`)
- **Contexte** : les pratiques d'émulation de token non signées par le serveur
  (injection directe de claims via l'encodeur de test) risquaient d'être
  acceptées ; le validateur initial comparait `audience.equals(value)` avec une
  valeur typée `List`, ce qui rejetait tous les tokens légitimes (401 en masse)
  sans pour autant valider positivement les tokens forgés.
- **Décision** : exiger `issuer` ET `audience`. Le validateur accepte `aud` en
  `String` ou `List<String>` (voir `matchesAudience` dans `SecurityCryptoConfig`).
  Par défaut, quand `SUP2I_JWT_AUDIENCE` n'est pas posée, `audience()` retombe
  sur l'issuer.
- **Conséquences** : tous les tokens émis par le backend portent `aud` ; les
  tokens forgés sans `aud` ou avec une audience étrangère sont rejetés en 401 ;
  les suis frappés par les helpers de test ont été alignés (`audience(...)`).
- **Coût** : format `aud` en `List` — les clients lisent `aud[0]`.

## D13.2 — Rate limiting en mémoire (Bucket4j), sans store distribué
- **Contexte** : budget Phase 13 limité ; le poster n'a qu'un réplica par
  environnement (compose). Un store Redis/PostgreSQL ajoute du couple
  « opérationnel » sans bénéfice mesurable à ce stade.
- **Décision** : buckets en mémoire (Bucket4j) sur le chemin `POST /auth/*`,
  clé par adresse IP (1ʳᵉ valeur `X-Forwarded-For` sinon `remoteAddr`), refus
  429 immédiat + `Retry-After`.
- **Conséquences** : en cas de passage multi-réplicas, chaque pod a sa propre
  comptabilité (limites à ajuster) ; à reconsidérer (Redis) si un load balancer
  distribue vraiment le trafic.

## D13.3 — Audit log transactionnel `REQUIRES_NEW`
- **Contexte** : un audit qui doit « survivre » à la transaction qu'il décrit
  (ex. rejet d'un flux) ne peut pas écrire dans la même transaction.
- **Décision** : `AuditLogService.record` en `REQUIRES_NEW`, IP capturée depuis
  `RequestContextHolder`, `RESULT_SUCCESS` par défaut, déclaré dans
  `docs/SECURITY.md` §2.5.
- **Conséquences** : surcharge d'écriture d'1 ligne SQL par événement ; pas de
  risque de rollback croisé avec le flux audité. Les codes d'événements sont
  nominatifs (`USER_ACTIVATED`, …) et non générés par date.

## D13.4 — XSS : rejet (`@SafeText`) plutôt que nettoyage
- **Contexte** : le nettoyage/sanitization store-ou-render est compliqué à
  rendre hermétique et dénature des contenus légitimes.
- **Décision** : valider à la saisie et **rejeter (400 VALIDATION_ERROR)** les
  valeurs contenant `<script>`, `<iframe>`, les gestionnaires d'événements et
  schémas `javascript:`/`data:`/`vbscript:`/`expression(`. Périmètre volontaire :
  champ libres les plus exposés (waste reason, PO notes, avis).
- **Conséquences** : plus aucune donnée persistée n'exige de désinfection en
  sortie ; les nouveaux champs libres doivent ajouter `@SafeText` (rappels dans
  la revue de code).

## D13.5 — SQL : l'injection = pas une feature
- **Contexte** : une base de paramétrage complet (JPA/JPQL/Criteria + JDBC)
  est déjà en place.
- **Décision** : aucune concaténation SQL n'est autorisée ; tout accès passe par
  requêtes préparées ; un test E2E vérifie que des payloads d'injection
  (login, forgot-password) ne donnent ni accès ni altération (`users` inchangé).
- **Conséquences** : RAS — politique d'écriture de code.

## D13.6 — Erreurs uniformes + traceId, observabilité prête pour Sentry
- **Contexte** : avant la Phase 13, l'exception générique n'était pas
  interceptée (stack 500) et les logs n'exposaient pas de corrélation.
- **Décision** : `GlobalExceptionHandler` intercepte tout, corps d'erreur JSON
  standardisé ± `traceId` ; logback produit du **JSON structuré (MDC)** hors
  dev, non-prod en texte lisible ; `traceId` sert de pont log ↔ client.
- **Conséquences** : les incidents 5xx se tracent au traceId ; le pipeline
  d'ingestion (Sentry/ELK) n'a qu'à parser le JSON (déjà prévu).

## D13.7 — Prod : fail-fast sur les secrets, segmentation en profil
- **Contexte** : datasource pointée par des variables `DB_*` non fournies par le
  compose (incohérence de chaîne).
- **Décision** : `application-prod.yaml` s'appuie sur les variables
  Spring canonicales (`SPRING_DATASOURCE_*` d'`docker-compose.yml`) et a
  `server.forward-headers-strategy: framework` + `graceful shutdown` + springdoc
  désactivé ; les secrets JWT/MFA restent issus de `SUP2I_*`. Démarrage en prod
  sans les variables ⇒ échec net (pas de valeur « bidon »).
- **Conséquences** : `DB_URL/DB_USER/DB_PASSWORD` supprimées du profil prod ;
  la chaîne compose → app est la seule source de datasource documentée
  (voir runbook).

## D13.8 — Anomalies connues (assumées)
- `application.yaml` porte `login-protection`, `mfa` et `cors` sous `sup2i.loyalty`
  (indentation héritée, hors `sup2i.security`). Conséquence : au profil local le
  binding de ces toggles est perdu ; tous les tests qui en dépendent posent les
  propriétés explicitement (inline `@SpringBootTest(properties=…)`). Correction
  d'indentation = à faire lors de la Phase 0 / refactor config, pas prioritaire
  pour la livraison.
- La politique de mot de passe interdit par **sous-chaîne** : un mot très court
  (ex. « pass ») n'est volontairement pas dans la liste (faux positifs) ; la
  liste ne contient que des entrées ≥ 6 caractères sans risque.