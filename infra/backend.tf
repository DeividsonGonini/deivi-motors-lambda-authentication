terraform {
  backend "s3" {
    bucket = var.bucket_tfstate #Nome do bucket
    key    = "infra-lambda-authentication/terraform.tfstate" #Caminho onde o tfstate será salvo
    region = var.region
  }
}
