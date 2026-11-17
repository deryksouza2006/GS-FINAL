# AgroConecta — auditoria final de código

Concluída em 15/11/2026 00:11 -03.

## Resumo executivo

Foram inventariados e verificados estaticamente **426 arquivos texto** do estado inicial: 188 no backend e 238 no mobile. Esse conjunto inclui **374 arquivos de código/teste** (148 Java e 226 TS/TSX/MJS), além de configurações, documentação, migrations e ferramentas. A leitura aprofundada foi dirigida pelos riscos de autenticação, isolamento, transações, concorrência, contratos e IA. Seis imagens binárias foram apenas inventariadas e ficaram fora dessa contagem. Não se afirma execução de todos os fluxos em dispositivo.

Foram corrigidos **6 problemas: 0 críticos, 0 altos, 2 médios e 4 baixos**. Também foram removidos **91 comentários** de código/configuração conforme solicitado. Backend: BUILD SUCCESS, 182 testes, 181 aprovados, 1 ignorado, nenhuma falha/erro. Mobile: 105 testes aprovados, TypeScript aprovado e dependências compatíveis com Expo 58.0.25.

Não houve chamada Gemini/embedding, uso da conta de auditoria, solicitação HTTP à API real, escrita no banco real, reinício da API ou dos serviços permanentes, instalação de dependências, build nativo ou commit. A única mudança de apresentação foi o nome público AgroConecta, expressamente autorizado; layout, estilos e navegação permaneceram iguais.

## Escopo e método

A auditoria cobre backend GS-FINAL e mobile citizentech-mobile. Foram consultados AGENTS.md e as instruções fornecidas. O pedido específico prevaleceu sobre as recomendações genéricas de migrar navegação e executar lint: React Navigation foi preservado, nenhum lint foi executado ou configurado. Não se executou uma suíte completa para baseline; foram usados os relatórios anteriores. Testes direcionados antecederam a única suíte completa final.

O inventário considerou arquivos próprios versionados e não ignorados, incluindo os arquivos novos das etapas anteriores. Varreduras incluíram comentários, TODO/FIXME, logs temporários, padrões de segredos/JWT, referências/imports, métodos privados, propriedades, DTOs e endpoints. A análise de referências Java levou em conta registros por Spring; ausência de import não foi usada como prova de código morto. Testes e mocks permaneceram restritos ao ambiente de teste.

