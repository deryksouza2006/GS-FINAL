# Agro Conecta — estado da implementação

Esta revisão acrescenta atividades agrícolas, perfil agronômico versionado, arquivamento,
indicadores hídricos determinísticos, jobs RabbitMQ/outbox, alertas vinculados aos jobs,
orientação RAG solicitada por job, simulação identificada, chuva por intervalo,
observação pós-irrigação, painel inicial, saúde de comunicação de sensores e manutenção técnica.

Swagger: `/swagger-ui.html`. OpenAPI: `/v3/api-docs`.
JWT emitido inclui audiência `agroconecta`; clientes devem renovar tokens antigos.
Produção: ativar perfil `prod` e fornecer `JWT_SECRET`. CORS usa `CORS_ALLOWED_ORIGINS`.
Simulação: habilitar `app.demo.enabled=true` apenas no ambiente de demonstração.

## Contratos acrescentados

- `/api/crop-areas/{id}/activities`: CRUD, filtros `type`, `from`, `to`, paginação.
- `/api/crop-areas/{id}/agronomic-profile`: GET/PUT; cada PUT preserva a versão anterior.
- `PATCH /api/crop-areas/{id}/archive`: corpo `{"archived":true}`.
- `POST /api/crop-areas/{id}/risk-jobs`: 202, snapshot das entradas, publicação por outbox.
- `GET /api/risk-jobs/{id}` e `GET /api/crop-areas/{id}/risk-jobs`.
- `POST /api/risk-jobs/{id}/guidance`: consulta IA com RAG, preserva classificação determinística.
- `GET /api/alerts`; PATCH `/api/alerts/{jobId}/acknowledge` e `/resolve`.
- `GET /api/dashboard` e `/api/sensors/{id}/health`.
- `POST /api/crop-areas/{id}/sensor-readings/simulate`: `{"scenario":"NORMAL"}`.
- `GET /api/crop-areas/{id}/activities/{activityId}/irrigation-observation`.
- `PUT /api/farms/{farmId}/technicians/{technicianId}`: associação feita pelo proprietário.
- `PATCH /api/technician/sensors/{id}/maintenance`: técnico associado.

## Dispositivos e técnicos

O produtor gera ou rotaciona a chave pelo `POST /api/sensors/{id}/device-key`.
O sensor envia `POST /api/device-readings` com `X-Device-Key` e o corpo de leitura,
omitindo `source`. O servidor grava `DEVICE` e armazena somente o hash da chave.
Use HTTPS fora do ambiente local.

O operador do banco provisiona contas registradas com `scripts/provision-technician.sql`.
Depois, o técnico renova seu login e o produtor associa o ID dele à propriedade.
O técnico associado pode instalar sensores em `POST /api/technician/sensors` e registrar
calibração e manutenção na rota indicada acima.

## Análise, alertas e painel

O job registra leituras, perfil e limiares. A outbox registra a confirmação como `QUEUED`.
O consumidor usa bloqueio no banco e grava o resultado atomicamente. A entrega não confirmada
é retomada pelo broker. Jobs publicados há mais de 15 minutos podem ser republicados até três
vezes. Esgotamento de publicação ou consumo produz `FAILED` com mensagem sanitizada.
A orientação é solicitada separadamente e usa o resultado, as leituras e as atividades recentes.

O fluxo legado de IA automática permanece disponível mediante
`app.readings.legacy-ai-enabled=true`. O padrão é desativado.
Configure os limiares em `app.risk` e altere `rules-version` junto com eles.
O padrão `demo-v1` usa disponibilidade crítica de 20%, atenção de 40%, VPD de 2 kPa,
secagem de -3 pontos percentuais por hora e validade das leituras de 24 horas.
Os acumulados de chuva indicam cobertura parcial ou indisponível quando aplicável.

O alerta vinculado ao job tem tipo, justificativa, regras, nível, origem demonstrativa e
auditoria de reconhecimento/resolução. Consulte também `GET /api/alerts/{id}` e
`GET /api/crop-areas/{id}/alerts`. O painel inclui propriedades, resumo dos cultivos ativos,
atividades, alertas em aberto, jobs pendentes ou com falha e sensores que precisam de atenção.

## Demonstração

Importe [a coleção Postman](../postman/AgroConecta.postman_collection.json) e execute-a em uma
instância de teste. A coleção cria um produtor, propriedade, cultivo, perfil, estação, leitura
autenticada e job. Acompanhe o job no Swagger até `COMPLETED_WITHOUT_AI`. Com Gemini e RAG
configurados, solicite a orientação e examine as fontes. Use o cenário `CRITICAL` do simulador
em um cultivo de demonstração para validar a identificação `SIMULATED`.

O teste integrado usa PostgreSQL e RabbitMQ reais em contêineres. O acesso ao Gemini real
depende de chave do provedor. O cliente Mobile fica a cargo da integração posterior.

Perfis e medições devem usar a mesma base volumétrica calibrada. Um valor acima da capacidade
de campo não comprova saturação. Regras demo não constituem recomendação agronômica validada.
