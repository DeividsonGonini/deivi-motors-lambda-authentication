variable "bucket_tfstate" {
  description = "Nome do bucket onde fica salvo os tfstates do projeto"
  type        = string
  default     = "tfstate-infra-deivi-motors"
}

variable "region" {
  description = "Região da AWS Norte da Virginia"
  type        = string
  default     = "us-east-1"
}

variable "cognito_config_lambda" {
  description = "Configurações Cognito para Lambda"
  type        = string
  default     = "cognito_auth_configuration-deivi-motors"
}