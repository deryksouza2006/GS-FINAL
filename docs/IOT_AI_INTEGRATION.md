# Integração com agro-conecta-iot-ai

Simulador, avaliação e evidências: https://github.com/LucasViana130/agro-conecta-iot-ai

`APP_DEMO_ENABLED=true` habilita `POST /api/simulated-readings`. JWT de produtor,
sensor pertencente ao proprietário, `source=SIMULATED` e `measuredAt` obrigatórios.
Payload usa CreateReadingRequest; validações, persistência e outbox são compartilhados.
Rotas manual/device continuam rejeitando SIMULATED. A origem é controlada pelo servidor.

Criar análise com POST /api/crop-areas/{id}/risk-jobs; consultar GET /api/risk-jobs/{id}.
POST /api/risk-jobs/{id}/guidance preserva o contrato RiskJob e acrescenta em guidance
signals, riskLevel e fallback. Sem dados, fontes ou provedor, retorna 200 com fallback=true,
reason, sources vazias e avisos. Não persiste contingência nem altera status/risco;
é possível tentar novamente depois. Geração válida continua persistida e cacheada.

Saúde mantém communicationStatus e administrativeStatus e acrescenta calculatedHealth:
HEALTHY, STALE (24h), OFFLINE (48h), POSSIBLE_FAULT ou INSUFFICIENT_DATA.
Sequência constante ou saltos abruptos sinalizam suspeita por regras demonstrativas,
nunca diagnóstico. Consulte SensorHealthCalculator para os limites explícitos.

400 validação, 401 JWT ausente, 403 papel inválido, 404 ausente/outro proprietário,
409 estado incompatível. Swagger documenta a nova rota. Mobile pode manter as rotas
legadas; deve distinguir fallback e identificar dados simulados.

Testes comuns não chamam Gemini. JobGuidanceTests verifica contexto insuficiente,
ausência de fontes, timeout, risco preservado, atribuição de fontes e negação do dono.
No repositório IoT, scripts/verify_java.py verifica seis cenários contra API, PostgreSQL
e RabbitMQ reais locais com AI desabilitada. Avaliação real de Gemini exige chave,
acervo indexado e revisão humana; mocks não comprovam qualidade agronômica.
