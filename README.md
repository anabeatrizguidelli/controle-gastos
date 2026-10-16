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

## Documentação

Os diagramas UML (abrir em https://app.diagrams.net) estão em [`docs/`](docs):
- `diagrama-de-classes.drawio`: diagrama de classes do projeto.
- `casos-de-uso-e-classes.drawio`: casos de uso e uma versão detalhada das classes.
