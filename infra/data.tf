data "terraform_remote_state" "cluster" {
  backend = "s3"

  config = {
    bucket = var.bucket_tfstate
    key    = "infra-terraform/terraform.tfstate"
    region = var.region
  }
}