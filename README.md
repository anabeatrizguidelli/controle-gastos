# Controle de Gastos

Aplicação web para estudantes universitários acompanharem os próprios gastos
(mesada, bolsa, estágio), com avisos quando estiverem perto do limite mensal.

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

## Tecnologias

- Java 21+
- Spring Boot 4 (Web MVC, Thymeleaf, Data JPA, Validation, Security, DevTools)
- Banco H2 (em arquivo)
- Maven (via Maven Wrapper — não precisa instalar)

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
- [ ] Atualizar o diagrama de classes com `Usuario` e `Fonte`

Evidências do teste (JSON e prints) em [`docs/evidencias/`](docs/evidencias).

## Documentação

Os diagramas UML (abrir em https://app.diagrams.net) estão em [`docs/`](docs):
- `diagrama-de-classes.drawio`: diagrama de classes do projeto.
- `casos-de-uso-e-classes.drawio`: casos de uso e uma versão detalhada das classes.
