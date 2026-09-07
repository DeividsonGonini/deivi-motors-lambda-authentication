######## Permissões para Lambda ########
# Cria a Role IAM para a Lambda
resource "aws_iam_role" "lambda_exec_role" {
  name = "lambda-authentication-role-deivi-motors"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

#  Policy básica para logs no CloudWatch
resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  role       = aws_iam_role.lambda_exec_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

#  Policy para acessar Secrets Manager
resource "aws_iam_role_policy" "lambda_secrets_policy" {
  name = "lambda-secrets-access"
  role = aws_iam_role.lambda_exec_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret",
          "secretsmanager:ListSecrets"
        ]
        Resource = "*"
      }
    ]
  })
}

#  Policy para acessar o Cognito
resource "aws_iam_role_policy" "lambda_cognito_policy" {
  name = "lambda-cognito-access"
  role = aws_iam_role.lambda_exec_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "cognito-idp:AdminGetUser",
          "cognito-idp:ListUsers",
          "cognito-idp:AdminCreateUser",
          "cognito-idp:AdminInitiateAuth",
          "cognito-idp:AdminRespondToAuthChallenge",
          "cognito-idp:AdminSetUserPassword",
          "cognito-idp:DescribeUserPool",
          "cognito-idp:ListUserPools",
          "cognito-idp:ListUserPoolClients"
        ]
        Resource = "*"
      }
    ]
  })
}


# Lambda Function
resource "aws_lambda_function" "deivi-motors-authentication" {
  function_name = "AuthenticationFunctionDeiviMotors"
  handler       = "authentication.App::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 60
  architectures = ["x86_64"]

  # Usando o JAR gerado no Maven (precisa empacotar antes de rodar o Terraform)
  filename         = "../AuthenticationFunction/target/AuthenticationFunction-1.0-SNAPSHOT.jar"
  source_code_hash = filebase64sha256("../AuthenticationFunction/target/AuthenticationFunction-1.0-SNAPSHOT.jar")


  role          = aws_iam_role.lambda_exec_role.arn
  # Role já existente no AWS Academy (não cria nova role)
  # role = "arn:aws:iam::891377152273:role/LabRole"

  environment {
    variables = {
      SECRET_NAME = aws_secretsmanager_secret.cognito_auth_configuration.name
    }
  }
}

# Permissão para API Gateway invocar a Lambda
resource "aws_lambda_permission" "apigw" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.deivi-motors-authentication.function_name
  principal     = "apigateway.amazonaws.com"
}
