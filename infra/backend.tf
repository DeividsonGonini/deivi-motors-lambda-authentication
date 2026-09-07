terraform {
  backend "s3" {
    bucket = "tfstate-infra-deivi-motors" #Nome do bucket
    key    = "infra-lambda-authentication/terraform.tfstate" #Caminho onde o tfstate será salvo
    region = "us-east-1"
  }
}
