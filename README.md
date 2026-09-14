# Lambda Auth (Cognito)

Lambda em Java para autenticação de usuários com Amazon Cognito.

## O que ela faz

1. Cria usuario no cognito (`201`).
2. Consulta o usuário no Cognito (`200`).
3. Autentica usuario já cadastrado, faz login e retorna tokens (`200`).

Também retorna erros de validação (`400`) e credenciais inválidas (`401`).

## Stack

- Java 17
- AWS Lambda
- Amazon API Gateway
- Amazon Cognito
- AWS Secrets Manager
- AWS SDK for Java 2.x
- Jackson
- JUnit 5
- Mockito
- Maven

# SonarCloud Code Coverage

Deivi Motors Service
[Coverage](https://sonarcloud.io/project/overview?id=deivi-motors-lambda-auth)

## Configuração esperada

A função lê a variável de ambiente `SECRET_NAME` e busca no Secrets Manager um JSON com:

- `USER_POOL_ID`
- `CLIENT_ID`

## Estrutura do repositório

- `AuthenticationFunction/`: código Java da Lambda e testes
- `infra/`: recursos Terraform (IAM, secret, Lambda e permissões)
- `template.yaml`: template SAM disponível no projeto

## Build e testes

No diretório `AuthenticationFunction`:

- `mvn test`
- `mvn package`

## Arquitetura

A Lambda funciona como uma camada de integração entre o API Gateway e o Amazon Cognito.

```text
Cliente
   |
   v
API Gateway
   |
   v
AWS Lambda
AuthenticationFunction
   |
   +----------------------+
   |                      |
   v                      v
Cognito User Pool     Secrets Manager
```
O API Gateway recebe as requisições HTTP e encaminha os dados para a Lambda.

A Lambda:
- Identifica o endpoint solicitado.
- Carrega as configurações do Cognito através do Secrets Manager.
- Valida os dados recebidos.
- Executa operações no Cognito.
- Retorna a resposta para o API Gateway.

## Funcionalidades

A Lambda disponibiliza três operações principais:

| Método | Endpoint           | Descrição                       |
| ------ | ------------------ | ------------------------------- |
| POST   | `/customers`       | Cadastra um novo cliente        |
| GET    | `/customers/{cpf}` | Consulta um cliente pelo CPF    |
| POST   | `/authentications` | Realiza autenticação do cliente |

## Cadastro de cliente
POST /customers

Cria um novo usuário no Cognito.

Request
```json
{
"email": "cliente@email.com",
"password": "Senha@123",
"cpf": "12345678909",
"completeName": "Cliente Teste"
}
```

### Campos
| Campo          | Obrigatório | Descrição                |
| -------------- | ----------- | ------------------------ |
| `email`        | Sim         | E-mail do cliente        |
| `password`     | Sim         | Senha do cliente         |
| `cpf`          | Sim         | CPF do cliente           |
| `completeName` | Sim         | Nome completo do cliente |

O CPF deve ser enviado sem máscara.

Exemplo: 12345678909
--------- -
### Funcionamento do cadastro

A Lambda verifica primeiro se o CPF já está cadastrado no Cognito.
```text
POST /customers
|
v
Valida body
|
v
Remove máscara do CPF
|
v
AdminGetUser
|
+---- Usuário encontrado ---> 409
|
+---- Usuário não encontrado
|
v
AdminCreateUser
|
v
AdminSetUserPassword
|
v
201
```
O CPF é utilizado como username do usuário no Cognito.

Resposta de sucesso - 
HTTP 201 Created
```json
{
"message": "Usuario cadastrado com sucesso",
"email": "cliente@email.com",
"completeName": "Cliente Teste",
"cpf": "12345678909"
}
```

--- -
## Consulta de cliente
POST /customers/{cpf}

Consulta um cliente utilizando o CPF.

Exemplo
`GET /customers/12345678909`

Funcionamento:
Como o CPF é utilizado como username no Cognito, a consulta utiliza diretamente AdminGetUser.

Fluxo
```text
GET /customers/{cpf}
          |
          v
Valida CPF
          |
          v
Remove máscara
          |
          v
AdminGetUser
          |
          +---- Usuário encontrado ---> 200
          |
          +---- Usuário não encontrado ---> 404
```
Resposta de sucesso -
HTTP 200
```json
{
"email": "cliente@email.com",
"completeName": "Cliente Teste",
"cpf": "12345678909"
}
```
--- - 
## Autenticação
POST /authentications

Realiza a autenticação do cliente utilizando CPF e senha.

```json
{
  "cpf": "12345678909",
  "password": "Senha@123"
}
```

Fluxo:
```text
POST /authentications
          |
          v
Valida CPF e senha
          |
          v
Remove máscara do CPF
          |
          v
AdminGetUser
          |
          +---- Usuário não encontrado ---> 401
          |
          v
InitiateAuth
          |
          v
Cognito
          |
          v
Tokens
```

A Lambda envia para o Cognito:
```text
USERNAME = CPF
PASSWORD = senha
```

Resposta de sucesso -
HTTP 200
```json
{
  "message": "Login realizado com sucesso",
  "authentication": {
    "TokenType": "Bearer",
    "AccessToken": "access-token",
    "IdToken": "id-token",
    "RefreshToken": "refresh-token"
  }
}
```