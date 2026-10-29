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
- Spring Boot 4 (Web MVC, Thymeleaf, Data JPA, Validation, DevTools)
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

## Telas e endpoints

| Método | Caminho | O que faz |
|---|---|---|
| GET | `/despesas` | Página com a lista de despesas (valores em R$ 0,00) |
| GET | `/despesas/nova` | Formulário de nova despesa |
| POST | `/despesas` | Recebe o formulário, valida e salva |
| GET | `/api/despesas` | Lista as despesas em JSON |
| GET | `/api/despesas/{id}` | Uma despesa em JSON (404 se não existir) |
| POST | `/api/despesas` | Cadastra uma despesa enviada em JSON (201) |
| GET | `/api/categorias` | Lista as categorias em JSON |

Na primeira execução o sistema cadastra as categorias Alimentação, Transporte e Lazer
e duas despesas de exemplo.

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
curl -X POST http://localhost:8080/api/despesas -H "Content-Type: application/json" \
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
- [ ] Receitas (RF2)
- [ ] Totais por categoria, total do mês e saldo (RF4)
- [ ] Alertas de 80% e 100% do limite (RF5)

Evidências do teste (JSON e prints) em [`docs/evidencias/`](docs/evidencias).

## Documentação

Os diagramas UML (abrir em https://app.diagrams.net) estão em [`docs/`](docs):
- `diagrama-de-classes.drawio`: diagrama de classes do projeto.
- `casos-de-uso-e-classes.drawio`: casos de uso e uma versão detalhada das classes.
