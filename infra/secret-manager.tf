data "aws_caller_identity" "current" {}

resource "aws_secretsmanager_secret" "cognito_auth_configuration" {
  name        = "cognito_auth_configuration-deivi-motors-3"
  description = "Configurações Cognito para Lambda"
}

resource "aws_secretsmanager_secret_version" "cognito_auth_configuration_value" {
  secret_id = aws_secretsmanager_secret.cognito_auth_configuration.id
  secret_string = jsonencode({
    USER_POOL_ID     = data.terraform_remote_state.cluster.outputs.cognito_userpool_id
    CLIENT_ID        = data.terraform_remote_state.cluster.outputs.cognito_client_id
  })
}
