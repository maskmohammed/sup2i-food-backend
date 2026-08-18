-- SUP2I FOOD — Flyway migration generated from FINAL V3
-- PostgreSQL 17+

-- 24. COMMENTAIRES / INVARIANTS MÉTIER NON REMPLAÇABLES PAR DE SIMPLES CHECK
-- ============================================================================

COMMENT ON TABLE stock_balances IS
'Cache opérationnel. Toute mutation critique doit être transactionnelle, verrouillée et accompagnée d un inventory_movement. Stock physique négatif interdit.';

COMMENT ON TABLE stock_reservations IS
'Règle MVP: réservation lors de AWAITING_PAYMENT; emballé consommé au paiement; ingrédients des produits préparés consommés au démarrage PREPARING.';

COMMENT ON TABLE meal_usages IS
'Anti-double distribution: création atomique. Index unique partiel = maximum un MealUsage VALID par étudiant/date/type. Une correction passe par REVERSED, jamais DELETE.';

COMMENT ON TABLE orders IS
'Le backend est source de vérité. Toutes les transitions passent par le service métier et sont journalisées. Une commande non payée ne part jamais en préparation.';

COMMENT ON TABLE payments IS
'Paiements idempotents. Le QR ne prouve jamais un paiement. MVP CASH/CARD_TPE; ONLINE et WALLET sont prévus sans changer Order.';

COMMENT ON TABLE qr_credentials IS
'Credential opaque/hashed. Le champ medium permet QR aujourd hui et NFC futur. Aucune donnée personnelle ou prix ne doit être accepté depuis le credential comme source de vérité.';

COMMENT ON TABLE integration_connectors IS
'Extension pour Cactus/PHP, ERP étudiants, TPE, paiement en ligne et imports. Les détails dépendant de SUP2I restent dans OPEN_DECISIONS.md.';

COMMENT ON TABLE report_snapshots IS
'Snapshots optionnels pour rapports lourds/audit analytique. Le CA réel reste calculé depuis les paiements/commandes, distinct des estimations d enquête hors campus.';

COMMENT ON TABLE shopping_carts IS
'Le panier serveur permet synchronisation multi-device et mesure future des paniers abandonnés. Les prix du panier ne sont jamais source de vérité pour la commande.';

COMMENT ON TABLE wallet_accounts IS
'Roadmap V2. Toute activation réelle du wallet nécessitera validation comptable, sécurité et procédures SUP2I; les transactions restent en ledger immuable.';
