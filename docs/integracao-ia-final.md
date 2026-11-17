# AgroConecta — integração final de IA

Execução concluída em 14/11/2026 23:41 -03. Escopo: backend GS-FINAL e documentação do mobile. Nenhuma alteração visual no aplicativo.

## Causa e solução

O JobGuidanceController obtinha um RiskJob pelo serviço de leitura; a transação terminava antes de construir o fallback. Com open-in-view=false, a entidade estava destacada. O controller chamava apenas fallbackGuidance na entidade em memória e a devolvia, sem uma gravação transacional. Assim, o POST continha o fallback e o GET seguinte continuava retornando guidance=null.

Foi criada RiskJobService.saveFallbackGuidance, uma operação @Transactional que identifica o produtor ativo, bloqueia a linha com PESSIMISTIC_WRITE usando id e ownerId e grava o JSONB na coluna guidance existente. A operação rejeita job sem resultado e nunca usa a entidade destacada do controller como destino de persistência. Não houve migration.

saveGuidance também usa o lock por proprietário. As duas operações copiam riskLevel do resultado Java sob lock. Guidance válida existente (fallback=false) tem prioridade: uma falha concorrente tardia não a substitui. O fallback mantém fallback=true e COMPLETED_WITHOUT_AI, permitindo uma nova tentativa manual. Sucesso muda somente guidance/status para COMPLETED e é reutilizado sem nova chamada ao modelo. Result, measurements, thresholds, rainfall e o estado/auditoria do alerta são preservados.

O controller diferencia PROVIDER_UNAVAILABLE, CALL_TIMEOUT (inclui timeout de transporte), NO_RELEVANT_SOURCES, INVALID_STRUCTURED_OUTPUT e INSUFFICIENT_CONTEXT. Conversão JSON inválida usa exceção sanitizada, sem resposta bruta/causa sensível. A orientação válida inclui fontes recuperadas, modelo, versão do prompt e data; ambos os resultados incluem avisos de demonstração/simulação quando aplicáveis. IDs de perfil e descrições livres do diário não são enviados ao modelo.

## Configuração e retentativas

| Configuração | Valor aplicado |
| --- | --- |
| Solução | AgroConecta (documentação; apresentação mobile preservada) |
| Chat | gemini-3.5-flash-lite |
| Embedding | gemini-embedding-001 |
| Dimensão | 768 |
| AI_CALL_TIMEOUT | PT90S |
| AI_PROCESSING_TIMEOUT | PT5M |
| Temperatura | 0.2 |
| Timeout mobile | 180000 ms, exclusivo de guidance |
| Spring AI 2.0.2 | spring.ai.retry.max-attempts=0 (o código instalado interpreta como maxRetries) |
| SDK Google 1.65.0 | HttpRetryOptions.attempts=1; mesmo cliente para chat e embedding |
| Thinking | Sem thinking-budget legado; o modelo usa seu padrão minimal |

processingTimeout deve superar callTimeout; callTimeout é positivo e inferior a 180 s. O cliente Google também recebe timeout de transporte. A chave é exigida somente quando o provedor real está habilitado; APP_AI_ENABLED=false e o provedor local dos testes iniciam sem chave. Modelos e timeouts podem ser alterados pelo ambiente. Chave carregada somente ao iniciar o processo Java, a partir do arquivo privado indicado pelo usuário; não foi copiada ao Expo nem a arquivos do projeto. Os .env reais, os manifestos e os lockfiles mantêm os hashes iniciais.

A investigação encontrou retries adicionais independentes: o SDK Google tenta até cinco vezes por padrão, e Spring AI 2.0.2 usa o valor max-attempts como quantidade de retries. Ambos foram limitados. O mobile já usa retry=false e permaneceu sem mudanças de código. O fluxo legado agronomic_analyses não foi expandido; sua execução automática foi mantida desabilitada na instância real.

