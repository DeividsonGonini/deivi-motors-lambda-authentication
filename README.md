# Lambda Auth (Cognito)

Lambda em Java para autenticação de usuários com Amazon Cognito.

## O que ela faz

Recebe `nomeCompleto`, `cpf`, `email`, `password` e:

1. Consulta o usuário no Cognito.
2. Se não existir, cria usuário e define senha permanente (`201`).
3. Se já existir, faz login e retorna tokens (`200`).

Também retorna erros de validação (`400`) e credenciais inválidas (`401`).

## Stack

- Java 17
- AWS Lambda
- Amazon Cognito
- AWS Secrets Manager
- AWS SDK v2
- Maven
- Terraform (infra)
- SAM template (arquivo de referência no repositório)

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
