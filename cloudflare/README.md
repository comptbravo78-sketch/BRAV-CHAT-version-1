# Déploiement Cloudflare 100% — BRAV Chat (v4.0.0)

Architecture centralisée sur Cloudflare :
- **Worker** : `bold-darkness-7957.kolopay-ci.workers.dev`
- **D1** : `brav_chat` (`005a0119-2bb4-40ce-a50c-b3c3a461bc19`)
- **R2** : `bravchat` (WEUR)
- **KV** : Cache IA, disjoncteur modèles, quotas & règles
- **Queues** : `brav-tasks` (métriques & notifications asynchrones)
- **Durable Objects** : `UserHub`, `CallRoom`
- **Workers AI + AI Gateway** : `brav-chat-gateway`

## Commandes Cloud Shell (depuis `/cloudflare`)

1. Créer l'espace KV et reporter l'ID retourné dans `wrangler.toml` (`REPLACE_WITH_KV_NAMESPACE_ID`) :
   ```bash
   npx wrangler kv namespace create BRAV_KV
   ```

2. Créer la file Cloudflare Queue :
   ```bash
   npx wrangler queues create brav-tasks
   ```

3. Appliquer le schéma D1 complet :
   ```bash
   npx wrangler d1 execute brav_chat --remote --file=schema.sql
   ```

4. Créer l'AI Gateway dans le tableau de bord Cloudflare (`AI > AI Gateway`) nommé `brav-chat-gateway` (ou ajustez `AI_GATEWAY_ID` dans `wrangler.toml`).

5. Enregistrer les secrets TURN / SFU (jamais stockés dans l'application Android) :
   ```bash
   npx wrangler secret put SFU_API_TOKEN
   npx wrangler secret put TURN_TOKEN_ID
   npx wrangler secret put TURN_API_TOKEN
   ```

6. Déployer le Worker :
   ```bash
   npx wrangler deploy
   ```

7. Vérifier les services :
   ```bash
   curl https://bold-darkness-7957.kolopay-ci.workers.dev/v1/config
   ```