A configuração antiga thinking-budget=0 foi removida por incompatibilidade com a família Gemini 3. A configuração final passou nos testes direcionados e, em seguida, na suíte completa e no empacotamento final. Referências: [Spring AI 2.0.2 Google GenAI](https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html), [Gemini 3.5 Flash-Lite](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite), [thinking](https://ai.google.dev/gemini-api/docs/thinking).

## Testes locais e empacotamento

| Grupo | Total | Aprovados | Ignorados | Falhos/erros |
| --- | --- | --- | --- | --- |
| Backend: suíte completa | 178 | 177 | 1 | 0 |
| Mobile: testes existentes | 105 | 105 | 0 | 0 |
| TypeScript mobile | 1 | 1 | 0 | 0 |

A suíte backend foi executada com Java 21.0.13, GOOGLE_API_KEY e RUN_GEMINI_MANUAL_TEST removidos do ambiente e APP_AI_ENABLED=false. Testes que habilitam o provedor local o fazem explicitamente. Não houve dependência de chamada Google real no build. O teste externo GeminiManualTests permaneceu ignorado por projeto.

Foi necessário interromper a primeira execução de clean package porque a suíte completa encontrou que a configuração aninhada de clientes Google também era registrada com o provedor fake. A condição foi corrigida e recebeu regressão. Na segunda execução completa, os contratos funcionais passaram, mas um teste de configuração ficou desligado por precedência da variável global APP_AI_ENABLED=false; o cenário foi corrigido para definir app.ai.enabled explicitamente. A remoção do thinking-budget legado foi incluída e os sete testes de configuração passaram também no empacotamento incremental. Somente então a execução completa final de clean package concluiu com BUILD SUCCESS. As repetições decorreram dessas falhas comprovadas; nenhuma chamada Google foi feita durante essas etapas. Os logs dessas etapas não foram usados para ocultar falhas intermediárias; as contagens acima refletem o estado final.

| Classe | Total | Falhos | Erros | Ignorados |
| --- | --- | --- | --- | --- |
| br.com.fiap.citizentech.CitizentechApiApplicationTests | 1 | 0 | 0 | 0 |
| br.com.fiap.citizentech.analysis.AgronomicAnalysisIntegrationTests | 10 | 0 | 0 | 0 |
| br.com.fiap.citizentech.analysis.GeminiManualTests | 1 | 0 | 0 | 1 |
| br.com.fiap.citizentech.analysis.RagAndPromptTests | 6 | 0 | 0 | 0 |
| br.com.fiap.citizentech.analysis.config.AiConfigurationTests | 7 | 0 | 0 | 0 |
| br.com.fiap.citizentech.auth.AuthIntegrationTests | 23 | 0 | 0 | 0 |
| br.com.fiap.citizentech.auth.JwtPropertiesTests | 4 | 0 | 0 | 0 |
| br.com.fiap.citizentech.croparea.CropAreaIntegrationTests | 25 | 0 | 0 | 0 |
| br.com.fiap.citizentech.farm.FarmIntegrationTests | 19 | 0 | 0 | 0 |
| br.com.fiap.citizentech.sensor.SensorIntegrationTests | 22 | 0 | 0 | 0 |
| br.com.fiap.citizentech.sensorreading.OutboxUnitTests | 3 | 0 | 0 | 0 |
| br.com.fiap.citizentech.sensorreading.ReadingRabbitIntegrationTests | 3 | 0 | 0 | 0 |
| br.com.fiap.citizentech.sensorreading.SensorReadingIntegrationTests | 24 | 0 | 0 | 0 |
| br.com.fiap.citizentech.water.JobGuidanceTests | 11 | 0 | 0 | 0 |
| br.com.fiap.citizentech.water.ProfileValidationTests | 1 | 0 | 0 | 0 |
| br.com.fiap.citizentech.water.RainfallCalculatorTests | 1 | 0 | 0 | 0 |
| br.com.fiap.citizentech.water.RiskJobGuidanceIntegrationTests | 6 | 0 | 0 | 0 |
| br.com.fiap.citizentech.water.SensorHealthTests | 2 | 0 | 0 | 0 |
| br.com.fiap.citizentech.water.WaterIndicatorsTests | 4 | 0 | 0 | 0 |
| br.com.fiap.citizentech.water.WaterRiskCalculatorTests | 4 | 0 | 0 | 0 |
| br.com.fiap.citizentech.water.WaterWorkflowIntegrationTests | 1 | 0 | 0 | 0 |

Testes novos/ajustados relevantes:

| Cobertura | Evidência automatizada |
| --- | --- |
| Persistência de fallback e GET idêntico | RiskJobGuidanceIntegrationTests com PostgreSQL real descartável e MockMvc, sem transação compartilhada entre POST/GET |
| Risco/snapshots/alerta imutáveis | Comparação antes/depois e gravação com riskLevel divergente para comprovar cópia autoritativa |
| Retentativa manual e sucesso persistido/reutilizado | Fallback → sucesso; POST seguinte não incrementa chamadas do cliente |
| Concorrência | Duas transações com lock, sucesso e fallback simultâneos; sucesso não pode ser rebaixado |
| Isolamento | Outro produtor recebe 404 nos endpoints e não pode salvar guidance pelo serviço |
| Privacidade do contexto | Snapshot e atividades de outro produtor não aparecem no contexto capturado |
| Motivos de falha | Provider, timeout/deadline/transporte, fontes insuficientes, JSON inválido, validação inválida e dados insuficientes |
| RAG/prompt | Passagens maliciosas ficam em dados não confiáveis, fora do sistema; modelo não altera risco; URLs inseguras são excluídas |
| Configuração | Sem chave quando desligado/fake; chave obrigatória para Gemini; binding Google GenAI e dimensão 768; timeouts válidos |
| Sem retries | RetryTemplate executa uma vez; HttpRetryOptions limita a uma tentativa e cliente é compartilhado com embedding |
| RAG/PGVector | Carregamento idempotente, quatro documentos e similaridade com embeddings locais de teste em banco descartável |

A proteção de prompt foi comprovada na composição das mensagens e na autoridade dos campos de saída; isso não representa garantia absoluta sobre o conteúdo textual de qualquer modelo. O risco oficial é protegido no serviço independentemente do texto retornado.

Lint: não há configuração funcional. O comando Expo iniciou instalação automática mesmo em CI; foi interrompido, o manifesto foi restaurado e não houve instalação concluída. Package.json/package-lock recuperaram os hashes iniciais, sem ESLint/configuração criada, sem pacotes extras ou versões divergentes em node_modules. TypeScript e os 105 testes do mobile passaram.

## RAG e dados permanentes

Banco real: agronomy_vector_store com vector(768), índice HNSW por cosseno, quatro linhas/fontes. Os quatro checksums correspondem exatamente aos arquivos, gemini-embedding-001 e dimensão 768. IDs determinísticos calculados novamente e conferidos. Uma consulta SQL de similaridade real sobre um vetor existente recuperou as quatro fontes; essa verificação não chamou Google. O carregador da instância reiniciada não reingeriu documentos.

Os snapshots antes/depois incluem IDs/checksums/hash dos vetores e timestamps de rag_ingestion. Resultado das comparações:

```json
{
  "farms": true,
  "crop_areas": true,
  "sensors": true,
  "sensor_readings": true,
  "field_activities": true,
  "agronomic_profiles": true,
  "risk_jobs_snapshots_and_alerts": true,
  "vectors_ids_checksums_and_dimensions": true,
  "ingestion_timestamps_unchanged": true,
  "existing_containers_not_restarted": true
}
```

Dados preservados: propriedade #9; talhões #8–12; sensores #10–18; leituras #11–23; atividades #8–11; perfis #5–10; RiskJobs #9–12 e alertas #10 ACKNOWLEDGED, #11 OPEN, #12 RESOLVED. Somente guidance e, em caso de sucesso, status do job #11 foram autorizados a mudar. Nenhum DELETE, recriação de job ou escrita direta no banco real foi realizado.

## Validação real controlada

A instância antiga da API foi substituída por uma única JVM com as variáveis definitivas. Como ela tinha hooks de parada do Compose, seu processo foi encerrado sem executar esses hooks para preservar PostgreSQL e RabbitMQ. A nova instância usa spring.docker.compose.enabled=false e legacy-ai-enabled=false. Nenhum container existente foi reiniciado. O harness de diagnóstico é temporário e inicia a própria API; a chave permanece somente no ambiente dessa JVM.

Diagnóstica curta, JSON estruturado, chamada única, sem retry:

```json
{
  "model": "gemini-3.5-flash-lite",
  "chatRequests": 1,
  "healthHttp": 200,
  "http": 200,
  "modelVersion": "gemini-3.5-flash-lite",
  "structuredValid": true
}
```

Após a diagnóstica aprovada, foi feito um POST sem body em /api/risk-jobs/11/guidance, seguido de GET no mesmo ID. Resultado:

```json
{
  "postHttp": 200,
  "getHttp": 200,
  "persistedExactly": true,
  "unchanged": {
    "result": true,
    "measurements": true,
    "thresholds": true,
    "rainfall": true,
    "alertStatus": true,
    "alert": true,
    "profileId": true,
    "fieldCapacity": true,
    "wiltingPoint": true
  },
  "fallback": false,
  "reason": null,
  "riskLevel": "CRITICAL",
  "alertStatus": "OPEN",
  "status": "COMPLETED"
}
```

O JSON de guidance do GET é igual ao do POST. Não houve nova solicitação para forçar sucesso nem criação de RiskJob. O campo result.riskLevel continuou CRITICAL e o alerta continuou OPEN. O reuso de orientação válida foi comprovado nos testes locais, sem gastar outra chamada real.

Orçamento utilizado pelo fluxo executado: duas gerações de chat (diagnóstica + guidance), um embedding de consulta e nenhuma reingestão. Não houve repetição de POST ou da diagnóstica. As camadas de retry foram desabilitadas antes da execução. Cotas da camada gratuita variam por projeto/modelo, podem retornar 429 e não justificam repetição automática: [limites Google](https://ai.google.dev/gemini-api/docs/rate-limits).

## JSON final sanitizado do RiskJob #11

```json
{
  "measurements": [
    {
      "id": 21,
      "sensorId": 16,
      "measuredAt": "2026-11-15T01:13:39.329557Z",
      "soilMoisture": 19.5,
      "airTemperature": null,
      "airHumidity": null,
      "simulated": false
    },
    {
      "id": 22,
      "sensorId": 17,
      "measuredAt": "2026-11-15T01:12:39.382305Z",
      "soilMoisture": null,
      "airTemperature": 26.8,
      "airHumidity": 63.0,
      "simulated": false
    },
    {
      "id": 20,
      "sensorId": 16,
      "measuredAt": "2026-11-14T23:18:39.289556Z",
      "soilMoisture": 19.6,
      "airTemperature": null,
      "airHumidity": null,
      "simulated": false
    }
  ],
  "acknowledgedAt": null,
  "acknowledgedBy": null,
  "alert": {
    "type": "LOW_SOIL_WATER",
    "title": "Disponibilidade hídrica baixa",
    "justification": "Disponibilidade hídrica baixa. Regras acionadas: LOW_WATER_AVAILABILITY",
    "level": "CRITICAL",
    "rules": [
      "LOW_WATER_AVAILABILITY"
    ],
    "demo": true,
    "status": "OPEN",
    "createdAt": "2026-11-15T01:21:10.988581Z",
    "acknowledgedAt": null,
    "acknowledgedBy": null,
    "resolvedAt": null,
    "resolvedBy": null
  },
  "alertStatus": "OPEN",
  "attempts": 1,
  "completedAt": "2026-11-15T01:21:10.988581Z",
  "createdAt": "2026-11-15T01:21:07.249555Z",
  "cropAreaId": 10,
  "cropType": "Milho",
  "demo": true,
  "errorMessage": null,
  "fieldCapacity": 45.0,
  "guidance": {
    "model": "gemini-3.5-flash-lite",
    "signals": [
      "Umidade do solo medida em 19,5%.",
      "Temperatura registrada de 26,8 °C e umidade do ar de 63,0% em leituras anteriores.",
      "Estádio da cultura não informado."
    ],
    "sources": [
      {
        "url": "https://www.embrapa.br/web/agencia-de-informacao-tecnologica/cultivos/milho/pre-producao/caracteristicas-da-especie-e-relacoes-com-o-ambiente/caracteristicas-da-planta",
        "title": "Desenvolvimento do milho e disponibilidade de água",
        "version": "1",
        "sourceId": "embrapa-milho-desenvolvimento",
        "institution": "Embrapa"
      },
      {
        "url": "https://www.embrapa.br/en/web/agencia-de-informacao-tecnologica/cultivos/milho/producao/irrigacao/manejo",
        "title": "Manejo de irrigação do milho",
        "version": "1",
        "sourceId": "embrapa-irrigacao-milho",
        "institution": "Embrapa"
      },
      {
        "url": "https://www.infoteca.cnptia.embrapa.br/handle/doc/974063",
        "title": "Umidade do solo na agricultura familiar",
        "version": "1",
        "sourceId": "embrapa-umidade-familiar",
        "institution": "Embrapa"
      },
      {
        "url": "https://www.fao.org/land-water/water/agricultural-water-management/irrigation/en",
        "title": "Gestão de água e irrigação",
        "version": "1",
        "sourceId": "fao-irrigation-management",
        "institution": "FAO"
      }
    ],
    "summary": "O sistema classificou o risco como CRÍTICO devido à baixa disponibilidade hídrica indicada. Faltam dados essenciais como o estádio da cultura, localidade, área e calibração local do solo, tornando o contexto RAG e as leituras parciais para uma recomendação definitiva de irrigação.",
    "fallback": false,
    "warnings": [
      "Dados ausentes ou desatualizados: local, tamanho da área, bateria do sensor e estágio da cultura não foram informados.",
      "Insuficiência do contexto RAG: a literatura ressalta que uma leitura isolada não substitui a avaliação das condições locais e a calibração do solo.",
      "Percentuais de umidade não devem ser tratados como tensão hídrica ou água disponível sem calibração local.",
      "As leituras são reais e não simuladas.",
      "A análise utiliza parâmetros ou regras demonstrativos",
      "A orientação não substitui avaliação agronômica"
    ],
    "riskLevel": "CRITICAL",
    "generatedAt": "2026-11-15T02:40:56.034875851Z",
    "promptVersion": "agronomic-v1",
    "recommendation": "O risco foi classificado como CRÍTICO conforme o resultado fixo do sistema (regras de disponibilidade hídrica baixa). A recomendação apoia a decisão e não substitui um engenheiro agrônomo. Uma leitura percentual isolada ou o uso de umidade do solo sem calibração local não determinam com precisão a lâmina de irrigação ou a tensão hídrica. Conforme a Embrapa, a programação da irrigação depende das características do solo, da profundidade das raízes e das condições de cultivo, sendo fundamental confirmar a calibração e a representatividade das medições. Além disso, a falta de informação sobre o estádio de desenvolvimento do milho impede avaliar com segurança os impactos na formação dos grãos. Realize avaliações de campo (como tato e facilidade de penetração do solo) e consulte um engenheiro agrônomo para planejar o manejo adequado.",
    "recommendedActions": [
      "Registrar o estádio de desenvolvimento da cultura em campo.",
      "Confirmar a calibração e a representatividade das medições de umidade do solo.",
      "Avaliar a umidade do solo por métodos qualitativos locais, como tato e facilidade de penetração.",
      "Consultar um engenheiro agrônomo para auxiliar na interpretação dos dados e na decisão de manejo."
    ]
  },
  "id": 11,
  "latestRainAt": null,
  "ownerId": 5,
  "profileId": 8,
  "rainfall": {
    "last7Days": {
      "millimeters": 0.0,
      "complete": false,
      "limitation": "Cobertura parcial da janela"
    },
    "last24Hours": {
      "millimeters": 0.0,
      "complete": false,
      "limitation": "Cobertura parcial da janela"
    }
  },
  "recoveryCount": 0,
  "resolvedAt": null,
  "resolvedBy": null,
  "result": {
    "riskLevel": "CRITICAL",
    "waterAvailabilityPercent": 15.0,
    "rawAvailabilityPercent": 15.0,
    "vpdKpa": 1.3037762493396732,
    "moistureChangePerHour": -0.052173610580204095,
    "rulesTriggered": [
      "LOW_WATER_AVAILABILITY"
    ],
    "limitations": [],
    "rulesVersion": "demo-v1",
    "demo": true,
    "dataQuality": "COMPLETE",
    "moistureTrend": "DRYING_SLOWLY"
  },
  "rulesVersion": "demo-v1",
  "secondsSinceRain": null,
  "startedAt": "2026-11-15T01:21:10.988329Z",
  "status": "COMPLETED",
  "thresholds": {
    "criticalAvailability": 20.0,
    "attentionAvailability": 40.0,
    "highVpd": 2.0,
    "rapidDryingPerHour": -3.0,
    "staleAfter": "PT24H",
    "version": "demo-v1"
  },
  "wiltingPoint": 15.0
}
```

## Arquivos modificados nesta execução

### GS-FINAL

- `src/main/java/br/com/fiap/citizentech/analysis/ai/InvalidStructuredOutputException.java`
- `src/test/java/br/com/fiap/citizentech/analysis/config/AiConfigurationTests.java`
- `src/test/java/br/com/fiap/citizentech/water/RiskJobGuidanceIntegrationTests.java`
- `.env.example`
- `README.md`
- `src/main/java/br/com/fiap/citizentech/analysis/ai/GeminiAgronomicAiClient.java`
- `src/main/java/br/com/fiap/citizentech/analysis/config/AiProperties.java`
- `src/main/java/br/com/fiap/citizentech/analysis/config/GeminiConfig.java`
- `src/main/java/br/com/fiap/citizentech/analysis/rag/PgVectorAgronomyKnowledge.java`
- `src/main/java/br/com/fiap/citizentech/water/JobGuidanceController.java`
- `src/main/java/br/com/fiap/citizentech/water/RiskJobRepository.java`
- `src/main/java/br/com/fiap/citizentech/water/RiskJobService.java`
- `src/main/resources/application.yml`
- `src/test/java/br/com/fiap/citizentech/analysis/RagAndPromptTests.java`
- `src/test/java/br/com/fiap/citizentech/water/JobGuidanceTests.java`

### citizentech-mobile

- `README.md`
- `docs/integracao-ia-final.md` — este relatório.

As alterações anteriores do repositório mobile e os relatórios das etapas 11/12 foram preservados. Sem alterações visuais no mobile, mudança de package/artifactId/banco/exchanges/filas, migration nova, dependências novas instaladas, build nativo ou commit.
