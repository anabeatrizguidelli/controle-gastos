# Controle de Gastos

## Descrição
Aplicação web para estudantes universitários acompanharem os próprios gastos
(mesada, bolsa, estágio), com avisos quando estiverem perto do limite mensal.

## Tecnologias utilizadas
- Java 21 e Spring Boot 4 (Web MVC, Thymeleaf, Data JPA, Validation, Security)
- HTML e CSS
- JavaScript
- Banco H2 (em arquivo) no desenvolvimento e PostgreSQL (Supabase) em produção
- Maven (via Maven Wrapper — não precisa instalar)
- Docker, Render, Supabase e Brevo (detalhes em [Serviços externos](#serviços-externos))

## Integrantes
- Ana Beatriz Guidelli
- Douglas William Barbosa de Oliveira
- Gabriella Silva
- Henrique Rikelme de Lima Dias
- Tiago Alves Ayala
- Victor Hugo Machado Leal

## Requisitos

**Funcionais**
- RF1. Registrar despesa (descrição, valor, data e categoria).
- RF2. Registrar receita (descrição, valor, data e fonte).
- RF3. Listar despesas e receitas.
- RF4. Mostrar total por categoria, total do mês e saldo (receitas − despesas).
- RF5. Alertar quando os gastos de uma categoria chegarem a 80% ou 100% do limite mensal.

**Não funcionais**
- RNF1. Interface legível em tela de celular.
- RNF2. Valores monetários no formato R$ 0,00.

**Implementados além do escopo original**

Os requisitos acima são os definidos no início do projeto e continuam valendo. Durante o desenvolvimento o grupo implementou também os itens abaixo, que não estavam previstos.

Funcionais:
- RF6. Criar conta e entrar com e-mail ou nome de usuário e senha; cada pessoa só vê os próprios dados.
- RF7. Confirmar o e-mail da conta nova com um código de 6 dígitos enviado por e-mail.
- RF8. Recuperar a senha ("esqueci minha senha") com um código enviado ao e-mail cadastrado.
- RF9. Gerenciar categorias (com limite mensal opcional) e fontes de receita: criar, editar, excluir e listar, com cadastro rápido no formulário de lançamento. Contas novas já começam com categorias e fontes padrão.
- RF10. Editar e excluir despesas e receitas, com confirmação antes de excluir.
- RF11. Registrar compra parcelada (até 120 parcelas mensais), com os centavos distribuídos entre as parcelas e opção de alterar o valor só de uma parcela ou também das demais pendentes, e de excluir uma parcela ou a compra inteira.
- RF12. Registrar despesas e receitas fixas, lançadas automaticamente todo mês (até 12 meses à frente), com alteração "só este mês" ou "os próximos meses" e encerramento.
- RF13. Informar o vencimento da despesa e marcá-la como paga ou pendente.
- RF14. Mostrar o saldo do mês anterior, o saldo disponível e o saldo projetado para o fim do mês, considerando as despesas pendentes.
- RF15. Painel com gráfico de pizza por categoria, barras de gasto x limite, histórico dos últimos 6 meses e navegação entre meses.
- RF16. Sugestões de economia: onde mais gasta, comparação com o mês anterior, limites perto do fim, previsão até o fim do mês e pequenos gastos que se acumulam.
- RF17. API JSON autenticada para consultar e cadastrar despesas, receitas, categorias e fontes e marcar pagamentos.

Não funcionais:
- RNF3. Segurança: senhas com BCrypt; códigos de verificação guardados só como hash SHA-256, válidos por 15 minutos, de uso único, com até 5 tentativas e reenvio a cada 60 segundos; proteção CSRF nos formulários.
- RNF4. Modo claro e modo escuro, com a escolha salva no navegador.
- RNF5. Aplicação publicada na nuvem: Docker no Render e banco PostgreSQL no Supabase, com senhas e chaves só em variáveis de ambiente.
- RNF6. E-mail enviado pela API HTTP da Brevo (o plano gratuito do Render bloqueia SMTP); sem provedor configurado em produção, os recursos que dependem de e-mail ficam desligados.
- RNF7. Testes automatizados com banco em memória separado, sem mexer nos dados locais.
- RNF8. Proteção contra envio duplicado de formulário (clique duplo) e contra lançamento repetido das despesas e receitas fixas.

## Como executar

No Windows:

```
mvnw.cmd spring-boot:run
```

No Linux/macOS:

```
./mvnw spring-boot:run
```

Depois acesse http://localhost:8080.
O console do banco fica em http://localhost:8080/h2-console
(JDBC URL: `jdbc:h2:file:./data/controle-gastos`, usuário `sa`, sem senha).

## Funcionalidades

- **Login** (`/login`) e **criar conta** (`/cadastro`). Senhas guardadas com BCrypt.
  Todas as outras telas exigem login.
- **Painel** (`/painel`): saldo do mês anterior (sobra acumulada de todos os meses anteriores), receitas e despesas do mês e saldo disponível (saldo anterior + receitas − despesas); alertas de 80%/100% do limite;
  gráfico de pizza com a participação de cada categoria; barras de gasto x limite;
  histórico dos últimos 6 meses. As setas trocam de mês.
- **Despesas** e **Receitas**: listagem por mês, cadastro, edição e exclusão
  (com confirmação). No formulário, o botão **+ Nova** cria uma categoria ou fonte
  sem sair da tela.
- **Sugestões** (`/sugestoes`): onde a pessoa mais gasta, o que subiu ou caiu em relação ao mês anterior, limites perto do fim (com quanto pode gastar por dia), quanto conseguiu guardar, previsão até o fim do mês, pequenos gastos que se acumulam e dicas por categoria.
- **Modo escuro e modo claro**: botão no menu lateral (e no canto da tela de login). A escolha fica salva no navegador.
- **Categorias** e **Fontes de receita**: criar, editar, excluir e listar.
  Não é possível excluir uma categoria/fonte que ainda tem lançamentos.

O visual (tema escuro) foi baseado no protótipo `index.html` da disciplina.

## Endpoints da API (JSON)

A API usa o mesmo login (HTTP Basic) e só devolve os dados do usuário autenticado.

| Método | Caminho | O que faz |
|---|---|---|
| GET | `/api/despesas` | Lista as despesas |
| GET | `/api/despesas/{id}` | Uma despesa (404 se não existir ou for de outro usuário) |
| POST | `/api/despesas` | Cadastra uma despesa (201) |
| PUT | `/api/despesas/{id}/pagamento` | Marca (`{"pago": true}`) ou desmarca (`{"pago": false}`) o pagamento |
| GET | `/api/receitas` | Lista as receitas |
| GET | `/api/receitas/{id}` | Uma receita |
| GET/POST | `/api/categorias` | Lista / cria categorias |
| GET/POST | `/api/fontes` | Lista / cria fontes de receita |

Exemplo de resposta de `GET /api/despesas`:

```json
[
  {
    "categoria": { "id": 1, "limiteMensal": 400.0, "nome": "Alimentação" },
    "data": "2026-10-28",
    "descricao": "Almoço RU",
    "id": 1,
    "valor": 12.5
  }
]
```

Exemplo de cadastro pela API:

```
curl -u estudante@exemplo.com:senha123 -X POST http://localhost:8080/api/despesas -H "Content-Type: application/json" \
  -d '{"descricao":"Cinema","valor":30.0,"data":"2026-10-29","categoria":{"id":3}}'
```

## Testes

```
mvnw.cmd test
```

Os testes usam um banco em memória separado, então não mexem nos dados de `./data`.

O registro da validação funcional de 13/11/2026 (inicialização, testes automáticos e testes ponta a ponta) está em [`docs/testes/TESTES-FUNCIONAIS.md`](docs/testes/TESTES-FUNCIONAIS.md).

## Progresso

- [x] Tema, requisitos e diagramas UML
- [x] Classes de domínio `Categoria` e `Despesa`, iguais ao diagrama de classes
- [x] Controller com endpoint GET (`/api/despesas`), testado
- [x] Formulário de nova despesa e POST
- [x] Login e dados separados por usuário
- [x] Gerenciamento de categorias e fontes de receita
- [x] Receitas (RF2)
- [x] Edição e exclusão de despesas e receitas
- [x] Painel com totais por categoria, total do mês, saldo e histórico (RF4)
- [x] Alertas de 80% e 100% do limite (RF5)
- [x] Atualizar o diagrama de classes com `Usuario`, `Fonte` e as classes novas
- [x] Registrar os requisitos implementados além do escopo original (RF6 a RF17, RNF3 a RNF8)

Evidências do teste (JSON e prints) em [`docs/evidencias/`](docs/evidencias).

## Documentação

Os diagramas UML (abrir em https://app.diagrams.net) estão em [`docs/`](docs):
- `diagrama-de-classes.drawio`: diagrama de classes do projeto, atualizado com o código atual. Tem duas páginas: "Diagrama de Classes" (classes de domínio e `ControleGastos`) e "Serviços" (camada de serviços e envio de e-mail). Imagens: [`diagrama-de-classes.png`](docs/diagrama-de-classes.png) e [`diagrama-de-servicos.png`](docs/diagrama-de-servicos.png).
- `casos-de-uso-e-classes.drawio`: casos de uso e a versão detalhada das classes feita no início do projeto (proposta original).
- [`dicionario-de-dados.md`](docs/dicionario-de-dados.md): dicionário de dados das tabelas do banco, com cada campo e o que ele faz.
- [`servicos-externos.md`](docs/servicos-externos.md): serviços e tecnologias externas (GitHub, Render, Supabase, Brevo e bibliotecas).

## Serviços externos

| Serviço | Para que usamos |
|---|---|
| GitHub | Código-fonte, histórico, documentação e revisão por pull request. A branch `deploy-render` é a publicada. |
| Render | Hospeda a aplicação na internet, construída com Docker a partir do `Dockerfile`. |
| Supabase | Banco PostgreSQL de produção (região São Paulo, conexão com SSL). |
| Brevo | Envio dos e-mails com os códigos de confirmação de conta e de recuperação de senha, pela API HTTP. |
| Google Fonts | Fontes do visual (Plus Jakarta Sans e JetBrains Mono). |

Localmente nada disso é necessário: o banco é o H2 em arquivo e os e-mails aparecem no log. Senhas e chaves de produção ficam só nas variáveis de ambiente do Render. Detalhes, variáveis usadas e bibliotecas em [`docs/servicos-externos.md`](docs/servicos-externos.md).
