# Runbook — sup2i-food-backend

Procédures d'exploitation : démarrage, secrets, arrêt/redémarrage, sauvegarde,
restauration, rollback. Référence `docs/SECURITY.md` pour le détail sécurité.

## 1. Démarrage local (dev)
- Prérequis : Java 21, PostgreSQL 17.10 local (db `sup2i_food`), volumes
  Flyway (V001→V067) appliqués au démarrage (`spring.jpa.hibernate.ddl-auto=validate`).
- `.\mvnw.cmd spring-boot:run` (Windows) / `./mvnw spring-boot:run` (Unix).
- Profil par défaut `local` : secrets JWT/MFA nécessaires dans l'environnement
  car `SUP2I_JWT_SECRET_BASE64` et `SUP2I_MFA_ENCRYPTION_KEY_BASE64` sont
  sans valeur par défaut → les poser avant le premier démarrage :
  ```
  $env:SUP2I_JWT_SECRET_BASE64 = openssl rand -base64 32
  $env:SUP2I_MFA_ENCRYPTION_KEY_BASE64 = openssl rand -base64 32
  ```
- Vérifier : `GET localhost:8080/actuator/health` → `{"status":"UP"}`.

## 2. Démarrage en conteneurs (docker compose)
```
cp .env.example .env   # puis remplir POSTGRES_PASSWORD, SUP2I_JWT_SECRET_BASE64,
                       # SUP2I_MFA_ENCRYPTION_KEY_BASE64 (et CORS si besoin)
docker compose up --build --detach
```
- Le compose fournit lui-même `SPRING_DATASOURCE_*` (reliées à `POSTGRES_*`) et
  rejette le démarrage si les secrets manquent (vérif `:?`).
- Profil application : le compose lance l'app avec le contexte par défaut ;
  pour activer le profil de production (`application-prod.yaml`) passer
  `SPRING_PROFILES_ACTIVE=prod` au service `backend`.

## 3. Secrets & configuration
Générés une fois, stockés hors du dépôt (`.env` sent non versionné) :
| Variable | Usage | Génération |
|---|---|---|
| `SUP2I_JWT_SECRET_BASE64` | Signer les JWT | `openssl rand -base64 32` |
| `SUP2I_MFA_ENCRYPTION_KEY_BASE64` | Chiffrer les secrets TOTP au repos | `openssl rand -base64 32` |
| `SUP2I_JWT_AUDIENCE` (optionnel) | Audience attendue (défaut = issuer) | nom logique du client |
| `SUP2I_RATE_LIMIT_ENABLED` | Rate limiting on/off (défaut true) | — |
| `SUP2I_CORS_ENABLED` / `SUP2I_CORS_ALLOWED_ORIGINS` | CORS (défaut off) | listes d'origines |

**Rotation de clé** : les tokens signés avec l'ancienne clé deviennent invalides
immédiatement (chaque déploiement « renouvelle la clé » = expulsion de tous les
clients). Planifier la rotation à une fenêtre de maintenance.

## 4. Santé & monitoring
- `GET /actuator/health` : public, exporte probes Kubernetes
  (`/actuator/health/liveness`, `/actuator/health/readiness`) si souhaité.
- `GET /actuator/info` et `/actuator/metrics` : derrière authentification.
- Logstructuré JSON en prod (« service » + MDC + `traceId`) ; rechercher un
  incident par `traceId` rendu au client dans l'erreur 5xx.
- Données tomber au fil de l'eau : `AuditLog` (audit_logs) — surveiller de
  l'espace de cette table en volume.

## 5. Arrêt / redémarrage
```
docker compose down          # arrêt (volumes conservés)
docker compose down -v       # destruction des volumes SGBD (⚠ destructive)
docker compose up --build -d # redémarrage avec rebuild
```
- À l'arrêt rond : `server.shutdown=graceful` (profil prod) — les requêtes en
  vol sont purgées avec un délai raisonnable des connexions.

## 6. Sauvegarde & restauration
Sauvegarder la base (schéma Flyway + données) ; a minima quotidienne,
idéalement hors du nœud applicatif.
```
# dump
docker compose exec db pg_dump -U postgres -d sup2i_food -Fc -f /tmp/sup2i.dump
docker cp sup2i-food-backend-db-1:/tmp/sup2i.dump ./backup/sup2i_$(date -Iseconds).dump

# restore (base vide)
docker compose exec -T db pg_restore -U postgres -d sup2i_food --clean --if-exists < backup/sup2i_XXXX.dump
```
- **Conséquence post-restore** : les `sid` de session ne correspondent plus à la
  base restaurée si le dump est antérieur à l'émission des tokens → les clients
  doivent se reconnecter. Comportement voulu (cf. SECURITY.md §4).
- Tester régulièrement la restauration sur un environnement de repli.

## 7. Rollback applicatif
- App est stateless (état = base uniquement) : re-tagger l'image précédente et
  redéployer : `docker compose up -d` / orchestrateur. Aucune migration à
  rétrograder tant qu'on ne rewrote pas Flyway (jamais : les migrations sont
  immuables ; rollback = données compatibles descendante sinon corrompues).
- En cas de schéma incompatible : restaurer le dump pré-migration (§6) puis
  redéployer l'image correspondante au schéma restauré.

## 8. Conduite d'un incident (5xx)
1. Relever le `traceId` renvoyé au client avec la réponse d'erreur.
2. Rechercher ce `traceId` dans les logs JSON du pod/conteneur · période ± 2 min.
3. Corriger → relancer la suite de tests : `.\mvnw.cmd test` (Windows) ou
   `./mvnw test`.
4. Redéployer ; vérifier `/actuator/health` puis un flux d'auth nominal.

## 9. Checklist avant mise en production
- [ ] `.env` rempli (deux secrets + CORS) exclu du dépôt.
- [ ] `SUP2I_JWT_AUDIENCE` cohérente avec les clients (défaut = issuer).
- [ ] Reverse proxy TLS en place ; `forward-headers-strategy=framework` actif.
- [ ] Sauvegarde automatisée testée.
- [ ] `SUP2I_RATE_LIMIT_ENABLED=true` (réglages par défaut raisonnables pour le
      pilote ; les limites s'ajustent dans `application.yaml`).
- [ ] Ce runbook et SECURITY.md à jour des changements de config.