Para a alteração do nome centralizado, foram consultadas as páginas oficiais do SDK correspondente: [Expo SDK 58](https://docs.expo.dev/versions/v58.0.0/), [app config/name](https://docs.expo.dev/versions/v58.0.0/config/app/) e [Constants.expoConfig](https://docs.expo.dev/versions/v58.0.0/sdk/constants/), além do índice llms.txt. Nenhum pacote foi adicionado.

## Estado inicial dos repositórios

Ambos já continham alterações não commitadas. Foram registrados git status --short --untracked-files=all, HEAD e hashes por arquivo antes da primeira edição. As listas abaixo refletem o início desta etapa; não são a lista de mudanças causadas por esta auditoria.

### GS-FINAL

```
 M .env.example
 M README.md
 M src/main/java/br/com/fiap/citizentech/analysis/ai/GeminiAgronomicAiClient.java
 M src/main/java/br/com/fiap/citizentech/analysis/config/AiProperties.java
 M src/main/java/br/com/fiap/citizentech/analysis/config/GeminiConfig.java
 M src/main/java/br/com/fiap/citizentech/analysis/rag/PgVectorAgronomyKnowledge.java
 M src/main/java/br/com/fiap/citizentech/water/JobGuidanceController.java
 M src/main/java/br/com/fiap/citizentech/water/RiskJobRepository.java
 M src/main/java/br/com/fiap/citizentech/water/RiskJobService.java
 M src/main/resources/application.yml
 M src/test/java/br/com/fiap/citizentech/analysis/RagAndPromptTests.java
 M src/test/java/br/com/fiap/citizentech/water/JobGuidanceTests.java
?? src/main/java/br/com/fiap/citizentech/analysis/ai/InvalidStructuredOutputException.java
?? src/test/java/br/com/fiap/citizentech/analysis/config/AiConfigurationTests.java
?? src/test/java/br/com/fiap/citizentech/water/RiskJobGuidanceIntegrationTests.java
```

### citizentech-mobile

```
 M .env.example
 M README.md
 M src/components/cropAreas/CropAreaStatusBadge.tsx
 M src/hooks/activities/useActivityCache.ts
 M src/hooks/cropAreas/useCropAreaCache.ts
 M src/hooks/farms/useCreateFarm.ts
 M src/hooks/farms/useDeleteFarm.ts
 M src/hooks/farms/useUpdateFarm.ts
 M src/hooks/readings/useReadingCache.ts
 M src/hooks/sensors/useSensorCache.ts
 M src/screens/readings/ReadingFormScreen.tsx
 M src/screens/settings/AboutScreen.tsx
 M src/services/api.ts
 M src/utils/activityFormatting.ts
 M src/utils/activityValidation.ts
 M src/utils/cropAreaFormatting.ts
 M src/utils/numberFormatting.ts
 M src/utils/readingFormatting.ts
 M src/utils/readingValidation.ts
 M src/utils/sensorFormatting.ts
 M tests/activity-utils.test.mjs
 M tests/agronomic-profile-utils.test.mjs
 M tests/auth-utils.test.mjs
 M tests/reading-utils.test.mjs
?? docs/auditoria-etapa-11.md
?? docs/auditoria-etapa-12.md
?? docs/integracao-ia-final.md
?? tests/audit-regressions.test.mjs
```

As correções anteriores de cache, formatos, validações, leitura de chuva, documentação e integração de IA foram mantidas. Arquivos previamente alterados que participam desta etapa receberam apenas as mudanças descritas abaixo. Todos os relatórios preexistentes mantiveram seus hashes.

## Problemas encontrados e correções

| ID | Severidade | Problema comprovado | Correção e evidência |
| --- | --- | --- | --- |
| M1 | Média | AuthService.me convertia um usuário desativado em UserResponse após aceitar um JWT ainda válido. A recuperação mobile podia validar uma conta inativa. | Verificação de isEnabled com 403 no envelope existente. Teste meRejectsExistingTokenAfterUserIsDisabled. Contas ativas continuam com 200 e usuário removido mantém o 404 anterior. |
| M2 | Média | O texto de IA podia afirmar que as leituras eram reais e não simuladas, mesmo no cenário manual demonstrativo. simulated=false não distingue MANUAL de DEVICE. | Origem manual confirmada para todos os IDs do snapshot por consulta de contagem limitada ao proprietário. Contexto inclui a proveniência; o aviso conhecido é corrigido e o aviso manual/demonstrativo é incluído quando comprovado. Leituras SIMULATED mantêm seu aviso. Testes unitários e PostgreSQL descartável. |
| B1 | Baixa | OutboxPublisher tinha JdbcTemplate por field injection; seu construtor permitia instância sem uma dependência exigida pelo caminho RiskAnalysisRequested. | JdbcTemplate final na injeção por construtor. Teste existente atualizado para fornecer o mock; regras transacionais, SQL, locks, confirmações e retries preservados. |
| B2 | Baixa | src/types/api.ts não tinha consumidores; dois imports Java não eram usados. | Arquivo de tipos removido após grafo de imports a partir de App/index/testes; imports removidos de SensorHealthController e SensorReadingIntegrationTests. Parsing de erros usa unknown e refinamento e foi preservado. |
| B3 | Baixa | Nome provisório no app.json/BrandMark/Sobre e grafia Agro Conecta no título OpenAPI/metadado Maven. | AgroConecta em expo.name, título compartilhado via Constants.expoConfig.name, identificação Sobre, OpenAPI e pom.name. Identificadores internos preservados e explicados nos dois READMEs. |
| B4 | Baixa | README mobile afirmava ausência de corpo no PATCH de alertas, mas RiskJobController devolve RiskJob. | Documentação corrigida: o mobile ignora o corpo do PATCH e consulta GET para confirmar o estado. Não houve alteração no serviço HTTP ou endpoint. |

A correção de proveniência modifica somente futuras gerações de guidance. Guidance válida já persistida continua sendo reutilizada; nenhum JSON histórico, inclusive o do RiskJob #11, foi reescrito. A frase histórica contida no relatório anterior continua como evidência do resultado daquela execução. O cálculo Java, campos determinísticos e indicadores demo não foram alterados.

## Comentários removidos e preservados

| Grupo | Comentários removidos |
| --- | --- |
| Java | 34 |
| TS/TSX/MJS | 35 |
| configurações | 22 |
| Total | 91 |

Contagem por token de comentário: cada // conta uma unidade; cada bloco/Javadoc conta uma unidade. Foram editados 60 arquivos nessa remoção. A análise usou lexer Java com proteção para strings, chars e text blocks, e parser/scanner TypeScript com proteção de strings, templates, regex e texto JSX. Não foi uma substituição global por regex. URLs e conteúdo textual de testes permaneceram intactos.

A AST impressa sem comentários foi idêntica antes/depois nos 26 arquivos mobile afetados pela remoção. Os tokens Java foram idênticos em 21 arquivos que receberam apenas limpeza; os outros 7 arquivos dessa comparação também tiveram as correções funcionais/imports explicitadas. Identificadores e mecanismos já autoexplicativos, como lockOwned, authoritativeGuidance, isSessionCurrent, operações serializadas de SecureStore e SQL FOR UPDATE SKIP LOCKED, foram preservados. Não se reduziram regras de segurança ou transação para eliminar comentários.

Não restaram comentários em src/main/java, src/test/java, src/**/*.ts(x), tests/**/*.mjs, App.tsx ou index.ts. Não foram encontrados directives eslint-disable, ts-ignore, istanbul ignore ou noinspection que exigissem permanência nesses arquivos.

Permaneceram dois comentários operacionais em scripts/provision-technician.sql: explicam a necessidade de acesso administrativo e a passagem de technician_email ao psql. Eles documentam o modo obrigatório de operação de um script administrativo fora da abrangência de código-fonte indicada; o script não foi executado. Licenças, wrappers Maven gerados, migrations V1–V19, prompts, documentos RAG, README e relatórios não foram submetidos à remoção de comentários. O exemplo .env do mobile manteve os valores e perdeu apenas dois comentários redundantes com o README.

## Código morto e legado

A única exclusão foi src/types/api.ts: continha ApiErrorResponse e ProblemDetail sem imports/reexports nem uso no código ou testes. A tipagem e o refinamento efetivamente usados no parser continuam em utils/errors.ts. Foram removidos os imports BusinessException de SensorHealthController e BigDecimal de SensorReadingIntegrationTests. A verificação TypeScript adicional com noUnusedLocals/noUnusedParameters passou; a busca de métodos privados Java não encontrou candidatos sem chamadas/referências de método.

Controllers, configurações, schedulers, listeners e handlers com registro por annotations foram mantidos, mesmo quando nenhuma outra classe os importava diretamente. Não se removeram DTOs consumidos por endpoints, classes persistidas ou configurações em uso. O fluxo legado agronomic_analyses e seus endpoints, retries, recuperação, modelos, tabelas, migrations e testes foram preservados. O fluxo oficial mobile continua RiskJob + guidance.

## Arquitetura e persistência

| Área | Verificação |
| --- | --- |
| Camadas | Controllers delegam às regras de serviço nos CRUDs. Services usam DTOs/records e injeção por construtor. As relações JPA e FKs continuam iguais. Uma classe pública por arquivo; packages históricos em minúsculas preservados. |
| Acoplamento | Há orquestração/consultas SQL em DashboardController, TechnicianController e controladores auxiliares, além de dependências entre croparea e water para proteger histórico. Não foi introduzido ciclo de injeção. Separar essas responsabilidades seria refatoração ampla sem correção funcional comprovada e ficou fora desta etapa. |
| Números e nulidade | BigDecimal e precisão de entrada preservados; percentuais opcionais/ausentes, datas e unidades seguem os normalizadores existentes. Nenhuma fórmula ou limiar de risco foi alterado. |
| Transações/locks | Gravações CRUD e outbox transacionais; locks por proprietário, ordem de bloqueio de propriedades, REQUIRES_NEW e SKIP LOCKED mantidos. Guidance e fallback usam lockOwned e copiam o risco determinístico. |
| Consultas e N+1 | Listagens principais de sensores, leituras, talhões e análises mantêm EntityGraph; atividades expõem IDs das relações. A nova confirmação de origem usa uma contagem para a coleção de IDs, sem consulta por leitura. Não houve medição de desempenho em carga ou consulta ao banco permanente. |
| Idempotência/filas | Outbox com confirmação e publicação obrigatória, tentativas/backoff limitados, listeners idempotentes e DLQ preservados; testes RabbitMQ usam container descartável. |
| Migrações/índices | 19 migrations e todos os checksums de arquivo preservados. Nenhuma migration, índice, tabela, exchange, fila ou chave publicada renomeada. |
| IA | Timeout PT90S menor que 180s mobile; processingTimeout PT5M maior que callTimeout. Sem retry automático no fluxo guidance; saída estruturada validada; campos de risco vêm do serviço; fallback continua persistido e manualmente retryable. |
| RAG | Passagens permanecem dados não confiáveis separados das instruções. Fontes exigem metadados e URLs HTTP(S) sem credenciais. Corpus e prompts ficaram intactos, sem embeddings, ingestão ou chamadas de busca reais. |

Catches genéricos foram avaliados individualmente. Foram mantidos nos limites de tarefas assíncronas, fallback, inicialização RAG e handler global por serem necessários ao encerramento, sanitização e recuperação. O caminho DLQ preserva o descarte controlado de payload malformado. Não foram encontrados System.out, printStackTrace, console.log/console.warn, TODO/FIXME ou mocks em código de produção que exigissem remoção adicional. Logs operacionais de estado/erro foram mantidos; não são logs temporários de auditoria.

## Segurança

| Controle | Resultado e limite da verificação |
| --- | --- |
| Autenticação | JWT HMAC HS256 com issuer/audience/expiração e segredo mínimo; login valida conta ativa. auth/me passou a rejeitar conta desativada, sem mudar o formato de UserResponse ou ApiErrorResponse. |
| Isolamento | IDs de proprietário são obtidos do principal e do banco. Consultas/locks de CRUD e guidance restringem produtor. Testes mantêm negação de acesso horizontal e revogação. |
| Entrada | DTOs Jakarta, allowlist de campos nos serviços mobile e fail-on-unknown-properties=true preservados. Perfil e dados de medição continuam validados, sem mass assignment de owner/role/id/status. |
| Segredos | Varredura em arquivos próprios não encontrou chave Google literal, JWT literal, chave privada ou referência à conta de auditoria. Credenciais dos testes/exemplos são sintéticas/de desenvolvimento; não são credenciais da conta permanente. .env reais e arquivo privado de chave não foram expostos. A chave privada não foi lida nesta etapa. |
| Logs/erros | Prompt/completion logging desligado; exceções de saída estruturada sanitizadas; parser mobile impede payloads diagnósticos comuns. Sem payloads de autenticação em logs ou armazenamento público. |
| CORS/profiles | Origins por allowlist configurável; sem wildcard com credenciais. Prod exige JWT_SECRET e desliga Compose. Valores de desenvolvimento não são credenciais recomendadas para produção. |
| Mobile | JWT persistido apenas em SecureStore e usado em memória; auth/me valida recuperação. 401/403 encerram a sessão correspondente. Erro de rede mantém token para nova recuperação; logout cancela/limpa queries e serializa operações de armazenamento. |
| Links e HTTP | Axios central com URL pública validada, timeout geral 15s e guidance 180s. URLs de fontes filtradas antes de Linking. Nenhuma GOOGLE_API_KEY, JWT em AsyncStorage ou token em EXPO_PUBLIC_* foi adicionado. |

A revisão de segurança é estática e apoiada por testes locais. Não é um pentest, análise de dependências por CVE ou teste em ambiente de produção. Não se executaram cenários destrutivos nem chamadas com a conta de auditoria.

## Mobile, funcionalidades e contratos HTTP

A auditoria verificou tipos, serviços, hooks, cache, navegação e estados de Farms, CropAreas, diário, perfil agronômico, sensores/saúde, leituras, dashboard, RiskJobs, alertas, guidance, tema, perfil, sessão e Sobre. O grafo de imports considerou App/index e os testes. TypeScript estrito e navegação tipada foram mantidos, sem any explícito no código-fonte. unknown continua refinado por guards, sem substituir validação por casts cegos.

Query keys continuam separando recursos, IDs, página e filtros. Cache de sessão é limpo no logout; callbacks de mutations verificam a sessão capturada. Invalidações cruzadas após transferência/renomeação foram preservadas. Polling de RiskJob continua cancelável por foco/estado do aplicativo, com orçamento de 60s e sem renovação automática em consulta manual. Guidance não ganha retry ou geração automática. Dados anteriores permanecem disponíveis durante erro de atualização quando já suportado.

| Fluxo | Contrato conferido e preservado |
| --- | --- |
| Auth | POST /api/auth/register e /login; GET /api/auth/me. Bearer/UserResponse mantidos; usuário inativo passa a receber o 403 já suportado pelo cliente. |
| Farms e CropAreas | CRUD, filtros/paginação, campos area/unit, arquivamento e conflitos; sem renomear URLs/campos. |
| Diário e perfil | Atividades por cropArea com Instants/quantidade/unidade e paginação; perfil GET/PUT com revisões e demo; ausência 404 conforme consumo existente. |
| Sensores e leituras | Metadados/saúde, medição por tipo, source MANUAL enviado pelo app e SIMULATED preservado no backend, chuva com intervalo/measuredAt, última leitura 404 e conflitos 409. |
| Dashboard | Páginas customizadas usam page; alertas Spring Page usam number. Normalização parcial e dados ausentes mantidos. |
| RiskJobs | POST por talhão sem body, 202; GET do mesmo job; listas Spring Page. Snapshots e risco Java oficiais preservados. |
| Alertas | PATCH retorna RiskJob; cliente pode ignorar o corpo e confirmar via GET. Reconhecimento/resolução não alteram o risco. |
| Guidance | POST sem body e timeout 180s; GET devolve guidance persistida. Mesmos campos de fallback, sources, warnings, model e risco. Mudança de texto de proveniência não altera schema. |
| Erros | 400/401/403/404/409/5xx, timeout e rede seguem parsers existentes, sem renderização de respostas brutas. |

Nenhum caminho, verbo HTTP, request DTO, response DTO, campo serializado, enum de contrato ou paginação foi alterado nesta etapa. O novo helper de proveniência e o método de repository são internos. Não houve redesenho, alteração de estilo, migração de navegação, criação/edição de ios/android, prebuild ou EAS. A validação visual, de teclado e leitor de tela em dispositivo permanece manual.

## Testes direcionados

```bash
env -u DEBUG -u GOOGLE_API_KEY -u RUN_GEMINI_MANUAL_TEST \
  APP_AI_ENABLED=false JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./mvnw -Dtest=AuthIntegrationTests,JobGuidanceTests,RiskJobGuidanceIntegrationTests,OutboxUnitTests test
```

Resultado: BUILD SUCCESS, 47 testes aprovados, nenhum ignorado, falho ou erro. Foi uma execução direcionada; não houve repetição para esconder falhas.

| Alteração | Comprovação |
| --- | --- |
| Conta desativada | Novo teste mantém o token emitido, desativa o usuário somente no PostgreSQL descartável e exige 403 em auth/me. |
| Proveniência manual | Novo teste de guidance exige a formulação manual/demonstrativa, remove a afirmação incorreta e confirma o dado de proveniência no contexto capturado, sem provedor real. |
| Origem não confirmada/SIMULATED | Novo teste impede rótulo manual sem comprovação e conserva aviso de simulação; os testes anteriores de demo/risco/fontes permanecem. |
| Consulta de origem | Novo teste PostgreSQL distingue MANUAL, DEVICE e SIMULATED e comprova que IDs de outro proprietário não entram na contagem. |
| Outbox | Teste anterior recebeu JdbcTemplate pelo construtor; todas as assertions de confirmação, retries, histórico sanitizado e ausência de delete foram mantidas. |

Os testes direcionados usam somente MockMvc/mocks locais e containers descartáveis definidos pelo projeto. Atualizações/limpezas de fixtures observadas nesses testes não se conectam ao banco permanente.

## Validação final

Backend executado **uma vez** após as correções, com Java 21.0.13.0.3, sem GOOGLE_API_KEY/RUN_GEMINI_MANUAL_TEST e com IA externa desligada. DEBUG também foi removido do ambiente para não habilitar logging de depuração herdado.

```bash
env -u DEBUG -u GOOGLE_API_KEY -u RUN_GEMINI_MANUAL_TEST \
  APP_AI_ENABLED=false JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./mvnw clean package
```

| Validação | Resultado |
| --- | --- |
| Backend clean package | BUILD SUCCESS |
| Backend total | 182 |
| Backend aprovados | 181 |
| Backend ignorados | 1 |
| Backend falhos/erros | 0 |
| Mobile Node 22.23.5 | 105 aprovados / 0 falhos |
| npx --no-install tsc --noEmit | Aprovado, sem diagnósticos |
| Verificação extra TS noUnusedLocals/noUnusedParameters | Aprovada, sem diagnósticos |
| CI=1 npx --no-install expo install --check | Dependencies are up to date; Expo 58.0.25 |
| git diff --check, ambos | Aprovado |

GeminiManualTests permaneceu ignorado por opt-in, com suas condições e assertions intactas. Todas as outras suites passaram. Maven exibiu avisos de metadados de repositórios opcionais com HTTP 401; eles não impediram o build. Os avisos de Mockito sobre carregamento dinâmico de agente e SpringDoc são informativos e não foram escondidos por alterações de testes/configuração. Nenhuma execução completa precisou ser repetida nesta auditoria.

| Suite backend | Total | Falhos | Erros | Ignorados |
| --- | --- | --- | --- | --- |
| br.com.fiap.citizentech.CitizentechApiApplicationTests | 1 | 0 | 0 | 0 |
| br.com.fiap.citizentech.analysis.AgronomicAnalysisIntegrationTests | 10 | 0 | 0 | 0 |
| br.com.fiap.citizentech.analysis.GeminiManualTests | 1 | 0 | 0 | 1 |
| br.com.fiap.citizentech.analysis.RagAndPromptTests | 6 | 0 | 0 | 0 |
| br.com.fiap.citizentech.analysis.config.AiConfigurationTests | 7 | 0 | 0 | 0 |
| br.com.fiap.citizentech.auth.AuthIntegrationTests | 24 | 0 | 0 | 0 |
| br.com.fiap.citizentech.auth.JwtPropertiesTests | 4 | 0 | 0 | 0 |
| br.com.fiap.citizentech.croparea.CropAreaIntegrationTests | 25 | 0 | 0 | 0 |
| br.com.fiap.citizentech.farm.FarmIntegrationTests | 19 | 0 | 0 | 0 |
| br.com.fiap.citizentech.sensor.SensorIntegrationTests | 22 | 0 | 0 | 0 |
| br.com.fiap.citizentech.sensorreading.OutboxUnitTests | 3 | 0 | 0 | 0 |
| br.com.fiap.citizentech.sensorreading.ReadingRabbitIntegrationTests | 3 | 0 | 0 | 0 |
| br.com.fiap.citizentech.sensorreading.SensorReadingIntegrationTests | 24 | 0 | 0 | 0 |
| br.com.fiap.citizentech.water.JobGuidanceTests | 13 | 0 | 0 | 0 |
| br.com.fiap.citizentech.water.ProfileValidationTests | 1 | 0 | 0 | 0 |
| br.com.fiap.citizentech.water.RainfallCalculatorTests | 1 | 0 | 0 | 0 |
| br.com.fiap.citizentech.water.RiskJobGuidanceIntegrationTests | 7 | 0 | 0 | 0 |
| br.com.fiap.citizentech.water.SensorHealthTests | 2 | 0 | 0 | 0 |
| br.com.fiap.citizentech.water.WaterIndicatorsTests | 4 | 0 | 0 | 0 |
| br.com.fiap.citizentech.water.WaterRiskCalculatorTests | 4 | 0 | 0 | 0 |
| br.com.fiap.citizentech.water.WaterWorkflowIntegrationTests | 1 | 0 | 0 | 0 |

```bash
node --experimental-strip-types --test tests/*.test.mjs
npx --no-install tsc --noEmit
CI=1 npx --no-install expo install --check
```

Lint não foi executado, conforme proibição explícita do pedido; não foi criada configuração nem iniciada instalação automática. Expo Doctor não foi repetido.

## Revisão do diff e preservação

Foram aprovadas 37 verificações de hashes/metadados/HEAD. Package.json e package-lock.json ficaram idênticos ao início; o pom.xml mudou somente o nome público e a remoção de um comentário XML, sem alteração semântica de dependências/plugins/versões. Expo SDK 58.0.25, Java 21, Spring Boot 4.1.2 e Spring AI 2.0.2 foram preservados. Nenhuma dependência foi instalada.

Migrations V1–V19, corpus RAG, prompts, AGENTS.md, .env reais e todos os relatórios anteriores permaneceram idênticos por hash. O app.json alterou somente expo.name; slug/plugins/opções nativas permaneceram iguais. No mobile, além dos comentários e do tipo morto, o único código de apresentação modificado foi a identificação pública centralizada e o texto do nome em Sobre. Nenhum estilo foi alterado.

Nenhuma requisição HTTP foi enviada pela auditoria à API permanente; nenhum comando SQL, POST/PATCH/DELETE, consumo de filas ou login foi dirigido aos dados reais. As únicas escritas de dados ocorreram nos ambientes descartáveis dos testes existentes. Assim, propriedade #9, talhões #8–12, sensores #10–18, leituras #11–23, atividades #8–11, perfis #5–10, RiskJobs #9–12 e alertas #10–12 ficaram fora das operações desta etapa. Não foi realizada comparação SQL em produção nesta auditoria para evitar novas consultas aos dados permanentes.

Não houve geração de chat, embedding, reingestão, acesso ao arquivo seguro da chave ou execução do teste manual externo. Acesso à rede ficou limitado à documentação oficial e às verificações/metadados de ferramentas/dependências. Não houve nova instalação, novo serviço, commit ou push; HEADs permaneceram iguais.

## Arquivos modificados nesta auditoria

Lista exata obtida por comparação com os hashes do início desta etapa, incluindo arquivos anteriormente não versionados. Ela não atribui a esta auditoria as demais alterações já existentes no git diff.

### GS-FINAL — 38 arquivos

| Arquivo | Operação | Comentários removidos nesta etapa |
| --- | --- | --- |
| .gitignore | modificado | 6 |
| README.md | modificado | 0 |
| pom.xml | modificado | 1 |
| src/main/java/br/com/fiap/citizentech/analysis/ai/GeminiAgronomicAiClient.java | modificado | 1 |
| src/main/java/br/com/fiap/citizentech/analysis/ai/InvalidStructuredOutputException.java | modificado | 1 |
| src/main/java/br/com/fiap/citizentech/analysis/config/AnalysisDeliveryConfig.java | modificado | 1 |
| src/main/java/br/com/fiap/citizentech/analysis/config/GeminiConfig.java | modificado | 1 |
| src/main/java/br/com/fiap/citizentech/analysis/rag/AgronomyRagLoader.java | modificado | 2 |
| src/main/java/br/com/fiap/citizentech/analysis/rag/PgVectorAgronomyKnowledge.java | modificado | 1 |
| src/main/java/br/com/fiap/citizentech/analysis/service/AgronomicAnalysisService.java | modificado | 1 |
| src/main/java/br/com/fiap/citizentech/analysis/service/AnalysisContextService.java | modificado | 1 |
| src/main/java/br/com/fiap/citizentech/analysis/service/AnalysisTransactions.java | modificado | 2 |
| src/main/java/br/com/fiap/citizentech/auth/config/ApiDocumentationConfig.java | modificado | 0 |
| src/main/java/br/com/fiap/citizentech/auth/service/AuthService.java | modificado | 1 |
| src/main/java/br/com/fiap/citizentech/croparea/service/CropAreaCapacityService.java | modificado | 1 |
| src/main/java/br/com/fiap/citizentech/croparea/service/CropAreaService.java | modificado | 1 |
| src/main/java/br/com/fiap/citizentech/farm/service/FarmService.java | modificado | 1 |
| src/main/java/br/com/fiap/citizentech/sensor/service/SensorService.java | modificado | 1 |
| src/main/java/br/com/fiap/citizentech/sensorreading/controller/SimulatedReadingController.java | modificado | 1 |
| src/main/java/br/com/fiap/citizentech/sensorreading/repository/SensorReadingRepository.java | modificado | 0 |
| src/main/java/br/com/fiap/citizentech/shared/outbox/OutboxPublisher.java | modificado | 3 |
| src/main/java/br/com/fiap/citizentech/water/JobGuidanceController.java | modificado | 2 |
| src/main/java/br/com/fiap/citizentech/water/RiskJob.java | modificado | 1 |
| src/main/java/br/com/fiap/citizentech/water/RiskJobService.java | modificado | 1 |
| src/main/java/br/com/fiap/citizentech/water/SensorHealthCalculator.java | modificado | 1 |
| src/main/java/br/com/fiap/citizentech/water/SensorHealthController.java | modificado | 0 |
| src/main/java/br/com/fiap/citizentech/water/SensorSimulationController.java | modificado | 1 |
| src/main/java/br/com/fiap/citizentech/water/WaterIndicators.java | modificado | 2 |
| src/main/java/br/com/fiap/citizentech/water/WaterRiskCalculator.java | modificado | 1 |
| src/main/resources/application.yml | modificado | 1 |
| src/test/java/br/com/fiap/citizentech/analysis/GeminiManualTests.java | modificado | 1 |
| src/test/java/br/com/fiap/citizentech/auth/AuthIntegrationTests.java | modificado | 1 |
| src/test/java/br/com/fiap/citizentech/farm/FarmIntegrationTests.java | modificado | 1 |
| src/test/java/br/com/fiap/citizentech/sensorreading/OutboxUnitTests.java | modificado | 0 |
| src/test/java/br/com/fiap/citizentech/sensorreading/SensorReadingIntegrationTests.java | modificado | 1 |
| src/test/java/br/com/fiap/citizentech/water/JobGuidanceTests.java | modificado | 0 |
| src/test/java/br/com/fiap/citizentech/water/RiskJobGuidanceIntegrationTests.java | modificado | 1 |
| src/test/resources/application.properties | modificado | 2 |

### citizentech-mobile — 34 arquivos

| Arquivo | Operação | Comentários removidos nesta etapa |
| --- | --- | --- |
| .env.example | modificado | 2 |
| .gitignore | modificado | 10 |
| README.md | modificado | 0 |
| app.json | modificado | 0 |
| index.ts | modificado | 3 |
| src/components/navigation/BrandMark.tsx | modificado | 0 |
| src/contexts/AuthContext.tsx | modificado | 1 |
| src/hooks/activities/useActivityCache.ts | modificado | 1 |
| src/hooks/farms/useUpdateFarm.ts | modificado | 1 |
| src/hooks/readings/useReadingCache.ts | modificado | 1 |
| src/hooks/riskJobs/guidanceKeys.ts | modificado | 1 |
| src/hooks/riskJobs/useGuidance.ts | modificado | 4 |
| src/hooks/riskJobs/useRiskJobCache.ts | modificado | 1 |
| src/hooks/sensors/useSensorCache.ts | modificado | 1 |
| src/screens/activities/ActivityFormScreen.tsx | modificado | 1 |
| src/screens/farms/FarmFormScreen.tsx | modificado | 1 |
| src/screens/farms/FarmListScreen.tsx | modificado | 1 |
| src/screens/readings/ReadingFormScreen.tsx | modificado | 1 |
| src/screens/settings/AboutScreen.tsx | modificado | 0 |
| src/services/api.ts | modificado | 1 |
| src/services/tokenStorage.ts | modificado | 1 |
| src/types/api.ts | removido | 0 |
| src/types/riskJob.ts | modificado | 2 |
| src/utils/activityValidation.ts | modificado | 2 |
| src/utils/dashboardNormalization.ts | modificado | 1 |
| src/utils/errors.ts | modificado | 1 |
| src/utils/farmValidation.ts | modificado | 1 |
| src/utils/guidanceNormalization.ts | modificado | 1 |
| src/utils/guidancePolicy.ts | modificado | 1 |
| src/utils/readingValidation.ts | modificado | 2 |
| src/utils/safeSourceUrl.ts | modificado | 1 |
| tests/guidance-utils.test.mjs | modificado | 2 |
| tests/risk-job-utils.test.mjs | modificado | 1 |
| docs/auditoria-final-codigo.md | novo | 0 |

## Pendências que não justificam mudança nesta etapa

- Validar manualmente interação, teclado, leitor de tela, navegação e links em Android/iOS; não houve build nativo ou automação visual.
- Manter o fluxo agronomic_analyses como legado até decisão específica de descontinuação; não excluir tabelas, migrations ou compatibilidade por falta de uso mobile.
- Avaliar separação futura da orquestração de alguns controllers e dos SQLs de dashboard/técnicos, caso surja necessidade concreta; sem refatoração estética nesta auditoria.
- Manter ausência de lint registrada; instalar ferramenta/configurar pipeline exigiria outro escopo.
- Orientações já persistidas preservam seu texto histórico, mesmo quando incluem a formulação antiga sobre leituras. Nenhuma correção retroativa foi executada por ser proibida a alteração de dados permanentes.
- A configuração de produção depende de segredos e allowlist próprios do ambiente; exemplos locais não substituem essa operação. Não foram criadas políticas novas ou alterados contratos para isso.

## Confirmações finais

Sem funcionalidades novas, alteração de cálculo de risco, layout, contrato HTTP, dependências ou versões. Somente o nome público autorizado foi atualizado. Sem migration, prompt ou corpus alterado. Sem testes removidos/enfraquecidos: quatro novos testes foram acrescentados, testes anteriores conservaram suas assertions e o teste Google opt-in permaneceu ignorado. Sem uso da conta de auditoria, alteração/exclusão de dados permanentes, chamada Gemini/embedding, reinício de serviços reais, commit ou push. Todas as mudanças preexistentes foram preservadas.
