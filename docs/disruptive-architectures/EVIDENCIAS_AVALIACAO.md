# Evidências de avaliação — AgroConecta

Disciplina: Disruptive Architectures: IoT, IoB & Generative IA — Global Solution 2026/2.

Organização documental em 16/11/2026. Os resultados abaixo são de execuções anteriores registradas e de testes existentes na implementação consultada. Nenhuma suíte Java, nova geração Gemini, consulta de embedding ou operação com dados permanentes foi executada nesta preparação da entrega.

O **GS-FINAL é o repositório principal da entrega**. Esta versão centraliza a avaliação da API e da IA; o mobile é complementar. Os relatórios históricos copiados para `docs/` preservam as contagens e condições das respectivas execuções.

## Origem e alcance das evidências

| Referência | Evidência disponível | Como interpretar |
| --- | --- | --- |
| [Auditoria final de código — validação final](../auditoria-final-codigo.md#validação-final) | BUILD SUCCESS, 182 testes, 181 aprovados, um ignorado, zero falhas/erros; discriminação das suítes. | Resultado da execução final de 15/11/2026 com Java 21, sem chave Google e com IA externa desligada. |
| [Integração final — validação real controlada](../integracao-ia-final.md#validação-real-controlada) | Diagnóstica e POST/GET reais com HTTP 200, `fallback=false` e comparação dos snapshots. | Execução de 14/11/2026, anterior à última auditoria. |
| [Integração final — RAG e dados permanentes](../integracao-ia-final.md#rag-e-dados-permanentes) | Quatro registros/fontes, dimensão 768, IDs/checksums e ausência de reingestão no ambiente validado. | Verificação registrada naquela execução, sem repetição nesta etapa. |
| [Integração final — JSON sanitizado](../integracao-ia-final.md#json-final-sanitizado-do-riskjob-11) | Modelo, quatro referências, orientação persistida, risco CRITICAL e alerta OPEN. | Registro histórico do resultado; não contém previsão de sucesso em chamadas futuras. |
| Código dos testes do backend | Condições controladas de falha, autorização, concorrência, persistência e risco. | Asserções verificadas nos testes locais incluídos na suíte registrada, sem exigir falhas reais do Google. |

O relatório de integração real menciona **178 testes** porque pertence à etapa anterior. O total final registrado após a auditoria é **182**. O teste opt-in `GeminiManualTests` ficou ignorado na suíte final; a evidência real do provedor vem do roteiro HTTP documentado, não da execução desse teste durante o build.

A mesma [auditoria final](../auditoria-final-codigo.md#validação-final) registra **105 testes aprovados no mobile**, além de TypeScript aprovado e compatibilidade de dependências. Essa contagem avalia a interface e suas regras locais, sem representar 105 chamadas ao provedor ou validações manuais em dispositivo. Não houve nova execução nesta etapa.

No backend, os testes citados estão sob `src/test/java/br/com/fiap/citizentech/`:

| Classe | Caminho relativo | Papel |
| --- | --- | --- |
| `JobGuidanceTests` | `water/JobGuidanceTests.java` | Testes unitários com provedor e recuperação controlados. |
| `RiskJobGuidanceIntegrationTests` | `water/RiskJobGuidanceIntegrationTests.java` | HTTP via MockMvc, persistência, transações e locks em PostgreSQL descartável, com cliente de IA local. |
| `RagAndPromptTests` | `analysis/RagAndPromptTests.java` | Composição do prompt, conversão JSON e critérios de atribuição das fontes. |
| `AiConfigurationTests` | `analysis/config/AiConfigurationTests.java` | Configuração sem chave, timeouts e tentativa única nas camadas Google/Spring AI. |

Os nomes dos métodos abaixo permitem localizar as asserções no código, sem copiar dados de contas, credenciais ou payloads pessoais.

## 1. Suíte final de 182 testes

- **Objetivo:** verificar regressões do backend após as correções da auditoria final.
- **Entrada ou condição:** Java 21, IA externa desligada e teste Google manual sem habilitação; bancos e broker descartáveis nos testes de integração.
- **Comportamento esperado:** empacotamento aprovado, testes locais passando e teste externo ignorado.
- **Evidência existente:** seção “Validação final” da [auditoria de código](../auditoria-final-codigo.md#validação-final): **182 executados na contagem total, 181 aprovados, um ignorado, nenhuma falha ou erro**, com `BUILD SUCCESS`. A tabela por suíte inclui 13 casos de `JobGuidanceTests` e sete de `RiskJobGuidanceIntegrationTests`.
- **Limitação:** a contagem inclui módulos além da IA; não representa 182 avaliações de qualidade agronômica ou chamadas ao Gemini. Não houve nova execução nesta etapa documental.

## 2. Disponibilidade real do Gemini

- **Objetivo:** confirmar acesso ao modelo definido para a orientação antes do fluxo completo.
- **Entrada ou condição:** uma chamada diagnóstica curta com JSON estruturado ao `gemini-3.5-flash-lite`, no ambiente backend habilitado.
- **Comportamento esperado:** HTTP 200, modelo identificado e JSON válido, sem exposição da chave.
- **Evidência existente:** [validação real controlada](../integracao-ia-final.md#validação-real-controlada), com `healthHttp=200`, `http=200`, `structuredValid=true`, `chatRequests=1` e `modelVersion=gemini-3.5-flash-lite`.
- **Limitação:** é uma execução pontual de disponibilidade e formato, não garantia de acesso futuro, latência, gratuidade ou correção agronômica. Não foi uma comparação com outros modelos.

## 3. Recuperação das quatro fontes RAG

- **Objetivo:** verificar que a orientação utiliza referências recuperadas do corpus institucional existente.
- **Entrada ou condição:** consulta RAG no fluxo real de guidance do job crítico registrado no relatório de integração.
- **Comportamento esperado:** recuperar conteúdo atribuído, compatível com o índice de 768 dimensões, e persistir as fontes utilizadas.
- **Evidência existente:** o [JSON sanitizado](../integracao-ia-final.md#json-final-sanitizado-do-riskjob-11) contém quatro entradas em `guidance.sources`: desenvolvimento do milho, manejo de irrigação do milho, umidade na agricultura familiar e gestão de água/irrigação. São três referências Embrapa e uma FAO. A [verificação do RAG](../integracao-ia-final.md#rag-e-dados-permanentes) também registra quatro linhas/fontes, IDs/checksums preservados e `vector(768)`.
- **Limitação:** a consulta SQL de similaridade e a consulta semântica com embedding são evidências distintas; a primeira, sozinha, não demonstraria acesso ao provedor. O retorno de quatro referências naquele job não garante o mesmo número em toda solicitação nem mede relevância por julgamento agronômico independente.

## 4. Orientação válida persistida e recuperada por GET

- **Objetivo:** comprovar persistência da guidance, além da exibição temporária na sessão.
- **Entrada ou condição:** POST real sem body para guidance do job crítico, seguido de GET do mesmo ID.
- **Comportamento esperado:** orientação válida disponível no GET, com fontes e o mesmo risco do resultado calculado.
- **Evidência existente:** [validação real controlada](../integracao-ia-final.md#validação-real-controlada): `postHttp=200`, `getHttp=200`, `persistedExactly=true`, `fallback=false`, `riskLevel=CRITICAL`, `alertStatus=OPEN` e `status=COMPLETED`. O teste `explicitRetryReplacesFallbackAndSuccessIsPersistedAndReused`, em `RiskJobGuidanceIntegrationTests`, compara o JSON de guidance entre respostas HTTP locais e confirma o estado persistido.
- **Limitação:** os campos `postHttp`, `getHttp` e `persistedExactly` pertencem ao resumo do roteiro de validação, não ao schema HTTP do RiskJob. A execução externa foi pontual; o teste de integração utiliza provedor controlado.

## 5. Reutilização sem nova chamada

- **Objetivo:** impedir regeneração desnecessária de uma orientação já válida.
- **Entrada ou condição:** novo POST para um job cuja guidance tem `fallback=false`.
- **Comportamento esperado:** devolver o JSON existente, sem aumentar as chamadas ao cliente de geração.
- **Evidência existente:** `persistedFallbackAllowsAnExplicitNewAttemptButSuccessIsReused`, em `JobGuidanceTests`, verifica `analyze` chamado uma única vez após fallback, sucesso e novo pedido. `explicitRetryReplacesFallbackAndSuccessIsPersistedAndReused`, em `RiskJobGuidanceIntegrationTests`, mantém o contador do cliente em dois após uma falha inicial, um sucesso e um POST de reutilização; também compara a guidance reutilizada.
- **Limitação:** a ausência de nova chamada foi comprovada com cliente local instrumentado. O relatório real informa que não repetiu geração para gastar outra chamada apenas na demonstração do reuso. Não se apresenta esse cenário como uma segunda medição externa de cache.

## 6. Provedor indisponível e persistência do fallback

- **Objetivo:** manter o resultado determinístico acessível quando a orientação não puder ser obtida.
- **Entrada ou condição:** cliente/recuperação controlados lançam falha; no teste de integração, o cliente de IA é configurado como indisponível.
- **Comportamento esperado:** `fallback=true`, motivo `PROVIDER_UNAVAILABLE`, fontes vazias, orientação de contingência segura e risco original preservado. O GET posterior deve devolver o fallback persistido.
- **Evidência existente:** `unavailableProviderFallsBackWithoutChangingRisk`, em `JobGuidanceTests`, verifica contingência e ausência de detalhes privados. `postFallbackIsCommittedAndGetReturnsExactlyThePersistedJson`, em `RiskJobGuidanceIntegrationTests`, exige igualdade POST/GET, motivo correto, estado `COMPLETED_WITHOUT_AI`, risco CRITICAL e snapshot inalterado.
- **Limitação:** a falha é induzida localmente; não foi provocada uma indisponibilidade real do Google nesta etapa. O motivo também abrange falhas de obtenção não classificadas em categorias mais específicas.

## 7. Timeout

- **Objetivo:** encerrar a espera pela tarefa de IA e identificar o limite excedido.
- **Entrada ou condição:** recuperação local demora mais que o prazo reduzido do teste, ou o cliente lança uma exceção de timeout de transporte encapsulada.
- **Comportamento esperado:** fallback com motivo `CALL_TIMEOUT`, sem substituir o risco Java.
- **Evidência existente:** `timeoutReturnsFallback` e `providerTransportTimeoutAlsoUsesCallTimeout`, em `JobGuidanceTests`. `invalidTimeoutOrderingOrMobileDeadlineIsRejected`, em `AiConfigurationTests`, também rejeita configuração inválida de tempos.
- **Limitação:** os tempos dos testes são reduzidos e controlados; não medem latência real de rede nem garantem que o provedor interrompa o processamento de uma requisição já recebida.

## 8. Saída estruturada inválida

- **Objetivo:** impedir que uma resposta não validada seja apresentada como guidance válida.
- **Entrada ou condição:** DTO com campos obrigatórios inválidos, exceção sanitizada de conversão ou texto não convertível em JSON vindo de modelo simulado.
- **Comportamento esperado:** motivo `INVALID_STRUCTURED_OUTPUT`; o conteúdo bruto do provedor não deve aparecer na mensagem destinada ao usuário.
- **Evidência existente:** `invalidStructuredOutputHasItsOwnReason` e `invalidJsonConversionHasItsOwnReason`, em `JobGuidanceTests`; `invalidJsonIsClassifiedWithoutLeakingTheProviderOutput`, em `RagAndPromptTests`, verifica a exceção específica sem causa ou texto privado.
- **Limitação:** validação de schema, enums e comprimentos não prova veracidade do texto. Os casos não correspondem a respostas inválidas obtidas de uma chamada real nesta etapa.

## 9. Fontes insuficientes

- **Objetivo:** evitar geração quando a recuperação não fornecer contexto elegível suficiente.
- **Entrada ou condição:** recuperação local retorna `RagContext.sufficient=false`.
- **Comportamento esperado:** fallback `NO_RELEVANT_SOURCES`, sem invocar o cliente de geração.
- **Evidência existente:** `noSourcesDoesNotInvokeModel`, em `JobGuidanceTests`, verifica motivo e ausência de interações com o modelo. `unsafeOrUnattributedSourceUrlsNeverReachGuidance`, em `RagAndPromptTests`, demonstra a exclusão de fontes inseguras. No código de `PgVectorAgronomyKnowledge`, suficiência exige pelo menos três passagens elegíveis.
- **Limitação:** o teste do controller recebe a insuficiência pronta; não é uma avaliação estatística da recuperação com consultas reais diversas. O limiar de passagens não assegura diversidade institucional ou relevância agronômica.

## 10. Contexto insuficiente

- **Objetivo:** não gerar uma explicação como se houvesse dados suficientes quando o cálculo aponta falta de contexto.
- **Entrada ou condição:** resultado determinístico com nível `INSUFFICIENT_DATA`.
- **Comportamento esperado:** fallback `INSUFFICIENT_CONTEXT`, sem recuperação RAG ou geração e sem transformar insuficiência em risco normal.
- **Evidência existente:** `insufficientContextDoesNotRetrieveOrGenerate`, em `JobGuidanceTests`, verifica o motivo e ausência de interações com os dois colaboradores externos.
- **Limitação:** cobre a decisão a partir do nível determinístico informado no teste; não equivale a uma classificação agronômica independente de toda combinação possível de dados ausentes.

## 11. Concorrência e prioridade da orientação válida

- **Objetivo:** impedir corrupção do job ou sobrescrita de sucesso por uma falha tardia.
- **Entrada ou condição:** duas transações concorrentes tentam persistir guidance válida e fallback no mesmo job.
- **Comportamento esperado:** orientação válida prevalece, estado final `COMPLETED` e snapshots determinísticos intactos; um fallback posterior não deve rebaixar o sucesso.
- **Evidência existente:** `concurrentFallbackAndSuccessCannotDowngradeValidGuidanceOrSnapshots`, em `RiskJobGuidanceIntegrationTests`, executa concorrência com PostgreSQL descartável, verifica `fallback=false`, estado, snapshot e nova tentativa de gravação de fallback após o sucesso.
- **Limitação:** o teste cobre persistência e locks em um cenário local. Não mede capacidade sob carga nem comprova deduplicação de toda chamada ao modelo iniciada simultaneamente antes da primeira gravação.

## 12. Isolamento por produtor

- **Objetivo:** impedir leitura, geração, gravação ou envio de contexto de um produtor para outro.
- **Entrada ou condição:** um segundo produtor tenta acessar o job alheio; dados privados de outra fixture são inseridos no ambiente descartável.
- **Comportamento esperado:** endpoints negam acesso, serviços não gravam guidance alheia e o contexto enviado ao cliente de IA não contém os dados do outro produtor.
- **Evidência existente:** `otherOwnerCannotReadGenerateOrSaveGuidance` e `promptContextUsesOnlyOwnedSnapshotAndActivityTypesNotOtherProducers`, em `RiskJobGuidanceIntegrationTests`; `ownerDenialNeverCallsProvider` e `revocationWhileGeneratingIsNotConvertedIntoSuccessfulFallback`, em `JobGuidanceTests`.
- **Limitação:** são contas e dados de teste isolados, não dados pessoais da demonstração. A cobertura não é uma certificação de segurança ou teste de invasão completo.

## 13. Preservação do risco calculado pelo Java

- **Objetivo:** garantir que a IA explique a classificação sem substituí-la.
- **Entrada ou condição:** modelo controlado sugere `LOW` para um job CRITICAL, ou uma tentativa de gravação fornece risco divergente do resultado persistido.
- **Comportamento esperado:** guidance mantém `riskLevel=CRITICAL`; resultado, medições, regras, chuva e alerta não são modificados pela IA.
- **Evidência existente:** `onlyRetrievedSourcesReturnedAndModelCannotChangeRisk`, em `JobGuidanceTests`, e `serviceAlwaysCopiesRiskFromLockedDeterministicResult`, em `RiskJobGuidanceIntegrationTests`. Na [validação real](../integracao-ia-final.md#validação-real-controlada), as comparações de resultado, medições, thresholds, chuva, perfil e alerta são verdadeiras; o risco ficou CRITICAL e o alerta OPEN.
- **Limitação:** preservar o cálculo não prova calibração do solo, qualidade das leituras ou validade agronômica universal dos limiares demonstrativos.

## 14. Prompt injection e origem demonstrativa

- **Objetivo:** separar instruções de dados recuperados e evitar interpretar dados demonstrativos como medição de campo comprovada.
- **Entrada ou condição:** texto malicioso em passagem/contexto controlado; avisos do modelo com afirmação inadequada sobre a origem das leituras.
- **Comportamento esperado:** evidência permanece no JSON de dados, sem se tornar instrução de sistema; a aplicação conserva a autoridade sobre o risco e os avisos SIMULATED/demo.
- **Evidência existente:** `structuredOutputKeepsInjectionInsideDataAndSearchDisabled`, em `RagAndPromptTests`; `manualDemoReadingsNeverClaimToBeValidatedRealMeasurements` e `unconfirmedReadingOriginIsNeverLabeledManual`, em `JobGuidanceTests`; `manualProvenanceRequiresAllSnapshotReadingsToExistAndBelongToOwner`, em `RiskJobGuidanceIntegrationTests`.
- **Limitação:** a composição correta de mensagens não garante resistência absoluta do modelo a toda injeção. A correção de avisos se aplica a futuras gerações; o texto histórico do relatório real não foi reescrito. Leituras manuais continuam sem equivaler a hardware validado.

## 15. Ausência de retry automático de guidance

- **Objetivo:** evitar repetição automática de geração ou embedding por falhas transitórias no fluxo de orientação.
- **Entrada ou condição:** configuração de retry do Spring AI, opções do cliente Google e mutation mobile.
- **Comportamento esperado:** tentativa única nas camadas do provedor; falha resulta em contingência ou consulta de estado, deixando outra geração a critério do usuário.
- **Evidência existente:** `disabledAiStartsWithoutKeyAndBindsDefinitiveDefaultsToGoogleGenai`, em `AiConfigurationTests`, verifica uma execução do mecanismo Spring AI; `googleSdkHasOneAttemptAndEmbeddingUsesTheSameBoundedClient` confirma `attempts=1` e cliente compartilhado. No mobile, `GUIDANCE_MUTATION_RETRY=false` e o timeout de 180 segundos estão definidos em `src/utils/guidancePolicy.ts`. O [roteiro real](../integracao-ia-final.md#validação-real-controlada) registra duas gerações de chat e um embedding de consulta, sem reingestão ou repetição para forçar sucesso.
- **Limitação:** outbox, consumidores e fluxo legado mantêm seus próprios retries. A evidência não permite afirmar que não existe repetição automática em nenhum outro módulo da solução.

## Requisitos do enunciado oficial

O PDF oficial foi analisado; os requisitos abaixo correspondem ao enunciado confirmado para a Global Solution 2026/2. O aprofundamento pode ser RAG básico, Machine Learning, Deep Learning **ou** Visão Computacional. A escolha do AgroConecta é **RAG básico**, sem obrigação de implementar todas essas alternativas. O foco da avaliação é a IA generativa integrada e o aprofundamento técnico; ESP32, MQTT e hardware físico não são requisitos.

| Requisito | Evidência ou situação |
| --- | --- |
| Funcionalidade de IA integrada à CitizenTech | Orientação contextual sobre risco hídrico para pequenos produtores, solicitada e exibida no aplicativo; problema, público e contribuição descritos no README. |
| IA generativa por API ou execução local | Gemini `gemini-3.5-flash-lite` por API, com validação real histórica no cenário 2. Não há execução local do modelo. |
| Aprofundamento técnico | RAG básico com PostgreSQL e PGVector; embedding `gemini-embedding-001`, 768 dimensões, três referências Embrapa e uma FAO; cenário 3. |
| Fontes, preparação dos dados e fluxo | [Arquitetura da IA](ARQUITETURA_IA.md): corpus curado local, fragmentação, metadados, checksums, ingestão, recuperação, prompt e persistência. |
| Entradas representativas e situações de falha | Cenários 1 a 15: execução externa pontual e testes controlados, com limites de comprovação explícitos. Não há estudo estatístico de desempenho agronômico. |
| Proteção de credenciais e dados pessoais | Chave apenas no processo Java; exemplos sem credenciais reais; contexto minimizado, autorização por produtor e saída sanitizada; cenários 8, 12 e 14. Não equivale a uma certificação de segurança. |
| Limitações explícitas | Limitações por cenário e seção de limitações da arquitetura; ausência de validação agronômica independente e de estudo de campo. |
| Repositório com código, dependências e exemplos de configuração sem credenciais | GS-FINAL contém código Java, `pom.xml`, wrapper Maven, migrations e `.env.example`; README mantém execução e infraestrutura. Publicação e acesso à versão final ainda devem ser confirmados. |
| README com integrantes, funcionalidade, execução, modelos, tecnologias, justificativas e diagrama | Conteúdo técnico e seção da disciplina no [README](../../README.md#entrega--disruptive-architectures-iot-iob--generative-ia). **Nomes e RMs pendentes.** |
| Evidências, testes, resultados, critérios e limitações | Este documento e os relatórios históricos vinculados. Critérios de aceitação técnica são os comportamentos esperados de cada cenário, sem atribuição antecipada de nota. |
| Arquivo `.txt` com nomes, RMs e link do repositório | **Pendente:** nomes e RMs não fornecidos e link definitivo a confirmar. O arquivo não foi criado nesta etapa. |

## Rubrica da entrega

Os valores são as pontuações máximas do enunciado, **não notas obtidas**. A atribuição da nota cabe à avaliação da disciplina.

| Critério | Pontos | Evidências e limites |
| --- | ---: | --- |
| Adequação ao problema e público | 10 | README: dificuldade de interpretar leituras para manejo hídrico, pequenos produtores e contribuição CitizenTech. Benefício esperado, sem impacto em campo medido. |
| IA generativa integrada | 25 | Cenários 2, 4 e 5: Gemini real, guidance persistida e reutilização. Interface complementar; demonstração presencial ainda deve ocorrer. |
| Aprofundamento técnico | 25 | RAG básico, PGVector e embeddings; cenário 3 e arquitetura. Corpus pequeno; não inclui treinamento próprio ou avaliação ampla da recuperação. |
| Dados e documentação da arquitetura | 15 | Quatro referências atribuídas, preparação, diagrama, prompt, limites e responsabilidades em ARQUITETURA_IA.md. |
| Testes e avaliação dos resultados | 15 | 182 testes na API (181 aprovados e um manual ignorado), 105 no mobile, validação real histórica e cenários controlados com critérios e limitações. |
| Organização, reprodução e uso responsável | 10 | README, configuração de exemplo sem credenciais, isolamento, fallback e limitações. Nomes/RMs, arquivo `.txt` e publicação/acesso final ainda pendentes. |
| **Total** | **100** | Não representa pontuação já atribuída. |

## Critérios da apresentação presencial

| Critério | Pontos | Preparação e comprovação pendente |
| --- | ---: | --- |
| Funcionamento da IA integrada | 25 | Evidência histórica do fluxo Gemini + RAG disponível; demonstrar o funcionamento integrado durante a apresentação. Disponibilidade passada não garante acesso futuro. |
| Domínio técnico, arquitetura, fluxo e limitações | 25 | Explicar separação do risco Java e orientação generativa, recuperação, persistência, fallback e limitações documentadas. Domínio deve ser demonstrado pelos integrantes. |
| Capacidade de explicar adaptação a uma mudança solicitada | 25 | Responder à mudança proposta pela avaliação e explicar as adaptações necessárias; não há evidência antecipada dessa situação. |
| Participação dos integrantes | 25 | Depende da identificação e da participação efetiva dos integrantes na apresentação. |
| **Total** | **100** | Critérios presenciais ainda sujeitos à apresentação. |

## Pendências da entrega e limites de comprovação

| Item | Situação documentada |
| --- | --- |
| Identificação dos integrantes | Nomes e RMs devem ser incluídos no README quando fornecidos. Nenhum arquivo de identificação foi criado. |
| Arquivo obrigatório `.txt` | Depende de nomes, RMs e link definitivo do repositório principal GS-FINAL. |
| Publicação e acesso ao repositório | Confirmar URL e acesso à versão final entregue. A organização local não comprova publicação; não houve commit ou push nesta etapa. |
| Apresentação presencial | Os quatro critérios de 25 pontos dependem da demonstração, explicação técnica, resposta à mudança e participação dos integrantes. |
| Escopo de sensores | Não há ESP32 físico, MQTT ou dados de satélite; leituras são manuais/simuladas. RabbitMQ é mensageria interna. A API possui contrato para leituras externas futuras, sem comprovação de integração física. Essas ausências não são requisitos descumpridos deste enunciado. |
| Avaliação com produtores e impacto | Não há estudo de campo, métrica de economia de água, produtividade ou mudança comportamental. A relação com CitizenTech e ODS é uma justificativa da proposta. |
| Validação agronômica | Parâmetros demonstrativos e quatro fontes curtas não equivalem a homologação de recomendações. |
| Interface em dispositivo | Os relatórios mantêm validação manual de navegação, teclado, acessibilidade e links em Android/iOS como pendência; os 105 testes do mobile e o teste HTTP real não a substituem. |
| Novos testes externos | Não realizados nesta etapa. A integração real existente é evidência histórica e não assegura disponibilidade do provedor na data da apresentação. |

Para compreender como os controles citados se articulam, consulte [ARQUITETURA_IA.md](ARQUITETURA_IA.md). A visão geral, o diagrama e a relação com os ODS estão na [seção de entrega do README](../../README.md#entrega--disruptive-architectures-iot-iob--generative-ia).
