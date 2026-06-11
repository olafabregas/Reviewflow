variable "environment" { type = string }
variable "vpc_id" { type = string }
variable "app_port" { type = number }
variable "ssh_allowed_cidrs" {
  type        = list(string)
  description = "CIDR blocks allowed SSH access. Restrict to your IP: [\"YOUR.IP/32\"]"
  default     = ["0.0.0.0/0"]  # CHANGE THIS before production apply
}
